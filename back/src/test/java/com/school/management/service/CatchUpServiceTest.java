package com.school.management.service;

import com.school.management.dto.CatchUpRequestDTO;
import com.school.management.dto.StudentAbsenceDTO;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.CatchUpRequestEntity;
import com.school.management.persistance.CatchUpStatus;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.GroupTypeEntity;
import com.school.management.persistance.PricingEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.CatchUpRequestRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.payment.PaymentStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires (JUnit 5 + Mockito) pour {@link CatchUpService}.
 *
 * <p>Couvre chaque rejet de précondition, chaque transition légale/illégale de la
 * machine à états, l'annulation avec/sans motif, les champs de l'effet de bord à la
 * complétion, ainsi que le filtrage des séances disponibles (groupe d'origine inclus).</p>
 */
class CatchUpServiceTest {

    private static final long STUDENT_ID = 7L;
    private static final long ATTENDANCE_ID = 11L;
    private static final long ORIGINAL_SESSION_ID = 21L;
    private static final long ORIGINAL_GROUP_ID = 31L;
    private static final long MISSED_SERIES_ID = 41L;
    private static final long REQUEST_ID = 51L;
    private static final long CATCHUP_SESSION_ID = 61L;
    private static final long CATCHUP_GROUP_ID = 71L;

    private static final long TYPE_ID = 100L;
    private static final long OTHER_TYPE_ID = 200L;
    private static final double PRICE = 30.0;
    private static final double OTHER_PRICE = 45.0;

    private CatchUpRequestRepository requestRepo;
    private AttendanceRepository attendanceRepo;
    private SessionRepository sessionRepo;
    private PaymentStatusService paymentStatusService;
    private CatchUpService service;

    @BeforeEach
    void setUp() {
        requestRepo = mock(CatchUpRequestRepository.class);
        attendanceRepo = mock(AttendanceRepository.class);
        sessionRepo = mock(SessionRepository.class);
        paymentStatusService = mock(PaymentStatusService.class);
        when(requestRepo.save(any(CatchUpRequestEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(attendanceRepo.save(any(AttendanceEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new CatchUpService(requestRepo, attendanceRepo, sessionRepo, paymentStatusService);
    }

    // ==================================================================
    // create
    // ==================================================================

    @Test
    void create_success_setsPendingAndFields() {
        GroupEntity group = group(ORIGINAL_GROUP_ID, TYPE_ID, PRICE);
        SessionEntity original = sessionWithSeries(ORIGINAL_SESSION_ID, group, MISSED_SERIES_ID);
        AttendanceEntity att = attendance(ATTENDANCE_ID, true);

        when(attendanceRepo.findById(ATTENDANCE_ID)).thenReturn(Optional.of(att));
        when(sessionRepo.findById(ORIGINAL_SESSION_ID)).thenReturn(Optional.of(original));
        when(paymentStatusService.isStudentPaymentOverdueForSeries(STUDENT_ID, MISSED_SERIES_ID))
                .thenReturn(false);

        CatchUpRequestEntity result = service.create(dto("note"));

        assertThat(result.getStatus()).isEqualTo(CatchUpStatus.PENDING);
        assertThat(result.getRequestDate()).isNotNull();
        assertThat(result.getStudent().getId()).isEqualTo(STUDENT_ID);
        assertThat(result.getOriginalSession()).isSameAs(original);
        assertThat(result.getOriginalGroup()).isSameAs(group);
        assertThat(result.getOriginalAttendance()).isSameAs(att);
        assertThat(result.getNotes()).isEqualTo("note");
    }

    @Test
    void create_success_whenCatchUpRightNull_treatedAsGranted() {
        GroupEntity group = group(ORIGINAL_GROUP_ID, TYPE_ID, PRICE);
        SessionEntity original = sessionWithSeries(ORIGINAL_SESSION_ID, group, MISSED_SERIES_ID);
        AttendanceEntity att = attendance(ATTENDANCE_ID, null);

        when(attendanceRepo.findById(ATTENDANCE_ID)).thenReturn(Optional.of(att));
        when(sessionRepo.findById(ORIGINAL_SESSION_ID)).thenReturn(Optional.of(original));
        when(paymentStatusService.isStudentPaymentOverdueForSeries(STUDENT_ID, MISSED_SERIES_ID))
                .thenReturn(false);

        CatchUpRequestEntity result = service.create(dto(null));

        assertThat(result.getStatus()).isEqualTo(CatchUpStatus.PENDING);
    }

    @Test
    void create_success_whenNoSeries_skipsPaymentCheck() {
        GroupEntity group = group(ORIGINAL_GROUP_ID, TYPE_ID, PRICE);
        SessionEntity original = session(ORIGINAL_SESSION_ID, group); // pas de série
        AttendanceEntity att = attendance(ATTENDANCE_ID, true);

        when(attendanceRepo.findById(ATTENDANCE_ID)).thenReturn(Optional.of(att));
        when(sessionRepo.findById(ORIGINAL_SESSION_ID)).thenReturn(Optional.of(original));

        CatchUpRequestEntity result = service.create(dto(null));

        assertThat(result.getStatus()).isEqualTo(CatchUpStatus.PENDING);
        verify(paymentStatusService, never()).isStudentPaymentOverdueForSeries(any(), any());
    }

    @Test
    void create_attendanceNotFound_throwsNotFound() {
        when(attendanceRepo.findById(ATTENDANCE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(dto(null)))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void create_catchUpRightRevoked_throwsBadRequest() {
        AttendanceEntity att = attendance(ATTENDANCE_ID, false);
        when(attendanceRepo.findById(ATTENDANCE_ID)).thenReturn(Optional.of(att));

        assertThatThrownBy(() -> service.create(dto(null)))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_originalSessionNotFound_throwsNotFound() {
        AttendanceEntity att = attendance(ATTENDANCE_ID, true);
        when(attendanceRepo.findById(ATTENDANCE_ID)).thenReturn(Optional.of(att));
        when(sessionRepo.findById(ORIGINAL_SESSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(dto(null)))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void create_missedSessionNotPaid_throwsBadRequest() {
        GroupEntity group = group(ORIGINAL_GROUP_ID, TYPE_ID, PRICE);
        SessionEntity original = sessionWithSeries(ORIGINAL_SESSION_ID, group, MISSED_SERIES_ID);
        AttendanceEntity att = attendance(ATTENDANCE_ID, true);

        when(attendanceRepo.findById(ATTENDANCE_ID)).thenReturn(Optional.of(att));
        when(sessionRepo.findById(ORIGINAL_SESSION_ID)).thenReturn(Optional.of(original));
        when(paymentStatusService.isStudentPaymentOverdueForSeries(STUDENT_ID, MISSED_SERIES_ID))
                .thenReturn(true); // en retard → non payé

        assertThatThrownBy(() -> service.create(dto(null)))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void create_nullDto_throwsNpe() {
        assertThatThrownBy(() -> service.create(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ==================================================================
    // getAvailableSessions
    // ==================================================================

    @Test
    void getAvailableSessions_returnsOnlyCompatible_includingOriginalGroup() {
        GroupEntity originalGroup = group(ORIGINAL_GROUP_ID, TYPE_ID, PRICE);
        SessionEntity original = session(ORIGINAL_SESSION_ID, originalGroup);

        // compatible : même groupe d'origine
        SessionEntity sameGroupSession = session(1L, originalGroup);
        // compatible : autre groupe même type + même prix
        SessionEntity compatibleOther = session(2L, group(80L, TYPE_ID, PRICE));
        // incompatible : type différent
        SessionEntity wrongType = session(3L, group(81L, OTHER_TYPE_ID, PRICE));
        // incompatible : prix différent
        SessionEntity wrongPrice = session(4L, group(82L, TYPE_ID, OTHER_PRICE));

        when(sessionRepo.findById(ORIGINAL_SESSION_ID)).thenReturn(Optional.of(original));
        when(sessionRepo.findAll()).thenReturn(List.of(sameGroupSession, compatibleOther, wrongType, wrongPrice));

        List<SessionEntity> result = service.getAvailableSessions(STUDENT_ID, ORIGINAL_SESSION_ID);

        assertThat(result).extracting(SessionEntity::getId)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void getAvailableSessions_originalSessionNotFound_throwsNotFound() {
        when(sessionRepo.findById(ORIGINAL_SESSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAvailableSessions(STUDENT_ID, ORIGINAL_SESSION_ID))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getAvailableSessions_excludesSessionsWithNullGroup() {
        GroupEntity originalGroup = group(ORIGINAL_GROUP_ID, TYPE_ID, PRICE);
        SessionEntity original = session(ORIGINAL_SESSION_ID, originalGroup);
        SessionEntity nullGroupSession = session(9L, null);

        when(sessionRepo.findById(ORIGINAL_SESSION_ID)).thenReturn(Optional.of(original));
        when(sessionRepo.findAll()).thenReturn(List.of(nullGroupSession));

        List<SessionEntity> result = service.getAvailableSessions(STUDENT_ID, ORIGINAL_SESSION_ID);

        assertThat(result).isEmpty();
    }

    // ==================================================================
    // schedule
    // ==================================================================

    @Test
    void schedule_fromPending_compatible_setsScheduled() {
        GroupEntity originalGroup = group(ORIGINAL_GROUP_ID, TYPE_ID, PRICE);
        GroupEntity catchUpGroup = group(CATCHUP_GROUP_ID, TYPE_ID, PRICE);
        SessionEntity catchUpSession = session(CATCHUP_SESSION_ID, catchUpGroup);
        CatchUpRequestEntity pending = request(CatchUpStatus.PENDING, originalGroup);

        when(requestRepo.findById(REQUEST_ID)).thenReturn(Optional.of(pending));
        when(sessionRepo.findById(CATCHUP_SESSION_ID)).thenReturn(Optional.of(catchUpSession));

        CatchUpRequestEntity result = service.schedule(REQUEST_ID, CATCHUP_SESSION_ID, CATCHUP_GROUP_ID);

        assertThat(result.getStatus()).isEqualTo(CatchUpStatus.SCHEDULED);
        assertThat(result.getCatchUpSession()).isSameAs(catchUpSession);
        assertThat(result.getCatchUpGroup()).isSameAs(catchUpGroup);
        assertThat(result.getScheduledDate()).isNotNull();
    }

    @Test
    void schedule_requestNotFound_throwsNotFound() {
        when(requestRepo.findById(REQUEST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.schedule(REQUEST_ID, CATCHUP_SESSION_ID, CATCHUP_GROUP_ID))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void schedule_notPending_throwsConflict() {
        CatchUpRequestEntity scheduled = request(CatchUpStatus.SCHEDULED, group(ORIGINAL_GROUP_ID, TYPE_ID, PRICE));
        when(requestRepo.findById(REQUEST_ID)).thenReturn(Optional.of(scheduled));

        assertThatThrownBy(() -> service.schedule(REQUEST_ID, CATCHUP_SESSION_ID, CATCHUP_GROUP_ID))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void schedule_catchUpSessionNotFound_throwsNotFound() {
        CatchUpRequestEntity pending = request(CatchUpStatus.PENDING, group(ORIGINAL_GROUP_ID, TYPE_ID, PRICE));
        when(requestRepo.findById(REQUEST_ID)).thenReturn(Optional.of(pending));
        when(sessionRepo.findById(CATCHUP_SESSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.schedule(REQUEST_ID, CATCHUP_SESSION_ID, CATCHUP_GROUP_ID))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void schedule_incompatibleGroup_throwsBadRequest() {
        GroupEntity originalGroup = group(ORIGINAL_GROUP_ID, TYPE_ID, PRICE);
        GroupEntity incompatible = group(CATCHUP_GROUP_ID, OTHER_TYPE_ID, PRICE);
        SessionEntity catchUpSession = session(CATCHUP_SESSION_ID, incompatible);
        CatchUpRequestEntity pending = request(CatchUpStatus.PENDING, originalGroup);

        when(requestRepo.findById(REQUEST_ID)).thenReturn(Optional.of(pending));
        when(sessionRepo.findById(CATCHUP_SESSION_ID)).thenReturn(Optional.of(catchUpSession));

        assertThatThrownBy(() -> service.schedule(REQUEST_ID, CATCHUP_SESSION_ID, CATCHUP_GROUP_ID))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ==================================================================
    // complete
    // ==================================================================

    @Test
    void complete_fromScheduled_setsCompletedAndCreatesAttendance() {
        GroupEntity originalGroup = group(ORIGINAL_GROUP_ID, TYPE_ID, PRICE);
        GroupEntity catchUpGroup = group(CATCHUP_GROUP_ID, TYPE_ID, PRICE);
        SessionEntity originalSession = session(ORIGINAL_SESSION_ID, originalGroup);
        SessionEntity catchUpSession = session(CATCHUP_SESSION_ID, catchUpGroup);
        StudentEntity student = StudentEntity.builder().id(STUDENT_ID).build();

        CatchUpRequestEntity scheduled = CatchUpRequestEntity.builder()
                .id(REQUEST_ID)
                .status(CatchUpStatus.SCHEDULED)
                .student(student)
                .originalGroup(originalGroup)
                .originalSession(originalSession)
                .catchUpSession(catchUpSession)
                .catchUpGroup(catchUpGroup)
                .build();
        when(requestRepo.findById(REQUEST_ID)).thenReturn(Optional.of(scheduled));

        CatchUpRequestEntity result = service.complete(REQUEST_ID);

        assertThat(result.getStatus()).isEqualTo(CatchUpStatus.COMPLETED);
        assertThat(result.getCompletedDate()).isNotNull();

        ArgumentCaptor<AttendanceEntity> captor = ArgumentCaptor.forClass(AttendanceEntity.class);
        verify(attendanceRepo).save(captor.capture());
        AttendanceEntity att = captor.getValue();
        assertThat(att.getIsPresent()).isTrue();
        assertThat(att.getIsCatchUp()).isTrue();
        assertThat(att.getStudent()).isSameAs(student);
        assertThat(att.getSession()).isSameAs(catchUpSession);
        assertThat(att.getGroup()).isSameAs(catchUpGroup);
        assertThat(att.getMissedSession()).isSameAs(originalSession);
    }

    @Test
    void complete_requestNotFound_throwsNotFound() {
        when(requestRepo.findById(REQUEST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.complete(REQUEST_ID))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void complete_notScheduled_throwsConflict() {
        CatchUpRequestEntity pending = request(CatchUpStatus.PENDING, group(ORIGINAL_GROUP_ID, TYPE_ID, PRICE));
        when(requestRepo.findById(REQUEST_ID)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.complete(REQUEST_ID))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));
        verify(attendanceRepo, never()).save(any());
    }

    // ==================================================================
    // cancel
    // ==================================================================

    @Test
    void cancel_fromPending_withReason_setsCancelledAndReason() {
        CatchUpRequestEntity pending = request(CatchUpStatus.PENDING, group(ORIGINAL_GROUP_ID, TYPE_ID, PRICE));
        when(requestRepo.findById(REQUEST_ID)).thenReturn(Optional.of(pending));

        CatchUpRequestEntity result = service.cancel(REQUEST_ID, "indisponible");

        assertThat(result.getStatus()).isEqualTo(CatchUpStatus.CANCELLED);
        assertThat(result.getCancellationReason()).isEqualTo("indisponible");
    }

    @Test
    void cancel_fromScheduled_withoutReason_setsCancelledNoReason() {
        CatchUpRequestEntity scheduled = request(CatchUpStatus.SCHEDULED, group(ORIGINAL_GROUP_ID, TYPE_ID, PRICE));
        when(requestRepo.findById(REQUEST_ID)).thenReturn(Optional.of(scheduled));

        CatchUpRequestEntity result = service.cancel(REQUEST_ID, null);

        assertThat(result.getStatus()).isEqualTo(CatchUpStatus.CANCELLED);
        assertThat(result.getCancellationReason()).isNull();
    }

    @Test
    void cancel_requestNotFound_throwsNotFound() {
        when(requestRepo.findById(REQUEST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(REQUEST_ID, "x"))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void cancel_fromCompleted_throwsConflict() {
        CatchUpRequestEntity completed = request(CatchUpStatus.COMPLETED, group(ORIGINAL_GROUP_ID, TYPE_ID, PRICE));
        when(requestRepo.findById(REQUEST_ID)).thenReturn(Optional.of(completed));

        assertThatThrownBy(() -> service.cancel(REQUEST_ID, "x"))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void cancel_fromCancelled_throwsConflict() {
        CatchUpRequestEntity cancelled = request(CatchUpStatus.CANCELLED, group(ORIGINAL_GROUP_ID, TYPE_ID, PRICE));
        when(requestRepo.findById(REQUEST_ID)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> service.cancel(REQUEST_ID, "x"))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    // ==================================================================
    // reads
    // ==================================================================

    @Test
    void getPendingRequests_delegatesToRepository() {
        CatchUpRequestEntity pending = request(CatchUpStatus.PENDING, group(ORIGINAL_GROUP_ID, TYPE_ID, PRICE));
        when(requestRepo.findByStatus(CatchUpStatus.PENDING)).thenReturn(List.of(pending));

        assertThat(service.getPendingRequests()).containsExactly(pending);
    }

    @Test
    void getRequestsByStudent_delegatesToRepository() {
        CatchUpRequestEntity r = request(CatchUpStatus.PENDING, group(ORIGINAL_GROUP_ID, TYPE_ID, PRICE));
        when(requestRepo.findByStudentId(STUDENT_ID)).thenReturn(List.of(r));

        assertThat(service.getRequestsByStudent(STUDENT_ID)).containsExactly(r);
    }

    @Test
    void getAllRequests_returnsEnrichedDto_fullyPopulated() {
        GroupEntity originalGroup = group(ORIGINAL_GROUP_ID, TYPE_ID, PRICE);
        originalGroup.setName("Groupe Origine");
        GroupEntity catchUpGroup = group(CATCHUP_GROUP_ID, TYPE_ID, PRICE);
        catchUpGroup.setName("Groupe Rattrapage");
        SessionEntity originalSession = session(ORIGINAL_SESSION_ID, originalGroup);
        originalSession.setTitle("Séance manquée");
        SessionEntity catchUpSession = session(CATCHUP_SESSION_ID, catchUpGroup);
        catchUpSession.setTitle("Séance rattrapage");
        StudentEntity student = StudentEntity.builder().id(STUDENT_ID).build();
        student.setFirstName("Jean");
        student.setLastName("Dupont");
        AttendanceEntity att = AttendanceEntity.builder().id(ATTENDANCE_ID).build();

        CatchUpRequestEntity r = CatchUpRequestEntity.builder()
                .id(REQUEST_ID)
                .status(CatchUpStatus.SCHEDULED)
                .student(student)
                .originalGroup(originalGroup)
                .originalSession(originalSession)
                .originalAttendance(att)
                .catchUpGroup(catchUpGroup)
                .catchUpSession(catchUpSession)
                .build();
        when(requestRepo.findAll()).thenReturn(List.of(r));

        var result = service.getAllRequests();

        assertThat(result).hasSize(1);
        var dto = result.get(0);
        assertThat(dto.studentName()).isEqualTo("Jean Dupont");
        assertThat(dto.studentId()).isEqualTo(STUDENT_ID);
        assertThat(dto.originalSessionName()).isEqualTo("Séance manquée");
        assertThat(dto.originalGroupName()).isEqualTo("Groupe Origine");
        assertThat(dto.catchUpSessionName()).isEqualTo("Séance rattrapage");
        assertThat(dto.catchUpGroupName()).isEqualTo("Groupe Rattrapage");
        assertThat(dto.originalAttendanceId()).isEqualTo(ATTENDANCE_ID);
        assertThat(dto.catchUpSessionId()).isEqualTo(CATCHUP_SESSION_ID);
        assertThat(dto.catchUpGroupId()).isEqualTo(CATCHUP_GROUP_ID);
        assertThat(dto.status()).isEqualTo(CatchUpStatus.SCHEDULED);
    }

    @Test
    void getAllRequests_handlesNullAssociationsAndBlankName() {
        // Étudiant présent mais sans prénom/nom → studentName vide → null.
        StudentEntity blankStudent = StudentEntity.builder().id(STUDENT_ID).build();
        CatchUpRequestEntity blankNameReq = CatchUpRequestEntity.builder()
                .id(REQUEST_ID).status(CatchUpStatus.PENDING)
                .student(blankStudent)
                .build();
        // Toutes les associations nulles.
        CatchUpRequestEntity emptyReq = CatchUpRequestEntity.builder()
                .id(52L).status(CatchUpStatus.CANCELLED)
                .build();
        when(requestRepo.findAll()).thenReturn(List.of(blankNameReq, emptyReq));

        var result = service.getAllRequests();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).studentName()).isNull();
        assertThat(result.get(0).studentId()).isEqualTo(STUDENT_ID);
        assertThat(result.get(1).studentName()).isNull();
        assertThat(result.get(1).studentId()).isNull();
        assertThat(result.get(1).originalSessionId()).isNull();
        assertThat(result.get(1).originalGroupId()).isNull();
        assertThat(result.get(1).originalAttendanceId()).isNull();
        assertThat(result.get(1).catchUpSessionId()).isNull();
        assertThat(result.get(1).catchUpGroupId()).isNull();
        assertThat(result.get(1).originalSessionName()).isNull();
        assertThat(result.get(1).originalGroupName()).isNull();
        assertThat(result.get(1).catchUpSessionName()).isNull();
        assertThat(result.get(1).catchUpGroupName()).isNull();
    }

    @Test
    void getEligibleAbsences_filtersAndMapsAllVariants() {
        GroupEntity group = group(ORIGINAL_GROUP_ID, TYPE_ID, PRICE);
        group.setName("Groupe Math");
        SessionEntity sess = session(ORIGINAL_SESSION_ID, group);
        sess.setTitle("Séance 1");
        sess.setSessionTimeStart(new Date());
        SessionSeriesEntity series = new SessionSeriesEntity();
        series.setId(MISSED_SERIES_ID);
        sess.setSessionSeries(series);

        // abs1 : groupe direct + session + série
        AttendanceEntity abs1 = AttendanceEntity.builder()
                .id(ATTENDANCE_ID).isPresent(false).isJustified(true).catchUpRight(true)
                .session(sess).group(group).sessionSeries(series).build();
        // abs2 : groupe direct null → fallback via session.getGroup(), sans série
        SessionEntity sess2 = session(22L, group);
        sess2.setTitle("Séance 2");
        AttendanceEntity abs2 = AttendanceEntity.builder()
                .id(14L).isPresent(false).catchUpRight(true)
                .session(sess2).build();
        // abs3 : session null et groupe null
        AttendanceEntity abs3 = AttendanceEntity.builder()
                .id(15L).isPresent(false).catchUpRight(true)
                .build();
        // revoked : droit au rattrapage révoqué → exclu
        AttendanceEntity revoked = AttendanceEntity.builder()
                .id(12L).isPresent(false).catchUpRight(false)
                .session(sess).group(group).build();
        // alreadyRequested : une demande active existe déjà → exclu
        AttendanceEntity alreadyRequested = AttendanceEntity.builder()
                .id(13L).isPresent(false).catchUpRight(true)
                .session(sess).group(group).build();

        when(attendanceRepo.findAbsencesByStudentId(STUDENT_ID))
                .thenReturn(List.of(abs1, abs2, abs3, revoked, alreadyRequested));

        // Demandes existantes : PENDING avec absence 13 (bloque), CANCELLED sur abs1
        // (n'annule pas l'éligibilité), PENDING sans absence (filtre nonNull).
        CatchUpRequestEntity pendingWithAtt = CatchUpRequestEntity.builder()
                .id(REQUEST_ID).status(CatchUpStatus.PENDING)
                .originalAttendance(AttendanceEntity.builder().id(13L).build()).build();
        CatchUpRequestEntity cancelled = CatchUpRequestEntity.builder()
                .id(52L).status(CatchUpStatus.CANCELLED)
                .originalAttendance(AttendanceEntity.builder().id(ATTENDANCE_ID).build()).build();
        CatchUpRequestEntity pendingNoAtt = CatchUpRequestEntity.builder()
                .id(53L).status(CatchUpStatus.PENDING).build();
        when(requestRepo.findByStudentId(STUDENT_ID))
                .thenReturn(List.of(pendingWithAtt, cancelled, pendingNoAtt));

        var result = service.getEligibleAbsences(STUDENT_ID);

        assertThat(result).extracting(StudentAbsenceDTO::attendanceId)
                .containsExactlyInAnyOrder(ATTENDANCE_ID, 14L, 15L);

        StudentAbsenceDTO d1 = result.stream()
                .filter(a -> a.attendanceId() == ATTENDANCE_ID).findFirst().orElseThrow();
        assertThat(d1.sessionId()).isEqualTo(ORIGINAL_SESSION_ID);
        assertThat(d1.sessionTitle()).isEqualTo("Séance 1");
        assertThat(d1.groupName()).isEqualTo("Groupe Math");
        assertThat(d1.seriesId()).isEqualTo(MISSED_SERIES_ID);
        assertThat(d1.isJustified()).isTrue();

        StudentAbsenceDTO d2 = result.stream()
                .filter(a -> a.attendanceId() == 14L).findFirst().orElseThrow();
        assertThat(d2.groupName()).isEqualTo("Groupe Math"); // fallback via session
        assertThat(d2.seriesId()).isNull();

        StudentAbsenceDTO d3 = result.stream()
                .filter(a -> a.attendanceId() == 15L).findFirst().orElseThrow();
        assertThat(d3.sessionId()).isNull();
        assertThat(d3.sessionTitle()).isNull();
        assertThat(d3.groupId()).isNull();
        assertThat(d3.groupName()).isNull();
    }

    // ==================================================================
    // isCompatible edge cases (null groups)
    // ==================================================================

    @Test
    void isCompatible_nullGroups_returnFalse() {
        GroupEntity g = group(1L, TYPE_ID, PRICE);
        assertThat(service.isCompatible(null, g)).isFalse();
        assertThat(service.isCompatible(g, null)).isFalse();
    }

    @Test
    void isCompatible_nullGroupTypeOnBoth_matchesByNullType() {
        GroupEntity a = new GroupEntity();
        a.setId(1L);
        a.setGroupType(null);
        PricingEntity pa = new PricingEntity();
        pa.setPrice(PRICE);
        a.setPrice(pa);

        GroupEntity b = new GroupEntity();
        b.setId(2L);
        b.setGroupType(null);
        PricingEntity pb = new PricingEntity();
        pb.setPrice(PRICE);
        b.setPrice(pb);

        assertThat(service.isCompatible(a, b)).isTrue();
    }

    @Test
    void isCompatible_nullPriceOnBoth_matchesByNullPrice() {
        GroupTypeEntity type = new GroupTypeEntity();
        type.setId(TYPE_ID);

        GroupEntity a = new GroupEntity();
        a.setId(1L);
        a.setGroupType(type);
        a.setPrice(null);

        GroupEntity b = new GroupEntity();
        b.setId(2L);
        b.setGroupType(type);
        b.setPrice(null);

        assertThat(service.isCompatible(a, b)).isTrue();
    }

    @Test
    void isCompatible_differentSchoolYears_returnFalse() {
        // Années scolaires différentes (toutes deux non nulles) : incompatibles même si le type
        // et le prix par séance coïncident (interdiction de rattrapage inter-années).
        GroupEntity a = group(1L, TYPE_ID, PRICE);
        SchoolYearEntity y1 = new SchoolYearEntity();
        y1.setId(100L);
        a.setSchoolYear(y1);

        GroupEntity b = group(2L, TYPE_ID, PRICE);
        SchoolYearEntity y2 = new SchoolYearEntity();
        y2.setId(200L);
        b.setSchoolYear(y2);

        assertThat(service.isCompatible(a, b)).isFalse();
    }

    @Test
    void isCompatible_sameNonNullSchoolYear_matches() {
        // Même année scolaire (non nulle), même type, même prix → compatibles.
        GroupEntity a = group(1L, TYPE_ID, PRICE);
        SchoolYearEntity y1 = new SchoolYearEntity();
        y1.setId(100L);
        a.setSchoolYear(y1);

        GroupEntity b = group(2L, TYPE_ID, PRICE);
        SchoolYearEntity y2 = new SchoolYearEntity();
        y2.setId(100L);
        b.setSchoolYear(y2);

        assertThat(service.isCompatible(a, b)).isTrue();
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private CatchUpRequestDTO dto(String notes) {
        return new CatchUpRequestDTO(STUDENT_ID, ORIGINAL_SESSION_ID, ORIGINAL_GROUP_ID, ATTENDANCE_ID, notes);
    }

    private static AttendanceEntity attendance(long id, Boolean catchUpRight) {
        return AttendanceEntity.builder()
                .id(id)
                .isPresent(false)
                .catchUpRight(catchUpRight)
                .build();
    }

    private static CatchUpRequestEntity request(CatchUpStatus status, GroupEntity originalGroup) {
        return CatchUpRequestEntity.builder()
                .id(REQUEST_ID)
                .status(status)
                .student(StudentEntity.builder().id(STUDENT_ID).build())
                .originalGroup(originalGroup)
                .originalSession(session(ORIGINAL_SESSION_ID, originalGroup))
                .build();
    }

    private static GroupEntity group(long id, long groupTypeId, double price) {
        GroupTypeEntity type = new GroupTypeEntity();
        type.setId(groupTypeId);
        PricingEntity pricing = new PricingEntity();
        pricing.setPrice(price);
        GroupEntity group = new GroupEntity();
        group.setId(id);
        group.setGroupType(type);
        group.setPrice(pricing);
        return group;
    }

    private static SessionEntity session(long id, GroupEntity group) {
        SessionEntity session = new SessionEntity();
        session.setId(id);
        session.setGroup(group);
        return session;
    }

    private static SessionEntity sessionWithSeries(long id, GroupEntity group, long seriesId) {
        SessionEntity session = session(id, group);
        SessionSeriesEntity series = new SessionSeriesEntity();
        series.setId(seriesId);
        session.setSessionSeries(series);
        return session;
    }
}
