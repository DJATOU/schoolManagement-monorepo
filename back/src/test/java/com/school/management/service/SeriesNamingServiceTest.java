package com.school.management.service;

import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.repository.SessionSeriesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires (JUnit 5 + Mockito) pour {@link SeriesNamingService}.
 *
 * <p>Couvre : première série d'un mois → 001, N-ième série → N+1, redémarrage au
 * changement de mois, remplissage par des zéros (009→010, 099→100) et le format complet
 * de {@link SeriesNamingService#buildName}.</p>
 */
class SeriesNamingServiceTest {

    private static final long GROUP_ID = 7L;

    private SessionSeriesRepository repository;
    private SeriesNamingService service;

    @BeforeEach
    void setUp() {
        repository = mock(SessionSeriesRepository.class);
        service = new SeriesNamingService(repository);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Date dateAt(int year, int month, int day) {
        LocalDate ld = LocalDate.of(year, month, day);
        return Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static SessionSeriesEntity seriesAt(int year, int month, int day) {
        SessionSeriesEntity s = new SessionSeriesEntity();
        s.setSerieTimeStart(dateAt(year, month, day));
        return s;
    }

    private static List<SessionSeriesEntity> seriesInMonth(int year, int month, int count) {
        List<SessionSeriesEntity> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(seriesAt(year, month, 1 + (i % 27)));
        }
        return list;
    }

    private GroupEntity group(String name) {
        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);
        group.setName(name);
        return group;
    }

    // ------------------------------------------------------------------
    // nextSequenceNumber
    // ------------------------------------------------------------------

    @Test
    void nextSequenceNumber_firstInMonth_returnsOne() {
        when(repository.findByGroupId(GROUP_ID)).thenReturn(List.of());

        assertThat(service.nextSequenceNumber(GROUP_ID, dateAt(2026, 3, 10))).isEqualTo(1);
    }

    @Test
    void nextSequenceNumber_nthInMonth_returnsCountPlusOne() {
        when(repository.findByGroupId(GROUP_ID)).thenReturn(seriesInMonth(2026, 3, 4));

        assertThat(service.nextSequenceNumber(GROUP_ID, dateAt(2026, 3, 20))).isEqualTo(5);
    }

    @Test
    void nextSequenceNumber_monthChange_restartsAtOne() {
        // 6 séries en mars 2026 ; une nouvelle série en avril 2026 redémarre à 1.
        when(repository.findByGroupId(GROUP_ID)).thenReturn(seriesInMonth(2026, 3, 6));

        assertThat(service.nextSequenceNumber(GROUP_ID, dateAt(2026, 4, 1))).isEqualTo(1);
    }

    @Test
    void nextSequenceNumber_sameMonthDifferentYear_doesNotCount() {
        // Séries en mars 2025 ne comptent pas pour mars 2026.
        when(repository.findByGroupId(GROUP_ID)).thenReturn(seriesInMonth(2025, 3, 5));

        assertThat(service.nextSequenceNumber(GROUP_ID, dateAt(2026, 3, 1))).isEqualTo(1);
    }

    @Test
    void nextSequenceNumber_mixedMonths_countsOnlyTargetMonth() {
        List<SessionSeriesEntity> mixed = new ArrayList<>();
        mixed.addAll(seriesInMonth(2026, 3, 2)); // mois cible
        mixed.addAll(seriesInMonth(2026, 2, 3)); // mois précédent
        mixed.addAll(seriesInMonth(2026, 4, 4)); // mois suivant
        when(repository.findByGroupId(GROUP_ID)).thenReturn(mixed);

        assertThat(service.nextSequenceNumber(GROUP_ID, dateAt(2026, 3, 15))).isEqualTo(3);
    }

    @Test
    void nextSequenceNumber_ignoresNullSerieTimeStart() {
        List<SessionSeriesEntity> list = new ArrayList<>(seriesInMonth(2026, 3, 2));
        list.add(new SessionSeriesEntity()); // serieTimeStart == null
        when(repository.findByGroupId(GROUP_ID)).thenReturn(list);

        assertThat(service.nextSequenceNumber(GROUP_ID, dateAt(2026, 3, 15))).isEqualTo(3);
    }

    @Test
    void nextSequenceNumber_nullGroupId_throws() {
        assertThatThrownBy(() -> service.nextSequenceNumber(null, dateAt(2026, 3, 1)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nextSequenceNumber_nullDate_throws() {
        assertThatThrownBy(() -> service.nextSequenceNumber(GROUP_ID, null))
                .isInstanceOf(NullPointerException.class);
    }

    // ------------------------------------------------------------------
    // buildName — format complet et remplissage par zéros
    // ------------------------------------------------------------------

    @Test
    void buildName_fullFormat_firstInMonth() {
        when(repository.findByGroupId(GROUP_ID)).thenReturn(List.of());

        String name = service.buildName(group("Math-A"), dateAt(2026, 3, 5));

        assertThat(name).isEqualTo("Math-A - 03-2026-001");
    }

    @Test
    void buildName_zeroPadding_009to010() {
        // 9 séries existantes dans le mois → prochaine séquence = 10 → "010".
        when(repository.findByGroupId(GROUP_ID)).thenReturn(seriesInMonth(2026, 3, 9));

        String name = service.buildName(group("G1"), dateAt(2026, 3, 28));

        assertThat(name).isEqualTo("G1 - 03-2026-010");
    }

    @Test
    void buildName_zeroPadding_099to100() {
        // 99 séries existantes dans le mois → prochaine séquence = 100 → "100".
        when(repository.findByGroupId(GROUP_ID)).thenReturn(seriesInMonth(2026, 3, 99));

        String name = service.buildName(group("G1"), dateAt(2026, 3, 28));

        assertThat(name).isEqualTo("G1 - 03-2026-100");
    }

    @Test
    void buildName_twoDigitMonth_december() {
        when(repository.findByGroupId(GROUP_ID)).thenReturn(List.of());

        String name = service.buildName(group("Physique"), dateAt(2026, 12, 1));

        assertThat(name).isEqualTo("Physique - 12-2026-001");
    }

    @Test
    void buildName_usesFirstSessionMonth_notCreationMonth() {
        // Séance planifiée en septembre, saisie (par exemple) en août : le nom doit porter
        // le mois de la SÉANCE. C'est la date passée à buildName qui décide, jamais « now ».
        when(repository.findByGroupId(GROUP_ID)).thenReturn(List.of());

        String name = service.buildName(group("Groupe arabe2"), dateAt(2026, 9, 1));

        assertThat(name).isEqualTo("Groupe arabe2 - 09-2026-001");
    }

    @Test
    void buildName_isLanguageNeutral_noWordAndNumericMonth() {
        // Aucun mot traduisible et un mois numérique : le nom stocké reste lisible en
        // français comme en anglais, et l'interface préfixe « Série » / « Series ».
        when(repository.findByGroupId(GROUP_ID)).thenReturn(List.of());

        String name = service.buildName(group("G1"), dateAt(2026, 8, 20));

        assertThat(name)
                .doesNotContain("Série")
                .doesNotContain("Series")
                .doesNotContain("August")
                .doesNotContain("août")
                .isEqualTo("G1 - 08-2026-001");
    }

    @Test
    void buildName_nullGroup_throws() {
        assertThatThrownBy(() -> service.buildName(null, dateAt(2026, 3, 1)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void buildName_nullDate_throws() {
        assertThatThrownBy(() -> service.buildName(group("G1"), null))
                .isInstanceOf(NullPointerException.class);
    }
}
