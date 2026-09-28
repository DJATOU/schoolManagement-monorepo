package com.school.management.service.payment;

import com.school.management.dto.payment.PaymentQuoteDTO;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.service.payment.AllocationPlan.SeriesAllocation;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests de propriété (jqwik) pour {@link PaymentAllocationService}.
 *
 * <p>Le dépôt de séries et le {@link PaymentQuoteService} sont simulés (Mockito), comme dans
 * {@code PaymentCostResolverPropertyTest} : le plan est une décision pure, aucune base n'est
 * nécessaire pour l'éprouver sur des centaines de chaînes de séries.</p>
 *
 * <p>Deux invariants gouvernent la répartition. La <strong>conservation</strong> interdit qu'un
 * centime apparaisse ou disparaisse entre la saisie et le plan : c'est elle qui rend le refus
 * total de l'exigence 5.11 cohérent, puisque le reliquat non plaçable est explicitement porté au
 * lieu d'être perdu. L'<strong>absence de dépassement</strong> garantit qu'aucune série n'est
 * créditée au-delà de son plafond, donc qu'un report ne peut pas fabriquer le trop-perçu que
 * cette fonctionnalité supprime.</p>
 */
class PaymentAllocationServicePropertyTest {

    private static final int MONEY_SCALE = PaymentCostCalculator.MONEY_SCALE;
    private static final RoundingMode MONEY_ROUNDING = PaymentCostCalculator.MONEY_ROUNDING;

    private static final Long STUDENT_ID = 1L;
    private static final Long GROUP_ID = 2L;
    private static final long FIRST_SERIES_ID = 100L;

    /**
     * Une série de la chaîne, réduite à ce que le plan consomme : son décompte facturable
     * (zéro = série non ouverte) et son plafond encaissable.
     */
    record SeriesSpec(long id, int billableSessions, BigDecimal ceiling) {
    }

    // ---------------------------------------------------------------------
    // Générateurs
    // ---------------------------------------------------------------------

    /** Plafond encaissable d'une série : 0.00 .. 1000.00, échelle 2 (inclut la série soldée). */
    @Provide
    Arbitrary<BigDecimal> ceiling() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, new BigDecimal("1000.00"))
                .ofScale(2);
    }

    /** Montant versé : 0.01 .. 5000.00, échelle 2, toujours strictement positif. */
    @Provide
    Arbitrary<BigDecimal> amount() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("0.01"), new BigDecimal("5000.00"))
                .ofScale(2);
    }

    /**
     * Chaîne de 1 à 6 séries, identifiants croissants, mêlant séries ouvertes, séries soldées
     * (plafond nul) et séries non ouvertes (aucune séance facturable).
     */
    @Provide
    Arbitrary<List<SeriesSpec>> chain() {
        Arbitrary<SeriesSpec> specArbitrary = Combinators.combine(
                        Arbitraries.integers().between(0, 8),
                        ceiling())
                .as((billable, ceiling) -> new SeriesSpec(0L, billable, ceiling));
        return specArbitrary.list().ofMinSize(1).ofMaxSize(6).map(specs -> {
            List<SeriesSpec> withIds = new ArrayList<>();
            for (int i = 0; i < specs.size(); i++) {
                SeriesSpec spec = specs.get(i);
                withIds.add(new SeriesSpec(FIRST_SERIES_ID + i, spec.billableSessions(),
                        spec.ceiling()));
            }
            return List.copyOf(withIds);
        });
    }

    // ---------------------------------------------------------------------
    // Property 1 — Conservation du versement
    // ---------------------------------------------------------------------

    // Feature: prorata-billing-and-payment-carry-over, Property 1: For any accepted payment, sum of amounts allocated to series = payment amount. No cent disappears or appears during the distribution.
    @Property(tries = 200)
    void property1_allocatedAmountsPlusUnplaceableEqualThePaymentAmount(
            @ForAll("chain") List<SeriesSpec> chain,
            @ForAll("amount") BigDecimal amount) {

        AllocationPlan plan = planFor(chain, amount);

        BigDecimal expected = amount.setScale(MONEY_SCALE, MONEY_ROUNDING);
        assertThat(plan.totalAllocated().add(plan.unplaceable()))
                .isEqualByComparingTo(expected);
        assertThat(plan.unplaceable()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        // Un plan complet n'a rien laissé de côté : les deux formulations coïncident.
        assertThat(plan.isComplete())
                .isEqualTo(plan.totalAllocated().compareTo(expected) == 0);
    }

    // ---------------------------------------------------------------------
    // Property 2 — Aucun dépassement par série
    // ---------------------------------------------------------------------

    // Feature: prorata-billing-and-payment-carry-over, Property 2: For any series retained in the plan, amount allocated <= payable ceiling of that series at computation time.
    @Property(tries = 200)
    void property2_noSeriesReceivesMoreThanItsCeiling(
            @ForAll("chain") List<SeriesSpec> chain,
            @ForAll("amount") BigDecimal amount) {

        Map<Long, SeriesSpec> byId = new HashMap<>();
        chain.forEach(spec -> byId.put(spec.id(), spec));

        AllocationPlan plan = planFor(chain, amount);

        for (SeriesAllocation allocation : plan.allocations()) {
            SeriesSpec spec = byId.get(allocation.seriesId());
            // Une allocation nulle n'a rien à faire dans le plan : elle ferait apparaître un
            // report de 0,00 DA dans l'historique et sur le reçu.
            assertThat(allocation.amount()).isGreaterThan(BigDecimal.ZERO);
            assertThat(allocation.amount()).isLessThanOrEqualTo(spec.ceiling());
            // Une série non ouverte ne peut rien recevoir (exigence 5.8).
            assertThat(spec.billableSessions()).isPositive();
            // Seule la série visée reçoit une imputation directe, les autres sont des reports.
            assertThat(allocation.carriedOver()).isEqualTo(spec.id() != FIRST_SERIES_ID);
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /** Construit le service avec ses dépendances simulées et calcule le plan de la chaîne. */
    private AllocationPlan planFor(List<SeriesSpec> chain, BigDecimal amount) {
        SessionSeriesRepository seriesRepository = mock(SessionSeriesRepository.class);
        PaymentQuoteService quoteService = mock(PaymentQuoteService.class);

        List<SessionSeriesEntity> entities = new ArrayList<>();
        for (SeriesSpec spec : chain) {
            entities.add(seriesEntity(spec.id()));
            when(quoteService.quote(STUDENT_ID, spec.id())).thenReturn(quote(spec));
        }
        when(seriesRepository.findByGroupId(GROUP_ID)).thenReturn(List.copyOf(entities));

        return new PaymentAllocationService(seriesRepository, quoteService)
                .plan(STUDENT_ID, GROUP_ID, FIRST_SERIES_ID, amount);
    }

    private static SessionSeriesEntity seriesEntity(long id) {
        SessionSeriesEntity series = new SessionSeriesEntity();
        series.setId(id);
        series.setName("Série " + id);
        return series;
    }

    private static PaymentQuoteDTO quote(SeriesSpec spec) {
        BigDecimal zero = BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING);
        return new PaymentQuoteDTO(STUDENT_ID, spec.id(),
                spec.billableSessions(), spec.billableSessions(), 0, 0,
                zero, zero, zero, zero, zero, zero, zero,
                spec.ceiling(), zero, false, false);
    }
}
