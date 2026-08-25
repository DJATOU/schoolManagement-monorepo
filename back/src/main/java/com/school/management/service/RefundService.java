package com.school.management.service;

import com.school.management.dto.RefundRequestDTO;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.RefundEntity;
import com.school.management.repository.PaymentRepository;
import com.school.management.repository.RefundRepository;
import com.school.management.service.exception.CustomServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.Objects;

/**
 * Service de gestion des remboursements (refunds).
 *
 * <p>Un remboursement est rattaché à un paiement précis. La règle métier
 * essentielle (requirement 13.4) est qu'un remboursement ne peut pas dépasser le
 * montant effectivement versé du paiement rattaché : aucun geste commercial n'est
 * autorisé. Le montant doit également être non nul et positif.</p>
 *
 * <p>Pour rester cohérent, l'étudiant enregistré sur le remboursement est celui du
 * paiement rattaché ({@code payment.getStudent()}). Le {@code studentId} porté par le
 * DTO reste purement informatif, ce qui évite d'ajouter une dépendance à
 * {@code StudentRepository}.</p>
 */
@Service
public class RefundService {

    /** Échelle monétaire (2 décimales) et politique d'arrondi (HALF_UP). */
    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;

    public RefundService(RefundRepository refundRepository,
                         PaymentRepository paymentRepository) {
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
    }

    /**
     * Crée un remboursement après validation.
     *
     * <p>Règles (requirements 13.1, 13.4) :</p>
     * <ul>
     *   <li>le paiement rattaché doit exister (sinon 404) ;</li>
     *   <li>le montant doit être non nul et ≥ 0 (sinon 400) ;</li>
     *   <li>le montant ne doit pas dépasser le montant versé du paiement rattaché
     *       (aucun geste commercial ; sinon 400).</li>
     * </ul>
     *
     * @param dto données de création
     * @return le remboursement persisté
     * @throws CustomServiceException (HTTP 404) si le paiement rattaché est introuvable
     * @throws CustomServiceException (HTTP 400) si le montant est invalide ou dépasse le versé
     */
    @Transactional
    public RefundEntity create(RefundRequestDTO dto) {
        Objects.requireNonNull(dto, "La requête de remboursement ne doit pas être nulle.");

        // Chargement du paiement d'origine.
        PaymentEntity payment = paymentRepository.findById(dto.paymentId())
                .orElseThrow(() -> new CustomServiceException(
                        "Paiement introuvable pour l'identifiant : " + dto.paymentId(),
                        HttpStatus.NOT_FOUND));

        // Validation du montant : non nul et positif.
        BigDecimal amount = dto.amount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new CustomServiceException(
                    "Le montant du remboursement doit être renseigné et positif.",
                    HttpStatus.BAD_REQUEST);
        }

        // Le montant versé du paiement rattaché (Double → BigDecimal, null → ZERO, échelle 2).
        BigDecimal paidAmount = toMoney(payment.getAmountPaid());
        BigDecimal normalizedAmount = amount.setScale(MONEY_SCALE, MONEY_ROUNDING);

        // Règle 13.4 : le remboursement ne peut pas dépasser le montant payé (aucun geste commercial).
        if (normalizedAmount.compareTo(paidAmount) > 0) {
            throw new CustomServiceException(
                    "Le montant du remboursement ne peut pas dépasser le montant payé.",
                    HttpStatus.BAD_REQUEST);
        }

        RefundEntity refund = RefundEntity.builder()
                .payment(payment)
                // Cohérence : l'étudiant est celui du paiement rattaché (le studentId du DTO reste informatif).
                .student(payment.getStudent())
                .amount(normalizedAmount)
                .refundDate(dto.refundDate() != null ? dto.refundDate() : new Date())
                .build();

        return refundRepository.save(refund);
    }

    /** Convertit un montant {@code Double} legacy en {@code BigDecimal} (null → 0) à l'échelle 2. */
    private BigDecimal toMoney(Double value) {
        BigDecimal result = (value == null) ? BigDecimal.ZERO : BigDecimal.valueOf(value);
        return result.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }
}
