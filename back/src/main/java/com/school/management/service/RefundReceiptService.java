package com.school.management.service;

import com.school.management.dto.RefundReceiptDTO;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.RefundEntity;
import com.school.management.persistance.RefundReceiptIssuanceEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.RefundReceiptIssuanceRepository;
import com.school.management.repository.RefundRepository;
import com.school.management.service.exception.CustomServiceException;
import org.springframework.data.domain.AuditorAware;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Produit les données du reçu d'un remboursement et enregistre chaque émission (exigence 8).
 *
 * <h2>Pourquoi c'est une écriture et non une lecture</h2>
 * Le rang du duplicata exige de compter les productions. Une méthode de lecture ne pourrait pas
 * signaler qu'un reçu a déjà été imprimé, or c'est le point de contrôle qui compte : un reçu de
 * caisse réimprimé sans mention peut servir deux fois auprès d'une même famille.
 *
 * <h2>Pourquoi les replis sont résolus ici</h2>
 * « Hors série », « Hors groupe » et « Administrateur non identifié » sont décidés côté serveur.
 * L'exigence 8.6 impose que deux productions du même reçu affichent des valeurs identiques
 * caractère pour caractère ; laisser ces choix au client rendrait la stabilité dépendante de son
 * code, alors qu'il s'agit d'une pièce comptable.
 *
 * <h2>Ce que ce service ne fait pas</h2>
 * Il n'assemble aucun PDF. Le rendu reste côté client, avec pdfmake, comme pour le reçu de
 * versement : les deux documents doivent se ressembler pour être distingués par ce qui compte — le
 * titre, le sens de l'opération, le libellé du montant — et deux moteurs de rendu produiraient deux
 * mises en page divergentes.
 */
@Service
public class RefundReceiptService {

    /** Repli lorsque le versement n'est rattaché à aucune série (exigence 8.3). */
    private static final String NO_SERIES = "Hors série";

    /** Repli lorsque le versement n'est rattaché à aucun groupe (exigence 8.12). */
    private static final String NO_GROUP = "Hors groupe";

    /** Repli lorsque l'auteur de l'enregistrement n'est pas identifiable (exigence 8.11). */
    private static final String UNKNOWN_ADMIN = "Administrateur non identifié";

    /** Identifiant technique d'audit, qui ne désigne aucune personne. */
    private static final String SYSTEM_AUDITOR = "system";

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    /** Longueur maximale du nom de fichier proposé. */
    private static final int MAX_FILE_NAME_LENGTH = 150;

    private final RefundRepository refundRepository;
    private final RefundReceiptIssuanceRepository issuanceRepository;
    private final AuditorAware<String> auditorAware;

    public RefundReceiptService(RefundRepository refundRepository,
                               RefundReceiptIssuanceRepository issuanceRepository,
                               AuditorAware<String> auditorAware) {
        this.refundRepository = refundRepository;
        this.issuanceRepository = issuanceRepository;
        this.auditorAware = auditorAware;
    }

    /**
     * Enregistre une émission de reçu et retourne les données du document.
     *
     * @param refundId identifiant du remboursement
     * @return les données du reçu, avec son rang d'émission
     * @throws CustomServiceException 404 si le remboursement est introuvable ou inactif
     *         (exigence 8.7). Aucune donnée partielle n'est restituée : un reçu incomplet
     *         attesterait d'une remise d'argent sans pouvoir l'identifier
     */
    @Transactional
    public RefundReceiptDTO issue(Long refundId) {
        RefundEntity refund = refundRepository.findById(refundId)
                .filter(RefundEntity::isActive)
                .orElseThrow(() -> new CustomServiceException(
                        "Remboursement introuvable ou inactif pour l'identifiant : " + refundId,
                        HttpStatus.NOT_FOUND));

        int rank = issuanceRepository.findMaxRank(refundId) + 1;
        LocalDateTime issuedAt = LocalDateTime.now();
        String issuedBy = currentAuditor();

        issuanceRepository.save(RefundReceiptIssuanceEntity.builder()
                .refund(refund)
                .rank(rank)
                .issuedAt(issuedAt)
                .issuedBy(issuedBy)
                .build());

        return buildReceipt(refund, rank, issuedAt);
    }

    private RefundReceiptDTO buildReceipt(RefundEntity refund, int rank, LocalDateTime issuedAt) {
        StudentEntity student = refund.getStudent();
        PaymentEntity payment = refund.getPayment();

        String firstName = student != null ? nullToEmpty(student.getFirstName()) : "";
        String lastName = student != null ? nullToEmpty(student.getLastName()) : "";

        return new RefundReceiptDTO(
                refund.getId(),
                refund.getRefundNumber(),
                refund.getRefundDate(),
                money(refund.getAmount()),
                refund.getReason(),
                firstName,
                lastName,
                payment != null ? payment.getPaymentDate() : null,
                payment != null ? money(payment.getAmountPaid()) : money(BigDecimal.ZERO),
                groupName(payment),
                seriesName(payment),
                recordedBy(refund),
                rank,
                issuedAt,
                fileName(refund, firstName, lastName));
    }

    /** Nom du groupe du versement, ou repli explicite plutôt qu'une case vide. */
    private String groupName(PaymentEntity payment) {
        GroupEntity group = payment != null ? payment.getGroup() : null;
        if (group == null || isBlank(group.getName())) {
            return NO_GROUP;
        }
        return group.getName();
    }

    /** Nom de la série du versement, ou repli explicite. */
    private String seriesName(PaymentEntity payment) {
        SessionSeriesEntity series = payment != null ? payment.getSessionSeries() : null;
        if (series == null || isBlank(series.getName())) {
            return NO_SERIES;
        }
        return series.getName();
    }

    /**
     * Administrateur ayant enregistré le remboursement, issu de l'audit JPA.
     *
     * <p>L'identifiant technique {@code system} est remplacé par une mention lisible : imprimer
     * « system » sur un reçu remis à une famille laisserait croire à un nom d'agent.</p>
     */
    private String recordedBy(RefundEntity refund) {
        String createdBy = refund.getCreatedBy();
        if (isBlank(createdBy) || SYSTEM_AUDITOR.equals(createdBy)) {
            return UNKNOWN_ADMIN;
        }
        return createdBy;
    }

    /**
     * Nom de fichier proposé, dérivé du numéro de pièce et du nom de l'étudiant (exigence 8.8).
     *
     * <p>Identique à chaque production du reçu d'un même remboursement : un duplicata doit se
     * classer à côté de l'original, pas sous un autre nom. Les caractères hors lettres, chiffres,
     * tiret et soulignement sont retirés — un nom d'étudiant accentué ou ponctué produirait sinon un
     * fichier que certains systèmes refusent.</p>
     */
    private String fileName(RefundEntity refund, String firstName, String lastName) {
        // Les soulignements de bord sont retirés avant le test de vacuité : sans cela, un nom
        // entièrement non translittérable produirait « _ », qui n'est pas vide mais ne nomme rien.
        String student = (firstName + "_" + lastName).trim();
        String slug = java.text.Normalizer.normalize(student, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("\\s+", "_")
                .replaceAll("[^A-Za-z0-9_-]", "")
                .replaceAll("^_+|_+$", "");
        if (slug.isBlank()) {
            slug = "etudiant";
        }
        String base = nullToEmpty(refund.getRefundNumber()) + "_" + slug;
        if (base.length() > MAX_FILE_NAME_LENGTH) {
            base = base.substring(0, MAX_FILE_NAME_LENGTH);
        }
        return base.toLowerCase(Locale.ROOT) + ".pdf";
    }

    private String currentAuditor() {
        return auditorAware.getCurrentAuditor().orElse(SYSTEM_AUDITOR);
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private BigDecimal money(Double value) {
        BigDecimal result = value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value);
        return result.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
