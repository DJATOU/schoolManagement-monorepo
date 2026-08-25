package com.school.management.service;

import com.school.management.dto.CatchUpRequestDTO;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.CatchUpRequestEntity;
import com.school.management.persistance.CatchUpStatus;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.GroupTypeEntity;
import com.school.management.persistance.PricingEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.CatchUpRequestRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.payment.PaymentStatusService;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests de propriété (jqwik) pour {@link CatchUpService}.
 *
 * <p>Les repositories et {@link PaymentStatusService} sont mockés afin que les 100+
 * itérations restent rapides.</p>
 */
class CatchUpServicePropertyTest {

    private static final long STUDENT_ID = 7L;
    private static final long ATTENDANCE_ID = 11L;
    private static final long ORIGINAL_SESSION_ID = 21L;
    private static final long ORIGINAL_GROUP_ID = 31L;
    private static final long MISSED_SERIES_ID = 41L;
    private static final long REQUEST_ID = 51L;
    private static final long CATCHUP_SESSION_ID = 61L;

    private static final long ORIGINAL_TYPE_ID = 100L;
    private static final double ORIGINAL_PRICE = 30.0;

    // ------------------------------------------------------------------
    // Property 16 — Catch-up creation preconditions
    // ------------------------------------------------------------------

    // Feature: payment-attendance-rules, Property 16: For any catch-up request creation attempt, the request is rejected when the original attendance's Catch_Up_Right is false, and rejected when the missed session is not paid.
    @Property(tries = 100)
    void property16_creationPreconditions(
            @ForAll boolean rightGranted,
            @ForAll boolean paid) {

        CatchUpRequestRepository requestRepo = mock(CatchUpRequestRepository.class);
        AttendanceRepository attendanceRepo = mock(AttendanceRepository.class);
        SessionRepository sessionRepo = mock(SessionRepository.class);
        PaymentStatusService paymentStatusService = mock(PaymentStatusService.class);
        when(requestRepo.save(any(CatchUpRequestEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        GroupEntity originalGroup = group(ORIGINAL_GROUP_ID, ORIGINAL_TYPE_ID, ORIGINAL_PRICE);
        SessionSeriesEntity series = new SessionSeriesEntity();
        series.setId(MISSED_SERIES_ID);
        SessionEntity originalSession = session(ORIGINAL_SESSION_ID, originalGroup);
        originalSession.setSessionSeries(series);

        AttendanceEntity attendance = AttendanceEntity.builder()
                .id(ATTENDANCE_ID)
                .isPresent(false)
                .catchUpRight(rightGranted)
                .build();

        when(attendanceRepo.findById(ATTENDANCE_ID)).thenReturn(Optional.of(attendance));
        when(sessionRepo.findById(ORIGINAL_SESSION_ID)).thenReturn(Optional.of(originalSession));
        // "payé" ⇔ non en retard ; l'inverse (overdue) est renvoyé par le service de statut.
        when(paymentStatusService.isStudentPaymentOverdueForSeries(STUDENT_ID, MISSED_SERIES_ID))
                .thenReturn(!paid);

        CatchUpService service = new CatchUpService(requestRepo, attendanceRepo, sessionRepo, paymentStatusService);
        CatchUpRequestDTO dto = new CatchUpRequestDTO(
                STUDENT_ID, ORIGINAL_SESSION_ID, ORIGINAL_GROUP_ID, ATTENDANCE_ID, null);

        if (rightGranted && paid) {
            CatchUpRequestEntity created = service.create(dto);
            assertThat(created.getStatus()).isEqualTo(CatchUpStatus.PENDING);
            assertThat(created.getRequestDate()).isNotNull();
        } else {
            assertThatThrownBy(() -> service.create(dto))
                    .isInstanceOf(CustomServiceException.class)
                    .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                            .isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }

    // ------------------------------------------------------------------
    // Property 17 — Catch-up compatibility filter
    // ------------------------------------------------------------------

    // Feature: payment-attendance-rules, Property 17: For any set of candidate sessions, the available catch-up sessions returned are exactly those whose group has both the same Group_Type and the same Price_Per_Session as the original group (the original group itself included when compatible); scheduling against any session outside this set is rejected.
    @Property(tries = 100)
    void property17_compatibilityFilter(@ForAll("candidateGroups") List<int[]> candidates) {

        CatchUpRequestRepository requestRepo = mock(CatchUpRequestRepository.class);
        AttendanceRepository attendanceRepo = mock(AttendanceRepository.class);
        SessionRepository sessionRepo = mock(SessionRepository.class);
        PaymentStatusService paymentStatusService = mock(PaymentStatusService.class);
        when(requestRepo.save(any(CatchUpRequestEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        GroupEntity originalGroup = group(ORIGINAL_GROUP_ID, ORIGINAL_TYPE_ID, ORIGINAL_PRICE);
        SessionEntity originalSession = session(ORIGINAL_SESSION_ID, originalGroup);

        // Construction des séances candidates, chaque candidat = {typeId, priceTag}.
        List<SessionEntity> allSessions = new ArrayList<>();
        List<Long> expectedCompatibleIds = new ArrayList<>();
        long sessionIdSeq = 1000L;
        for (int[] candidate : candidates) {
            long typeId = candidate[0];
            double price = candidate[1]; // 0 → même prix que l'original, sinon prix différent
            double actualPrice = (price == 0) ? ORIGINAL_PRICE : ORIGINAL_PRICE + price;
            long sid = sessionIdSeq++;
            GroupEntity g = group(2000L + sid, typeId, actualPrice);
            SessionEntity s = session(sid, g);
            allSessions.add(s);
            if (typeId == ORIGINAL_TYPE_ID && actualPrice == ORIGINAL_PRICE) {
                expectedCompatibleIds.add(sid);
            }
        }

        when(sessionRepo.findById(ORIGINAL_SESSION_ID)).thenReturn(Optional.of(originalSession));
        when(sessionRepo.findAll()).thenReturn(allSessions);

        CatchUpService service = new CatchUpService(requestRepo, attendanceRepo, sessionRepo, paymentStatusService);

        List<SessionEntity> available = service.getAvailableSessions(STUDENT_ID, ORIGINAL_SESSION_ID);
        List<Long> availableIds = available.stream().map(SessionEntity::getId).toList();

        assertThat(availableIds).containsExactlyInAnyOrderElementsOf(expectedCompatibleIds);

        // Planifier contre une séance incompatible doit être rejeté (400).
        SessionEntity incompatible = allSessions.stream()
                .filter(s -> !expectedCompatibleIds.contains(s.getId()))
                .findFirst()
                .orElse(null);
        if (incompatible != null) {
            CatchUpRequestEntity pending = CatchUpRequestEntity.builder()
                    .id(REQUEST_ID)
                    .status(CatchUpStatus.PENDING)
                    .originalGroup(originalGroup)
                    .build();
            when(requestRepo.findById(REQUEST_ID)).thenReturn(Optional.of(pending));
            when(sessionRepo.findById(incompatible.getId())).thenReturn(Optional.of(incompatible));

            assertThatThrownBy(() -> service.schedule(REQUEST_ID, incompatible.getId(),
                    incompatible.getGroup().getId()))
                    .isInstanceOf(CustomServiceException.class)
                    .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                            .isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }

    /**
     * Génère une liste de candidats {@code {typeId, priceTag}} :
     * <ul>
     *   <li>{@code typeId} ∈ {ORIGINAL_TYPE_ID, autre} ;</li>
     *   <li>{@code priceTag} ∈ {0 (même prix), delta ≠ 0 (prix différent)}.</li>
     * </ul>
     */
    @Provide
    Arbitrary<List<int[]>> candidateGroups() {
        Arbitrary<Integer> typeId = Arbitraries.of((int) ORIGINAL_TYPE_ID, 999);
        Arbitrary<Integer> priceTag = Arbitraries.of(0, 5, 10);
        Arbitrary<int[]> pair = Combinators.combine(typeId, priceTag)
                .as((t, p) -> new int[] { t, p });
        return pair.list().ofMinSize(1).ofMaxSize(8);
    }

    // ------------------------------------------------------------------
    // Property 18 — Catch-up lifecycle state machine
    // ------------------------------------------------------------------

    private enum Op { SCHEDULE, COMPLETE, CANCEL }

    // Feature: payment-attendance-rules, Property 18: For any catch-up request and requested transition, the transition succeeds only for allowed edges (PENDING->SCHEDULED, SCHEDULED->COMPLETED, PENDING->CANCELLED, SCHEDULED->CANCELLED) and is rejected otherwise; completing a request sets status COMPLETED and creates an attendance with isPresent == true and isCatchUp == true linked to the catch-up session/group and the missed session.
    @Property(tries = 100)
    void property18_lifecycleStateMachine(
            @ForAll("statuses") CatchUpStatus start,
            @ForAll("ops") Op op) {

        CatchUpRequestRepository requestRepo = mock(CatchUpRequestRepository.class);
        AttendanceRepository attendanceRepo = mock(AttendanceRepository.class);
        SessionRepository sessionRepo = mock(SessionRepository.class);
        PaymentStatusService paymentStatusService = mock(PaymentStatusService.class);
        when(requestRepo.save(any(CatchUpRequestEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(attendanceRepo.save(any(AttendanceEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        GroupEntity originalGroup = group(ORIGINAL_GROUP_ID, ORIGINAL_TYPE_ID, ORIGINAL_PRICE);
        GroupEntity catchUpGroup = group(ORIGINAL_GROUP_ID + 1, ORIGINAL_TYPE_ID, ORIGINAL_PRICE);
        SessionEntity originalSession = session(ORIGINAL_SESSION_ID, originalGroup);
        SessionEntity catchUpSession = session(CATCHUP_SESSION_ID, catchUpGroup);
        StudentEntity student = StudentEntity.builder().id(STUDENT_ID).build();

        CatchUpRequestEntity request = CatchUpRequestEntity.builder()
                .id(REQUEST_ID)
                .status(start)
                .student(student)
                .originalGroup(originalGroup)
                .originalSession(originalSession)
                // pour SCHEDULED, la séance/le groupe de rattrapage sont déjà présents.
                .catchUpSession(start == CatchUpStatus.SCHEDULED ? catchUpSession : null)
                .catchUpGroup(start == CatchUpStatus.SCHEDULED ? catchUpGroup : null)
                .build();
        when(requestRepo.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(sessionRepo.findById(CATCHUP_SESSION_ID)).thenReturn(Optional.of(catchUpSession));

        CatchUpService service = new CatchUpService(requestRepo, attendanceRepo, sessionRepo, paymentStatusService);

        boolean allowed = isAllowed(start, op);

        if (!allowed) {
            assertThatThrownBy(() -> invoke(service, op, catchUpSession, catchUpGroup))
                    .isInstanceOf(CustomServiceException.class)
                    .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                            .isEqualTo(HttpStatus.CONFLICT));
            return;
        }

        CatchUpRequestEntity result = invoke(service, op, catchUpSession, catchUpGroup);

        switch (op) {
            case SCHEDULE -> assertThat(result.getStatus()).isEqualTo(CatchUpStatus.SCHEDULED);
            case CANCEL -> assertThat(result.getStatus()).isEqualTo(CatchUpStatus.CANCELLED);
            case COMPLETE -> {
                assertThat(result.getStatus()).isEqualTo(CatchUpStatus.COMPLETED);
                assertThat(result.getCompletedDate()).isNotNull();
                // Effet de bord : une présence de rattrapage a été créée.
                org.mockito.ArgumentCaptor<AttendanceEntity> captor =
                        org.mockito.ArgumentCaptor.forClass(AttendanceEntity.class);
                org.mockito.Mockito.verify(attendanceRepo).save(captor.capture());
                AttendanceEntity att = captor.getValue();
                assertThat(att.getIsPresent()).isTrue();
                assertThat(att.getIsCatchUp()).isTrue();
                assertThat(att.getSession()).isSameAs(catchUpSession);
                assertThat(att.getGroup()).isSameAs(catchUpGroup);
                assertThat(att.getMissedSession()).isSameAs(originalSession);
            }
        }
    }

    private static boolean isAllowed(CatchUpStatus start, Op op) {
        return switch (op) {
            case SCHEDULE -> start == CatchUpStatus.PENDING;
            case COMPLETE -> start == CatchUpStatus.SCHEDULED;
            case CANCEL -> start == CatchUpStatus.PENDING || start == CatchUpStatus.SCHEDULED;
        };
    }

    private static CatchUpRequestEntity invoke(CatchUpService service, Op op,
                                               SessionEntity catchUpSession, GroupEntity catchUpGroup) {
        return switch (op) {
            case SCHEDULE -> service.schedule(REQUEST_ID, catchUpSession.getId(), catchUpGroup.getId());
            case COMPLETE -> service.complete(REQUEST_ID);
            case CANCEL -> service.cancel(REQUEST_ID, "motif");
        };
    }

    @Provide
    Arbitrary<CatchUpStatus> statuses() {
        return Arbitraries.of(CatchUpStatus.values());
    }

    @Provide
    Arbitrary<Op> ops() {
        return Arbitraries.of(Op.values());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

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
}
