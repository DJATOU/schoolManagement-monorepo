package com.school.management.dto;

import java.math.BigDecimal;

/**
 * Plafond de remboursement d'un paiement (exigences 7.10, 9.2).
 *
 * <p>Les trois montants sont exposés ensemble, et non le seul plafond : un administrateur qui a une
 * famille devant lui doit pouvoir dire <em>pourquoi</em> le plafond vaut ce qu'il vaut. « Vous ne
 * pouvez pas rembourser plus de 40 € » sans expliquer que 60 € ont déjà été rendus sur un versement
 * de 100 € oblige à aller chercher l'information ailleurs.</p>
 *
 * @param paymentId          identifiant du paiement concerné
 * @param amountPaid         montant versé du paiement
 * @param alreadyRefunded    somme des remboursements actifs déjà accordés sur ce paiement
 * @param refundableCap      plafond restant, égal à {@code amountPaid − alreadyRefunded}
 */
public record RefundCapDTO(
        Long paymentId,
        BigDecimal amountPaid,
        BigDecimal alreadyRefunded,
        BigDecimal refundableCap) {
}
