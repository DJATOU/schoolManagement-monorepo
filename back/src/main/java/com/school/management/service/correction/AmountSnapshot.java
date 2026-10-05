package com.school.management.service.correction;

import com.school.management.service.payment.PaymentCostResolver;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Montants d'une Série pour un étudiant à un instant donné : ce que l'Aperçu montre avant et après
 * une correction (exigence 4.1).
 *
 * <p>Les montants sont ramenés à deux décimales : deux photographies des mêmes montants sont égales
 * quelle que soit l'échelle renvoyée par la base.</p>
 *
 * @param cost      coût de la Série au prorata, réduction appliquée
 * @param dueSoFar  montant dû à ce jour : séances suivies × prix net
 * @param paid      montant versé, remboursements déduits
 * @param remaining reste à payer sur le coût, jamais négatif
 * @param late      en retard : versé inférieur au dû à ce jour
 */
public record AmountSnapshot(BigDecimal cost, BigDecimal dueSoFar, BigDecimal paid, BigDecimal remaining,
                             boolean late) {

    private static final int MONEY_SCALE = 2;

    public AmountSnapshot {
        cost = money(cost, "cost");
        dueSoFar = money(dueSoFar, "dueSoFar");
        paid = money(paid, "paid");
        remaining = money(remaining, "remaining");
    }

    /** Photographie tirée du statut calculé par le résolveur de coût. */
    public static AmountSnapshot of(PaymentCostResolver.PaymentStatusResult status) {
        BigDecimal remaining = status.monthTotalCost().subtract(status.amountPaid()).max(BigDecimal.ZERO);
        return new AmountSnapshot(status.monthTotalCost(), status.amountDueSoFar(), status.amountPaid(),
                remaining, status.late());
    }

    /** Forme canonique, pour l'empreinte de l'Aperçu. */
    String canonical() {
        return cost.toPlainString() + ";" + dueSoFar.toPlainString() + ";" + paid.toPlainString() + ";"
                + remaining.toPlainString() + ";" + late;
    }

    private static BigDecimal money(BigDecimal value, String name) {
        return Objects.requireNonNull(value, name).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
