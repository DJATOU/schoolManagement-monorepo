package com.school.management.service.payment;

import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.repository.StudentGroupRepository;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.payment.BillableSessionsResolver.BillableSessions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires (exemples, dépôts simulés) de {@link BillableSessionsResolverImpl}.
 *
 * <p>Le résolveur est la source unique de la règle du prorata : une séance est facturable si
 * sa date est postérieure ou égale à l'inscription au groupe, <strong>ou</strong> si l'étudiant
 * y possède une présence active. Les tests couvrent les quatre cas de l'exigence 1, l'ajout
 * d'une séance après coup (1.6), et les cas limites que l'implémentation traite
 * explicitement : série introuvable, groupe absent, inscription sans date, séance sans date,
 * fiche de présence sans séance.</p>
 */
@ExtendWith(MockitoExtension.class)
class BillableSessionsResolverTest {

    private static final Long STUDENT_ID = 5L;
    private static final Long SERIES_ID = 20L;
    private static final Long GROUP_ID = 3L;

    /** Date d'inscription de référence : toutes les séances se situent autour d'elle. */
    private static final Date ENROLMENT_DATE = date("2025-01-10");

    @Mock private SessionSeriesRepository sessionSeriesRepository;
    @Mock private SessionRepository sessionRepository;
    @Mock private AttendanceRepository attendanceRepository;
    @Mock private StudentGroupRepository studentGroupRepository;

    @InjectMocks private BillableSessionsResolverImpl resolver;

    // ------------------------------------------------------------------
    // Fabriques de données
    // ------------------------------------------------------------------

    private static Date date(String isoDate) {
        return Date.from(LocalDate.parse(isoDate).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private SessionEntity session(Long id, Date start) {
        SessionEntity session = new SessionEntity();
        session.setId(id);
        session.setSessionTimeStart(start);
        return session;
    }

    private AttendanceEntity attendance(SessionEntity session, Boolean present) {
        AttendanceEntity attendance = new AttendanceEntity();
        attendance.setSession(session);
        attendance.setIsPresent(present);
        attendance.setActive(true);
        return attendance;
    }

    /** Série rattachée à un groupe identifié : le cas courant. */
    private void givenSeries() {
        givenSeries(group(GROUP_ID));
    }

    private void givenSeries(GroupEntity group) {
        SessionSeriesEntity series = new SessionSeriesEntity();
        series.setId(SERIES_ID);
        series.setGroup(group);
        when(sessionSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series));
    }

    private GroupEntity group(Long id) {
        GroupEntity group = new GroupEntity();
        group.setId(id);
        return group;
    }

    /** Inscription active au groupe, avec ou sans date d'affectation. */
    private void givenEnrolment(Date dateAssigned) {
        StudentGroupEntity enrolment = new StudentGroupEntity();
        enrolment.setGroup(group(GROUP_ID));
        enrolment.setDateAssigned(dateAssigned);
        when(studentGroupRepository.findByGroupIdAndStudentIdAndActiveTrue(GROUP_ID, STUDENT_ID))
                .thenReturn(Optional.of(enrolment));
    }

    private void givenNoEnrolment() {
        when(studentGroupRepository.findByGroupIdAndStudentIdAndActiveTrue(GROUP_ID, STUDENT_ID))
                .thenReturn(Optional.empty());
    }

    private void givenSessions(SessionEntity... sessions) {
        when(sessionRepository.findBySessionSeriesId(SERIES_ID)).thenReturn(List.of(sessions));
    }

    private void givenAttendances(AttendanceEntity... attendances) {
        when(attendanceRepository.findByStudentIdAndSessionSeriesIdAndActiveTrue(STUDENT_ID, SERIES_ID))
                .thenReturn(List.of(attendances));
    }

    // ------------------------------------------------------------------
    // Série introuvable
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Série introuvable → CustomServiceException 404")
    void seriesNotFound() {
        when(sessionSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(STUDENT_ID, SERIES_ID))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verify(sessionRepository, never()).findBySessionSeriesId(SERIES_ID);
    }

    // ------------------------------------------------------------------
    // Exigences 1.1 et 1.3 : la date d'inscription trie les séances
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Séance postérieure ou égale à l'inscription → facturable (exigence 1.1)")
    void sessionOnOrAfterEnrolmentIsBillable() {
        givenSeries();
        givenEnrolment(ENROLMENT_DATE);
        // La séance du 10 janvier tombe exactement à la date d'inscription : la borne est incluse.
        givenSessions(session(1L, ENROLMENT_DATE), session(2L, date("2025-01-17")));

        BillableSessions result = resolver.resolve(STUDENT_ID, SERIES_ID);

        assertThat(result.billable()).extracting(SessionEntity::getId).containsExactly(1L, 2L);
        assertThat(result.excluded()).isEmpty();
        assertThat(result.billableCount()).isEqualTo(2);
        assertThat(result.excludedCount()).isZero();
        assertThat(result.attendedCount()).isZero();
        assertThat(result.enrolled()).isTrue();
        assertThat(result.enrollmentDate()).isEqualTo(ENROLMENT_DATE);
    }

    @Test
    @DisplayName("Séance antérieure à l'inscription et non suivie → exclue (exigence 1.3)")
    void sessionBeforeEnrolmentWithoutAttendanceIsExcluded() {
        givenSeries();
        givenEnrolment(ENROLMENT_DATE);
        givenSessions(session(1L, date("2025-01-03")), session(2L, date("2025-01-17")));

        BillableSessions result = resolver.resolve(STUDENT_ID, SERIES_ID);

        assertThat(result.billable()).extracting(SessionEntity::getId).containsExactly(2L);
        assertThat(result.excluded()).extracting(SessionEntity::getId).containsExactly(1L);
        assertThat(result.attendedCount()).isZero();
    }

    @Test
    @DisplayName("Séance antérieure à l'inscription mais suivie → facturable (exigence 1.2)")
    void sessionBeforeEnrolmentButAttendedIsBillable() {
        SessionEntity past = session(1L, date("2025-01-03"));
        givenSeries();
        givenEnrolment(ENROLMENT_DATE);
        givenSessions(past, session(2L, date("2025-01-17")));
        givenAttendances(attendance(past, true));

        BillableSessions result = resolver.resolve(STUDENT_ID, SERIES_ID);

        // La séance a été consommée : elle est due, même antérieure à l'inscription.
        assertThat(result.billable()).extracting(SessionEntity::getId).containsExactly(1L, 2L);
        assertThat(result.excluded()).isEmpty();
        assertThat(result.attendedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Séance sans date de début et non suivie → exclue")
    void sessionWithoutStartDateIsExcludedWhenNotAttended() {
        givenSeries();
        givenEnrolment(ENROLMENT_DATE);
        givenSessions(session(1L, date("2025-01-17")), session(2L, null));

        BillableSessions result = resolver.resolve(STUDENT_ID, SERIES_ID);

        assertThat(result.billable()).extracting(SessionEntity::getId).containsExactly(1L);
        assertThat(result.excluded()).extracting(SessionEntity::getId).containsExactly(2L);
    }

    // ------------------------------------------------------------------
    // Exigence 1.4 : sans inscription, seules les séances suivies comptent
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Aucune inscription → seules les séances suivies sont facturables (exigence 1.4)")
    void withoutEnrolmentOnlyAttendedSessionsAreBillable() {
        SessionEntity attended = session(1L, date("2025-01-17"));
        givenSeries();
        givenNoEnrolment();
        givenSessions(attended, session(2L, date("2025-01-24")));
        givenAttendances(attendance(attended, true));

        BillableSessions result = resolver.resolve(STUDENT_ID, SERIES_ID);

        assertThat(result.billable()).extracting(SessionEntity::getId).containsExactly(1L);
        assertThat(result.excluded()).extracting(SessionEntity::getId).containsExactly(2L);
        assertThat(result.attendedCount()).isEqualTo(1);
        assertThat(result.enrolled()).isFalse();
        assertThat(result.enrollmentDate()).isNull();
    }

    @Test
    @DisplayName("Inscription active sans date_assigned → seules les séances suivies sont facturables")
    void enrolmentWithoutDateAssignedFallsBackOnAttendance() {
        SessionEntity attended = session(1L, date("2025-01-17"));
        givenSeries();
        givenEnrolment(null);
        givenSessions(attended, session(2L, date("2025-01-24")));
        givenAttendances(attendance(attended, true));

        BillableSessions result = resolver.resolve(STUDENT_ID, SERIES_ID);

        // L'inscription existe, mais sans date aucune séance n'est retenue à ce titre.
        assertThat(result.enrolled()).isTrue();
        assertThat(result.enrollmentDate()).isNull();
        assertThat(result.billable()).extracting(SessionEntity::getId).containsExactly(1L);
        assertThat(result.excluded()).extracting(SessionEntity::getId).containsExactly(2L);
    }

    @Test
    @DisplayName("Série sans groupe → aucune inscription recherchée, seules les séances suivies comptent")
    void seriesWithoutGroupIsNotEnrolled() {
        givenSeries(null);
        givenSessions(session(1L, date("2025-01-17")));

        BillableSessions result = resolver.resolve(STUDENT_ID, SERIES_ID);

        assertThat(result.enrolled()).isFalse();
        assertThat(result.billable()).isEmpty();
        assertThat(result.excluded()).extracting(SessionEntity::getId).containsExactly(1L);
        verify(studentGroupRepository, never())
                .findByGroupIdAndStudentIdAndActiveTrue(GROUP_ID, STUDENT_ID);
    }

    @Test
    @DisplayName("Groupe sans identifiant → aucune inscription recherchée")
    void groupWithoutIdIsNotEnrolled() {
        givenSeries(group(null));
        givenSessions(session(1L, date("2025-01-17")));

        BillableSessions result = resolver.resolve(STUDENT_ID, SERIES_ID);

        assertThat(result.enrolled()).isFalse();
        assertThat(result.enrollmentDate()).isNull();
        assertThat(result.excludedCount()).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Exigence 1.6 : séance ajoutée après coup
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Séance ajoutée après coup avec date postérieure → incluse (exigence 1.6)")
    void sessionAddedAfterwardsIsBillable() {
        SessionEntity initial = session(1L, date("2025-01-17"));
        SessionEntity added = session(2L, date("2025-01-31"));
        givenSeries();
        givenEnrolment(ENROLMENT_DATE);
        when(sessionRepository.findBySessionSeriesId(SERIES_ID))
                .thenReturn(List.of(initial), List.of(initial, added));

        BillableSessions before = resolver.resolve(STUDENT_ID, SERIES_ID);
        BillableSessions after = resolver.resolve(STUDENT_ID, SERIES_ID);

        assertThat(before.billableCount()).isEqualTo(1);
        // Aucun recalcul figé : la séance supplémentaire entre dans le décompte au prochain appel.
        assertThat(after.billable()).extracting(SessionEntity::getId).containsExactly(1L, 2L);
        assertThat(after.excluded()).isEmpty();
    }

    // ------------------------------------------------------------------
    // attendedCount : sous-ensemble strict des séances facturables
    // ------------------------------------------------------------------

    @Test
    @DisplayName("attendedCount ne compte que des séances présentes dans billable")
    void attendedCountCountsOnlyBillableSessions() {
        SessionEntity inSeries = session(1L, date("2025-01-17"));
        // Fiche de présence rattachée à une séance absente de la série : elle ne doit rien compter.
        SessionEntity foreign = session(99L, date("2025-01-18"));
        givenSeries();
        givenEnrolment(ENROLMENT_DATE);
        givenSessions(inSeries);
        givenAttendances(attendance(inSeries, true), attendance(foreign, true));

        BillableSessions result = resolver.resolve(STUDENT_ID, SERIES_ID);

        assertThat(result.billable()).extracting(SessionEntity::getId).containsExactly(1L);
        assertThat(result.attendedCount()).isEqualTo(1);
        assertThat(result.attendedCount()).isLessThanOrEqualTo(result.billableCount());
    }

    @Test
    @DisplayName("Absence enregistrée → séance facturable mais non comptée comme suivie")
    void absenceMakesSessionBillableWithoutRaisingAttendedCount() {
        SessionEntity past = session(1L, date("2025-01-03"));
        SessionEntity unknownStatus = session(2L, date("2025-01-05"));
        givenSeries();
        givenEnrolment(ENROLMENT_DATE);
        givenSessions(past, unknownStatus);
        // Une fiche d'absence, et une fiche sans statut : les deux séances ont été suivies
        // administrativement, aucune ne relève le seuil de retard.
        givenAttendances(attendance(past, false), attendance(unknownStatus, null));

        BillableSessions result = resolver.resolve(STUDENT_ID, SERIES_ID);

        assertThat(result.billable()).extracting(SessionEntity::getId).containsExactly(1L, 2L);
        assertThat(result.attendedCount()).isZero();
    }

    @Test
    @DisplayName("Fiche de présence sans séance ou sans identifiant de séance → ignorée")
    void attendanceWithoutUsableSessionIsIgnored() {
        givenSeries();
        givenEnrolment(ENROLMENT_DATE);
        givenSessions(session(1L, date("2025-01-03")));
        givenAttendances(attendance(null, true), attendance(session(null, date("2025-01-03")), true));

        BillableSessions result = resolver.resolve(STUDENT_ID, SERIES_ID);

        // Ces fiches ne peuvent désigner aucune séance : la séance passée reste exclue.
        assertThat(result.billable()).isEmpty();
        assertThat(result.excluded()).extracting(SessionEntity::getId).containsExactly(1L);
        assertThat(result.attendedCount()).isZero();
    }

    // ------------------------------------------------------------------
    // Immuabilité du résultat
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Les listes retournées sont immuables")
    void returnedListsAreImmutable() {
        givenSeries();
        givenEnrolment(ENROLMENT_DATE);
        givenSessions(session(1L, date("2025-01-03")), session(2L, date("2025-01-17")));

        BillableSessions result = resolver.resolve(STUDENT_ID, SERIES_ID);
        SessionEntity intruder = session(3L, date("2025-02-01"));

        assertThatThrownBy(() -> result.billable().add(intruder))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.excluded().add(intruder))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
