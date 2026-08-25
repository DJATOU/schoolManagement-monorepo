package com.school.management.service.payment;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de propriété (jqwik) pour {@link PaymentCostCalculator}.
 *
 * <p>Chaque propriété correspond à une propriété de correction du design
 * (payment-attendance-rules). Les générateurs incluent les valeurs limites :
 * zéro séance, prix nul, taux 0.00 et taux 1.00.
 */
class PaymentCostCalculatorPropertyTest {

    private static final int MONEY_SCALE = PaymentCostCalculator.MONEY_SCALE;
    private static final RoundingMode MONEY_ROUNDING = PaymentCostCalculator.MONEY_ROUNDING;

    // ---------------------------------------------------------------------
    // Générateurs (@Provide) — bornés pour éviter tout overflow.
    // ---------------------------------------------------------------------

    /** Prix par séance : 0.00 .. 10000.00, échelle 2 (inclut le prix nul). */
    @Provide
    Arbitrary<BigDecimal> pricePerSession() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, new BigDecimal("10000.00"))
                .ofScale(2);
    }

    /** Taux de réduction : 0.00 .. 1.00, échelle 2 (inclut 0.00 et 1.00). */
    @Provide
    Arbitrary<BigDecimal> rate() {
        Arbitrary<BigDecimal> range = Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, BigDecimal.ONE)
                .ofScale(2);
        // Assure la présence des bornes exactes 0.00 et 1.00 dans l'échantillonnage.
        return Arbitraries.frequencyOf(
                net.jqwik.api.Tuple.of(8, range),
                net.jqwik.api.Tuple.of(1, Arbitraries.just(new BigDecimal("0.00"))),
                net.jqwik.api.Tuple.of(1, Arbitraries.just(new BigDecimal("1.00"))));
    }

    /** Montant versé : 0.00 .. 1000000.00, échelle 2. */
    @Provide
    Arbitrary<BigDecimal> amountPaid() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, new BigDecimal("1000000.00"))
                .ofScale(2);
    }

    private static BigDecimal expected(int sessions, BigDecimal price, BigDecimal rate) {
        BigDecimal base = price.multiply(BigDecimal.valueOf(sessions));
        BigDecimal multiplier = BigDecimal.ONE.subtract(rate);
        return base.multiply(multiplier).setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    // ---------------------------------------------------------------------
    // Property 1 — Month total cost arithmetic
    // ---------------------------------------------------------------------

    // Feature: payment-attendance-rules, Property 1: For any non-negative plannedSessions, non-negative pricePerSession, and rate in [0.00, 1.00], monthTotalCost() equals round(plannedSessions x pricePerSession x (1 - rate)) at scale 2 HALF_UP.
    @Property(tries = 100)
    void property1_monthTotalCostArithmetic(
            @ForAll @IntRange(min = 0, max = 1000) int plannedSessions,
            @ForAll @IntRange(min = 0, max = 1000) int attendedSessions,
            @ForAll("pricePerSession") BigDecimal price,
            @ForAll("rate") BigDecimal rate) {

        PaymentCostCalculator calc =
                new PaymentCostCalculator(plannedSessions, attendedSessions, price, rate);

        assertThat(calc.monthTotalCost())
                .isEqualByComparingTo(expected(plannedSessions, price, rate));
    }

    // ---------------------------------------------------------------------
    // Property 2 — Amount-due-so-far arithmetic
    // ---------------------------------------------------------------------

    // Feature: payment-attendance-rules, Property 2: For any non-negative attendedSessions, non-negative pricePerSession, and rate in [0.00, 1.00], amountDueSoFar() equals round(attendedSessions x pricePerSession x (1 - rate)) at scale 2 HALF_UP; when rate == 1.00 both amountDueSoFar() and monthTotalCost() equal zero.
    @Property(tries = 100)
    void property2_amountDueSoFarArithmetic(
            @ForAll @IntRange(min = 0, max = 1000) int plannedSessions,
            @ForAll @IntRange(min = 0, max = 1000) int attendedSessions,
            @ForAll("pricePerSession") BigDecimal price,
            @ForAll("rate") BigDecimal rate) {

        PaymentCostCalculator calc =
                new PaymentCostCalculator(plannedSessions, attendedSessions, price, rate);

        assertThat(calc.amountDueSoFar())
                .isEqualByComparingTo(expected(attendedSessions, price, rate));

        if (rate.compareTo(BigDecimal.ONE) == 0) {
            assertThat(calc.amountDueSoFar()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(calc.monthTotalCost()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // ---------------------------------------------------------------------
    // Property 3 — Monetary outputs are scale-2
    // ---------------------------------------------------------------------

    // Feature: payment-attendance-rules, Property 3: For any valid calculator inputs, every returned monetary amount has scale exactly equal to MONEY_SCALE (2).
    @Property(tries = 100)
    void property3_monetaryOutputsAreScale2(
            @ForAll @IntRange(min = 0, max = 1000) int plannedSessions,
            @ForAll @IntRange(min = 0, max = 1000) int attendedSessions,
            @ForAll("pricePerSession") BigDecimal price,
            @ForAll("rate") BigDecimal rate) {

        PaymentCostCalculator calc =
                new PaymentCostCalculator(plannedSessions, attendedSessions, price, rate);

        assertThat(calc.monthTotalCost().scale()).isEqualTo(MONEY_SCALE);
        assertThat(calc.amountDueSoFar().scale()).isEqualTo(MONEY_SCALE);
    }

    // ---------------------------------------------------------------------
    // Property 4 — Negative monetary inputs are rejected
    // ---------------------------------------------------------------------

    // Feature: payment-attendance-rules, Property 4: For any input where plannedSessions, attendedSessions, pricePerSession, or amountPaid is negative, the calculator rejects the input with a validation error.
    @Property(tries = 100)
    void property4_negativeMonetaryInputsAreRejected(
            @ForAll @IntRange(min = -1000, max = 1000) int plannedSessions,
            @ForAll @IntRange(min = -1000, max = 1000) int attendedSessions,
            @ForAll("pricePerSession") BigDecimal price,
            @ForAll("rate") BigDecimal rate,
            @ForAll("amountPaid") BigDecimal paid) {

        boolean negativeSessions = plannedSessions < 0 || attendedSessions < 0;

        if (negativeSessions) {
            // Séances négatives → rejet immédiat par le constructeur.
            assertThatThrownBy(() ->
                    new PaymentCostCalculator(plannedSessions, attendedSessions, price, rate))
                    .isInstanceOf(IllegalArgumentException.class);
        } else {
            // Prix négatif → rejet par le constructeur.
            BigDecimal negativePrice = price.add(BigDecimal.ONE).negate();
            assertThatThrownBy(() ->
                    new PaymentCostCalculator(plannedSessions, attendedSessions, negativePrice, rate))
                    .isInstanceOf(IllegalArgumentException.class);

            // amountPaid négatif → rejet par isLate / isMonthFullyPaid.
            PaymentCostCalculator calc =
                    new PaymentCostCalculator(plannedSessions, attendedSessions, price, rate);
            BigDecimal negativePaid = paid.add(BigDecimal.ONE).negate();
            assertThatThrownBy(() -> calc.isLate(negativePaid))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> calc.isMonthFullyPaid(negativePaid))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ---------------------------------------------------------------------
    // Property 5 — Amount due is monotonic and bounded
    // ---------------------------------------------------------------------

    // Feature: payment-attendance-rules, Property 5: For any calculator inputs, amountDueSoFar() is non-decreasing as attendedSessions increases, and whenever attendedSessions <= plannedSessions, amountDueSoFar() <= monthTotalCost().
    @Property(tries = 100)
    void property5_amountDueIsMonotonicAndBounded(
            @ForAll("monotonicInputs") MonotonicInputs inputs) {

        PaymentCostCalculator lower = new PaymentCostCalculator(
                inputs.plannedSessions, inputs.attendedSessions, inputs.price, inputs.rate);
        PaymentCostCalculator higher = new PaymentCostCalculator(
                inputs.plannedSessions, inputs.attendedSessions + inputs.delta, inputs.price, inputs.rate);

        // Non décroissant lorsque attendedSessions augmente.
        assertThat(higher.amountDueSoFar())
                .isGreaterThanOrEqualTo(lower.amountDueSoFar());

        // Borné par le coût total du mois quand attended <= planned.
        if (inputs.attendedSessions <= inputs.plannedSessions) {
            assertThat(lower.amountDueSoFar())
                    .isLessThanOrEqualTo(lower.monthTotalCost());
        }
    }

    record MonotonicInputs(int plannedSessions, int attendedSessions, int delta,
                           BigDecimal price, BigDecimal rate) {}

    @Provide
    Arbitrary<MonotonicInputs> monotonicInputs() {
        Arbitrary<Integer> planned = Arbitraries.integers().between(0, 1000);
        Arbitrary<Integer> attended = Arbitraries.integers().between(0, 1000);
        Arbitrary<Integer> delta = Arbitraries.integers().between(0, 1000);
        return Combinators.combine(planned, attended, delta, pricePerSession(), rate())
                .as(MonotonicInputs::new);
    }
}
