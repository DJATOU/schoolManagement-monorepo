package com.school.management.service;

import com.school.management.persistance.SchoolYearEntity;
import com.school.management.repository.SchoolYearRepository;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.exception.NoCurrentSchoolYearException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires (JUnit 5 + Mockito) pour {@link CurrentSchoolYearService}.
 *
 * <p>Couvre la recherche de l'année courante (Exigence 2.5) et le rejet de toute opération
 * qui laisserait aucune année courante (Exigence 2.4).</p>
 */
class CurrentSchoolYearServiceTest {

    private SchoolYearRepository schoolYearRepository;
    private CurrentSchoolYearService service;

    @BeforeEach
    void setUp() {
        schoolYearRepository = mock(SchoolYearRepository.class);
        when(schoolYearRepository.save(any(SchoolYearEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        service = new CurrentSchoolYearService(schoolYearRepository);
    }

    /** Construit une date à partir de l'année/mois/jour (mois 1-based). */
    private static Date date(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(year, month - 1, day);
        return cal.getTime();
    }

    private SchoolYearEntity year(String label, boolean isCurrent) {
        return SchoolYearEntity.builder()
                .label(label)
                .startDate(date(2025, 9, 1))
                .endDate(date(2026, 6, 30))
                .isCurrent(isCurrent)
                .build();
    }

    // ------------------------------------------------------------------
    // Recherche de l'année courante (Exigence 2.5)
    // ------------------------------------------------------------------

    @Test
    void findCurrent_returnsTheCurrentYear() {
        SchoolYearEntity current = year("2025-2026", true);
        when(schoolYearRepository.findByIsCurrentTrue()).thenReturn(Optional.of(current));

        Optional<SchoolYearEntity> result = service.findCurrent();

        assertThat(result).containsSame(current);
    }

    @Test
    void findCurrent_returnsEmptyWhenNoneDefined() {
        when(schoolYearRepository.findByIsCurrentTrue()).thenReturn(Optional.empty());

        Optional<SchoolYearEntity> result = service.findCurrent();

        assertThat(result).isEmpty();
    }

    @Test
    void requireCurrent_returnsTheCurrentYear() {
        SchoolYearEntity current = year("2025-2026", true);
        when(schoolYearRepository.findByIsCurrentTrue()).thenReturn(Optional.of(current));

        SchoolYearEntity result = service.requireCurrent();

        assertThat(result).isSameAs(current);
    }

    @Test
    void requireCurrent_throwsWhenNoneDefined() {
        when(schoolYearRepository.findByIsCurrentTrue()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireCurrent())
                .isInstanceOf(NoCurrentSchoolYearException.class);
    }

    // ------------------------------------------------------------------
    // Rejet d'une opération laissant aucune année courante (Exigence 2.4)
    // ------------------------------------------------------------------

    @Test
    void makeCurrent_null_isRejected() {
        assertThatThrownBy(() -> service.makeCurrent(null))
                .isInstanceOf(CustomServiceException.class);

        // Aucune écriture ne doit avoir lieu lorsque la cible est nulle.
        verify(schoolYearRepository, never()).save(any(SchoolYearEntity.class));
    }

    @Test
    void makeCurrent_flipsPreviousCurrentToFalseAndTargetToTrue() {
        SchoolYearEntity previous = year("2025-2026", true);
        SchoolYearEntity target = year("2026-2027", false);
        when(schoolYearRepository.findByIsCurrentTrue()).thenReturn(Optional.of(previous));

        service.makeCurrent(target);

        assertThat(previous.getIsCurrent()).isFalse();
        assertThat(target.getIsCurrent()).isTrue();
    }
}
