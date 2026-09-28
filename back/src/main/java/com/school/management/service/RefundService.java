package com.school.management.service;

import com.school.management.dto.RefundCapDTO;
import com.school.management.dto.RefundRequestDTO;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.RefundEntity;
import com.school.management.repository.PaymentRepository;
import com.school.management.repository.RefundRepository;
import com.school.management.service.exception.CustomServiceException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Calendar;
import java.util.Date;
import java.util.Objects;

/**
 * Service de gestion des remboursements (refunds).
 *
 * <h2>Plafond cumulé, et non plafond par demande</h2>
 * La règle est qu'un remboursement ne peut pas dépasser ce que le versement a rapporté. Le contrôle
 * d'origine comparait chaque demande au seul montant versé, sans déduire les remboursements déjà
 * accordés : deux remboursements du montant total d'un même versement étaient donc tous deux
 * acceptés, et la caisse sortait deux fois l'argent entré une fois. Le plafond est désormais
 * <strong>cumulé par paiement</strong> (exigence 7.1) : montant versé diminué de la somme des
 * remboursements actifs.
 *
 * <h2>Ordre des contrôles, qui n'est pas indifférent</h2>
 * Le montant est arrondi puis validé <strong>avant</strong> l'évaluation du plafond (exigence 7.5).
 * L'inverse produirait un message trompeur : une saisie à 0 € recevrait un reproche sur le plafond
 * alors que le problème est le montant. Nommer la vraie cause épargne un aller-retour à
 * l'administrateur.
 *
 * <h2>Ce que le remboursement ne touche pas</h2>
 * Ni le montant versé du paiement, ni son imputation, ni le statut de paiement de la série, ni le
 * report d'excédent (exigence 7.13). La sortie de caisse est portée par le seul remboursement, ce
 * qui laisse le devis et le statut de retard inchangés : un remboursement est un mouvement de
 * caisse, pas une révision de ce que l'étudiant devait.
 */
@Service
public class RefundService {

    /** Échelle monétaire (2 décimales) et politique d'arrondi (HALF_UP). */
    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    /** Montant minimal encaissable : un remboursement de 0 € produirait une pièce sans objet. */
    private static final BigDecimal MIN_AMOUNT = new BigDecimal("0.01");

    /** Borne haute, alignée sur la précision de la colonne {@code numeric(12,2)}. */
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999.99");

    /** Longueur maximale du motif. */
    private static final int MAX_REASON_LENGTH = 500;

    /** Tentatives d'attribution du numéro de pièce en cas de collision (exigence 6.14). */
    private static final int MAX_NUMBER_ATTEMPTS = 3;

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final RefundNumberService refundNumberService;

    public RefundService(RefundRepository refundRepository,
                         PaymentRepository paymentRepository,
                         RefundNumberService refundNumberService) {
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
        this.refundNumberService = refundNumberService;
    }

    /**
     * Crée un remboursement après validation du montant, du motif et du plafond cumulé.
     *
     * @param dto données de création
     * @return le remboursement persisté, porteur de son numéro de pièce
     * @throws CustomServiceException 404 si le paiement rattaché est introuvable (exigence 7.6)
     * @throws CustomServiceException 400 si le montant est hors bornes (7.4), le motif absent ou
     *         trop long (6.2, 6.3), le plafond dépassé (7.3), ou le paiement sans étudiant (7.14)
     * @throws CustomServiceException 409 si le numéro de pièce n'a pu être attribué (6.14)
     */
    @Transactional
    public RefundEntity create(RefundRequestDTO dto) {
        Objects.requireNonNull(dto, "La requête de remboursement ne doit pas être nulle.");

        // 1) Montant : arrondi puis validé AVANT toute évaluation du plafond (exigence 7.5).
        BigDecimal amount = normalizedAmount(dto.amount());

        // 2) Motif : une sortie de caisse sans raison enregistrée n'est pas justifiable (6.1, 6.2).
        String reason = validatedReason(dto.reason());

        // 3) Paiement, chargé en verrou d'écriture pour sérialiser les demandes concurrentes
        //    portant sur le même paiement (exigence 7.8).
        PaymentEntity payment = paymentRepository.findByIdForUpdate(dto.paymentId())
                .orElseThrow(() -> new CustomServiceException(
                        "Paiement introuvable pour l'identifiant : " + dto.paymentId(),
                        HttpStatus.NOT_FOUND));

        // 4) Bénéficiaire : toujours celui du paiement (exigence 7.11). Sans étudiant identifiable,
        //    le reçu ne pourrait nommer personne, donc le remboursement est refusé (7.14).
        if (payment.getStudent() == null) {
            throw new CustomServiceException(
                    "Le paiement rattaché ne référence aucun étudiant : remboursement impossible, "
                            + "le reçu ne pourrait désigner aucun bénéficiaire.",
                    HttpStatus.BAD_REQUEST);
        }

        // 5) Plafond cumulé.
        BigDecimal amountPaid = toMoney(payment.getAmountPaid());
        BigDecimal alreadyRefunded = toMoney(refundRepository.sumActiveRefundsForPayment(payment.getId()));
        BigDecimal cap = amountPaid.subtract(alreadyRefunded);

        if (amount.compareTo(cap) > 0) {
            // Les trois montants sont nommés : l'administrateur a une famille devant lui, et
            // « montant trop élevé » l'obligerait à aller chercher l'information ailleurs.
            throw new CustomServiceException(String.format(
                    "Remboursement impossible : montant demandé %s €, mais le versement a rapporté "
                            + "%s € dont %s € déjà remboursé(s). Plafond restant : %s €.",
                    amount, amountPaid, alreadyRefunded, cap),
                    HttpStatus.BAD_REQUEST);
        }

        // 6) Enregistrement, avec attribution du numéro de pièce dans la même transaction.
        return saveWithNumber(payment, amount, reason, dto.refundDate());
    }

    /**
     * Montant versé, somme déjà remboursée et plafond restant d'un paiement (exigence 7.10).
     *
     * @throws CustomServiceException 404 si le paiement est introuvable (exigence 7.6)
     */
    @Transactional(readOnly = true)
    public RefundCapDTO cap(Long paymentId) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CustomServiceException(
                        "Paiement introuvable pour l'identifiant : " + paymentId,
                        HttpStatus.NOT_FOUND));

        BigDecimal amountPaid = toMoney(payment.getAmountPaid());
        BigDecimal alreadyRefunded = toMoney(refundRepository.sumActiveRefundsForPayment(paymentId));
        return new RefundCapDTO(paymentId, amountPaid, alreadyRefunded,
                amountPaid.subtract(alreadyRefunded));
    }

    /**
     * Réactive un remboursement désactivé, en refusant l'opération si elle ferait dépasser le
     * montant versé (exigence 7.12).
     *
     * <p><strong>Point non atteignable par l'interface actuelle</strong>, et conservé volontairement :
     * aucun chemin de code ne désactive un remboursement, et l'annulation est hors périmètre. La
     * garde existe pour qu'une fonctionnalité d'annulation future ne puisse pas rendre la caisse
     * négative en réactivant un remboursement après qu'un autre a consommé le plafond.</p>
     *
     * @throws CustomServiceException 404 si le remboursement est introuvable
     * @throws CustomServiceException 400 si la réactivation dépasserait le montant versé
     */
    @Transactional
    public RefundEntity reactivate(Long refundId) {
        RefundEntity refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new CustomServiceException(
                        "Remboursement introuvable pour l'identifiant : " + refundId,
                        HttpStatus.NOT_FOUND));

        if (refund.isActive()) {
            return refund;
        }

        PaymentEntity payment = refund.getPayment();
        BigDecimal amountPaid = toMoney(payment != null ? payment.getAmountPaid() : null);
        BigDecimal alreadyRefunded = payment == null
                ? BigDecimal.ZERO.setScale(MONEY_SCALE)
                : toMoney(refundRepository.sumActiveRefundsForPayment(payment.getId()));
        BigDecimal cap = amountPaid.subtract(alreadyRefunded);

        if (toMoney(refund.getAmount()).compareTo(cap) > 0) {
            throw new CustomServiceException(String.format(
                    "Réactivation impossible : elle porterait les remboursements au-delà du montant "
                            + "versé. Plafond restant : %s €.", cap),
                    HttpStatus.BAD_REQUEST);
        }

        refund.setActive(true);
        return refundRepository.save(refund);
    }

    // ==================================================================
    // Validation
    // ==================================================================

    /** Arrondit à l'échelle monétaire puis vérifie les bornes (exigence 7.4). */
    private BigDecimal normalizedAmount(BigDecimal raw) {
        if (raw == null) {
            throw new CustomServiceException(
                    "Le montant du remboursement est obligatoire.", HttpStatus.BAD_REQUEST);
        }
        BigDecimal amount = raw.setScale(MONEY_SCALE, MONEY_ROUNDING);
        if (amount.compareTo(MIN_AMOUNT) < 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new CustomServiceException(String.format(
                    "Le montant du remboursement doit être compris entre %s € et %s € "
                            + "(montant reçu après arrondi : %s €).",
                    MIN_AMOUNT, MAX_AMOUNT, amount),
                    HttpStatus.BAD_REQUEST);
        }
        return amount;
    }

    /** Vérifie que le motif est renseigné et de longueur acceptable (exigences 6.2, 6.3). */
    private String validatedReason(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CustomServiceException(
                    "Le motif du remboursement est obligatoire : une sortie de caisse doit pouvoir "
                            + "être justifiée.",
                    HttpStatus.BAD_REQUEST);
        }
        String reason = raw.strip();
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new CustomServiceException(String.format(
                    "Le motif du remboursement ne doit pas dépasser %d caractères (reçu : %d).",
                    MAX_REASON_LENGTH, reason.length()),
                    HttpStatus.BAD_REQUEST);
        }
        return reason;
    }

    // ==================================================================
    // Enregistrement
    // ==================================================================

    /**
     * Enregistre le remboursement en lui attribuant un numéro de pièce, avec rejeu borné sur
     * collision d'unicité (exigence 6.14).
     */
    private RefundEntity saveWithNumber(PaymentEntity payment,
                                        BigDecimal amount,
                                        String reason,
                                        Date requestedDate) {
        Date refundDate = requestedDate != null ? requestedDate : new Date();
        int year = yearOf(refundDate);

        DataIntegrityViolationException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_NUMBER_ATTEMPTS; attempt++) {
            RefundEntity refund = RefundEntity.builder()
                    .payment(payment)
                    .student(payment.getStudent())
                    .amount(amount)
                    .refundDate(refundDate)
                    .reason(reason)
                    .refundNumber(refundNumberService.nextNumber(year))
                    .build();
            try {
                return refundRepository.saveAndFlush(refund);
            } catch (DataIntegrityViolationException e) {
                // Un autre enregistrement a pris ce rang entre-temps : on recalcule et on rejoue.
                // Le rang consommé n'est ni comblé ni réutilisé (exigence 6.5).
                lastFailure = e;
            }
        }
        throw new CustomServiceException(
                "Le numéro de pièce du remboursement n'a pu être attribué après "
                        + MAX_NUMBER_ATTEMPTS + " tentatives. Réessayer.",
                lastFailure, HttpStatus.CONFLICT);
    }

    /** Année civile de la date du remboursement, qui borne la séquence du numéro de pièce. */
    private int yearOf(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar.get(Calendar.YEAR);
    }

    /** Convertit un montant {@code Double} legacy en {@code BigDecimal} (null → 0) à l'échelle 2. */
    private BigDecimal toMoney(Double value) {
        BigDecimal result = (value == null) ? BigDecimal.ZERO : BigDecimal.valueOf(value);
        return result.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    /** Normalise un {@code BigDecimal} éventuellement nul à l'échelle monétaire. */
    private BigDecimal toMoney(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(MONEY_SCALE, MONEY_ROUNDING);
    }
}
