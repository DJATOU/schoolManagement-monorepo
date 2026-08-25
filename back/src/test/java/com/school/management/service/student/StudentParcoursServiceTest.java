package com.school.management.service.student;

import com.school.management.dto.ParcoursDTO;
import com.school.management.dto.ParcoursYearDTO;
import com.school.management.mapper.GroupMapper;
import com.school.management.mapper.GroupMapperImpl;
import com.school.management.mapper.LeveLMapper;
import com.school.management.mapper.LeveLMapperImpl;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.LevelEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.repository.StudentGroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires (JUnit 5 + Mockito) pour {@link StudentParcoursService}.
 *
 * <p>Couvre la dérivation du parcours à partir des inscriptions actives :
 * regroupement par année scolaire, niveaux distincts (un seul / plusieurs),
 * omission des années sans inscription et tri par date de début décroissante.</p>
 */
class StudentParcoursServiceTest {

    private StudentGroupRepository studentGroupRepository;
    private StudentParcoursService service;

    @BeforeEach
    void setUp() {
        studentGroupRepository = mock(StudentGroupRepository.class);
        GroupMapper groupMapper = new GroupMapperImpl();
        LeveLMapper levelMapper = new LeveLMapperImpl();
        service = new StudentParcoursService(studentGroupRepository, groupMapper, levelMapper);
    }

    private static Date date(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(year, month - 1, day);
        return cal.getTime();
    }

    private static LevelEntity level(long id, String name) {
        LevelEntity level = new LevelEntity();
        level.setId(id);
        level.setName(name);
        return level;
    }

    private static SchoolYearEntity year(long id, String label, Date start) {
        SchoolYearEntity y = new SchoolYearEntity();
        y.setId(id);
        y.setLabel(label);
        y.setStartDate(start);
        return y;
    }

    private static GroupEntity group(long id, SchoolYearEntity year, LevelEntity level) {
        GroupEntity g = new GroupEntity();
        g.setId(id);
        g.setName("G" + id);
        g.setSchoolYear(year);
        g.setLevel(level);
        return g;
    }

    private static StudentGroupEntity enrollment(GroupEntity group) {
        StudentGroupEntity sg = new StudentGroupEntity();
        sg.setGroup(group);
        return sg;
    }

    @Test
    void getParcours_returnsEmptyYears_whenNoEnrollment() {
        when(studentGroupRepository.findByStudentIdAndActiveTrue(1L)).thenReturn(List.of());

        ParcoursDTO parcours = service.getParcours(1L);

        assertThat(parcours.getStudentId()).isEqualTo(1L);
        assertThat(parcours.getYears()).isEmpty();
    }

    @Test
    void getParcours_groupsByYear_ordersByStartDateDescending() {
        SchoolYearEntity y2023 = year(10L, "2023-2024", date(2023, 9, 1));
        SchoolYearEntity y2024 = year(20L, "2024-2025", date(2024, 9, 1));
        LevelEntity cp = level(100L, "CP");
        LevelEntity ce1 = level(200L, "CE1");

        GroupEntity g2023 = group(1L, y2023, cp);
        GroupEntity g2024 = group(2L, y2024, ce1);

        // Ordre d'entrée volontairement croissant pour vérifier le tri décroissant.
        when(studentGroupRepository.findByStudentIdAndActiveTrue(1L))
                .thenReturn(List.of(enrollment(g2023), enrollment(g2024)));

        ParcoursDTO parcours = service.getParcours(1L);

        assertThat(parcours.getYears()).hasSize(2);
        // Année la plus récente en premier (2024-2025), puis 2023-2024.
        assertThat(parcours.getYears().get(0).getSchoolYearId()).isEqualTo(20L);
        assertThat(parcours.getYears().get(1).getSchoolYearId()).isEqualTo(10L);
    }

    @Test
    void getParcours_reportsSingleLevel_whenGroupsShareLevel() {
        SchoolYearEntity y2024 = year(20L, "2024-2025", date(2024, 9, 1));
        LevelEntity cp = level(100L, "CP");

        GroupEntity gMath = group(1L, y2024, cp);
        GroupEntity gFrancais = group(2L, y2024, cp);

        when(studentGroupRepository.findByStudentIdAndActiveTrue(1L))
                .thenReturn(List.of(enrollment(gMath), enrollment(gFrancais)));

        ParcoursDTO parcours = service.getParcours(1L);

        assertThat(parcours.getYears()).hasSize(1);
        ParcoursYearDTO entry = parcours.getYears().get(0);
        assertThat(entry.getLevels()).hasSize(1);
        assertThat(entry.getLevels().get(0).getId()).isEqualTo(100L);
        assertThat(entry.getGroups()).hasSize(2);
    }

    @Test
    void getParcours_reportsMultipleLevels_whenGroupsSpanLevels() {
        SchoolYearEntity y2024 = year(20L, "2024-2025", date(2024, 9, 1));
        LevelEntity cp = level(100L, "CP");
        LevelEntity ce1 = level(200L, "CE1");

        GroupEntity g1 = group(1L, y2024, cp);
        GroupEntity g2 = group(2L, y2024, ce1);

        when(studentGroupRepository.findByStudentIdAndActiveTrue(1L))
                .thenReturn(List.of(enrollment(g1), enrollment(g2)));

        ParcoursDTO parcours = service.getParcours(1L);

        assertThat(parcours.getYears()).hasSize(1);
        assertThat(parcours.getYears().get(0).getLevels())
                .extracting(l -> l.getId())
                .containsExactlyInAnyOrder(100L, 200L);
    }

    @Test
    void getParcours_ignoresEnrollmentsWithoutSchoolYear() {
        SchoolYearEntity y2024 = year(20L, "2024-2025", date(2024, 9, 1));
        LevelEntity cp = level(100L, "CP");

        GroupEntity withYear = group(1L, y2024, cp);
        GroupEntity withoutYear = group(2L, null, cp); // année scolaire absente

        when(studentGroupRepository.findByStudentIdAndActiveTrue(1L))
                .thenReturn(List.of(enrollment(withYear), enrollment(withoutYear)));

        ParcoursDTO parcours = service.getParcours(1L);

        // Seule l'année scolaire renseignée apparaît (l'inscription sans année est omise).
        assertThat(parcours.getYears()).hasSize(1);
        assertThat(parcours.getYears().get(0).getSchoolYearId()).isEqualTo(20L);
    }
}
