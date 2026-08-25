package com.school.management.service;

import com.school.management.dto.AttendanceDTO;
import com.school.management.mapper.SessionMapper;
import com.school.management.persistance.SessionEntity;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.RoomRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.repository.TeacherRepository;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.payment.PaymentDetailDeactivationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de la règle de suppression d'une séance :
 * <strong>la suppression n'est possible que si les présences ne sont pas validées</strong>.
 *
 * <p>Une séance est considérée validée lorsqu'elle est marquée terminée ({@code isFinished})
 * ou qu'elle porte des fiches de présence actives. Ces présences comptent dans le nombre de
 * séances suivies, qui détermine le montant dû : les effacer fausserait les soldes.</p>
 */
class SessionDeletionGuardTest {

    private static final long SESSION_ID = 42L;

    private SessionRepository sessionRepository;
    private AttendanceService attendanceService;
    private PaymentDetailDeactivationService paymentDetailDeactivationService;
    private ReadOnlyYearGuard readOnlyYearGuard;
    private SessionService service;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(SessionRepository.class);
        attendanceService = mock(AttendanceService.class);
        paymentDetailDeactivationService = mock(PaymentDetailDeactivationService.class);
        readOnlyYearGuard = mock(ReadOnlyYearGuard.class);

        service = new SessionService(
                sessionRepository,
                mock(GroupRepository.class),
                mock(SessionMapper.class),
                mock(RoomRepository.class),
                mock(TeacherRepository.class),
                mock(SessionSeriesRepository.class),
                paymentDetailDeactivationService,
                attendanceService,
                readOnlyYearGuard,
                mock(SeriesRolloverService.class));
    }

    private SessionEntity session(boolean finished) {
        SessionEntity session = new SessionEntity();
        session.setId(SESSION_ID);
        session.setIsFinished(finished);
        return session;
    }

    // ------------------------------------------------------------------
    // Désactivation (chemin utilisé par l'interface pour « Supprimer »)
    // ------------------------------------------------------------------

    @Test
    void deactivate_withoutAttendance_deactivatesSessionAndRelatedRecords() {
        SessionEntity session = session(false);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(attendanceService.getAttendanceBySessionId(SESSION_ID)).thenReturn(List.of());

        service.deactivateSession(SESSION_ID);

        assertThat(session.getActive()).isFalse();
        verify(attendanceService).deactivateBySessionId(SESSION_ID);
        verify(paymentDetailDeactivationService).deactivatePaymentDetailsBySessionId(SESSION_ID);
    }

    @Test
    void deactivate_whenSessionFinished_isRefusedWith409() {
        SessionEntity session = session(true);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(attendanceService.getAttendanceBySessionId(SESSION_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.deactivateSession(SESSION_ID))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));

        // La séance reste intacte et rien n'est désactivé.
        assertThat(session.getActive()).isNotEqualTo(false);
        verify(attendanceService, never()).deactivateBySessionId(anyLong());
        verify(paymentDetailDeactivationService, never()).deactivatePaymentDetailsBySessionId(anyLong());
    }

    @Test
    void deactivate_whenActiveAttendanceExists_isRefusedWith409() {
        SessionEntity session = session(false);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        // Des présences actives existent même si la séance n'est pas marquée terminée.
        when(attendanceService.getAttendanceBySessionId(SESSION_ID))
                .thenReturn(List.of(new AttendanceDTO()));

        assertThatThrownBy(() -> service.deactivateSession(SESSION_ID))
                .isInstanceOf(CustomServiceException.class)
                .satisfies(e -> assertThat(((CustomServiceException) e).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(paymentDetailDeactivationService, never()).deactivatePaymentDetailsBySessionId(anyLong());
    }

    // ------------------------------------------------------------------
    // Les séances désactivées disparaissent des listes
    // ------------------------------------------------------------------

    @Test
    void listings_excludeDeactivatedSessions() {
        SessionEntity active = session(false);
        SessionEntity inactive = session(false);
        inactive.setId(43L);
        inactive.setActive(false);
        SessionEntity legacy = session(false);
        legacy.setId(44L);
        legacy.setActive(null); // Enregistrement antérieur au drapeau : reste visible.

        when(sessionRepository.findBySessionSeriesId(7L))
                .thenReturn(List.of(active, inactive, legacy));

        assertThat(service.getSessionsBySeriesId(7L))
                .extracting(SessionEntity::getId)
                .containsExactly(SESSION_ID, 44L);
    }

    // ------------------------------------------------------------------
    // Suppression définitive (endpoint DELETE)
    // ------------------------------------------------------------------

    @Test
    void delete_withoutAttendance_removesSession() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session(false)));
        when(attendanceService.getAttendanceBySessionId(SESSION_ID)).thenReturn(List.of());

        service.deleteSession(SESSION_ID);

        verify(sessionRepository).deleteById(SESSION_ID);
    }

    @Test
    void delete_whenSessionFinished_isRefusedAndNothingRemoved() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session(true)));
        when(attendanceService.getAttendanceBySessionId(SESSION_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.deleteSession(SESSION_ID))
                .isInstanceOf(CustomServiceException.class);

        verify(sessionRepository, never()).deleteById(anyLong());
    }
}
