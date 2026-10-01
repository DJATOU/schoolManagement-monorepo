package com.school.management.service.payment;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Statut stocké d'un cumul de série ({@code payments.status}), déduit du montant versé et du coût
 * au prorata.
 *
 * <p>Règle reprise de {@code PaymentDetailAdminService.recalculatePayment}, qui l'applique déjà,
 * pour que l'encaissement, son annulation et la correction d'un détail rendent le même statut au
 * même montant. {@code recalculatePayment} y sera raccordé en A.6.</p>
 *
 * <ol>
 *   <li>coût connu et versé au moins égal : {@code COMPLETED} — y compris le coût nul d'un étudiant
 *       exempté, qui n'a rien à payer ;</li>
 *   <li>rien de versé : {@code PENDING} ;</li>
 *   <li>sinon {@code IN_PROGRESS}. Un coût inconnu ne rend jamais {@code COMPLETED} : mieux vaut un
 *       statut « en cours » à vérifier qu'une série annoncée soldée à tort.</li>
 * </ol>
 *
 * <p>Ce statut n'est qu'un résumé affiché. Le retard et le solde sont recalculés à la lecture par
 * {@code PaymentCostResolver} et ne dépendent pas de lui.</p>
 */
public final class PaymentLineStatus {

    public static final String COMPLETED = "COMPLETED";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String PENDING = "PENDING";

    private PaymentLineStatus() {
    }

    /**
     * @param paid montant versé sur la série
     * @param cost coût au prorata de la série, vide s'il n'a pas pu être résolu
     */
    public static String of(BigDecimal paid, Optional<BigDecimal> cost) {
        if (cost.isPresent() && paid.compareTo(cost.get()) >= 0) {
            return COMPLETED;
        }
        if (paid.signum() <= 0) {
            return PENDING;
        }
        return IN_PROGRESS;
    }
}
