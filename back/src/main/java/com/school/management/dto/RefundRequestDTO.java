package com.school.management.dto;

import java.math.BigDecimal;
import java.util.Date;

/**
 * DTO de création d'un remboursement (refund).
 *
 * <p>Porte les données brutes soumises par l'administrateur. La validation métier est assurée par
 * {@code RefundService} : montant strictement positif et dans les bornes, motif non vide, et montant
 * n'excédant pas le Plafond_Remboursable du paiement rattaché (montant versé diminué des
 * remboursements actifs déjà accordés).</p>
 *
 * @param paymentId  identifiant du paiement d'origine auquel se rattache le remboursement
 * @param studentId  <strong>ignoré</strong> (exigence 7.11). Le bénéficiaire enregistré est toujours
 *                   l'étudiant du paiement rattaché : accepter un étudiant fourni par l'appelant
 *                   permettrait d'inscrire un remboursement au nom d'une autre famille que celle
 *                   qui a versé. Le champ est conservé pour ne pas casser les appelants existants.
 * @param amount     montant remboursé (strictement positif, ≤ Plafond_Remboursable)
 * @param refundDate date du remboursement (facultative ; par défaut la date courante si nulle)
 * @param reason     motif du remboursement (obligatoire, non vide, au plus 500 caractères).
 *                   Une sortie de caisse sans raison enregistrée n'est pas justifiable lors d'un
 *                   contrôle (exigence 6.1).
 */
public record RefundRequestDTO(
        Long paymentId,
        Long studentId,
        BigDecimal amount,
        Date refundDate,
        String reason) {
}
