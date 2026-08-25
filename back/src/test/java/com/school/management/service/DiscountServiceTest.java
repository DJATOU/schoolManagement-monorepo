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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires (JUnit 5 + Mockito) pour {@link DiscountService}.
 *
 * <p>Couvre chaque branche de portée, l'absence de réduction (0.00), l'exemption
 * (1.00), le rejet de conflit et les bornes de taux 0.00 / 1.00.</p>
 */
class DiscountServiceTest {

    private static final long STUDENT_ID = 1L;
    private static final long SERIES_ID = 20L;
    private static final long GROUP_ID = 10L;
    private static final long SESSION_ID = 30L;

    private DiscountRepository discountRepository;
    private SessionSeriesRepository seriesRepository;
    private DiscountService service;

    @BeforeEach
    void setUp() {
        discountRepository = mock(DiscountRepository.class);
        seriesRepository = mock(SessionSeriesRepository.class);
        when(discountRepository.save(any(DiscountEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        service = new DiscountService(discountRepository, seriesRepository);
    }

    // ------------------------------------------------------------------
    // create — validation de portée
    // ------------------------------------------------------------------

    @Test
    void create_groupScope_succeeds() {
        DiscountRequestDTO dto = new DiscountRequestDTO(
                STUDENT_ID, DiscountScope.GROUP, GROUP_ID, null, null, new BigDecimal("0.25"));

        DiscountEntity saved = service.create(dto);

        assertThat(saved.getScope()).isEqualTo(DiscountScope.GROUP);
        assertThat(saved.getGroupId()).isEqualTo(GROUP_ID);
        assertThat(saved.getRate()).isEqualByComparingTo("0.25");
    }

    @Test
    void create_seriesScope_succeeds() {
        DiscountRequestDTO dto = new DiscountRequestDTO(
                STUDENT_ID, DiscountScope.SERIES, null, SERIES_ID, null, new BigDecimal("0.10"));

        DiscountEntity saved = service.create(dto);

        assertThat(saved.getScope()).isEqualTo(DiscountScope.SERIES);
        assertThat(saved.getSeriesId()).isEqualTo(SERIES_ID);
    }

    @Test
    void create_sessionScope_succeeds() {
        DiscountRequestDTO dto = new DiscountRequestDTO(
                STUDENT_ID, DiscountScope.SESSION, null, null, SESSION_ID, new BigDecimal("0.50"));

        DiscountEntity saved = service.create(dto);

        assertThat(saved.getScope()).isEqualTo(DiscountScope.SESSION);
        assertThat(saved.getSessionId()).isEqualTo(SESSION_ID);
    }

    @Test
    void create_nullDto_throws() {
        assertThatThrownBy(() -> service.create(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void create_nullScope_rejected() {
        DiscountRequestDTO dto = new DiscountRequestDTO(
                STUDENT_ID, null, GROUP_ID, null, null, new BigDecimal("0.25"));

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_groupScopeWithoutGroupId_rejected() {
        DiscountRequestDTO dto = new DiscountRequestDTO(
                STUDENT_ID, DiscountScope.GROUP, null, null, null, new BigDecimal("0.25"));

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(CustomServiceException.class);
    }

    @Test
    void create_seriesScopeWithoutSeriesId_rejected() {
        DiscountRequestDTO dto = new DiscountRequestDTO(
                STUDENT_ID, DiscountScope.SERIES, null, null, null, new BigDecimal("0.25"));

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(CustomServiceException.class);
    }

    @Test
    void create_sessionScopeWithoutSessionId_rejected() {
        DiscountRequestDTO dto = new DiscountRequestDTO(
                STUDENT_ID, DiscountScope.SESSION, null, null, null, new BigDecimal("0.25"));

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(CustomServiceException.class);
    }

    @Test
    void create_multipleScopeReferences_rejected() {
        DiscountRequestDTO dto = new DiscountRequestDTO(
                STUDENT_ID, DiscountScope.GROUP, GROUP_ID, SERIES_ID, null, new BigDecimal("0.25"));

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(CustomServiceException.class);
    }

    @Test
    void create_scopeMismatch_rejected() {
        // scope GROUP mais seul seriesId est renseigné.
        DiscountRequestDTO dto = new DiscountRequestDTO(
                STUDENT_ID, DiscountScope.GROUP, null, SERIES_ID, null, new BigDecimal("0.25"));

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(CustomServiceException.class);
    }

    // ------------------------------------------------------------------
    // create — validation du taux
    // ------------------------------------------------------------------

    @Test
    void create_rateBoundaryZero_succeeds() {
        DiscountRequestDTO dto = new DiscountRequestDTO(
                STUDENT_ID, DiscountScope.GROUP, GROUP_ID, null, null, new BigDecimal("0.00"));

        assertThat(service.create(dto).getRate()).isEqualByComparingTo("0.00");
    }

    @Test
    void create_rateBoundaryOne_succeeds() {
        DiscountRequestDTO dto = new DiscountRequestDTO(
                STUDENT_ID, DiscountScope.GROUP, GROUP_ID, null, null, new BigDecimal("1.00"));

        assertThat(service.create(dto).getRate()).isEqualByComparingTo("1.00");
    }

    @Test
    void create_nullRate_rejected() {
        DiscountRequestDTO dto = new DiscountRequestDTO(
                STUDENT_ID, DiscountScope.GROUP, GROUP_ID, null, null, null);

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(CustomServiceException.class);
    }

    @Test
    void create_negativeRate_rejected() {
        DiscountRequestDTO dto = new DiscountRequestDTO(
                STUDENT_ID, DiscountScope.GROUP, GROUP_ID, null, null, new BigDecimal("-0.01"));

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(CustomServiceException.class);
    }

    @Test
    void create_rateAboveOne_rejected() {
        DiscountRequestDTO dto = new DiscountRequestDTO(
                STUDENT_ID, DiscountScope.GROUP, GROUP_ID, null, null, new BigDecimal("1.01"));

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(CustomServiceException.class);
    }

    // ------------------------------------------------------------------
    // resolveRate
    // ------------------------------------------------------------------

    private SessionSeriesEntity buildSeries() {
        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);
        SessionEntity session = new SessionEntity();
        session.setId(SESSION_ID);
        SessionSeriesEntity series = new SessionSeriesEntity();
        series.setId(SERIES_ID);
        series.setGroup(group);
        series.setSessions(Set.of(session));
        return series;
    }

    private DiscountEntity discount(long id, DiscountScope scope,
                                    Long groupId, Long seriesId, Long sessionId, String rate) {
        return DiscountEntity.builder()
                .id(id).scope(scope)
                .groupId(groupId).seriesId(seriesId).sessionId(sessionId)
                .rate(new BigDecimal(rate))
                .build();
    }

    @Test
    void resolveRate_noDiscount_returnsZero() {
        when(seriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(buildSeries()));
        when(discountRepository.findByStudentId(STUDENT_ID)).thenReturn(List.of());

        assertThat(service.resolveRate(STUDENT_ID, SERIES_ID)).isEqualByComparingTo("0.00");
    }

    @Test
    void resolveRate_groupScope_returnsGroupRate() {
        when(seriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(buildSeries()));
        when(discountRepository.findByStudentId(STUDENT_ID)).thenReturn(List.of(
                discount(1, DiscountScope.GROUP, GROUP_ID, null, null, "0.20")));

        assertThat(service.resolveRate(STUDENT_ID, SERIES_ID)).isEqualByComparingTo("0.20");
    }

    @Test
    void resolveRate_seriesScope_returnsSeriesRate() {
        when(seriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(buildSeries()));
        when(discountRepository.findByStudentId(STUDENT_ID)).thenReturn(List.of(
                discount(1, DiscountScope.SERIES, null, SERIES_ID, null, "0.30")));

        assertThat(service.resolveRate(STUDENT_ID, SERIES_ID)).isEqualByComparingTo("0.30");
    }

    @Test
    void resolveRate_sessionScope_returnsSessionRate() {
        when(seriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(buildSeries()));
        when(discountRepository.findByStudentId(STUDENT_ID)).thenReturn(List.of(
                discount(1, DiscountScope.SESSION, null, null, SESSION_ID, "0.40")));

        assertThat(service.resolveRate(STUDENT_ID, SERIES_ID)).isEqualByComparingTo("0.40");
    }

    @Test
    void resolveRate_prefersMostSpecific_sessionOverSeriesOverGroup() {
        when(seriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(buildSeries()));
        when(discountRepository.findByStudentId(STUDENT_ID)).thenReturn(List.of(
                discount(1, DiscountScope.GROUP, GROUP_ID, null, null, "0.10"),
                discount(2, DiscountScope.SERIES, null, SERIES_ID, null, "0.20"),
                discount(3, DiscountScope.SESSION, null, null, SESSION_ID, "0.50")));

        assertThat(service.resolveRate(STUDENT_ID, SERIES_ID)).isEqualByComparingTo("0.50");
    }

    @Test
    void resolveRate_seriesPreferredOverGroupWhenNoSession() {
        when(seriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(buildSeries()));
        when(discountRepository.findByStudentId(STUDENT_ID)).thenReturn(List.of(
                discount(1, DiscountScope.GROUP, GROUP_ID, null, null, "0.10"),
                discount(2, DiscountScope.SERIES, null, SERIES_ID, null, "0.20")));

        assertThat(service.resolveRate(STUDENT_ID, SERIES_ID)).isEqualByComparingTo("0.20");
    }

    @Test
    void resolveRate_exemption_groupRateOne_resolvesToOne() {
        when(seriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(buildSeries()));
        when(discountRepository.findByStudentId(STUDENT_ID)).thenReturn(List.of(
                discount(1, DiscountScope.GROUP, GROUP_ID, null, null, "1.00")));

        assertThat(service.resolveRate(STUDENT_ID, SERIES_ID)).isEqualByComparingTo("1.00");
    }

    @Test
    void resolveRate_ignoresNonApplicableDiscounts() {
        when(seriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(buildSeries()));
        // Réductions ne visant pas ce contexte (autre groupe / série / séance).
        when(discountRepository.findByStudentId(STUDENT_ID)).thenReturn(List.of(
                discount(1, DiscountScope.GROUP, 999L, null, null, "0.90"),
                discount(2, DiscountScope.SERIES, null, 888L, null, "0.80"),
                discount(3, DiscountScope.SESSION, null, null, 777L, "0.70")));

        assertThat(service.resolveRate(STUDENT_ID, SERIES_ID)).isEqualByComparingTo("0.00");
    }

    @Test
    void resolveRate_sameScopeMultiple_picksHighestId() {
        when(seriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(buildSeries()));
        when(discountRepository.findByStudentId(STUDENT_ID)).thenReturn(List.of(
                discount(1, DiscountScope.GROUP, GROUP_ID, null, null, "0.10"),
                discount(2, DiscountScope.GROUP, GROUP_ID, null, null, "0.35")));

        assertThat(service.resolveRate(STUDENT_ID, SERIES_ID)).isEqualByComparingTo("0.35");
    }

    @Test
    void resolveRate_sessionScopeWithNullSessionId_notApplicable() {
        // Réduction de portée SESSION mais sessionId null : elle n'est pas applicable au contexte
        // (couvre la branche d.getSessionId() == null). La portée série applicable prend le relais.
        when(seriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(buildSeries()));
        when(discountRepository.findByStudentId(STUDENT_ID)).thenReturn(List.of(
                discount(1, DiscountScope.SESSION, null, null, null, "0.90"),
                discount(2, DiscountScope.SERIES, null, SERIES_ID, null, "0.20")));

        assertThat(service.resolveRate(STUDENT_ID, SERIES_ID)).isEqualByComparingTo("0.20");
    }

    @Test
    void resolveRate_seriesWithNullGroupAndNullSessions_returnsZero() {
        SessionSeriesEntity series = new SessionSeriesEntity();
        series.setId(SERIES_ID);
        series.setGroup(null);
        series.setSessions(null);
        when(seriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series));
        when(discountRepository.findByStudentId(STUDENT_ID)).thenReturn(List.of(
                discount(1, DiscountScope.GROUP, GROUP_ID, null, null, "0.50")));

        assertThat(service.resolveRate(STUDENT_ID, SERIES_ID)).isEqualByComparingTo("0.00");
    }

    @Test
    void resolveRate_seriesNotFound_throwsNotFound() {
        when(seriesRepository.findById(SERIES_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveRate(STUDENT_ID, SERIES_ID))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void resolveRate_nullStudentId_throws() {
        assertThatThrownBy(() -> service.resolveRate(null, SERIES_ID))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void resolveRate_nullSeriesId_throws() {
        assertThatThrownBy(() -> service.resolveRate(STUDENT_ID, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void findAll_returnsAllDiscounts() {
        DiscountEntity d1 = new DiscountEntity();
        DiscountEntity d2 = new DiscountEntity();
        when(discountRepository.findAll()).thenReturn(List.of(d1, d2));

        assertThat(service.findAll()).containsExactly(d1, d2);
    }

    // ==================================================================
    // updateRate / delete
    // ==================================================================

    @Test
    void updateRate_updatesRateOfExistingDiscount() {
        DiscountEntity existing = new DiscountEntity();
        existing.setId(4L);
        existing.setRate(new BigDecimal("0.25"));
        when(discountRepository.findById(4L)).thenReturn(Optional.of(existing));

        DiscountEntity updated = service.updateRate(4L, new BigDecimal("0.75"));

        assertThat(updated.getRate()).isEqualByComparingTo("0.75");
    }

    @Test
    void updateRate_unknownId_throws404() {
        when(discountRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateRate(999L, new BigDecimal("0.50")))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateRate_rateOutOfRange_throws400() {
        assertThatThrownBy(() -> service.updateRate(4L, new BigDecimal("1.50")))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void delete_removesExistingDiscount() {
        DiscountEntity existing = new DiscountEntity();
        existing.setId(4L);
        when(discountRepository.findById(4L)).thenReturn(Optional.of(existing));

        service.delete(4L);

        org.mockito.Mockito.verify(discountRepository).delete(existing);
    }

    @Test
    void delete_unknownId_throws404() {
        when(discountRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }
}
