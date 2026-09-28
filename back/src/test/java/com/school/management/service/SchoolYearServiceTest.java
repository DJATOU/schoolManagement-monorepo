package com.school.management.service;

import com.school.management.persistance.SchoolYearEntity;
import com.school.management.repository.SchoolYearRepository;
import com.school.management.service.exception.CustomServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Calendar;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires (JUnit 5 + Mockito) pour {@link SchoolYearService}.
 *
 * <p>Couvre le rejet des champs obligatoires manquants (Exigence 1.2), le rejet d'un
 * libellé en double (Exigence 1.4) et le fait que la première année scolaire créée
 * devienne automatiquement l'année courante (Exigence 2.3).</p>
 */
class SchoolYearServiceTest {

    private SchoolYearRepository schoolYearRepository;
    private SchoolYearService service;

    @BeforeEach
    void setUp() {
        schoolYearRepository = mock(SchoolYearRepository.class);
        when(schoolYearRepository.save(any(SchoolYearEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // Par défaut : aucun libellé existant.
        when(schoolYearRepository.findByLabel(anyString())).thenReturn(Optional.empty());
        service = new SchoolYearService(schoolYearRepository);
    }

    /** Construit une date à partir de l'année/mois/jour (mois 1-based). */
    private static Date date(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(year, month - 1, day);
        return cal.getTime();
    }

    private SchoolYearEntity year(String label, Date start, Date end) {
        return SchoolYearEntity.builder()
                .label(label)
                .startDate(start)
                .endDate(end)
                .build();
    }

    // ------------------------------------------------------------------
    // Rejet des champs obligatoires manquants (Exigence 1.2)
    // ------------------------------------------------------------------

    @Test
    void create_blankLabel_rejectedBadRequest() {
        SchoolYearEntity sy = year("   ", date(2025, 9, 1), date(2026, 6, 30));

        assertThatThrownBy(() -> service.create(sy))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_nullLabel_rejectedBadRequest() {
        SchoolYearEntity sy = year(null, date(2025, 9, 1), date(2026, 6, 30));

        assertThatThrownBy(() -> service.create(sy))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_nullStartDate_rejectedBadRequest() {
        SchoolYearEntity sy = year("2025-2026", null, date(2026, 6, 30));

        assertThatThrownBy(() -> service.create(sy))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_nullEndDate_rejectedBadRequest() {
        SchoolYearEntity sy = year("2025-2026", date(2025, 9, 1), null);

        assertThatThrownBy(() -> service.create(sy))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ------------------------------------------------------------------
    // Rejet d'un libellé en double (Exigence 1.4)
    // ------------------------------------------------------------------

    @Test
    void create_duplicateLabel_rejectedBadRequest() {
        SchoolYearEntity existing = year("2025-2026", date(2025, 9, 1), date(2026, 6, 30));
        when(schoolYearRepository.findByLabel("2025-2026")).thenReturn(Optional.of(existing));

        SchoolYearEntity duplicate = year("2025-2026", date(2025, 9, 1), date(2026, 6, 30));

        assertThatThrownBy(() -> service.create(duplicate))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ------------------------------------------------------------------
    // La première année scolaire devient courante (Exigence 2.3)
    // ------------------------------------------------------------------

    @Test
    void create_firstYear_becomesCurrent() {
        // Aucune année existante : count() == 0.
        when(schoolYearRepository.count()).thenReturn(0L);
        SchoolYearEntity sy = year("2025-2026", date(2025, 9, 1), date(2026, 6, 30));

        SchoolYearEntity saved = service.create(sy);

        assertThat(saved.getIsCurrent()).isTrue();
    }

    @Test
    void create_subsequentYear_notMarkedCurrent() {
        // Une année existe déjà : count() > 0, l'année reste non courante par défaut.
        when(schoolYearRepository.count()).thenReturn(1L);
        SchoolYearEntity sy = year("2026-2027", date(2026, 9, 1), date(2027, 6, 30));

        SchoolYearEntity saved = service.create(sy);

        assertThat(saved.getIsCurrent()).isFalse();
    }
}
