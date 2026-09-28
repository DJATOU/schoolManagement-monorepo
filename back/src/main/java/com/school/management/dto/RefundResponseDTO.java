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
 * @param id            identifiant du remboursement
 * @param paymentId     identifiant du paiement d'origine rattaché
 * @param studentId     identifiant de l'étudiant bénéficiaire, issu du paiement rattaché
 * @param amount        montant remboursé
 * @param refundDate    date du remboursement
 * @param refundNumber  numéro de pièce, de la forme {@code REMB-AAAA-NNNN} (exigence 6.8)
 * @param reason        motif du remboursement. Nul pour les remboursements enregistrés avant la
 *                      traçabilité ; l'affichage doit alors porter la mention « Motif non renseigné
 *                      (antérieur à la traçabilité) » plutôt qu'un vide (exigence 6.10)
 * @param refundableCap plafond restant sur le paiement après ce remboursement (exigence 7.2)
 */
public record RefundResponseDTO(
        Long id,
        Long paymentId,
        Long studentId,
        BigDecimal amount,
        Date refundDate,
        String refundNumber,
        String reason,
        BigDecimal refundableCap) {
}
