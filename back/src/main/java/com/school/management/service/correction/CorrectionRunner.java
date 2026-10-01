package com.school.management.service.correction;

import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.repository.StudentRepository;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.payment.PaymentCostResolver;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Exécute une correction en Aperçu ou en confirmation, par le même code (spec admin-corrections,
 * exigences 4.1 à 4.4, D7).
 *
 * <h2>L'Aperçu est la correction exécutée puis annulée</h2>
 * <ol>
 *   <li>photographier les montants des Séries de la portée ;</li>
 *   <li>exécuter la correction ;</li>
 *   <li>photographier de nouveau les mêmes Séries ;</li>
 *   <li>construire l'Aperçu — Séries dont un montant change, effets — et son empreinte SHA-256 ;</li>
 *   <li>écrire les traces de la correction, avec son effet mesuré sur les montants (B.2) ;</li>
 *   <li>Aperçu : annuler la transaction. Confirmation : valider si l'empreinte est celle de l'Aperçu
 *       lu, sinon annuler et renvoyer le nouvel Aperçu (409).</li>
 * </ol>
 * Aucune règle n'est codée deux fois : l'Aperçu ne peut pas annoncer autre chose que ce que la
 * confirmation écrit. Le jeton n'est pas stocké ; l'Aperçu recalculé fait preuve. Un numéro de reçu
 * pris pendant un Aperçu est rendu avec la transaction : la confirmation reprend la séquence.
 *
 * <h2>Sa propre transaction, toujours</h2>
 * Le runner refuse d'être appelé dans une transaction ouverte : annuler un Aperçu annulerait aussi
 * le travail de l'appelant, et une confirmation pourrait encore être annulée par lui après avoir
 * été déclarée conforme.
 */
@Service
public class CorrectionRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(CorrectionRunner.class);

    /** Version du format canonique : la changer invalide les jetons en circulation, en le disant. */
    private static final String CANONICAL_VERSION = "correction-preview-v1";

    private final TransactionTemplate transactions;
    private final PaymentCostResolver costResolver;
    private final SessionSeriesRepository seriesRepository;
    private final StudentRepository studentRepository;
    private final CorrectionAuditService auditService;

    @PersistenceContext
    private EntityManager entityManager;

    public CorrectionRunner(PlatformTransactionManager transactionManager,
                            PaymentCostResolver costResolver,
                            SessionSeriesRepository seriesRepository,
                            StudentRepository studentRepository,
                            CorrectionAuditService auditService) {
        this.transactions = new TransactionTemplate(transactionManager);
        this.costResolver = costResolver;
        this.seriesRepository = seriesRepository;
        this.studentRepository = studentRepository;
        this.auditService = auditService;
    }

    /**
     * Exécute la correction.
     *
     * @param command      la correction
     * @param mode         Aperçu ou confirmation
     * @param previewToken jeton de l'Aperçu lu, obligatoire en confirmation, ignoré en Aperçu
     * @return l'Aperçu, son jeton et, en confirmation, le résultat
     * @throws CustomServiceException 400 si le jeton manque en confirmation ; le refus métier de la
     *                                correction, tel quel
     * @throws StalePreviewException  409 si les données ont changé depuis l'Aperçu
     * @throws IllegalStateException  si une transaction est déjà ouverte, ou si la correction touche
     *                                une Série hors de sa portée
     */
    public <T> CorrectionOutcome<T> run(CorrectionCommand<T> command, CorrectionMode mode, String previewToken) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(mode, "mode");
        if (mode == CorrectionMode.CONFIRM && (previewToken == null || previewToken.isBlank())) {
            throw new CustomServiceException(
                    "Confirmation sans aperçu : prévisualisez la correction avant de la confirmer.",
                    HttpStatus.BAD_REQUEST);
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Une correction ouvre sa propre transaction : elle ne peut pas être appelée dans une autre.");
        }

        return transactions.execute(status -> {
            Set<SeriesKey> scope = resolve(command.scope());
            Map<SeriesKey, AmountSnapshot> before = snapshot(scope);

            CorrectionExecution<T> execution = Objects.requireNonNull(command.execute(), "execution");
            Set<SeriesKey> outside = new TreeSet<>(execution.touched());
            outside.removeAll(scope);
            if (!outside.isEmpty()) {
                throw new IllegalStateException("Correction « " + command.fingerprint()
                        + " » : Séries touchées hors de la portée déclarée " + outside
                        + ". Leur état antérieur n'a pas été photographié, l'Aperçu les tairait.");
            }

            // Les montants « après » sont relus en base : tout ce que la correction a écrit doit y être.
            entityManager.flush();
            CorrectionPreview preview = CorrectionPreview.of(changes(before, snapshot(scope)), execution.effects());
            writeAudits(command, execution, preview);
            String token = fingerprint(command, preview);

            if (mode == CorrectionMode.PREVIEW) {
                status.setRollbackOnly();
                LOGGER.debug("Aperçu de la correction « {} » : {} série(s) changée(s)",
                        command.fingerprint(), preview.series().size());
                return new CorrectionOutcome<>(mode, preview, token, null);
            }
            if (!MessageDigest.isEqual(token.getBytes(StandardCharsets.US_ASCII),
                    previewToken.strip().getBytes(StandardCharsets.US_ASCII))) {
                LOGGER.info("Confirmation de la correction « {} » refusée : aperçu périmé", command.fingerprint());
                throw new StalePreviewException(preview, token);
            }
            LOGGER.info("Correction « {} » confirmée : {} série(s) changée(s)",
                    command.fingerprint(), preview.series().size());
            return new CorrectionOutcome<>(mode, preview, token, execution.result());
        });
    }

    // ------------------------------------------------------------------
    // Traces
    // ------------------------------------------------------------------

    /**
     * Écrit les traces de la correction, avec son effet mesuré sur les montants (exigences 11.3,
     * 11.5, 12.3).
     *
     * <p>Écrites dans les deux modes, avant la comparaison du jeton : l'Aperçu exécute ainsi tout ce
     * que la confirmation exécutera, refus de la trace compris, et un Aperçu périmé n'en laisse
     * aucune. Une correction sans trace n'a rien changé, et elle est refusée (11.3) ; si elle a
     * pourtant changé un montant ou produit un effet, c'est une erreur de programmation.</p>
     */
    private void writeAudits(CorrectionCommand<?> command, CorrectionExecution<?> execution,
                             CorrectionPreview preview) {
        if (execution.audits().isEmpty()) {
            if (!preview.amountsUnchanged() || !preview.effects().isEmpty()) {
                throw new IllegalStateException("Correction « " + command.fingerprint()
                        + " » sans trace, alors qu'elle change des montants ou produit des effets.");
            }
            throw new CustomServiceException(
                    "Correction sans changement effectif : rien à enregistrer.", HttpStatus.BAD_REQUEST);
        }
        for (AuditDraft draft : execution.audits()) {
            auditService.record(draft, preview.series());
        }
    }

    // ------------------------------------------------------------------
    // Photographies
    // ------------------------------------------------------------------

    /** Développe les groupes de la portée en Séries, dans l'ordre de l'Aperçu. */
    private Set<SeriesKey> resolve(CorrectionScope scope) {
        Set<SeriesKey> keys = new TreeSet<>(scope.series());
        for (CorrectionScope.StudentGroup group : scope.groups()) {
            for (SessionSeriesEntity series : seriesRepository.findByGroupId(group.groupId())) {
                keys.add(new SeriesKey(group.studentId(), series.getId()));
            }
        }
        return keys;
    }

    private Map<SeriesKey, AmountSnapshot> snapshot(Set<SeriesKey> keys) {
        Map<SeriesKey, AmountSnapshot> snapshots = new TreeMap<>();
        for (SeriesKey key : keys) {
            snapshots.put(key, AmountSnapshot.of(costResolver.resolve(key.studentId(), key.seriesId())));
        }
        return snapshots;
    }

    /** Séries dont un montant ou le statut a changé, nommées pour l'écran. */
    private List<SeriesAmountChange> changes(Map<SeriesKey, AmountSnapshot> before,
                                             Map<SeriesKey, AmountSnapshot> after) {
        List<SeriesAmountChange> changes = new ArrayList<>();
        for (Map.Entry<SeriesKey, AmountSnapshot> entry : before.entrySet()) {
            SeriesKey key = entry.getKey();
            AmountSnapshot then = entry.getValue();
            AmountSnapshot now = after.get(key);
            if (then.equals(now)) {
                continue;
            }
            SessionSeriesEntity series = seriesRepository.findById(key.seriesId()).orElseThrow();
            changes.add(new SeriesAmountChange(key.studentId(), studentName(key.studentId()), key.seriesId(),
                    series.getName(), series.getGroup() == null ? null : series.getGroup().getName(),
                    then, now));
        }
        return changes;
    }

    /** Prénom et nom ; l'identifiant si l'étudiant n'existe pas, sans quoi l'Aperçu échouerait. */
    private String studentName(Long studentId) {
        return studentRepository.findById(studentId)
                .map(CorrectionRunner::fullName)
                .orElse("Étudiant " + studentId);
    }

    private static String fullName(StudentEntity student) {
        return Stream.of(student.getFirstName(), student.getLastName())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "))
                .strip();
    }

    // ------------------------------------------------------------------
    // Empreinte
    // ------------------------------------------------------------------

    /**
     * Empreinte SHA-256 de la commande et de son Aperçu, en hexadécimal.
     *
     * <p>Chaque champ textuel est préfixé de sa longueur : aucune valeur, si exotique soit-elle, ne
     * peut se faire passer pour la frontière entre deux champs.</p>
     */
    static String fingerprint(CorrectionCommand<?> command, CorrectionPreview preview) {
        StringBuilder canonical = new StringBuilder(CANONICAL_VERSION).append('\n');
        field(canonical, Objects.requireNonNull(command.fingerprint(), "fingerprint")).append('\n');
        for (SeriesAmountChange change : preview.series()) {
            canonical.append("S|").append(change.studentId()).append('|').append(change.seriesId()).append('|')
                    .append(change.before().canonical()).append('|').append(change.after().canonical()).append('\n');
        }
        for (CorrectionEffect effect : preview.effects()) {
            canonical.append("E|").append(effect.type()).append('|');
            field(canonical, effect.description()).append('\n');
        }
        return Sha256.hex(canonical.toString());
    }

    private static StringBuilder field(StringBuilder canonical, String value) {
        return canonical.append(value.length()).append(':').append(value);
    }
}
