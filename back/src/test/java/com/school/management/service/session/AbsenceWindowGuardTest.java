package com.school.management.service.session;

import com.school.management.dto.session.RejectedAbsenceDTO;
import com.school.management.dto.session.RejectedAbsenceDTO.Reason;
import com.school.management.dto.session.RollCallDTO;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.repository.StudentGroupRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Cas limites du garde de fenêtre, dépôt simulé. Le comportement sur chaque point d'entrée est
 * éprouvé de bout en bout par {@code AbsenceWindowEndpointIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AbsenceWindowGuard — cas limites")
class AbsenceWindowGuardTest {

    private static final long GROUP_ID = 5L;
    private static final LocalDate DAY = LocalDate.of(2030, 1, 14);

    @Mock private StudentGroupRepository studentGroupRepository;
    @InjectMocks private AbsenceWindowGuard guard;

    private static Date at(LocalDate day) {
        return Date.from(day.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant());
    }

    private static GroupEntity group() {
        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);
        group.setName("Math 1ère A");
        return group;
    }

    private static StudentEntity student(Long id, String firstName, String lastName) {
        StudentEntity student = new StudentEntity();
        student.setId(id);
        student.setFirstName(firstName);
        student.setLastName(lastName);
        return student;
    }

    private static SessionEntity session(GroupEntity group, Date start) {
        SessionEntity session = new SessionEntity();
        session.setId(100L);
        session.setGroup(group);
        session.setSessionTimeStart(start);
        return session;
    }

    private static AttendanceEntity absence(StudentEntity student, SessionEntity session) {
        AttendanceEntity attendance = new AttendanceEntity();
        attendance.setStudent(student);
        attendance.setSession(session);
        attendance.setIsPresent(false);
        return attendance;
    }

    private static StudentGroupEntity enrolment(LocalDate arrival, LocalDate departure) {
        StudentGroupEntity enrolment = new StudentGroupEntity();
        enrolment.setDateAssigned(arrival == null ? null : at(arrival));
        enrolment.setDateLeft(departure == null ? null : at(departure));
        return enrolment;
    }

    @Test
    @DisplayName("présence, ou ligne sans étudiant ou sans séance : non jugées, sans lecture des inscriptions")
    void presencesAndIncompleteLinesAreNotJudged() {
        AttendanceEntity presence = absence(student(1L, "Amine", "Belkacem"), session(group(), at(DAY)));
        presence.setIsPresent(true);

        assertThat(guard.rejectedAbsences(List.of(
                presence,
                absence(null, session(group(), at(DAY))),
                absence(student(1L, "Amine", "Belkacem"), null)))).isEmpty();
        verifyNoInteractions(studentGroupRepository);
    }

    @Test
    @DisplayName("séance sans groupe : refusée, sans lecture des inscriptions, message dédié")
    void sessionWithoutGroup() {
        List<RejectedAbsenceDTO> rejected = guard.rejectedAbsences(List.of(
                absence(student(1L, "Amine", "Belkacem"), session(null, at(DAY)))));

        assertThat(rejected).singleElement().satisfies(line -> {
            assertThat(line.reason()).isEqualTo(Reason.NOT_ENROLLED);
            assertThat(line.message()).isEqualTo(
                    "Absence de Amine Belkacem le 14/01/2030 : la séance n'est rattachée à aucun groupe.");
        });
        verifyNoInteractions(studentGroupRepository);
    }

    @Test
    @DisplayName("groupe de la Série, à défaut du sien")
    void groupComesFromTheSeries() {
        SessionSeriesEntity series = new SessionSeriesEntity();
        series.setGroup(group());
        SessionEntity session = session(null, at(DAY));
        session.setSessionSeries(series);
        when(studentGroupRepository.findByGroupIdAndStudentId(GROUP_ID, 1L))
                .thenReturn(List.of(enrolment(DAY, null)));

        assertThat(guard.rejectedAbsences(List.of(absence(student(1L, "Amine", "Belkacem"), session)))).isEmpty();
    }

    @Test
    @DisplayName("groupe ou étudiant sans identifiant : aucune inscription lisible, refusée")
    void missingIdentifiers() {
        GroupEntity unsaved = group();
        unsaved.setId(null);

        assertThat(guard.rejectedAbsences(List.of(
                absence(student(1L, "Amine", "Belkacem"), session(unsaved, at(DAY))),
                absence(student(null, "Lina", "Haddad"), session(group(), at(DAY))))))
                .extracting(RejectedAbsenceDTO::reason)
                .containsExactly(Reason.NOT_ENROLLED, Reason.NOT_ENROLLED);
        verifyNoInteractions(studentGroupRepository);
    }

    @Test
    @DisplayName("séance non datée, nom manquant, inscription non datée : refusée et dite comme telle")
    void undatedSessionAndMissingNames() {
        when(studentGroupRepository.findByGroupIdAndStudentId(GROUP_ID, 1L))
                .thenReturn(Arrays.asList(enrolment(DAY.minusDays(30), null), enrolment(null, null)));

        RejectedAbsenceDTO line = guard.rejectedAbsences(List.of(
                absence(student(1L, null, "Belkacem"), session(group(), null)))).get(0);

        assertThat(line.sessionDay()).isNull();
        assertThat(line.windows()).containsExactly(
                new RollCallDTO.Window(null, null), new RollCallDTO.Window(DAY.minusDays(30), null));
        assertThat(line.message()).isEqualTo("Absence de Belkacem à une séance non datée : hors de son inscription "
                + "au groupe « Math 1ère A » (non datée ; à partir du 15/12/2029).");

        assertThat(guard.rejectedAbsences(List.of(absence(student(1L, "Amine", null), session(group(), null)))))
                .singleElement().satisfies(other -> assertThat(other.message()).startsWith("Absence de Amine "));
    }

    @Test
    @DisplayName("feuille sans absence refusée : aucun refus levé, quel que soit le point d'entrée")
    void nothingToRefuse() {
        assertThatCode(() -> guard.assertSubmittedAbsencesConcerned(List.of())).doesNotThrowAnyException();
        assertThatCode(() -> guard.assertMovedSessionAbsencesConcerned(List.of())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("refus : 409, lignes conservées, message selon le point d'entrée et le nombre")
    void refusalMessages() {
        StudentEntity amine = student(1L, "Amine", "Belkacem");
        StudentEntity lina = student(2L, "Lina", "Haddad");
        when(studentGroupRepository.findByGroupIdAndStudentId(GROUP_ID, 1L)).thenReturn(List.of());
        when(studentGroupRepository.findByGroupIdAndStudentId(GROUP_ID, 2L)).thenReturn(List.of());
        AttendanceEntity one = absence(amine, session(group(), at(DAY)));
        AttendanceEntity two = absence(lina, session(group(), at(DAY)));

        assertThatThrownBy(() -> guard.assertSubmittedAbsencesConcerned(List.of(one)))
                .isInstanceOfSatisfying(AbsenceOutsideWindowException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(409);
                    assertThat(e.getRejected()).hasSize(1);
                    assertThat(e.getMessage()).startsWith("Validation refusée : une absence est notée");
                });
        assertThatThrownBy(() -> guard.assertSubmittedAbsencesConcerned(List.of(one, two)))
                .hasMessageStartingWith("Validation refusée : 2 absences sont notées")
                .hasMessageContaining("Absence de Amine Belkacem le 14/01/2030")
                .hasMessageContaining("Absence de Lina Haddad le 14/01/2030");
        assertThatThrownBy(() -> guard.assertMovedSessionAbsencesConcerned(List.of(one)))
                .hasMessageStartingWith("Modification refusée : la séance porte une absence");
        assertThatThrownBy(() -> guard.assertMovedSessionAbsencesConcerned(List.of(one, two)))
                .hasMessageStartingWith("Modification refusée : la séance porte 2 absences");
    }
}
