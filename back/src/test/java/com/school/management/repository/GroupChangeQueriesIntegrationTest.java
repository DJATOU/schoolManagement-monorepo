package com.school.management.repository;

import com.school.management.domain.valueobject.EnrolmentWindow;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.service.group.GroupChangeDetector;
import com.school.management.service.group.GroupChangeDetector.GroupChange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests d'intégration (H2) du signalement de changement de groupe (exigences 10.1, 10.3).
 *
 * <p>Les tests unitaires du détecteur simulent les dépôts : ils vérifient la règle, pas les deux
 * requêtes qu'elle consomme. Ce test comble exactement ce trou, et il valide surtout
 * l'<strong>hypothèse de représentation</strong> sur laquelle repose tout le composant : une
 * clôture d'inscription est une ligne {@code active = false} datée par sa Date_Sortie
 * ({@code date_left}), un jour stocké à 00:00. Si cette hypothèse était fausse, le détecteur
 * daterait les clôtures au mauvais mois sans qu'aucun test simulé ne s'en aperçoive.</p>
 *
 * <p>La clôture est donc produite ici comme en production : ligne gérée désactivée et datée, puis
 * {@code flush}, ce que fait {@code EnrolmentCorrectionService.setDeparture}.</p>
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class GroupChangeQueriesIntegrationTest {

    @Autowired private TestEntityManager em;
    @Autowired private StudentGroupRepository studentGroupRepository;
    @Autowired private AttendanceRepository attendanceRepository;

    private GroupChangeDetector detector;
    private StudentEntity student;
    private GroupEntity maths;
    private GroupEntity physique;

    /** Mois des données ; la clôture est datée par sa Date_Sortie, posée dans ce mois. */
    private final YearMonth currentMonth = YearMonth.now();

    @BeforeEach
    void setUp() {
        detector = new GroupChangeDetector(studentGroupRepository, attendanceRepository);
        student = em.persist(StudentEntity.builder().firstName("Nour").lastName("Belkacem").build());
        maths = em.persist(GroupEntity.builder().name("Maths 1B").build());
        physique = em.persist(GroupEntity.builder().name("Physique 1B").build());
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    /** Inscription active dans un groupe ; la date d'arrivée fournie est conservée (5.1). */
    private StudentGroupEntity enrol(GroupEntity group, LocalDate assignedOn) {
        StudentGroupEntity enrolment = em.persist(StudentGroupEntity.builder()
                .student(student)
                .group(group)
                .dateAssigned(toDate(assignedOn))
                .build());
        em.flush();
        return enrolment;
    }

    /**
     * Clôture d'inscription, par le même chemin que {@code EnrolmentCorrectionService.setDeparture}. La date est
     * posée avec une heure : le stockage doit la ramener au jour.
     */
    private StudentGroupEntity close(StudentGroupEntity enrolment, LocalDate leftOn) {
        enrolment.setActive(false);
        enrolment.setDateLeft(Date.from(leftOn.atTime(17, 45).atZone(ZoneId.systemDefault()).toInstant()));
        em.flush();
        return enrolment;
    }

    private SessionEntity persistSession(GroupEntity group, LocalDate day) {
        SessionSeriesEntity series = em.persist(SessionSeriesEntity.builder()
                .name("Série " + group.getName())
                .group(group)
                .totalSessions(4)
                .serieTimeStart(new Date())
                .build());
        return em.persist(SessionEntity.builder()
                .title("Séance " + day)
                .group(group)
                .sessionSeries(series)
                .sessionTimeStart(toDate(day))
                .build());
    }

    private void persistAttendance(GroupEntity group, LocalDate day, boolean present) {
        SessionEntity session = persistSession(group, day);
        em.persist(AttendanceEntity.builder()
                .student(student)
                .session(session)
                .sessionSeries(session.getSessionSeries())
                .group(group)
                .isPresent(present)
                .build());
    }

    private static Date toDate(LocalDate day) {
        return Date.from(day.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    /** Borne haute du mois, identique à celle que pose le détecteur. */
    private Date endOfMonth() {
        return Date.from(currentMonth.atEndOfMonth().atTime(LocalTime.MAX)
                .atZone(ZoneId.systemDefault()).toInstant());
    }

    // ------------------------------------------------------------------
    // Représentation de la clôture en base
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Une clôture est une ligne active = false, datée par sa Date_Sortie à 00:00 ; "
            + "findByStudentId la restitue là où findByStudentIdAndActiveTrue l'ignore")
    void closureIsRepresentedByInactiveRowWithDepartureDay() {
        LocalDate arrival = currentMonth.atDay(1);
        LocalDate departure = currentMonth.atDay(Math.min(12, currentMonth.lengthOfMonth()));
        StudentGroupEntity enrolment = close(enrol(maths, arrival), departure);
        em.clear();

        StudentGroupEntity reloaded = em.find(StudentGroupEntity.class, enrolment.getId());
        assertThat(reloaded.getActive()).isFalse();
        assertThat(reloaded.getDateLeft().getTime())
                .as("le jour du départ, à 00:00 : l'heure de la saisie est effacée (D1)")
                .isEqualTo(toDate(departure).getTime());
        assertThat(reloaded.getDateAssigned().getTime()).isEqualTo(toDate(arrival).getTime());
        assertThat(reloaded.window().describe())
                .isEqualTo("du " + EnrolmentWindow.format(arrival) + " au " + EnrolmentWindow.format(departure));

        assertThat(studentGroupRepository.findByStudentId(student.getId())).hasSize(1);
        assertThat(studentGroupRepository.findByStudentIdAndActiveTrue(student.getId())).isEmpty();
    }

    // ------------------------------------------------------------------
    // Décompte des séances suivies sur le mois
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Le décompte ne retient que les présences actives du groupe sur le mois")
    void attendanceCountIsScopedToGroupPresenceAndMonth() {
        LocalDate inMonth = currentMonth.atDay(Math.min(15, currentMonth.lengthOfMonth()));
        LocalDate previousMonth = currentMonth.minusMonths(1).atDay(15);

        persistAttendance(maths, inMonth, true);
        persistAttendance(maths, inMonth, true);
        persistAttendance(maths, inMonth, false);          // absent : hors décompte
        persistAttendance(maths, previousMonth, true);     // autre mois : hors décompte
        persistAttendance(physique, inMonth, true);        // autre groupe : hors décompte
        em.flush();

        long count = attendanceRepository.countPresentForStudentAndGroupBetween(
                student.getId(), maths.getId(),
                toDate(currentMonth.atDay(1)), endOfMonth());

        assertThat(count).isEqualTo(2);
    }

    // ------------------------------------------------------------------
    // Bout en bout
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Départ de maths et arrivée en physique le même mois : signalement porté par "
            + "les données réelles, avec les décomptes de chaque groupe")
    void detectorFlagsRealGroupChange() {
        LocalDate day = currentMonth.atDay(Math.min(10, currentMonth.lengthOfMonth()));
        close(enrol(maths, currentMonth.atDay(1)), day);
        enrol(physique, day);
        persistAttendance(maths, day, true);
        persistAttendance(maths, day, true);
        persistAttendance(physique, day, true);
        em.flush();
        em.clear();

        List<GroupChange> changes = detector.detect(student.getId());

        assertThat(changes).hasSize(1);
        GroupChange change = changes.get(0);
        assertThat(change.yearMonth()).isEqualTo(currentMonth);
        assertThat(change.leftGroup().groupName()).isEqualTo("Maths 1B");
        assertThat(change.leftGroup().attendedCount()).isEqualTo(2);
        assertThat(change.joinedGroup().groupName()).isEqualTo("Physique 1B");
        assertThat(change.joinedGroup().attendedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Étudiant inscrit à deux matières sans aucune clôture : aucun signalement")
    void detectorIgnoresSimultaneousEnrolments() {
        enrol(maths, currentMonth.atDay(1));
        enrol(physique, currentMonth.atDay(2));
        em.flush();
        em.clear();

        assertThat(detector.detect(student.getId())).isEmpty();
    }
}
