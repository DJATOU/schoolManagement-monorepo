package com.school.management.service;

import com.school.management.dto.DashboardStatsDTO;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.persistance.StudentStatus;
import com.school.management.persistance.TeacherEntity;
import com.school.management.repository.TeacherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Effectifs du tableau de bord, sur une vraie base H2 : enseignants, groupes, groupes en activité.
 *
 * <p>Le cas réel qui a fait douter des chiffres : seize groupes importés, dont un seul avec des
 * élèves, et le fichier des enseignants importé deux fois. Le tableau de bord comptait juste, mais
 * « 16 groupes » ne disait pas qu'un seul était ouvert.</p>
 */
@DataJpaTest
@Import({ DashboardStatsService.class, CurrentSchoolYearService.class })
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@DisplayName("Tableau de bord : enseignants et groupes")
class DashboardStatsServiceIntegrationTest {

    @Autowired private TestEntityManager em;
    @Autowired private DashboardStatsService dashboard;
    @Autowired private TeacherRepository teacherRepository;

    private SchoolYearEntity current;
    private SchoolYearEntity past;

    @BeforeEach
    void effectifs() {
        current = em.persist(SchoolYearEntity.builder().label("2026-2027").startDate(new Date()).endDate(new Date())
                .isCurrent(true).build());
        past = em.persist(SchoolYearEntity.builder().label("2025-2026").startDate(new Date()).endDate(new Date())
                .isCurrent(false).build());

        TeacherEntity yasmine = em.persist(TeacherEntity.builder().firstName("Yasmine").lastName("Belaïd").build());
        em.persist(TeacherEntity.builder().firstName("Karim").lastName("Haddad").build());
        TeacherEntity retired = em.persist(TeacherEntity.builder().firstName("Omar").lastName("Bendjedid").build());

        // Année courante : un groupe avec deux élèves, un dont le seul élève est parti, un vide,
        // un désactivé qui avait des élèves.
        GroupEntity english = group("Anglais 1 AS", yasmine, current);
        enrol(english, student("Camille", "Amrani"), true);
        enrol(english, student("Nathan", "Belhadj"), true);
        enrol(group("Maths 1 AS", null, current), student("Walid", "Benamara"), false);
        group("Physique 1 AS", null, current);
        GroupEntity closed = group("Arabe 1 AS", null, current);
        enrol(closed, student("Aya", "Messaoudi"), true);
        // Année passée : un groupe avec un élève.
        enrol(group("Anglais 4 AM", null, past), student("Théo", "Lounis"), true);

        em.flush();
        closed.setActive(false);
        retired.setActive(false);
        em.flush();
    }

    private GroupEntity group(String name, TeacherEntity teacher, SchoolYearEntity year) {
        return em.persist(GroupEntity.builder().name(name).teacher(teacher).schoolYear(year).build());
    }

    private StudentEntity student(String first, String last) {
        StudentEntity student = StudentEntity.builder().status(StudentStatus.ACTIVE).build();
        student.setFirstName(first);
        student.setLastName(last);
        return em.persist(student);
    }

    /** {@code BaseEntity.onCreate} force l'inscription active : une inscription close est repassée ensuite. */
    private void enrol(GroupEntity group, StudentEntity student, boolean active) {
        StudentGroupEntity enrolment = em.persist(StudentGroupEntity.builder().group(group).student(student).build());
        if (!active) {
            em.flush();
            enrolment.setActive(false);
        }
    }

    @Test
    @DisplayName("année courante : enseignants actifs, groupes de l'année, dont en activité")
    void currentYear() {
        DashboardStatsDTO stats = dashboard.getStats(null, null, current.getId());

        assertThat(stats.getTotalTeachers()).isEqualTo(2);
        assertThat(stats.getTotalGroups()).isEqualTo(3);
        assertThat(stats.getActiveGroups()).isEqualTo(1);
    }

    @Test
    @DisplayName("sans année : tous les groupes actifs, dont ceux qui ont un élève, toutes années")
    void allYears() {
        DashboardStatsDTO stats = dashboard.getStats(null, null, null);

        assertThat(stats.getTotalGroups()).isEqualTo(4);
        assertThat(stats.getActiveGroups()).isEqualTo(2);
    }

    @Test
    @DisplayName("année passée : ses groupes, dont en activité")
    void pastYear() {
        DashboardStatsDTO stats = dashboard.getStats(null, null, past.getId());

        assertThat(stats.getTotalGroups()).isEqualTo(1);
        assertThat(stats.getActiveGroups()).isEqualTo(1);
    }

    @Test
    @DisplayName("enseignant déjà présent : reconnu malgré la casse et les espaces de bord")
    void teacherLookupIgnoresCaseAndEdgeSpaces() {
        assertThat(teacherRepository.existsByFullName("Yasmine", "Belaïd")).isTrue();
        assertThat(teacherRepository.existsByFullName(" yasmine ", "BELAÏD ")).isTrue();
        assertThat(teacherRepository.existsByFullName("Yasmine", "Haddad")).isFalse();
        assertThat(teacherRepository.existsByFullName("Nadia", "Aït Ahmed")).isFalse();
    }
}
