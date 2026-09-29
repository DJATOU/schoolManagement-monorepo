package com.school.management.service.payment;

import com.school.management.persistance.PaymentCarryOverEntity;
import com.school.management.persistance.PaymentIdempotencyEntity;
import com.school.management.repository.PaymentCarryOverRepository;
import com.school.management.repository.PaymentIdempotencyRepository;
import com.school.management.service.exception.CustomServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Distingue le rejeu d'un encaissement d'un second encaissement réel.
 *
 * <p><b>Pourquoi ce service existe.</b> Un double clic sur « Encaisser » et un second versement
 * du même montant le même jour produisent une requête rigoureusement identique. Accepter les
 * deux inscrit au registre de l'argent qui n'est jamais entré en caisse ; refuser les deux perd
 * un versement réel. Aucune donnée de la requête ne les sépare, seule l'intention le fait : le
 * client engendre une clé à l'ouverture du formulaire, si bien qu'un rejeu porte la même clé et
 * qu'un nouvel encaissement en porte une neuve. Le serveur n'a plus rien à deviner, et aucun
 * seuil de temps n'entre en jeu — un délai n'aurait déplacé l'arbitraire que vers la vitesse de
 * frappe de la caissière.</p>
 *
 * <p><b>Sans clé, le comportement d'avant est conservé.</b> La clé est facultative. Un appelant
 * qui n'en fournit pas obtient exactement l'ancien comportement, chaque requête étant un
 * encaissement. Rendre la clé obligatoire aurait cassé tous les clients existants d'un seul coup,
 * et un défaut engendré côté serveur n'aurait rien distingué : deux requêtes auraient reçu deux
 * clés, donc deux versements, ce que ce service existe pour éviter.</p>
 */
@Service
public class PaymentIdempotencyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentIdempotencyService.class);

    /** Longueur maximale acceptée, alignée sur la colonne. */
    private static final int MAX_KEY_LENGTH = 100;

    private final PaymentIdempotencyRepository idempotencyRepository;
    private final PaymentCarryOverRepository carryOverRepository;

    public PaymentIdempotencyService(PaymentIdempotencyRepository idempotencyRepository,
                                     PaymentCarryOverRepository carryOverRepository) {
        this.idempotencyRepository = idempotencyRepository;
        this.carryOverRepository = carryOverRepository;
    }

    /**
     * Valide la forme d'une clé fournie par le client.
     *
     * <p>Une clé vide n'est pas une absence de clé : elle traduit un client qui a cru en envoyer
     * une. La refuser vaut mieux que la traiter comme une absence, ce qui rétablirait
     * silencieusement le double encaissement que l'appelant cherchait à éviter.</p>
     *
     * @param idempotencyKey la clé brute, éventuellement nulle
     * @return la clé nettoyée, ou {@code null} si aucune clé n'a été fournie
     * @throws CustomServiceException 400 si la clé est vide ou trop longue
     */
    public String normalizeKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        String trimmed = idempotencyKey.trim();
        if (trimmed.isEmpty()) {
            throw new CustomServiceException(
                    "Clé d'idempotence vide : fournir une clé ou ne pas envoyer l'en-tête.",
                    HttpStatus.BAD_REQUEST);
        }
        if (trimmed.length() > MAX_KEY_LENGTH) {
            throw new CustomServiceException(
                    "Clé d'idempotence trop longue : " + trimmed.length() + " caractères pour un "
                            + "maximum de " + MAX_KEY_LENGTH + ".",
                    HttpStatus.BAD_REQUEST);
        }
        return trimmed;
    }

    /**
     * Retrouve le résultat d'un encaissement déjà traité sous cette clé.
     *
     * <p>L'empreinte de la requête est confrontée à celle conservée. Une clé réutilisée pour un
     * versement différent est un défaut du client, jamais un rejeu : la signaler vaut mieux que
     * renvoyer le résultat d'un autre encaissement, qui produirait un reçu portant un montant que
     * personne n'a versé.</p>
     *
     * @param key      la clé normalisée, ou {@code null} si l'appelant n'en fournit pas
     * @param studentId l'étudiant de la requête courante
     * @param groupId   le groupe de la requête courante
     * @param seriesId  la série visée par la requête courante
     * @param amount    le montant de la requête courante, échelle monétaire
     * @return le résultat du traitement original si la clé a déjà servi, vide sinon
     * @throws CustomServiceException 409 si la clé a servi pour un encaissement différent
     */
    public Optional<PaymentAllocationResult> findReplay(String key, Long studentId, Long groupId,
                                                        Long seriesId, BigDecimal amount) {
        if (key == null) {
            return Optional.empty();
        }

        Optional<PaymentIdempotencyEntity> existing = idempotencyRepository.findByIdempotencyKey(key);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        PaymentIdempotencyEntity record = existing.get();
        assertSameRequest(record, key, studentId, groupId, seriesId, amount);

        LOGGER.info("Rejeu détecté sur la clé {} : aucun second versement créé, résultat du "
                + "traitement original restitué.", key);

        return Optional.of(rebuild(record));
    }

    /**
     * Conserve l'empreinte d'un encaissement qui vient d'être traité.
     *
     * <p>L'écriture prend place dans la transaction de l'encaissement : si la ventilation échoue,
     * l'empreinte disparaît avec elle et une nouvelle tentative reste possible. Une empreinte
     * survivant à un échec bloquerait la reprise d'un versement qui n'a jamais abouti.</p>
     *
     * @param key    la clé normalisée, ou {@code null} pour ne rien conserver
     * @param result le résultat du traitement
     * @param originPaymentDate l'horodatage d'encaissement retenu par le serveur
     */
    public void remember(String key, PaymentAllocationResult result, java.util.Date originPaymentDate) {
        if (key == null) {
            return;
        }
        idempotencyRepository.save(PaymentIdempotencyEntity.builder()
                .idempotencyKey(key)
                .studentId(result.studentId())
                .groupId(result.groupId())
                .sessionSeriesId(result.seriesId())
                .amountReceived(result.amountReceived())
                .amountAllocated(result.amountAllocated())
                .payment(result.payment())
                .originPaymentDate(originPaymentDate)
                .build());
    }

    // ------------------------------------------------------------------
    // Interne
    // ------------------------------------------------------------------

    private void assertSameRequest(PaymentIdempotencyEntity record, String key, Long studentId,
                                   Long groupId, Long seriesId, BigDecimal amount) {
        boolean sameRequest = record.getStudentId().equals(studentId)
                && record.getGroupId().equals(groupId)
                && record.getSessionSeriesId().equals(seriesId)
                && record.getAmountReceived().compareTo(amount) == 0;

        if (!sameRequest) {
            throw new CustomServiceException(
                    "La clé d'idempotence " + key + " a déjà servi pour un encaissement différent "
                            + "(étudiant " + record.getStudentId() + ", série "
                            + record.getSessionSeriesId() + ", "
                            + record.getAmountReceived().toPlainString() + " DA). Utiliser une clé "
                            + "neuve pour un nouveau versement.",
                    HttpStatus.CONFLICT);
        }
    }

    /**
     * Reconstitue le résultat original, reports compris, depuis la table qui fait foi.
     *
     * <p>Les reports sont relus plutôt que recopiés : une copie pourrait diverger de
     * {@code payment_carry_over}, que consultent les relevés et l'historique.</p>
     */
    private PaymentAllocationResult rebuild(PaymentIdempotencyEntity record) {
        List<PaymentCarryOverEntity> carryOvers = carryOverRepository
                .findByStudentIdAndSourceSeriesIdAndOriginPaymentDateAndActiveTrueOrderByIdAsc(
                        record.getStudentId(), record.getSessionSeriesId(),
                        record.getOriginPaymentDate());

        List<PaymentAllocationResult.CarriedOverAmount> restored = carryOvers.stream()
                .map(carryOver -> new PaymentAllocationResult.CarriedOverAmount(
                        carryOver.getTargetSeries().getId(),
                        carryOver.getTargetSeries().getName(),
                        carryOver.getAmount()))
                .toList();

        return new PaymentAllocationResult(
                record.getStudentId(),
                record.getGroupId(),
                record.getSessionSeriesId(),
                record.getAmountReceived(),
                record.getAmountAllocated(),
                restored,
                record.getPayment());
    }
}
