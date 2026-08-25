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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Génération de séances récurrentes : un créneau fixe répété sur une période.
 *
 * <p>Répond au besoin « ce groupe a cours tous les lundis et mercredis de 12h à 14h toute
 * l'année » sans ressaisir chaque séance.</p>
 *
 * <h2>Pourquoi côté serveur</h2>
 * Une année de cours représente une centaine de séances. Les créer une par une depuis le
 * navigateur signifierait autant d'appels HTTP, sans transaction commune, avec un
 * rattachement de série calculé côté client. Ici tout se joue en une transaction, et le
 * rattachement passe par le {@link SeriesRolloverService} : les séries mensuelles du groupe
 * se créent et se remplissent au fil de la génération, en respectant
 * {@code sessionNumberPerSerie}.
 *
 * <h2>Conflits</h2>
 * Une occurrence dont la salle ou l'enseignant est déjà occupé n'est jamais créée en
 * silence. Selon {@code skipConflicts}, elle est soit écartée et signalée dans le compte
 * rendu, soit cause du rejet de toute la demande.
 */
@Service
public class RecurringSessionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecurringSessionService.class);

    /** Garde-fou : au-delà, la demande est probablement une erreur de saisie de dates. */
    private static final int MAX_OCCURRENCES = 400;

    private final SessionRepository sessionRepository;
    private final GroupRepository groupRepository;
    private final TeacherRepository teacherRepository;
    private final RoomRepository roomRepository;
    private final SeriesRolloverService seriesRolloverService;
    private final ReadOnlyYearGuard readOnlyYearGuard;

    public RecurringSessionService(SessionRepository sessionRepository,
            GroupRepository groupRepository,
            TeacherRepository teacherRepository,
            RoomRepository roomRepository,
            SeriesRolloverService seriesRolloverService,
            ReadOnlyYearGuard readOnlyYearGuard) {
        this.sessionRepository = sessionRepository;
        this.groupRepository = groupRepository;
        this.teacherRepository = teacherRepository;
        this.roomRepository = roomRepository;
        this.seriesRolloverService = seriesRolloverService;
        this.readOnlyYearGuard = readOnlyYearGuard;
    }

    /**
     * Génère les séances correspondant à la récurrence demandée.
     *
     * @param request créneau, jours de la semaine et période
     * @return le compte rendu (créées, écartées, conflits)
     * @throws CustomServiceException 400 si la demande est incohérente, 404 si une
     *                                référence est introuvable, 409 en cas de conflit
     *                                lorsque {@code skipConflicts} est faux
     */
    @Transactional
    public RecurringSessionResultDTO generate(RecurringSessionRequestDTO request) {
        Objects.requireNonNull(request, "La demande ne doit pas être nulle.");
        validate(request);

        GroupEntity group = groupRepository.findById(request.getGroupId())
                .orElseThrow(() -> new CustomServiceException(
                        "Groupe introuvable : " + request.getGroupId(), HttpStatus.NOT_FOUND));

        // Refuse de planifier dans une année scolaire close (exigence 9.2), avant tout calcul.
        readOnlyYearGuard.assertGroupMutable(group);

        TeacherEntity teacher = resolveTeacher(request, group);
        RoomEntity room = resolveRoom(request);

        List<LocalDate> dates = occurrenceDates(request);
        if (dates.isEmpty()) {
            throw new CustomServiceException(
                    "Aucune date ne correspond aux jours choisis sur la période demandée.",
                    HttpStatus.BAD_REQUEST);
        }
        if (dates.size() > MAX_OCCURRENCES) {
            throw new CustomServiceException(
                    "La récurrence produirait " + dates.size() + " séances (maximum "
                            + MAX_OCCURRENCES + "). Réduisez la période.",
                    HttpStatus.BAD_REQUEST);
        }

        List<Long> createdIds = new ArrayList<>();
        Set<Long> seriesIds = new LinkedHashSet<>();
        List<RecurringSessionResultDTO.Conflict> conflicts = new ArrayList<>();
        int occurrence = 0;

        for (LocalDate date : dates) {
            Date start = toDate(date.atTime(request.getStartTime()));
            Date end = toDate(date.atTime(request.getEndTime()));

            RecurringSessionResultDTO.Conflict conflict = findConflict(room, teacher, start, end);
            if (conflict != null) {
                if (!request.isSkipConflicts()) {
                    throw new CustomServiceException(
                            "Créneau déjà occupé le " + date + " (" + conflict.reason() + ").",
                            HttpStatus.CONFLICT);
                }
                conflicts.add(conflict);
                continue;
            }

            occurrence++;
            SessionEntity session = new SessionEntity();
            session.setTitle(buildTitle(request, occurrence));
            session.setSessionType(request.getSessionType());
            session.setGroup(group);
            session.setTeacher(teacher);
            session.setRoom(room);
            session.setSessionTimeStart(start);
            session.setSessionTimeEnd(end);
            session.setIsFinished(false);

            SessionEntity saved = sessionRepository.save(session);
            // Rattachement à la série courante du groupe, avec création de la suivante dès
            // que celle-ci est pleine : c'est le service de bascule qui décide, pas le client.
            SessionSeriesEntity series = seriesRolloverService.attachSessionToSeries(group, saved);

            createdIds.add(saved.getId());
            if (series != null && series.getId() != null) {
                seriesIds.add(series.getId());
            }
        }

        LOGGER.info("Récurrence groupe {} : {} séance(s) créée(s), {} écartée(s) pour conflit.",
                group.getId(), createdIds.size(), conflicts.size());

        return new RecurringSessionResultDTO(
                createdIds.size(), conflicts.size(), createdIds, new ArrayList<>(seriesIds), conflicts);
    }

    /**
     * Simule la récurrence sans rien enregistrer : nombre d'occurrences et conflits
     * prévus. Permet à l'interface d'afficher un récapitulatif fiable avant validation.
     */
    @Transactional(readOnly = true)
    public RecurringSessionResultDTO preview(RecurringSessionRequestDTO request) {
        Objects.requireNonNull(request, "La demande ne doit pas être nulle.");
        validate(request);

        GroupEntity group = groupRepository.findById(request.getGroupId())
                .orElseThrow(() -> new CustomServiceException(
                        "Groupe introuvable : " + request.getGroupId(), HttpStatus.NOT_FOUND));

        TeacherEntity teacher = resolveTeacher(request, group);
        RoomEntity room = resolveRoom(request);

        List<RecurringSessionResultDTO.Conflict> conflicts = new ArrayList<>();
        int planned = 0;

        for (LocalDate date : occurrenceDates(request)) {
            Date start = toDate(date.atTime(request.getStartTime()));
            Date end = toDate(date.atTime(request.getEndTime()));

            RecurringSessionResultDTO.Conflict conflict = findConflict(room, teacher, start, end);
            if (conflict != null) {
                conflicts.add(conflict);
            } else {
                planned++;
            }
        }

        return new RecurringSessionResultDTO(planned, conflicts.size(), List.of(), List.of(), conflicts);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void validate(RecurringSessionRequestDTO request) {
        if (request.getStartDate() == null || request.getEndDate() == null
                || request.getStartTime() == null || request.getEndTime() == null
                || request.getDaysOfWeek() == null || request.getDaysOfWeek().isEmpty()) {
            throw new CustomServiceException("Récurrence incomplète.", HttpStatus.BAD_REQUEST);
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new CustomServiceException(
                    "La date de fin précède la date de début.", HttpStatus.BAD_REQUEST);
        }
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new CustomServiceException(
                    "L'heure de fin doit suivre l'heure de début.", HttpStatus.BAD_REQUEST);
        }
    }

    /** Dates retenues : chaque jour de la période dont le jour de semaine est demandé. */
    private List<LocalDate> occurrenceDates(RecurringSessionRequestDTO request) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate cursor = request.getStartDate();
        while (!cursor.isAfter(request.getEndDate())) {
            if (request.getDaysOfWeek().contains(cursor.getDayOfWeek())) {
                dates.add(cursor);
            }
            cursor = cursor.plusDays(1);
        }
        return dates;
    }

    /**
     * Enseignant de la séance : celui demandé, sinon celui du groupe. Aucun enseignant
     * n'est un cas accepté (créneau à pourvoir).
     */
    private TeacherEntity resolveTeacher(RecurringSessionRequestDTO request, GroupEntity group) {
        if (request.getTeacherId() == null) {
            return group.getTeacher();
        }
        return teacherRepository.findById(request.getTeacherId())
                .orElseThrow(() -> new CustomServiceException(
                        "Enseignant introuvable : " + request.getTeacherId(), HttpStatus.NOT_FOUND));
    }

    private RoomEntity resolveRoom(RecurringSessionRequestDTO request) {
        if (request.getRoomId() == null) {
            return null;
        }
        return roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new CustomServiceException(
                        "Salle introuvable : " + request.getRoomId(), HttpStatus.NOT_FOUND));
    }

    /** Premier conflit rencontré sur le créneau, ou {@code null} s'il est libre. */
    private RecurringSessionResultDTO.Conflict findConflict(RoomEntity room, TeacherEntity teacher,
            Date start, Date end) {
        if (room != null && sessionRepository.existsRoomOverlap(room.getId(), start, end)) {
            return new RecurringSessionResultDTO.Conflict(start, "ROOM_BUSY", room.getName());
        }
        if (teacher != null && sessionRepository.existsTeacherOverlap(teacher.getId(), start, end)) {
            return new RecurringSessionResultDTO.Conflict(start, "TEACHER_BUSY",
                    teacher.getFirstName() + " " + teacher.getLastName());
        }
        return null;
    }

    private String buildTitle(RecurringSessionRequestDTO request, int occurrence) {
        String base = request.getTitle() == null || request.getTitle().isBlank()
                ? "Séance"
                : request.getTitle().trim();
        return request.isNumberTitles() ? base + " " + occurrence : base;
    }

    private Date toDate(LocalDateTime dateTime) {
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }
}
