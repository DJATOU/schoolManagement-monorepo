package com.school.management.dto;

import java.math.BigDecimal;
import java.util.Date;

/**
 * DTO minimal de création d'un remboursement (refund).
 *
 * <p>Porte les données brutes soumises par l'administrateur. La validation métier
 * (montant non nul, non négatif, et n'excédant pas le montant versé du paiement
 * rattaché) est assurée par {@code RefundService}. Le mapper MapStruct / DTO de
 * réponse sera ajouté ultérieurement (tâche 14.2) ; cette requête reste
 * volontairement minimale et réutilisable.</p>
 *
 * @param paymentId  identifiant du paiement d'origine auquel se rattache le remboursement
 * @param studentId  identifiant de l'étudiant bénéficiaire (informatif ; l'étudiant
 *                   effectivement enregistré provient du paiement rattaché pour rester cohérent)
 * @param amount     montant remboursé (doit être non nul, ≥ 0 et ≤ montant versé du paiement)
 * @param refundDate date du remboursement (facultative ; par défaut la date courante si nulle)
 */
public record RefundRequestDTO(
        Long paymentId,
        Long studentId,
        BigDecimal amount,
        Date refundDate) {
}
