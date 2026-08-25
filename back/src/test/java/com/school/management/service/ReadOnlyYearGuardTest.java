package com.school.management.service;

import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.service.exception.NoCurrentSchoolYearException;
import com.school.management.service.exception.ReadOnlySchoolYearException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires (JUnit 5 + Mockito) pour {@link ReadOnlyYearGuard}.
 *
 * <p>Vérifie que les mutations portant sur une année scolaire autre que l'année courante sont
 * rejetées (Exigence 9.2) et que l'année d'une série/séance est résolue en remontant jusqu'au
 * groupe (Exigence 3.4).</p>
 */
class ReadOnlyYearGuardTest {

    private static final Long CURRENT_ID = 1L;
    private static final Long PAST_ID = 2L;

    private CurrentSchoolYearService currentSchoolYearService;
    private ReadOnlyYearGuard guard;

    private SchoolYearEntity currentYear;
    private SchoolYearEntity pastYear;

    @BeforeEach
    void setUp() {
        currentSchoolYearService = mock(CurrentSchoolYearService.class);
        guard = new ReadOnlyYearGuard(currentSchoolYearService);

        currentYear = SchoolYearEntity.builder().label("2025-2026").isCurrent(true).build();
        currentYear.setId(CURRENT_ID);
        pastYear = SchoolYearEntity.builder().label("2024-2025").isCurrent(false).build();
        pastYear.setId(PAST_ID);

        when(currentSchoolYearService.requireCurrent()).thenReturn(currentYear);
    }

    // ------------------------------------------------------------------
    // assertMutable
    // ------------------------------------------------------------------

    @Test
    void assertMutable_allowsCurrentYear() {
        assertThatCode(() -> guard.assertMutable(currentYear)).doesNotThrowAnyException();
    }

    @Test
    void assertMutable_rejectsPastYear() {
        assertThatThrownBy(() -> guard.assertMutable(pastYear))
                .isInstanceOf(ReadOnlySchoolYearException.class);
    }

    @Test
    void assertMutable_rejectsNullYear() {
        assertThatThrownBy(() -> guard.assertMutable(null))
                .isInstanceOf(ReadOnlySchoolYearException.class);
    }

    @Test
    void assertMutable_propagatesWhenNoCurrentYear() {
        when(currentSchoolYearService.requireCurrent())
                .thenThrow(new NoCurrentSchoolYearException());

        assertThatThrownBy(() -> guard.assertMutable(currentYear))
                .isInstanceOf(NoCurrentSchoolYearException.class);
    }

    // ------------------------------------------------------------------
    // assertGroupMutable (resolves group.schoolYear)
    // ------------------------------------------------------------------

    @Test
    void assertGroupMutable_allowsGroupInCurrentYear() {
        GroupEntity group = GroupEntity.builder().schoolYear(currentYear).build();

        assertThatCode(() -> guard.assertGroupMutable(group)).doesNotThrowAnyException();
    }

    @Test
    void assertGroupMutable_rejectsGroupInPastYear() {
        GroupEntity group = GroupEntity.builder().schoolYear(pastYear).build();

        assertThatThrownBy(() -> guard.assertGroupMutable(group))
                .isInstanceOf(ReadOnlySchoolYearException.class);
    }

    @Test
    void assertGroupMutable_rejectsGroupWithNoYear() {
        GroupEntity group = GroupEntity.builder().build();

        assertThatThrownBy(() -> guard.assertGroupMutable(group))
                .isInstanceOf(ReadOnlySchoolYearException.class);
    }

    // ------------------------------------------------------------------
    // assertSeriesMutable (resolves series.group.schoolYear)
    // ------------------------------------------------------------------

    @Test
    void assertSeriesMutable_allowsSeriesInCurrentYear() {
        GroupEntity group = GroupEntity.builder().schoolYear(currentYear).build();
        SessionSeriesEntity series = SessionSeriesEntity.builder().group(group).build();

        assertThatCode(() -> guard.assertSeriesMutable(series)).doesNotThrowAnyException();
    }

    @Test
    void assertSeriesMutable_rejectsSeriesInPastYear() {
        GroupEntity group = GroupEntity.builder().schoolYear(pastYear).build();
        SessionSeriesEntity series = SessionSeriesEntity.builder().group(group).build();

        assertThatThrownBy(() -> guard.assertSeriesMutable(series))
                .isInstanceOf(ReadOnlySchoolYearException.class);
    }

    // ------------------------------------------------------------------
    // assertSessionMutable (resolves session.series.group.schoolYear)
    // ------------------------------------------------------------------

    @Test
    void assertSessionMutable_allowsSessionInCurrentYearViaSeries() {
        GroupEntity group = GroupEntity.builder().schoolYear(currentYear).build();
        SessionSeriesEntity series = SessionSeriesEntity.builder().group(group).build();
        SessionEntity session = SessionEntity.builder().sessionSeries(series).build();

        assertThatCode(() -> guard.assertSessionMutable(session)).doesNotThrowAnyException();
    }

    @Test
    void assertSessionMutable_rejectsSessionInPastYearViaSeries() {
        GroupEntity group = GroupEntity.builder().schoolYear(pastYear).build();
        SessionSeriesEntity series = SessionSeriesEntity.builder().group(group).build();
        SessionEntity session = SessionEntity.builder().sessionSeries(series).build();

        assertThatThrownBy(() -> guard.assertSessionMutable(session))
                .isInstanceOf(ReadOnlySchoolYearException.class);
    }

    @Test
    void assertSessionMutable_fallsBackToDirectGroupWhenNoSeries() {
        GroupEntity group = GroupEntity.builder().schoolYear(currentYear).build();
        SessionEntity session = SessionEntity.builder().group(group).build();

        assertThatCode(() -> guard.assertSessionMutable(session)).doesNotThrowAnyException();
    }
}
