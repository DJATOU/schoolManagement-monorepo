package com.school.management.mapper;

import com.school.management.dto.GroupDTO;
import com.school.management.dto.ParcoursDTO;
import com.school.management.dto.ParcoursYearDTO;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.LevelEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.repository.StudentGroupRepository;
import com.school.management.service.student.StudentParcoursService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests d'assemblage du parcours (Exigence 11.5).
 *
 * <p>Contrairement au test de dérivation ({@code StudentParcoursServiceTest}) qui
 * valide le regroupement/tri/omission, ce test se concentre sur la <b>correction
 * de l'assemblage des DTO</b> via les mappers réels ({@link GroupMapper},
 * {@link LeveLMapper}) : pour un cas représentatif (une année avec des groupes),
 * on vérifie que chaque {@link GroupDTO} et chaque niveau assemblé portent bien
 * les détails attendus (identifiant/libellé d'année scolaire, niveau, nom).</p>
 */
class ParcoursAssemblyTest {

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

    private static GroupEntity group(long id, String name, SchoolYearEntity year, LevelEntity level) {
        GroupEntity g = new GroupEntity();
        g.setId(id);
        g.setName(name);
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
    void getParcours_assemblesYearWithGroupAndLevelDetails() {
        SchoolYearEntity y2024 = year(20L, "2024-2025", date(2024, 9, 1));
        LevelEntity cp = level(100L, "CP");

        GroupEntity gMath = group(1L, "Maths CP", y2024, cp);
        GroupEntity gFrancais = group(2L, "Français CP", y2024, cp);

        when(studentGroupRepository.findByStudentIdAndActiveTrue(7L))
                .thenReturn(List.of(enrollment(gMath), enrollment(gFrancais)));

        ParcoursDTO parcours = service.getParcours(7L);

        assertThat(parcours.getStudentId()).isEqualTo(7L);
        assertThat(parcours.getYears()).hasSize(1);

        ParcoursYearDTO entry = parcours.getYears().get(0);
        // L'année scolaire assemblée porte identifiant et libellé.
        assertThat(entry.getSchoolYearId()).isEqualTo(20L);
        assertThat(entry.getSchoolYearLabel()).isEqualTo("2024-2025");

        // Niveau distinct assemblé via le mapper de niveau.
        assertThat(entry.getLevels()).hasSize(1);
        assertThat(entry.getLevels().get(0).getId()).isEqualTo(100L);
        assertThat(entry.getLevels().get(0).getName()).isEqualTo("CP");

        // Groupes assemblés via GroupMapper : nom, niveau et année scolaire propagés.
        assertThat(entry.getGroups()).hasSize(2);
        assertThat(entry.getGroups())
                .extracting(GroupDTO::getName)
                .containsExactly("Maths CP", "Français CP");
        assertThat(entry.getGroups())
                .allSatisfy(g -> {
                    assertThat(g.getSchoolYearId()).isEqualTo(20L);
                    assertThat(g.getSchoolYearLabel()).isEqualTo("2024-2025");
                    assertThat(g.getLevelId()).isEqualTo(100L);
                    assertThat(g.getLevelName()).isEqualTo("CP");
                });
    }
}
