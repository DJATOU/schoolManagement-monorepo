package com.school.management.dto;

import java.math.BigDecimal;
import java.util.Date;

/**
 * DTO de réponse d'un remboursement (refund).
 *
 * <p>Produit par {@code RefundMapper} à partir d'une {@code RefundEntity} en aplatissant
 * les relations paiement et étudiant vers leurs identifiants
 * ({@code payment.id → paymentId}, {@code student.id → studentId}).</p>
 *
 * @param id         identifiant du remboursement
 * @param paymentId  identifiant du paiement d'origine rattaché
 * @param studentId  identifiant de l'étudiant bénéficiaire
 * @param amount     montant remboursé
 * @param refundDate date du remboursement
 */
public record RefundResponseDTO(
        Long id,
        Long paymentId,
        Long studentId,
        BigDecimal amount,
        Date refundDate) {
}
