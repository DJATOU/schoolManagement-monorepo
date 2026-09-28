package com.school.management.service;

import com.school.management.dto.DiscountRequestDTO;
import com.school.management.persistance.DiscountEntity;
import com.school.management.persistance.DiscountScope;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.repository.DiscountRepository;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.service.exception.CustomServiceException;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests de propriété (jqwik) pour {@link DiscountService}.
 *
 * <p>Chaque propriété correspond à une propriété de correction du design
 * (payment-attendance-rules). Les repositories sont mockés (Mockito) afin que les
 * 100+ itérations restent rapides.</p>
 */
class DiscountServicePropertyTest {

    // ------------------------------------------------------------------
    // Property 20 — Discount has exactly one scope
    // ------------------------------------------------------------------

    // Feature: payment-attendance-rules, Property 20: For any discount creation request, creation succeeds only when exactly one scope reference (groupId xor seriesId xor sessionId) matching the declared scope is set; requests with zero or more than one scope reference are rejected.
    @Property(tries = 100)
    void property20_discountHasExactlyOneScope(
            @ForAll DiscountScope scope,
            @ForAll boolean groupSet,
            @ForAll boolean seriesSet,
            @ForAll boolean sessionSet) {

        DiscountRepository discountRepository = mock(DiscountRepository.class);
        SessionSeriesRepository seriesRepository = mock(SessionSeriesRepository.class);
        when(discountRepository.save(any(DiscountEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        DiscountService service = new DiscountService(discountRepository, seriesRepository);

        Long groupId = groupSet ? 10L : null;
        Long seriesId = seriesSet ? 20L : null;
        Long sessionId = sessionSet ? 30L : null;

        DiscountRequestDTO dto = new DiscountRequestDTO(
                1L, scope, groupId, seriesId, sessionId, new BigDecimal("0.50"));

        // Exactement une référence de portée doit être renseignée ET correspondre au scope.
        boolean exactlyOneSet = (groupSet ? 1 : 0) + (seriesSet ? 1 : 0) + (sessionSet ? 1 : 0) == 1;
        boolean matchesScope = switch (scope) {
            case GROUP -> groupSet;
            case SERIES -> seriesSet;
            case SESSION -> sessionSet;
        };
        boolean shouldSucceed = exactlyOneSet && matchesScope;

        if (shouldSucceed) {
            DiscountEntity saved = service.create(dto);
            assertThat(saved.getScope()).isEqualTo(scope);
        } else {
            assertThatThrownBy(() -> service.create(dto))
                    .isInstanceOf(CustomServiceException.class);
        }
    }

    // ------------------------------------------------------------------
    // Property 21 — Discount rate range
    // ------------------------------------------------------------------

    // Feature: payment-attendance-rules, Property 21: For any submitted rate outside [0.00, 1.00], discount creation is rejected with a validation error.
    @Property(tries = 100)
    void property21_discountRateRange(@ForAll("anyRate") BigDecimal rate) {

        DiscountRepository discountRepository = mock(DiscountRepository.class);
        SessionSeriesRepository seriesRepository = mock(SessionSeriesRepository.class);
        when(discountRepository.save(any(DiscountEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        DiscountService service = new DiscountService(discountRepository, seriesRepository);

        // Requête valide côté portée (GROUP + groupId) : seule la validité du taux est testée.
        DiscountRequestDTO dto = new DiscountRequestDTO(
                1L, DiscountScope.GROUP, 10L, null, null, rate);

        boolean inRange = rate != null
                && rate.compareTo(BigDecimal.ZERO) >= 0
                && rate.compareTo(BigDecimal.ONE) <= 0;

        if (inRange) {
            assertThat(service.create(dto).getRate()).isEqualByComparingTo(rate);
        } else {
            assertThatThrownBy(() -> service.create(dto))
                    .isInstanceOf(CustomServiceException.class);
        }
    }

    /** Taux couvrant l'intérieur et l'extérieur de [0.00, 1.00] (négatifs et > 1). */
    @Provide
    Arbitrary<BigDecimal> anyRate() {
        return Arbitraries.bigDecimals()
                .between(new BigDecimal("-2.00"), new BigDecimal("3.00"))
                .ofScale(2);
    }

    // ------------------------------------------------------------------
    // Property 22 — Single-scope discount selection
    // ------------------------------------------------------------------

    // Feature: payment-attendance-rules, Property 22: For any set of applicable discounts for a student and billing context, the resolved rate equals the rate of the single most-specific applicable scope (Session > Series > Group) and is never a sum or product of multiple scopes; a group-scope rate of 1.00 (exemption) resolves to 1.00.
    @Property(tries = 100)
    void property22_singleScopeDiscountSelection(@ForAll("scopeSelection") ScopeSelection sel) {

        long studentId = 1L;
        long seriesId = 20L;
        long groupId = 10L;
        long sessionId = 30L;

        // Série avec un groupe et une séance connus.
        GroupEntity group = new GroupEntity();
        group.setId(groupId);
        SessionEntity session = new SessionEntity();
        session.setId(sessionId);
        SessionSeriesEntity series = new SessionSeriesEntity();
        series.setId(seriesId);
        series.setGroup(group);
        series.setSessions(Set.of(session));

        List<DiscountEntity> discounts = new ArrayList<>();
        long idSeq = 1;
        if (sel.scopes.contains(DiscountScope.GROUP)) {
            discounts.add(buildDiscount(idSeq++, DiscountScope.GROUP, groupId, null, null, sel.groupRate));
        }
        if (sel.scopes.contains(DiscountScope.SERIES)) {
            discounts.add(buildDiscount(idSeq++, DiscountScope.SERIES, null, seriesId, null, sel.seriesRate));
        }
        if (sel.scopes.contains(DiscountScope.SESSION)) {
            discounts.add(buildDiscount(idSeq++, DiscountScope.SESSION, null, null, sessionId, sel.sessionRate));
        }

        DiscountRepository discountRepository = mock(DiscountRepository.class);
        SessionSeriesRepository seriesRepository = mock(SessionSeriesRepository.class);
        when(seriesRepository.findById(seriesId)).thenReturn(java.util.Optional.of(series));
        when(discountRepository.findByStudentId(studentId)).thenReturn(discounts);
        DiscountService service = new DiscountService(discountRepository, seriesRepository);

        BigDecimal resolved = service.resolveRate(studentId, seriesId);

        // Le taux attendu est celui de la portée applicable la plus spécifique.
        BigDecimal expected;
        if (sel.scopes.contains(DiscountScope.SESSION)) {
            expected = sel.sessionRate;
        } else if (sel.scopes.contains(DiscountScope.SERIES)) {
            expected = sel.seriesRate;
        } else if (sel.scopes.contains(DiscountScope.GROUP)) {
            expected = sel.groupRate;
        } else {
            expected = BigDecimal.ZERO;
        }

        assertThat(resolved).isEqualByComparingTo(expected.setScale(2, java.math.RoundingMode.HALF_UP));
        // Jamais une somme : le résultat n'excède jamais 1.00.
        assertThat(resolved).isLessThanOrEqualTo(BigDecimal.ONE);
    }

    private DiscountEntity buildDiscount(long id, DiscountScope scope,
                                         Long groupId, Long seriesId, Long sessionId,
                                         BigDecimal rate) {
        return DiscountEntity.builder()
                .id(id)
                .scope(scope)
                .groupId(groupId)
                .seriesId(seriesId)
                .sessionId(sessionId)
                .rate(rate)
                .build();
    }

    record ScopeSelection(Set<DiscountScope> scopes,
                          BigDecimal groupRate, BigDecimal seriesRate, BigDecimal sessionRate) {}

    @Provide
    Arbitrary<ScopeSelection> scopeSelection() {
        Arbitrary<Set<DiscountScope>> scopes = Arbitraries.of(DiscountScope.values())
                .set().ofMinSize(0).ofMaxSize(3)
                .map(s -> s.isEmpty() ? EnumSet.noneOf(DiscountScope.class) : EnumSet.copyOf(s));
        Arbitrary<BigDecimal> rate = Arbitraries.bigDecimals()
                .between(BigDecimal.ZERO, BigDecimal.ONE).ofScale(2);
        return Combinators.combine(scopes, rate, rate, rate)
                .as(ScopeSelection::new);
    }
}
