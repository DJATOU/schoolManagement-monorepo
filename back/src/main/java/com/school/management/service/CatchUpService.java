package com.school.management.service;

import com.school.management.dto.CatchUpRequestDTO;
import com.school.management.dto.CatchUpResponseDTO;
import com.school.management.dto.StudentAbsenceDTO;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.CatchUpRequestEntity;
import com.school.management.persistance.CatchUpStatus;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.CatchUpRequestRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.payment.PaymentStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service du workflow de rattrapage (catch-up).
 *
 * <p>Implémente la machine à états d'une demande de rattrapage
 * (PENDING → SCHEDULED → COMPLETED, avec annulation possible depuis PENDING ou
 * SCHEDULED) ainsi que ses effets de bord : création d'une présence de rattrapage à la
 * complétion, filtrage des séances de rattrapage compatibles, et validations à la
 * création (droit au rattrapage, séance manquée payée).</p>
 *
 * <p>Règles de compatibilité (requirement 8) : une séance de rattrapage est compatible
 * lorsque son groupe a le même {@code Group_Type} ET le même {@code Price_Per_Session}
 * que le groupe d'origine. Le groupe d'origine est trivialement compatible avec
 * lui-même.</p>
 *
 * <p>Transitions autorisées : {@code PENDING→SCHEDULED}, {@code SCHEDULED→COMPLETED},
 * {@code PENDING→CANCELLED}, {@code SCHEDULED→CANCELLED}. Toute autre transition est
 * rejetée (HTTP 409). Les messages restent en français.</p>
 */
@Service
public class CatchUpService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatchUpService.class);

    private final CatchUpRequestRepository catchUpRequestRepository;
    private final AttendanceRepository attendanceRepository;
    private final SessionRepository sessionRepository;
    private final PaymentStatusService paymentStatusService;

    public CatchUpService(CatchUpRequestRepository catchUpRequestRepository,
                          AttendanceRepository attendanceRepository,
                          SessionRepository sessionRepository,
                          PaymentStatusService paymentStatusService) {
        this.catchUpRequestRepository = catchUpRequestRepository;
        this.attendanceRepository = attendanceRepository;
        this.sessionRepository = sessionRepository;
        this.paymentStatusService = paymentStatusService;
    }

    // ------------------------------------------------------------------
    // Création
    // ------------------------------------------------------------------

    /**
     * Crée une demande de rattrapage à l'état {@code PENDING}.
     *
     * <p>Préconditions (requirement 7.4, 7.5) :</p>
     * <ul>
     *   <li>la fiche de présence d'origine doit exister (sinon 404) ;</li>
     *   <li>le droit au rattrapage ne doit pas être explicitement révoqué
     *       ({@code catchUpRight == Boolean.FALSE}) — {@code null} est traité comme vrai
     *       (défaut de l'entité) ;</li>
     *   <li>la séance manquée doit être payée par l'étudiant : on vérifie via
     *       {@link PaymentStatusService#isStudentPaymentOverdueForSeries(Long, Long)} que
     *       l'étudiant n'est pas en retard sur la série de la séance manquée. Si la séance
     *       d'origine n'a pas de série, la vérification de paiement est ignorée (aucune
     *       série facturable pour évaluer le retard).</li>
     * </ul>
     *
     * @param dto données de création
     * @return la demande persistée à l'état PENDING
     * @throws CustomServiceException (404) si la fiche de présence d'origine est introuvable
     * @throws CustomServiceException (400) si le droit au rattrapage est révoqué ou la séance non payée
     */
    @Transactional
    public CatchUpRequestEntity create(CatchUpRequestDTO dto) {
        Objects.requireNonNull(dto, "La requête de rattrapage ne doit pas être nulle.");

        // Chargement de la fiche de présence d'origine.
        AttendanceEntity originalAttendance = attendanceRepository.findById(dto.originalAttendanceId())
                .orElseThrow(() -> new CustomServiceException(
                        "Fiche de présence introuvable pour l'identifiant : " + dto.originalAttendanceId(),
                        HttpStatus.NOT_FOUND));

        // Requirement 7.4 : le droit au rattrapage ne doit pas être explicitement révoqué.
        if (Boolean.FALSE.equals(originalAttendance.getCatchUpRight())) {
            throw new CustomServiceException(
                    "Le droit au rattrapage a été révoqué pour cette absence.",
                    HttpStatus.BAD_REQUEST);
        }

        // Chargement de la séance manquée d'origine.
        SessionEntity originalSession = sessionRepository.findById(dto.originalSessionId())
                .orElseThrow(() -> new CustomServiceException(
                        "Séance introuvable pour l'identifiant : " + dto.originalSessionId(),
                        HttpStatus.NOT_FOUND));

        // Requirement 7.5 : la séance manquée doit être payée (étudiant non en retard sur la série).
        SessionSeriesEntity missedSeries = originalSession.getSessionSeries();
        if (missedSeries != null) {
            boolean overdue = paymentStatusService.isStudentPaymentOverdueForSeries(
                    dto.studentId(), missedSeries.getId());
            if (overdue) {
                throw new CustomServiceException(
                        "La séance manquée n'est pas payée : le rattrapage ne peut pas être demandé.",
                        HttpStatus.BAD_REQUEST);
            }
        } else {
            LOGGER.debug("Séance d'origine {} sans série : vérification de paiement ignorée.",
                    originalSession.getId());
        }

        // Groupe d'origine : celui du DTO si fourni, sinon celui de la séance manquée.
        GroupEntity originalGroup = originalSession.getGroup();

        StudentEntity student = StudentEntity.builder().id(dto.studentId()).build();

        CatchUpRequestEntity request = CatchUpRequestEntity.builder()
                .student(student)
                .originalSession(originalSession)
                .originalGroup(originalGroup)
                .originalAttendance(originalAttendance)
                .status(CatchUpStatus.PENDING)
                .requestDate(new Date())
                .notes(dto.notes())
                .build();

        return catchUpRequestRepository.save(request);
    }

    // ------------------------------------------------------------------
    // Séances disponibles (compatibilité)
    // ------------------------------------------------------------------

    /**
     * Retourne les séances de rattrapage disponibles pour une séance manquée.
     *
     * <p>Une séance est disponible lorsque son groupe est compatible avec le groupe
     * d'origine, c.-à-d. même {@code Group_Type} ET même {@code Price_Per_Session}
     * (requirement 8.1, 8.2, 8.3). Le groupe d'origine est inclus quand il est
     * compatible (il l'est trivialement).</p>
     *
     * @param studentId         identifiant de l'étudiant (non utilisé pour le filtrage, conservé pour l'API)
     * @param originalSessionId identifiant de la séance manquée d'origine
     * @return la liste des séances compatibles
     * @throws CustomServiceException (404) si la séance d'origine est introuvable
     */
    @Transactional(readOnly = true)
    public List<SessionEntity> getAvailableSessions(Long studentId, Long originalSessionId) {
        SessionEntity originalSession = sessionRepository.findById(originalSessionId)
                .orElseThrow(() -> new CustomServiceException(
                        "Séance introuvable pour l'identifiant : " + originalSessionId,
                        HttpStatus.NOT_FOUND));

        GroupEntity originalGroup = originalSession.getGroup();

        return sessionRepository.findAll().stream()
                .filter(session -> isCompatible(originalGroup, session.getGroup()))
                .toList();
    }

    // ------------------------------------------------------------------
    // Planification
    // ------------------------------------------------------------------

    /**
     * Planifie une demande {@code PENDING} sur une séance de rattrapage compatible.
     *
     * @param requestId        identifiant de la demande
     * @param catchUpSessionId identifiant de la séance de rattrapage
     * @param catchUpGroupId   identifiant du groupe de rattrapage
     * @return la demande à l'état SCHEDULED
     * @throws CustomServiceException (404) si la demande ou la séance est introuvable
     * @throws CustomServiceException (409) si la demande n'est pas à l'état PENDING
     * @throws CustomServiceException (400) si la séance de rattrapage n'est pas compatible
     */
    @Transactional
    public CatchUpRequestEntity schedule(Long requestId, Long catchUpSessionId, Long catchUpGroupId) {
        CatchUpRequestEntity request = loadRequest(requestId);

        // Requirement 9.6 : seule une demande PENDING peut être planifiée.
        if (request.getStatus() != CatchUpStatus.PENDING) {
            throw new CustomServiceException(
                    "Transition impossible : seule une demande en attente (PENDING) peut être planifiée.",
                    HttpStatus.CONFLICT);
        }

        SessionEntity catchUpSession = sessionRepository.findById(catchUpSessionId)
                .orElseThrow(() -> new CustomServiceException(
                        "Séance de rattrapage introuvable pour l'identifiant : " + catchUpSessionId,
                        HttpStatus.NOT_FOUND));

        GroupEntity catchUpGroup = catchUpSession.getGroup();

        // Requirement 8.4 : re-validation de la compatibilité du groupe de rattrapage.
        if (!isCompatible(request.getOriginalGroup(), catchUpGroup)) {
            throw new CustomServiceException(
                    "La séance de rattrapage n'est pas compatible avec le groupe d'origine "
                            + "(type de groupe et prix par séance doivent être identiques).",
                    HttpStatus.BAD_REQUEST);
        }

        request.setStatus(CatchUpStatus.SCHEDULED);
        request.setCatchUpSession(catchUpSession);
        request.setCatchUpGroup(catchUpGroup);
        request.setScheduledDate(new Date());

        return catchUpRequestRepository.save(request);
    }

    // ------------------------------------------------------------------
    // Complétion
    // ------------------------------------------------------------------

    /**
     * Complète une demande {@code SCHEDULED} : passe à {@code COMPLETED} et crée une
     * présence de rattrapage ({@code isPresent = true}, {@code isCatchUp = true}) reliée à
     * la séance / au groupe de rattrapage et à la séance manquée (requirement 9.4, 9.7,
     * 10.1).
     *
     * @param requestId identifiant de la demande
     * @return la demande à l'état COMPLETED
     * @throws CustomServiceException (404) si la demande est introuvable
     * @throws CustomServiceException (409) si la demande n'est pas à l'état SCHEDULED
     */
    @Transactional
    public CatchUpRequestEntity complete(Long requestId) {
        CatchUpRequestEntity request = loadRequest(requestId);

        // Requirement 9.6 : seule une demande SCHEDULED peut être complétée.
        if (request.getStatus() != CatchUpStatus.SCHEDULED) {
            throw new CustomServiceException(
                    "Transition impossible : seule une demande planifiée (SCHEDULED) peut être complétée.",
                    HttpStatus.CONFLICT);
        }

        SessionEntity catchUpSession = request.getCatchUpSession();

        // Exigence 1.7 : sans série, la présence créée échapperait au décompte des séances suivies
        // et au devis, qui lisent tous deux les présences PAR SÉRIE. Mieux vaut refuser la
        // complétion que produire une présence invisible pour la facturation.
        //
        // Séance absente et séance sans série sont traitées ensemble : dans les deux cas aucune
        // série ne peut être déterminée, et les distinguer n'aiderait pas l'administrateur, qui a
        // la même correction à faire.
        if (catchUpSession == null || catchUpSession.getSessionSeries() == null) {
            throw new CustomServiceException(String.format(
                    "La séance de rattrapage %s n'est rattachée à aucune série : complétion "
                            + "impossible. Rattacher cette séance à une série, puis réessayer.",
                    idOrNull(catchUpSession)),
                    HttpStatus.BAD_REQUEST);
        }
        SessionSeriesEntity catchUpSeries = catchUpSession.getSessionSeries();

        // Exigence 1.9 : deux rattrapages actifs de la même séance manquée rendraient le décompte
        // des séances suivies indéterminé — laquelle des deux compense l'absence ?
        SessionEntity missedSession = request.getOriginalSession();
        Long studentId = request.getStudent() != null ? request.getStudent().getId() : null;
        if (missedSession != null && studentId != null
                && attendanceRepository.existsByStudentIdAndMissedSessionIdAndActiveTrue(
                        studentId, missedSession.getId())) {
            throw new CustomServiceException(String.format(
                    "Un rattrapage est déjà enregistré pour la séance manquée %s : un seul "
                            + "rattrapage par séance manquée est possible.", missedSession.getId()),
                    HttpStatus.CONFLICT);
        }

        request.setStatus(CatchUpStatus.COMPLETED);
        request.setCompletedDate(new Date());

        // Effet de bord : création de la présence de rattrapage (requirement 9.4, 9.7, 10.1).
        // La présence d'origine n'est PAS modifiée (exigence 1.3) : elle reste une absence, et la
        // mention « Rattrapée » est dérivée à l'affichage. Réécrire la présence effacerait le fait
        // que l'étudiant n'était pas là.
        AttendanceEntity catchUpAttendance = AttendanceEntity.builder()
                .student(request.getStudent())
                .session(catchUpSession)
                // Exigence 1.1 : la série de la séance de rattrapage, dans la même transaction.
                .sessionSeries(catchUpSeries)
                .group(request.getCatchUpGroup())
                .missedSession(missedSession)
                .isPresent(true)
                .isCatchUp(true)
                .build();
        attendanceRepository.save(catchUpAttendance);

        return catchUpRequestRepository.save(request);
    }

    // ------------------------------------------------------------------
    // Annulation
    // ------------------------------------------------------------------

    /**
     * Annule une demande depuis les états {@code PENDING} ou {@code SCHEDULED}.
     *
     * @param requestId identifiant de la demande
     * @param reason    motif d'annulation (facultatif ; enregistré quand fourni)
     * @return la demande à l'état CANCELLED
     * @throws CustomServiceException (404) si la demande est introuvable
     * @throws CustomServiceException (409) si la demande est déjà COMPLETED ou CANCELLED
     */
    @Transactional
    public CatchUpRequestEntity cancel(Long requestId, String reason) {
        CatchUpRequestEntity request = loadRequest(requestId);

        // Requirement 9.6 : l'annulation n'est autorisée que depuis PENDING ou SCHEDULED.
        if (request.getStatus() != CatchUpStatus.PENDING
                && request.getStatus() != CatchUpStatus.SCHEDULED) {
            throw new CustomServiceException(
                    "Transition impossible : une demande complétée ou déjà annulée ne peut pas être annulée.",
                    HttpStatus.CONFLICT);
        }

        request.setStatus(CatchUpStatus.CANCELLED);
        if (reason != null) {
            request.setCancellationReason(reason);
        }

        return catchUpRequestRepository.save(request);
    }

    // ------------------------------------------------------------------
    // Lectures
    // ------------------------------------------------------------------

    /**
     * Retourne toutes les demandes de rattrapage (tous statuts), enrichies des libellés
     * (noms d'étudiant, de séances et de groupes). L'enrichissement est réalisé dans le
     * contexte transactionnel pour résoudre sans risque les relations LAZY.
     */
    @Transactional(readOnly = true)
    public List<CatchUpResponseDTO> getAllRequests() {
        return catchUpRequestRepository.findAll().stream()
                .map(this::toResponseDtoWithNames)
                .toList();
    }

    /** Construit un {@link CatchUpResponseDTO} enrichi des libellés (appel dans une transaction). */
    private CatchUpResponseDTO toResponseDtoWithNames(CatchUpRequestEntity r) {
        StudentEntity student = r.getStudent();
        SessionEntity originalSession = r.getOriginalSession();
        GroupEntity originalGroup = r.getOriginalGroup();
        SessionEntity catchUpSession = r.getCatchUpSession();
        GroupEntity catchUpGroup = r.getCatchUpGroup();

        String studentName = student != null
                ? ((student.getFirstName() != null ? student.getFirstName() : "")
                    + " " + (student.getLastName() != null ? student.getLastName() : "")).trim()
                : null;

        return new CatchUpResponseDTO(
                r.getId(),
                student != null ? student.getId() : null,
                originalSession != null ? originalSession.getId() : null,
                originalGroup != null ? originalGroup.getId() : null,
                r.getOriginalAttendance() != null ? r.getOriginalAttendance().getId() : null,
                catchUpSession != null ? catchUpSession.getId() : null,
                catchUpGroup != null ? catchUpGroup.getId() : null,
                r.getStatus(),
                r.getRequestDate(),
                r.getScheduledDate(),
                r.getCompletedDate(),
                r.getCancellationReason(),
                r.getNotes(),
                studentName != null && !studentName.isEmpty() ? studentName : null,
                originalSession != null ? originalSession.getTitle() : null,
                originalGroup != null ? originalGroup.getName() : null,
                catchUpSession != null ? catchUpSession.getTitle() : null,
                catchUpGroup != null ? catchUpGroup.getName() : null);
    }

    /** Retourne les demandes en attente (statut PENDING). */
    @Transactional(readOnly = true)
    public List<CatchUpRequestEntity> getPendingRequests() {
        return catchUpRequestRepository.findByStatus(CatchUpStatus.PENDING);
    }

    /** Retourne les demandes rattachées à un étudiant. */
    @Transactional(readOnly = true)
    public List<CatchUpRequestEntity> getRequestsByStudent(Long studentId) {
        return catchUpRequestRepository.findByStudentId(studentId);
    }

    /**
     * Liste les absences d'un étudiant éligibles à une demande de rattrapage.
     *
     * <p>Une absence est éligible lorsque :</p>
     * <ul>
     *   <li>c'est une fiche active {@code isPresent = false} ;</li>
     *   <li>le droit au rattrapage n'a pas été révoqué ({@code catchUpRight != FALSE}) ;</li>
     *   <li>aucune demande de rattrapage non annulée n'existe déjà pour cette absence
     *       (les demandes {@code CANCELLED} ne bloquent pas : on peut re-demander).</li>
     * </ul>
     *
     * @param studentId identifiant de l'étudiant
     * @return la liste des absences éligibles, sous forme de {@link StudentAbsenceDTO}
     */
    @Transactional(readOnly = true)
    public List<StudentAbsenceDTO> getEligibleAbsences(Long studentId) {
        // Identifiants des absences déjà rattachées à une demande non annulée.
        Set<Long> attendancesWithActiveRequest = catchUpRequestRepository.findByStudentId(studentId).stream()
                .filter(r -> r.getStatus() != CatchUpStatus.CANCELLED)
                .map(CatchUpRequestEntity::getOriginalAttendance)
                .filter(Objects::nonNull)
                .map(AttendanceEntity::getId)
                .collect(Collectors.toSet());

        return attendanceRepository.findAbsencesByStudentId(studentId).stream()
                .filter(a -> !Boolean.FALSE.equals(a.getCatchUpRight()))
                .filter(a -> !attendancesWithActiveRequest.contains(a.getId()))
                .map(this::toAbsenceDto)
                .toList();
    }

    /** Aplati une absence en {@link StudentAbsenceDTO} (nom/date de séance, groupe, série). */
    private StudentAbsenceDTO toAbsenceDto(AttendanceEntity a) {
        SessionEntity session = a.getSession();
        GroupEntity group = a.getGroup() != null ? a.getGroup()
                : (session != null ? session.getGroup() : null);
        return new StudentAbsenceDTO(
                a.getId(),
                session != null ? session.getId() : null,
                session != null ? session.getTitle() : null,
                session != null ? session.getSessionTimeStart() : null,
                group != null ? group.getId() : null,
                group != null ? group.getName() : null,
                a.getSessionSeries() != null ? a.getSessionSeries().getId() : null,
                a.getIsJustified(),
                a.getCatchUpRight());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Charge une demande ou lève une 404. */
    private CatchUpRequestEntity loadRequest(Long requestId) {
        return catchUpRequestRepository.findById(requestId)
                .orElseThrow(() -> new CustomServiceException(
                        "Demande de rattrapage introuvable pour l'identifiant : " + requestId,
                        HttpStatus.NOT_FOUND));
    }

    /**
     * Deux groupes sont compatibles lorsqu'ils ont la même année scolaire
     * ({@code School_Year}), le même type de groupe ({@code Group_Type}) ET le même prix par
     * séance ({@code Price_Per_Session}). La contrainte d'année interdit tout rattrapage
     * inter-années : on ne rattrape une séance manquée que dans un groupe de la même année.
     *
     * @param original  groupe d'origine
     * @param candidate groupe candidat
     * @return vrai si les groupes sont compatibles
     */
    boolean isCompatible(GroupEntity original, GroupEntity candidate) {
        if (original == null || candidate == null) {
            return false;
        }
        return sameSchoolYear(original, candidate)
                && sameGroupType(original, candidate)
                && samePricePerSession(original, candidate);
    }

    /**
     * Deux groupes appartiennent-ils à la même année scolaire ? Le rattrapage doit rester dans
     * l'année de la séance manquée (pas d'inter-années).
     */
    private boolean sameSchoolYear(GroupEntity a, GroupEntity b) {
        Long yearA = a.getSchoolYear() != null ? a.getSchoolYear().getId() : null;
        Long yearB = b.getSchoolYear() != null ? b.getSchoolYear().getId() : null;
        return Objects.equals(yearA, yearB);
    }

    private boolean sameGroupType(GroupEntity a, GroupEntity b) {
        Long typeA = a.getGroupType() != null ? a.getGroupType().getId() : null;
        Long typeB = b.getGroupType() != null ? b.getGroupType().getId() : null;
        return Objects.equals(typeA, typeB);
    }

    private boolean samePricePerSession(GroupEntity a, GroupEntity b) {
        Double priceA = (a.getPrice() != null) ? a.getPrice().getPrice() : null;
        Double priceB = (b.getPrice() != null) ? b.getPrice().getPrice() : null;
        return Objects.equals(priceA, priceB);
    }

    /** Identifiant d'une séance, ou {@code null} si la séance est absente. Sert aux messages. */
    private static Long idOrNull(SessionEntity session) {
        return session == null ? null : session.getId();
    }
}
