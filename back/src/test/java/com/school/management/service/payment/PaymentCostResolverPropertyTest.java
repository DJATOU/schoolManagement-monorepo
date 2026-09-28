package com.school.management.service.payment;

import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PricingEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.repository.PaymentRepository;
import com.school.management.repository.RefundRepository;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.service.DiscountService;
import com.school.management.service.payment.BillableSessionsResolver.BillableSessions;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests de propriété (jqwik) pour {@link PaymentCostResolver}.
 *
 * <p>Les dépôts, le {@link DiscountService} et le {@link BillableSessionsResolver} sont
 * simulés (Mockito) afin de garder les 100+ itérations peu coûteuses. Chaque propriété
 * correspond à une propriété de correction d'un design : les propriétés 6, 9 et 10 viennent
 * de payment-attendance-rules, la propriété d'encadrement du coût vient de
 * prorata-billing-and-payment-carry-over.</p>
 */
class PaymentCostResolverPropertyTest {

    private static final int MONEY_SCALE = PaymentCostCalculator.MONEY_SCALE;
    private static final RoundingMode MONEY_ROUNDING = PaymentCostCalculator.MONEY_ROUNDING;

    private static final Long STUDENT_ID = 1L;
    private static final Long SERIES_ID = 10L;

    private SessionSeriesEntity seriesWithPrice(int plannedSessions, BigDecimal price) {
        PricingEntity pricing = new PricingEntity();
        pricing.setPrice(price.doubleValue());
        GroupEntity group = new GroupEntity();
        group.setPrice(pricing);
        SessionSeriesEntity series = new SessionSeriesEntity();
        series.setGroup(group);
        series.setTotalSessions(plannedSessions);
        return series;
    }

    /** Séances fictives : seul leur nombre entre dans le calcul du coût. */
    private static List<SessionEntity> sessions(long firstId, int count) {
        List<SessionEntity> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            SessionEntity session = new SessionEntity();
            session.setId(firstId + i);
            list.add(session);
        }
        return List.copyOf(list);
    }

    /** Décompte facturable simulé, tel que le produirait le résolveur partagé. */
    private static BillableSessions billableSessions(int billableCount, int attendedCount,
                                                     int excludedCount) {
        return new BillableSessions(sessions(1L, billableCount), sessions(1000L, excludedCount),
                attendedCount, true, null);
    }

    // ---------------------------------------------------------------------
    // Générateurs
    // ---------------------------------------------------------------------

    /** Prix par séance : 0.00 .. 10000.00, échelle 2 (inclut le prix nul). */
    @Provide
    Arbitrary<BigDecimal> price() {
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
        return Arbitraries.frequencyOf(
                net.jqwik.api.Tuple.of(8, range),
                net.jqwik.api.Tuple.of(1, Arbitraries.just(new BigDecimal("0.00"))),
                net.jqwik.api.Tuple.of(1, Arbitraries.just(new BigDecimal("1.00"))));
    }

    /** Liste de fanions de présence (true/false), pouvant être vide. */
    @Provide
    Arbitrary<List<Boolean>> presenceFlags() {
        return Arbitraries.of(true, false).list().ofMaxSize(50);
    }

    private static BigDecimal expectedDue(int attended, BigDecimal price, BigDecimal rate) {
        BigDecimal base = price.multiply(BigDecimal.valueOf(attended));
        BigDecimal multiplier = BigDecimal.ONE.subtract(rate);
        return base.multiply(multiplier).setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    // ---------------------------------------------------------------------
    // Property 6 — Attended count uses only present records, series-scoped
    // ---------------------------------------------------------------------

    // Feature: payment-attendance-rules, Property 6: For any set of attendance records for a student within a series scope (spanning one or more groups, including completed catch-ups), the computed Attended_Sessions equals the number of records with isPresent == true.
    @Property(tries = 100)
    void property6_attendedCountUsesOnlyPresentRecords(
            @ForAll("presenceFlags") List<Boolean> flags,
            @ForAll("price") BigDecimal price,
            @ForAll("rate") BigDecimal rate) {

        int presentCount = (int) flags.stream().filter(Boolean::booleanValue).count();
        int billableCount = flags.size();

        SessionSeriesRepository seriesRepo = mock(SessionSeriesRepository.class);
        BillableSessionsResolver billableResolver = mock(BillableSessionsResolver.class);
        PaymentRepository paymentRepo = mock(PaymentRepository.class);
        RefundRepository refundRepo = mock(RefundRepository.class);
        DiscountService discountService = mock(DiscountService.class);

        when(seriesRepo.findById(SERIES_ID))
                .thenReturn(Optional.of(seriesWithPrice(billableCount, price)));
        // Le décompte present-only vient du résolveur partagé, borné à la série.
        when(billableResolver.resolve(STUDENT_ID, SERIES_ID))
                .thenReturn(billableSessions(billableCount, presentCount, 0));
        when(discountService.resolveRate(STUDENT_ID, SERIES_ID)).thenReturn(rate);

        PaymentCostResolver resolver = new PaymentCostResolver(
                seriesRepo, billableResolver, paymentRepo, refundRepo, discountService);

        PaymentCostCalculator calc = resolver.calculatorFor(STUDENT_ID, SERIES_ID);

        assertThat(calc.amountDueSoFar())
                .isEqualByComparingTo(expectedDue(presentCount, price, rate));
    }

    // ---------------------------------------------------------------------
    // Property 3 (prorata) — Encadrement du coût
    // ---------------------------------------------------------------------

    // Feature: prorata-billing-and-payment-carry-over, Property 3: For any combination of sessions, attendances and discount rate: 0 <= amountDueSoFar <= Cout_Serie_Prorata <= planned sessions x price x (1 - rate).
    @Property(tries = 200)
    void property3_costIsBoundedByNominalSeriesCost(@ForAll("prorataInputs") ProrataInputs inputs) {

        SessionSeriesRepository seriesRepo = mock(SessionSeriesRepository.class);
        BillableSessionsResolver billableResolver = mock(BillableSessionsResolver.class);
        PaymentRepository paymentRepo = mock(PaymentRepository.class);
        RefundRepository refundRepo = mock(RefundRepository.class);
        DiscountService discountService = mock(DiscountService.class);

        when(seriesRepo.findById(SERIES_ID))
                .thenReturn(Optional.of(seriesWithPrice(inputs.plannedSessions(), inputs.price())));
        when(billableResolver.resolve(STUDENT_ID, SERIES_ID)).thenReturn(billableSessions(
                inputs.billableSessions(), inputs.attendedSessions(),
                inputs.plannedSessions() - inputs.billableSessions()));
        when(discountService.resolveRate(STUDENT_ID, SERIES_ID)).thenReturn(inputs.rate());

        PaymentCostResolver resolver = new PaymentCostResolver(
                seriesRepo, billableResolver, paymentRepo, refundRepo, discountService);

        PaymentCostCalculator calc = resolver.calculatorFor(STUDENT_ID, SERIES_ID);

        BigDecimal amountDueSoFar = calc.amountDueSoFar();
        BigDecimal prorataCost = calc.monthTotalCost();
        // Borne haute : le coût nominal de la série, celui que l'ancien calcul produisait.
        BigDecimal nominalCost =
                expectedDue(inputs.plannedSessions(), inputs.price(), inputs.rate());

        assertThat(amountDueSoFar).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(amountDueSoFar).isLessThanOrEqualTo(prorataCost);
        assertThat(prorataCost).isLessThanOrEqualTo(nominalCost);
    }

    /**
     * Entrées du prorata avec les inclusions imposées par l'exigence 1 :
     * {@code attendues ⊆ facturables ⊆ planifiées}.
     */
    record ProrataInputs(int plannedSessions, int billableSessions, int attendedSessions,
                         BigDecimal price, BigDecimal rate) {}

    @Provide
    Arbitrary<ProrataInputs> prorataInputs() {
        return Arbitraries.integers().between(0, 200).flatMap(planned ->
                Arbitraries.integers().between(0, planned).flatMap(billable ->
                        Combinators.combine(
                                        Arbitraries.just(planned),
                                        Arbitraries.just(billable),
                                        Arbitraries.integers().between(0, billable),
                                        price(),
                                        rate())
                                .as(ProrataInputs::new)));
    }

    // ---------------------------------------------------------------------
    // Property 9 — Amount paid is the sum of non-cancelled payments
    // ---------------------------------------------------------------------

    // Feature: payment-attendance-rules, Property 9: For any set of payment records for a student and series, Amount_Paid equals the sum of the amounts of records whose status is not CANCELLED, and equals zero when no such record exists.
    @Property(tries = 100)
    void property9_amountPaidIsSumOfNonCancelledPayments(
            @ForAll("paidSum") BigDecimal paidSum) {

        SessionSeriesRepository seriesRepo = mock(SessionSeriesRepository.class);
        BillableSessionsResolver billableResolver = mock(BillableSessionsResolver.class);
        PaymentRepository paymentRepo = mock(PaymentRepository.class);
        RefundRepository refundRepo = mock(RefundRepository.class);
        DiscountService discountService = mock(DiscountService.class);

        when(seriesRepo.findById(SERIES_ID))
                .thenReturn(Optional.of(seriesWithPrice(8, new BigDecimal("30.00"))));
        when(billableResolver.resolve(anyLong(), anyLong()))
                .thenReturn(billableSessions(8, 0, 0));
        when(discountService.resolveRate(anyLong(), anyLong())).thenReturn(new BigDecimal("0.00"));
        // Somme des paiements non annulés fournie par le repository ; aucun remboursement.
        when(paymentRepo.sumAmountPaidForStudentAndSeries(STUDENT_ID, SERIES_ID)).thenReturn(paidSum);
        when(refundRepo.sumRefundsForStudentAndSeries(STUDENT_ID, SERIES_ID))
                .thenReturn(new BigDecimal("0.00"));

        PaymentCostResolver resolver = new PaymentCostResolver(
                seriesRepo, billableResolver, paymentRepo, refundRepo, discountService);

        PaymentCostResolver.PaymentStatusResult result = resolver.resolve(STUDENT_ID, SERIES_ID);

        assertThat(result.amountPaid())
                .isEqualByComparingTo(paidSum.setScale(MONEY_SCALE, MONEY_ROUNDING));
    }

    /** Somme versée : 0.00 .. 1000000.00, échelle 2 (inclut 0.00). */
    @Provide
    Arbitrary<BigDecimal> paidSum() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, new BigDecimal("1000000.00"))
                .ofScale(2);
    }

    // ---------------------------------------------------------------------
    // Property 10 — Effective amount paid excludes refunds
    // ---------------------------------------------------------------------

    // Feature: payment-attendance-rules, Property 10: For any set of non-cancelled payments and recorded refunds for a student and series, the effective Amount_Paid used for late status equals sum(payments) - sum(refunds).
    @Property(tries = 100)
    void property10_effectiveAmountPaidExcludesRefunds(
            @ForAll("paymentAndRefund") BigDecimal[] pr) {

        BigDecimal payments = pr[0];
        BigDecimal refunds = pr[1];

        SessionSeriesRepository seriesRepo = mock(SessionSeriesRepository.class);
        BillableSessionsResolver billableResolver = mock(BillableSessionsResolver.class);
        PaymentRepository paymentRepo = mock(PaymentRepository.class);
        RefundRepository refundRepo = mock(RefundRepository.class);
        DiscountService discountService = mock(DiscountService.class);

        when(seriesRepo.findById(SERIES_ID))
                .thenReturn(Optional.of(seriesWithPrice(8, new BigDecimal("30.00"))));
        when(billableResolver.resolve(anyLong(), anyLong()))
                .thenReturn(billableSessions(8, 0, 0));
        when(discountService.resolveRate(anyLong(), anyLong())).thenReturn(new BigDecimal("0.00"));
        when(paymentRepo.sumAmountPaidForStudentAndSeries(STUDENT_ID, SERIES_ID)).thenReturn(payments);
        when(refundRepo.sumRefundsForStudentAndSeries(STUDENT_ID, SERIES_ID)).thenReturn(refunds);

        PaymentCostResolver resolver = new PaymentCostResolver(
                seriesRepo, billableResolver, paymentRepo, refundRepo, discountService);

        PaymentCostResolver.PaymentStatusResult result = resolver.resolve(STUDENT_ID, SERIES_ID);

        BigDecimal expected = payments.subtract(refunds).setScale(MONEY_SCALE, MONEY_ROUNDING);
        assertThat(result.amountPaid()).isEqualByComparingTo(expected);
    }

    /** Couple (paiements P, remboursements R) avec 0 ≤ R ≤ P. */
    @Provide
    Arbitrary<BigDecimal[]> paymentAndRefund() {
        Arbitrary<BigDecimal> paymentsArb = Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, new BigDecimal("1000000.00"))
                .ofScale(2);
        return paymentsArb.flatMap(p -> {
            Arbitrary<BigDecimal> refundArb = Arbitraries.bigDecimals()
                    .between(BigDecimal.ZERO, p)
                    .ofScale(2);
            return Combinators.combine(Arbitraries.just(p), refundArb)
                    .as((payments, refunds) -> new BigDecimal[]{payments, refunds});
        });
    }
}
