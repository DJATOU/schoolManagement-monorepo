package com.school.management.service.session;

import com.school.management.dto.session.RecurringSessionRequestDTO;
import com.school.management.dto.session.RecurringSessionResultDTO;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.RoomEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.TeacherEntity;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.RoomRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.repository.TeacherRepository;
import com.school.management.service.ReadOnlyYearGuard;
import com.school.management.service.SeriesRolloverService;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.exception.ReadOnlySchoolYearException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de la génération de séances récurrentes.
 *
 * <p>Points verrouillés : le calcul des occurrences par jour de semaine, le rattachement
 * systématique à une série via le service de bascule, le traitement des conflits de salle
 * et d'enseignant, et le refus de planifier dans une année scolaire close.</p>
 */
class RecurringSessionServiceTest {

    private static final Long GROUP_ID = 100L;
    private static final Long ROOM_ID = 7L;
    private static final Long TEACHER_ID = 9L;

    private SessionRepository sessionRepository;
    private GroupRepository groupRepository;
    private TeacherRepository teacherRepository;
    private RoomRepository roomRepository;
    private SeriesRolloverService seriesRolloverService;
    private ReadOnlyYearGuard readOnlyYearGuard;

    private RecurringSessionService service;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(SessionRepository.class);
        groupRepository = mock(GroupRepository.class);
        teacherRepository = mock(TeacherRepository.class);
        roomRepository = mock(RoomRepository.class);
        seriesRolloverService = mock(SeriesRolloverService.class);
        readOnlyYearGuard = mock(ReadOnlyYearGuard.class);

        GroupEntity group = new GroupEntity();
        group.setId(GROUP_ID);
        group.setName("Groupe physique");
        group.setSessionNumberPerSerie(8);

        lenient().when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

        // Chaque séance sauvegardée reçoit un identifiant, comme le ferait la base.
        AtomicLong sequence = new AtomicLong(1);
        lenient().when(sessionRepository.save(any(SessionEntity.class))).thenAnswer(invocation -> {
            SessionEntity session = invocation.getArgument(0);
            session.setId(sequence.getAndIncrement());
            return session;
        });

        SessionSeriesEntity series = new SessionSeriesEntity();
        series.setId(500L);
        lenient().when(seriesRolloverService.attachSessionToSeries(any(), any())).thenReturn(series);

        lenient().when(sessionRepository.existsRoomOverlap(anyLong(), any(), any())).thenReturn(false);
        lenient().when(sessionRepository.existsTeacherOverlap(anyLong(), any(), any())).thenReturn(false);

        service = new RecurringSessionService(sessionRepository, groupRepository, teacherRepository,
                roomRepository, seriesRolloverService, readOnlyYearGuard);
    }

    private RecurringSessionRequestDTO request(LocalDate from, LocalDate to, DayOfWeek... days) {
        RecurringSessionRequestDTO request = new RecurringSessionRequestDTO();
        request.setGroupId(GROUP_ID);
        request.setTitle("Cours");
        request.setSessionType("COURS");
        request.setStartDate(from);
        request.setEndDate(to);
        request.setDaysOfWeek(Set.of(days));
        request.setStartTime(LocalTime.of(12, 0));
        request.setEndTime(LocalTime.of(14, 0));
        return request;
    }

    // ------------------------------------------------------------------
    // Génération
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Un jour par semaine sur quatre semaines → 4 séances")
    void generatesOneOccurrencePerWeek() {
        // Du lundi 7 au dimanche 27 septembre 2026 : 3 lundis (7, 14, 21).
        RecurringSessionResultDTO result = service.generate(
                request(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 27), DayOfWeek.MONDAY));

        assertThat(result.created()).isEqualTo(3);
        assertThat(result.skipped()).isZero();
        assertThat(result.sessionIds()).hasSize(3);
    }

    @Test
    @DisplayName("Deux jours par semaine → une occurrence par jour demandé")
    void generatesForSeveralWeekdays() {
        // Semaine du lundi 7 au dimanche 13 septembre 2026 : 1 lundi + 1 mercredi.
        RecurringSessionResultDTO result = service.generate(
                request(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 13),
                        DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));

        assertThat(result.created()).isEqualTo(2);
    }

    @Test
    @DisplayName("Chaque séance créée est rattachée à une série par le service de bascule")
    void attachesEverySessionToASeries() {
        service.generate(request(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 21), DayOfWeek.MONDAY));

        verify(seriesRolloverService, org.mockito.Mockito.times(3))
                .attachSessionToSeries(any(GroupEntity.class), any(SessionEntity.class));
    }

    @Test
    @DisplayName("Titres numérotés par occurrence")
    void numbersTitles() {
        service.generate(request(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 14), DayOfWeek.MONDAY));

        org.mockito.ArgumentCaptor<SessionEntity> captor =
                org.mockito.ArgumentCaptor.forClass(SessionEntity.class);
        verify(sessionRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());

        assertThat(captor.getAllValues()).extracting(SessionEntity::getTitle)
                .containsExactly("Cours 1", "Cours 2");
    }

    // ------------------------------------------------------------------
    // Conflits
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Salle occupée → occurrence écartée et signalée, les autres sont créées")
    void skipsOccurrenceWhenRoomBusy() {
        RoomEntity room = new RoomEntity();
        room.setId(ROOM_ID);
        room.setName("Salle A1");
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));

        // Le premier créneau est occupé, les suivants libres.
        when(sessionRepository.existsRoomOverlap(anyLong(), any(), any()))
                .thenReturn(true).thenReturn(false);

        RecurringSessionRequestDTO request =
                request(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 21), DayOfWeek.MONDAY);
        request.setRoomId(ROOM_ID);

        RecurringSessionResultDTO result = service.generate(request);

        assertThat(result.created()).isEqualTo(2);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.conflicts()).singleElement()
                .satisfies(conflict -> {
                    assertThat(conflict.reason()).isEqualTo("ROOM_BUSY");
                    assertThat(conflict.detail()).isEqualTo("Salle A1");
                });
    }

    @Test
    @DisplayName("Enseignant occupé → conflit signalé avec son nom")
    void reportsTeacherConflict() {
        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(TEACHER_ID);
        teacher.setFirstName("Omar");
        teacher.setLastName("Belkacem");
        when(teacherRepository.findById(TEACHER_ID)).thenReturn(Optional.of(teacher));
        when(sessionRepository.existsTeacherOverlap(anyLong(), any(), any())).thenReturn(true);

        RecurringSessionRequestDTO request =
                request(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 7), DayOfWeek.MONDAY);
        request.setTeacherId(TEACHER_ID);

        RecurringSessionResultDTO result = service.generate(request);

        assertThat(result.created()).isZero();
        assertThat(result.conflicts()).singleElement()
                .satisfies(conflict -> assertThat(conflict.reason()).isEqualTo("TEACHER_BUSY"));
    }

    @Test
    @DisplayName("skipConflicts = false → toute la demande est refusée (409), rien n'est créé")
    void rejectsWholeRequestWhenConflictsNotAllowed() {
        RoomEntity room = new RoomEntity();
        room.setId(ROOM_ID);
        room.setName("Salle A1");
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
        when(sessionRepository.existsRoomOverlap(anyLong(), any(), any())).thenReturn(true);

        RecurringSessionRequestDTO request =
                request(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 21), DayOfWeek.MONDAY);
        request.setRoomId(ROOM_ID);
        request.setSkipConflicts(false);

        assertThatThrownBy(() -> service.generate(request))
                .isInstanceOf(CustomServiceException.class);

        verify(sessionRepository, never()).save(any(SessionEntity.class));
    }

    // ------------------------------------------------------------------
    // Validation et gardes
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Année scolaire close → refus avant toute création")
    void refusesClosedSchoolYear() {
        doThrow(new ReadOnlySchoolYearException()).when(readOnlyYearGuard).assertGroupMutable(any());

        assertThatThrownBy(() -> service.generate(
                request(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 21), DayOfWeek.MONDAY)))
                .isInstanceOf(ReadOnlySchoolYearException.class);

        verify(sessionRepository, never()).save(any(SessionEntity.class));
    }

    @Test
    @DisplayName("Date de fin antérieure à la date de début → 400")
    void refusesInvertedDates() {
        assertThatThrownBy(() -> service.generate(
                request(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 7), DayOfWeek.MONDAY)))
                .isInstanceOf(CustomServiceException.class);
    }

    @Test
    @DisplayName("Heure de fin non postérieure à l'heure de début → 400")
    void refusesInvertedTimes() {
        RecurringSessionRequestDTO request =
                request(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 21), DayOfWeek.MONDAY);
        request.setEndTime(LocalTime.of(12, 0));

        assertThatThrownBy(() -> service.generate(request))
                .isInstanceOf(CustomServiceException.class);
    }

    @Test
    @DisplayName("Aucune date ne correspond aux jours choisis → 400 plutôt qu'un silence")
    void refusesEmptyOccurrenceSet() {
        // Du mardi 8 au jeudi 10 septembre 2026 : aucun dimanche.
        assertThatThrownBy(() -> service.generate(
                request(LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 10), DayOfWeek.SUNDAY)))
                .isInstanceOf(CustomServiceException.class);
    }

    @Test
    @DisplayName("Période démesurée → 400 (garde-fou contre une erreur de saisie)")
    void refusesTooManyOccurrences() {
        assertThatThrownBy(() -> service.generate(
                request(LocalDate.of(2026, 1, 1), LocalDate.of(2030, 1, 1),
                        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY)))
                .isInstanceOf(CustomServiceException.class);
    }

    // ------------------------------------------------------------------
    // Simulation
    // ------------------------------------------------------------------

    @Test
    @DisplayName("La simulation compte les occurrences sans rien enregistrer")
    void previewDoesNotPersist() {
        RecurringSessionResultDTO result = service.preview(
                request(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 21), DayOfWeek.MONDAY));

        assertThat(result.created()).isEqualTo(3);
        assertThat(result.sessionIds()).isEmpty();
        verify(sessionRepository, never()).save(any(SessionEntity.class));
        verify(seriesRolloverService, never()).attachSessionToSeries(any(), any());
    }
}
