package com.school.management.service.session;

import com.school.management.dto.session.RollCallDTO;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.repository.SessionRepository;
import com.school.management.repository.StudentGroupRepository;
import com.school.management.shared.exception.ResourceNotFoundException;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Cas limites de la Feuille_Appel, dépôts simulés. Le comportement ordinaire est éprouvé de bout en
 * bout par {@code RollCallEndpointIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RollCallService — cas limites")
class RollCallServiceTest {

    private static final long SESSION_ID = 100L;
    private static final long GROUP_ID = 5L;
    private static final LocalDate DAY = LocalDate.of(2030, 1, 14);

    @Mock private SessionRepository sessionRepository;
    @Mock private StudentGroupRepository studentGroupRepository;
    @InjectMocks private RollCallService service;

    private static Date day(LocalDate day) {
        return Date.from(day.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant());
    }

    private static GroupEntity group() {
        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);
        return group;
    }

    private static StudentEntity student(Long id, String firstName, String lastName) {
        StudentEntity student = new StudentEntity();
        student.setId(id);
        student.setFirstName(firstName);
        student.setLastName(lastName);
        return student;
    }

    private static StudentGroupEntity enrolment(StudentEntity student, LocalDate arrival) {
        StudentGroupEntity enrolment = new StudentGroupEntity();
        enrolment.setStudent(student);
        enrolment.setDateAssigned(day(arrival));
        return enrolment;
    }

    private void givenSession(SessionEntity session) {
        session.setId(SESSION_ID);
        session.setSessionTimeStart(day(DAY));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
    }

    @Test
    @DisplayName("séance sans groupe propre : celui de sa Série")
    void groupComesFromTheSeries() {
        SessionSeriesEntity series = new SessionSeriesEntity();
        series.setGroup(group());
        SessionEntity session = new SessionEntity();
        session.setSessionSeries(series);
        givenSession(session);
        when(studentGroupRepository.findByGroupId(GROUP_ID))
                .thenReturn(Arrays.asList(enrolment(student(1L, "Amine", "Belkacem"), DAY)));

        RollCallDTO rollCall = service.rollCall(SESSION_ID);

        assertThat(rollCall.groupId()).isEqualTo(GROUP_ID);
        assertThat(rollCall.students()).extracting(RollCallDTO.ConcernedStudent::id).containsExactly(1L);
    }

    @Test
    @DisplayName("séance sans groupe ni Série : feuille vide, sans lecture des inscriptions")
    void sessionWithoutGroup() {
        givenSession(new SessionEntity());

        RollCallDTO rollCall = service.rollCall(SESSION_ID);

        assertThat(rollCall.groupId()).isNull();
        assertThat(rollCall.sessionDay()).isEqualTo(DAY);
        assertThat(rollCall.students()).isEmpty();
        assertThat(rollCall.notConcerned()).isEmpty();
        verifyNoInteractions(studentGroupRepository);
    }

    @Test
    @DisplayName("séance sans Série ni groupe propre : même chose")
    void sessionWithSeriesWithoutGroup() {
        SessionEntity session = new SessionEntity();
        session.setSessionSeries(new SessionSeriesEntity());
        givenSession(session);

        assertThat(service.rollCall(SESSION_ID).groupId()).isNull();
    }

    @Test
    @DisplayName("inscription sans étudiant, ou étudiant sans identifiant : écartées")
    void incompleteRowsAreSkipped() {
        SessionEntity session = new SessionEntity();
        session.setGroup(group());
        givenSession(session);
        when(studentGroupRepository.findByGroupId(GROUP_ID)).thenReturn(Arrays.asList(
                enrolment(null, DAY),
                enrolment(student(null, "Sans", "Identifiant"), DAY),
                enrolment(student(2L, "Lina", "Haddad"), DAY)));

        assertThat(service.rollCall(SESSION_ID).students())
                .extracting(RollCallDTO.ConcernedStudent::id).containsExactly(2L);
    }

    @Test
    @DisplayName("nom ou prénom manquant : rangé après les autres, sans erreur ; inscription non datée expliquée")
    void missingNamesSortLast() {
        SessionEntity session = new SessionEntity();
        session.setGroup(group());
        givenSession(session);
        StudentGroupEntity undated = enrolment(student(4L, "Sans", "Date"), DAY);
        undated.setDateAssigned(null);
        when(studentGroupRepository.findByGroupId(GROUP_ID)).thenReturn(Arrays.asList(
                enrolment(student(3L, null, null), DAY),
                enrolment(student(1L, null, "Kaci"), DAY),
                enrolment(student(2L, "Amine", "Kaci"), DAY),
                undated));

        RollCallDTO rollCall = service.rollCall(SESSION_ID);

        assertThat(rollCall.students()).extracting(RollCallDTO.ConcernedStudent::id).containsExactly(2L, 1L, 3L);
        assertThat(rollCall.notConcerned()).singleElement()
                .satisfies(student -> assertThat(student.windows()).containsExactly(new RollCallDTO.Window(null, null)));
    }

    @Test
    @DisplayName("fenêtres d'un étudiant revenu : de la plus ancienne à la plus récente, quel que soit l'ordre lu")
    void windowsAreListedOldestFirst() {
        SessionEntity session = new SessionEntity();
        session.setGroup(group());
        givenSession(session);
        StudentEntity nour = student(3L, "Nour", "Zerrouki");
        StudentGroupEntity returned = enrolment(nour, DAY.plusDays(18));
        StudentGroupEntity first = enrolment(nour, LocalDate.of(2029, 9, 1));
        first.setDateLeft(day(LocalDate.of(2029, 12, 31)));
        when(studentGroupRepository.findByGroupId(GROUP_ID)).thenReturn(Arrays.asList(returned, first));

        assertThat(service.rollCall(SESSION_ID).notConcerned()).singleElement()
                .satisfies(student -> assertThat(student.windows()).containsExactly(
                        new RollCallDTO.Window(LocalDate.of(2029, 9, 1), LocalDate.of(2029, 12, 31)),
                        new RollCallDTO.Window(DAY.plusDays(18), null)));
    }

    @Test
    @DisplayName("séance inconnue : 404")
    void unknownSession() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rollCall(SESSION_ID)).isInstanceOf(ResourceNotFoundException.class);
    }
}
