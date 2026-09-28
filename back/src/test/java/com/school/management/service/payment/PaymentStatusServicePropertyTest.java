package com.school.management.service.payment;

import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PricingEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.PaymentDetailRepository;
import com.school.management.repository.PaymentRepository;
import com.school.management.repository.RefundRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.repository.StudentRepository;
import com.school.management.service.DiscountService;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests de propriété (jqwik) pour la dérivation de statut du {@link PaymentStatusService}.
 *
 * <p>Property 7 est une propriété du calculateur pur {@link PaymentCostCalculator}
 * (dérivation déterministe et idempotente). Property 8 vérifie que le
 * {@link PaymentStatusService}, câblé via un vrai {@link PaymentCostResolver} (repositories
 * simulés), produit exactement le même résultat de retard que le calculateur construit à
 * partir des mêmes entrées résolues.</p>
 */
class PaymentStatusServicePropertyTest {

    private static final Long STUDENT_ID = 1L;
    private static final Long SERIES_ID = 10L;

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

    /** Montant versé : 0.00 .. 1000000.00, échelle 2. */
    @Provide
    Arbitrary<BigDecimal> amountPaid() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, new BigDecimal("1000000.00"))
                .ofScale(2);
    }

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
    private static List<SessionEntity> fakeSessions(int count) {
        List<SessionEntity> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            SessionEntity session = new SessionEntity();
            session.setId((long) (i + 1));
            list.add(session);
        }
        return List.copyOf(list);
    }

    // ---------------------------------------------------------------------
    // Property 7 — Payment status derivation is deterministic and idempotent
    // ---------------------------------------------------------------------

    // Feature: payment-attendance-rules, Property 7: For any amountPaid, amountDueSoFar, and monthTotalCost, isLate equals amountPaid < amountDueSoFar and isMonthFullyPaid equals amountPaid >= monthTotalCost; the derivation depends only on these amounts (no time/grace-period input) and yields the same result on repeated evaluation.
    @Property(tries = 100)
    void property7_paymentStatusDerivationIsDeterministicAndIdempotent(
            @ForAll @IntRange(min = 0, max = 1000) int plannedSessions,
            @ForAll @IntRange(min = 0, max = 1000) int attendedSessions,
            @ForAll("price") BigDecimal price,
            @ForAll("rate") BigDecimal rate,
            @ForAll("amountPaid") BigDecimal paid) {

        PaymentCostCalculator calc =
                new PaymentCostCalculator(plannedSessions, attendedSessions, price, rate);

        BigDecimal amountDueSoFar = calc.amountDueSoFar();
        BigDecimal monthTotalCost = calc.monthTotalCost();

        boolean expectedLate = paid.compareTo(amountDueSoFar) < 0;
        boolean expectedFullyPaid = paid.compareTo(monthTotalCost) >= 0;

        // La dérivation dépend uniquement des montants (aucune notion de temps/grâce).
        assertThat(calc.isLate(paid)).isEqualTo(expectedLate);
        assertThat(calc.isMonthFullyPaid(paid)).isEqualTo(expectedFullyPaid);

        // Idempotence / déterminisme : évaluations répétées → résultats identiques.
        assertThat(calc.isLate(paid)).isEqualTo(calc.isLate(paid));
        assertThat(calc.isMonthFullyPaid(paid)).isEqualTo(calc.isMonthFullyPaid(paid));
    }

    // ---------------------------------------------------------------------
    // Property 8 — Status service matches the calculator
    // ---------------------------------------------------------------------

    // Feature: payment-attendance-rules, Property 8: For any resolved (plannedSessions, attendedSessions, pricePerSession, rate, amountPaid), the PaymentStatusService late and fully-paid results equal those of a PaymentCostCalculator constructed from the same inputs.
    @Property(tries = 100)
    void property8_statusServiceMatchesTheCalculator(
            @ForAll @IntRange(min = 0, max = 1000) int plannedSessions,
            @ForAll @IntRange(min = 0, max = 1000) int attendedSessions,
            @ForAll("price") BigDecimal price,
            @ForAll("rate") BigDecimal rate,
            @ForAll("amountPaid") BigDecimal paid) {

        // Repositories/dépendances du resolver simulés pour renvoyer les entrées résolues.
        SessionSeriesRepository seriesRepo = mock(SessionSeriesRepository.class);
        AttendanceRepository attendanceRepo = mock(AttendanceRepository.class);
        BillableSessionsResolver billableResolver = mock(BillableSessionsResolver.class);
        PaymentRepository paymentRepo = mock(PaymentRepository.class);
        RefundRepository refundRepo = mock(RefundRepository.class);
        DiscountService discountService = mock(DiscountService.class);

        when(seriesRepo.findById(SERIES_ID))
                .thenReturn(Optional.of(seriesWithPrice(plannedSessions, price)));
        // Depuis le prorata, les deux décomptes viennent du résolveur de séances facturables ;
        // toutes les séances planifiées sont ici facturables, ce qui reproduit le cas nominal.
        when(billableResolver.resolve(STUDENT_ID, SERIES_ID)).thenReturn(
                new BillableSessionsResolver.BillableSessions(
                        fakeSessions(plannedSessions), List.of(), attendedSessions, true, null));
        when(discountService.resolveRate(STUDENT_ID, SERIES_ID)).thenReturn(rate);
        when(paymentRepo.sumAmountPaidForStudentAndSeries(STUDENT_ID, SERIES_ID)).thenReturn(paid);
        when(refundRepo.sumRefundsForStudentAndSeries(STUDENT_ID, SERIES_ID))
                .thenReturn(new BigDecimal("0.00"));

        // Vrai resolver + vrai calculateur.
        PaymentCostResolver resolver = new PaymentCostResolver(
                seriesRepo, billableResolver, paymentRepo, refundRepo, discountService);

        // Vrai service (les autres repositories ne sont pas utilisés par le chemin testé).
        PaymentStatusService service = new PaymentStatusService(
                paymentRepo,
                mock(PaymentDetailRepository.class),
                mock(StudentRepository.class),
                mock(GroupRepository.class),
                mock(SessionRepository.class),
                seriesRepo,
                attendanceRepo,
                resolver,
                discountService,
                new PaymentQuoteService(seriesRepo, attendanceRepo, discountService,
                        resolver, billableResolver));

        // Calculateur de référence construit à partir des mêmes entrées.
        PaymentCostCalculator reference =
                new PaymentCostCalculator(plannedSessions, attendedSessions, price, rate);

        boolean serviceLate = service.isStudentPaymentOverdueForSeries(STUDENT_ID, SERIES_ID);

        assertThat(serviceLate).isEqualTo(reference.isLate(paid));
    }
}
