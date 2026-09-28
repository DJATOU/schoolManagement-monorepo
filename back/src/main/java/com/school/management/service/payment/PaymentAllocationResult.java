package com.school.management.service.payment;

import com.school.management.persistance.PaymentEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Résultat d'un encaissement réparti : ce qui a été imputé sur la série visée à la saisie et ce
 * qui a été reporté sur les séries suivantes (exigence 6.3).
 *
 * <p>Ce contrat existe parce qu'un versement ne crédite plus forcément une seule série. L'appelant
 * doit pouvoir dire à l'administrateur — et imprimer sur le reçu — « 240,00 DA sur Sept 2025,
 * 60,00 DA reportés sur Oct 2025 », ce qu'une simple {@link PaymentEntity} ne permet pas : le
 * registre {@code payments} porte un <strong>cumul</strong> par série, pas le détail du versement
 * du jour.</p>
 *
 * <p>La somme de {@link #amountAllocated()} et de {@link #amountCarriedOver()} est égale à
 * {@link #amountReceived()} : c'est l'invariant de conservation de l'exigence 4.3, vérifiable
 * directement sur ce résultat.</p>
 *
 * @param studentId       l'étudiant qui a versé
 * @param groupId         le groupe dont la chaîne de séries a été parcourue
 * @param seriesId        la série visée à la saisie, source des éventuels reports
 * @param amountReceived  le montant total reçu, échelle monétaire
 * @param amountAllocated la part imputée sur la série visée ; nulle lorsque cette série était
 *                        soldée et que la totalité a été reportée
 * @param carryOvers      les reports effectués, dans l'ordre croissant des identifiants de série ;
 *                        vide lorsque le versement tient sur la série visée
 * @param payment         la ligne de paiement principale créditée : celle de la série visée si
 *                        elle a reçu quelque chose, sinon celle de la première série créditée
 */
public record PaymentAllocationResult(
        Long studentId,
        Long groupId,
        Long seriesId,
        BigDecimal amountReceived,
        BigDecimal amountAllocated,
        List<CarriedOverAmount> carryOvers,
        PaymentEntity payment) {

    public PaymentAllocationResult {
        Objects.requireNonNull(amountReceived, "amountReceived ne doit pas être nul.");
        Objects.requireNonNull(amountAllocated, "amountAllocated ne doit pas être nul.");
        carryOvers = List.copyOf(Objects.requireNonNull(carryOvers, "carryOvers ne doit pas être nul."));
    }

    /**
     * Montant reporté sur une série autre que celle visée.
     *
     * @param seriesId   identifiant de la série créditée par report
     * @param seriesName nom de la série, pour que le reçu et l'écran la nomment sans relire la base
     * @param amount     montant reporté sur cette série
     */
    public record CarriedOverAmount(Long seriesId, String seriesName, BigDecimal amount) {
    }

    /** Somme des montants reportés, nulle en l'absence de report (exigence 7.4). */
    public BigDecimal amountCarriedOver() {
        return carryOvers.stream()
                .map(CarriedOverAmount::amount)
                .reduce(BigDecimal.ZERO.setScale(PaymentCostCalculator.MONEY_SCALE,
                        PaymentCostCalculator.MONEY_ROUNDING), BigDecimal::add);
    }
}
