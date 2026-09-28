package com.school.management.service;

import com.school.management.dto.session.SessionDTO;
import com.school.management.dto.session.SessionSearchCriteriaDTO;
import com.school.management.mapper.SessionMapper;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.RoomEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.TeacherEntity;
import com.school.management.repository.*;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.payment.PaymentDetailDeactivationService; // ← AJOUTÉ
import com.school.management.service.util.CommonSpecifications;
import com.school.management.shared.mapper.MappingContext;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;

@Service
public class SessionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionService.class);
    private static final String SESSION_NOT_FOUND_MESSAGE = "Session not found with id: ";
    private static final String GROUPID = "groupId";
    private static final String ROOMID = "roomId";
    private static final String TEACHERID = "teacherId";

    private final SessionRepository sessionRepository;
    private final RoomRepository roomRepository;
    private final TeacherRepository teacherRepository;
    private final GroupRepository groupRepository;
    private final SessionSeriesRepository sessionSeriesRepository;
    private final SessionMapper sessionMapper;

    // ========== NOUVELLES DÉPENDANCES AJOUTÉES ==========
    private final PaymentDetailDeactivationService paymentDetailDeactivationService; // ← AJOUTÉ
    private final AttendanceService attendanceService; // ← AJOUTÉ
    private final ReadOnlyYearGuard readOnlyYearGuard; // Garde lecture seule des années passées
    // Décide de la série d'accueil d'une séance et crée la suivante quand la courante est pleine
    private final SeriesRolloverService seriesRolloverService;

    // MappingContext pour SessionMapper
    private MappingContext mappingContext;

    @Autowired
    public SessionService(
            SessionRepository sessionRepository,
            GroupRepository groupRepository,
            SessionMapper sessionMapper,
            RoomRepository roomRepository,
            TeacherRepository teacherRepository,
            SessionSeriesRepository sessionSeriesRepository,
            PaymentDetailDeactivationService paymentDetailDeactivationService, // ← AJOUTÉ
            AttendanceService attendanceService, // ← AJOUTÉ
            ReadOnlyYearGuard readOnlyYearGuard,
            SeriesRolloverService seriesRolloverService) {
        this.sessionRepository = sessionRepository;
        this.groupRepository = groupRepository;
        this.sessionMapper = sessionMapper;
        this.roomRepository = roomRepository;
        this.teacherRepository = teacherRepository;
        this.sessionSeriesRepository = sessionSeriesRepository;
        this.paymentDetailDeactivationService = paymentDetailDeactivationService; // ← AJOUTÉ
        this.attendanceService = attendanceService; // ← AJOUTÉ
        this.readOnlyYearGuard = readOnlyYearGuard;
        this.seriesRolloverService = seriesRolloverService;
    }

    /**
     * PHASE 1 REFACTORING: Initialise le MappingContext après injection des
     * dépendances
     */
    @PostConstruct
    private void initMappingContext() {
        this.mappingContext = MappingContext.of(
                null, null, null, null, null,
                teacherRepository,
                null, // SchoolYearRepository
                roomRepository,
                groupRepository,
                sessionSeriesRepository,
                null,
                sessionRepository);
        LOGGER.debug("MappingContext initialized for SessionService");
    }

    /**
     * Retourne le MappingContext pour utilisation par les controllers
     */
    public MappingContext getMappingContext() {
        return mappingContext;
    }


    /**
     * Écarte les séances désactivées d'une liste de résultats.
     *
     * <p>Une séance « supprimée » est en réalité désactivée ({@code active = false}) afin de
     * conserver l'historique. Les requêtes de listing ne filtrant pas ce drapeau, une séance
     * supprimée continuait d'apparaître dans le calendrier et les listes de séries.</p>
     *
     * <p>Une valeur nulle est considérée comme active : seules les séances explicitement
     * désactivées sont masquées (les enregistrements antérieurs à l'ajout du drapeau ne
     * doivent pas disparaître).</p>
     */
    private List<SessionEntity> onlyActive(List<SessionEntity> sessions) {
        return sessions.stream()
                .filter(session -> !Boolean.FALSE.equals(session.getActive()))
                .toList();
    }

    public List<SessionEntity> getAllSessions() {
        return onlyActive(sessionRepository.findAll());
    }

    public List<SessionEntity> getAllSessionsWithDetail() {
        return onlyActive(sessionRepository.findAllWithDetails());
    }

    public Optional<SessionEntity> getSessionById(Long id) {
        return sessionRepository.findById(Objects.requireNonNull(id));
    }

    /**
     * Crée une séance et la rattache à la série décidée par le serveur.
     *
     * <p>La série n'est plus celle que le client envoie : elle est résolue par
     * {@link SeriesRolloverService}, qui rattache à la série courante tant qu'elle n'est pas
     * pleine et crée la suivante dès qu'elle atteint le nombre de séances prévu par le type
     * de groupe.</p>
     *
     * <p>Ce rattachement n'était appliqué que sur le chemin des séances récurrentes. La
     * création à l'unité acceptait la série fournie par le client sans aucun contrôle de
     * capacité, ce qui permettait de dépasser le nombre de séances prévu — une série de 2 se
     * retrouvait avec 3 séances. Comme la facturation repose sur {@code totalSessions} et non
     * sur les séances réellement présentes, la séance excédentaire n'était jamais facturée.
     * Le serveur est désormais seul décideur, comme il l'est déjà du nom de série.</p>
     *
     * <p>Transactionnel : le rattachement effectue une seconde écriture ; sans transaction
     * englobante, un échec entre les deux laisserait une séance orpheline de série.</p>
     */
    @Transactional
    public SessionEntity createSession(SessionEntity session) {
        Objects.requireNonNull(session);
        // Refuse la création d'une séance rattachée à une année passée (Exigence 9.2).
        // Appelé avant de détacher la série, le garde pouvant la remonter pour résoudre l'année.
        readOnlyYearGuard.assertSessionMutable(session);

        GroupEntity group = session.getGroup();
        if (group == null) {
            // Sans groupe, aucune série n'est résoluble : on conserve l'enregistrement direct.
            return sessionRepository.save(session);
        }

        // La série éventuellement fournie par le client est écartée avant l'enregistrement :
        // la laisser en place la ferait compter par le rollover et fausserait sa décision.
        session.setSessionSeries(null);
        SessionEntity saved = sessionRepository.save(session);
        seriesRolloverService.attachSessionToSeries(group, saved);
        return saved;
    }

    /**
     * Met à jour une séance champ par champ.
     *
     * <p>Transactionnel : un changement de groupe entraîne une seconde écriture pour rebasculer
     * la séance dans une série du nouveau groupe.</p>
     */
    @Transactional
    public SessionEntity updateSession(Long sessionId, Map<String, Object> updates) {
        SessionEntity session = getSessionById(sessionId)
                .orElseThrow(() -> new EntityNotFoundException("Session not found with ID: " + sessionId));

        // Refuse la modification d'une séance rattachée à une année passée (Exigence 9.2).
        readOnlyYearGuard.assertSessionMutable(session);

        GroupEntity groupBefore = session.getGroup();

        updateEntityRelations(session, updates);
        updateSessionTimes(session, updates);
        updateSimpleFields(session, updates);

        SessionEntity saved = sessionRepository.save(Objects.requireNonNull(session));

        // Un changement de groupe laissait la séance rattachée à une série de l'ANCIEN groupe :
        // séance et série pointaient alors vers deux groupes différents, et la séance était
        // facturée au titre d'un groupe qui ne l'accueillait plus. On la rebascule donc dans une
        // série du nouveau groupe, en repassant par le service de bascule (qui respecte la
        // capacité) plutôt qu'en réaffectant à l'aveugle.
        if (groupChanged(groupBefore, saved.getGroup())) {
            saved.setSessionSeries(null);
            saved = sessionRepository.save(saved);
            seriesRolloverService.attachSessionToSeries(saved.getGroup(), saved);
        }
        return saved;
    }

    /** Vrai si le groupe de la séance a effectivement changé (et que le nouveau est connu). */
    private boolean groupChanged(GroupEntity before, GroupEntity after) {
        if (after == null || after.getId() == null) {
            return false;
        }
        return before == null || before.getId() == null || !before.getId().equals(after.getId());
    }

    /**
     * Applique les champs simples d'une modification de séance, clé par clé.
     *
     * <p>Cette étape passait auparavant par {@code PatchService} (ModelMapper). Le client
     * envoie l'objet séance complet, qui contient à la fois {@code groupName} et l'objet
     * imbriqué {@code group}. ModelMapper voyait alors deux sources possibles pour
     * {@code group.name}, levait une {@code ConfigurationException} et abandonnait la
     * totalité du patch : aucune modification n'était enregistrée (le type de séance en
     * particulier restait inchangé) et le contrôleur renvoyait une erreur 500.</p>
     *
     * <p>On liste donc explicitement les champs modifiables. Les clés inconnues
     * ({@code groupName}, {@code group}, {@code students}, identifiants, champs d'audit)
     * sont ignorées : elles sont dérivées ou gérées ailleurs.</p>
     */
    private void updateSimpleFields(SessionEntity session, Map<String, Object> updates) {
        applyIfPresent(updates, "title", value -> session.setTitle(asString(value)));
        applyIfPresent(updates, "sessionType", value -> session.setSessionType(asString(value)));
        applyIfPresent(updates, "description", value -> session.setDescription(asString(value)));
        applyIfPresent(updates, "isFinished", value -> session.setIsFinished(asBoolean(value)));
    }

    private void applyIfPresent(Map<String, Object> updates, String key, Consumer<Object> setter) {
        if (updates.containsKey(key)) {
            setter.accept(updates.get(key));
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private Boolean asBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.valueOf(value.toString());
    }

    private void updateEntityRelations(SessionEntity session, Map<String, Object> updates) {
        if (updates.containsKey(GROUPID)) {
            Long groupId = extractId(updates.get(GROUPID));
            GroupEntity group = groupRepository.findById(Objects.requireNonNull(groupId))
                    .orElseThrow(() -> new EntityNotFoundException("Group not found with ID: " + groupId));
            session.setGroup(group);
            updates.remove(GROUPID);
        }

        if (updates.containsKey(ROOMID)) {
            Long roomId = extractId(updates.get(ROOMID));
            RoomEntity room = roomRepository.findById(Objects.requireNonNull(roomId))
                    .orElseThrow(() -> new EntityNotFoundException("Room not found with ID: " + roomId));
            session.setRoom(room);
            updates.remove(ROOMID);
        }

        if (updates.containsKey(TEACHERID)) {
            Long teacherId = extractId(updates.get(TEACHERID));
            TeacherEntity teacher = teacherRepository.findById(Objects.requireNonNull(teacherId))
                    .orElseThrow(() -> new EntityNotFoundException("Teacher not found with ID: " + teacherId));
            session.setTeacher(teacher);
            updates.remove(TEACHERID);
        }
    }

    private void updateSessionTimes(SessionEntity session, Map<String, Object> updates) {
        updateSessionTime("sessionTimeStart", updates, session::setSessionTimeStart);
        updateSessionTime("sessionTimeEnd", updates, session::setSessionTimeEnd);
    }

    private void updateSessionTime(String key, Map<String, Object> updates, Consumer<Date> setter) {
        if (updates.containsKey(key)) {
            Object timeObject = updates.get(key);
            if (timeObject instanceof Date date) {
                setter.accept(date);
            } else if (timeObject instanceof String dateString) {
                try {
                    Date parsedDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX").parse(dateString);
                    setter.accept(parsedDate);
                } catch (ParseException e) {
                    throw new IllegalArgumentException("Invalid date format for " + key, e);
                }
            }
            updates.remove(key);
        }
    }

    private Long extractId(Object idObj) {
        if (idObj == null) {
            return null;
        }
        try {
            return Long.valueOf(idObj.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid ID value: " + idObj);
        }
    }

    public void deleteSession(Long id) {
        Objects.requireNonNull(id);
        sessionRepository.findById(id).ifPresent(session -> {
            // Refuse la suppression d'une séance rattachée à une année passée (Exigence 9.2).
            readOnlyYearGuard.assertSessionMutable(session);
            // Même règle que la désactivation : présences validées → suppression refusée.
            assertAttendanceNotValidated(session);
        });
        sessionRepository.deleteById(id);
    }

    /**
     * Refuse la suppression d'une séance dont les présences sont validées.
     *
     * <p>Une séance est considérée validée lorsqu'elle est marquée terminée
     * ({@code isFinished}) ou qu'elle porte déjà des fiches de présence actives. Ces
     * présences comptent dans le nombre de séances suivies, qui détermine le montant dû
     * par l'étudiant : les supprimer fausserait les soldes. La séance doit être dévalidée
     * au préalable.</p>
     *
     * @param session la séance visée
     * @throws CustomServiceException (409) si les présences sont validées
     */
    private void assertAttendanceNotValidated(SessionEntity session) {
        boolean finished = Boolean.TRUE.equals(session.getIsFinished());
        boolean hasActiveAttendance = !attendanceService
                .getAttendanceBySessionId(session.getId()).isEmpty();

        if (finished || hasActiveAttendance) {
            throw new CustomServiceException(
                    "Les présences de cette séance sont validées : dévalidez-la avant de la supprimer.",
                    HttpStatus.CONFLICT);
        }
    }

    public List<SessionEntity> findSessionsByCriteria(SessionSearchCriteriaDTO criteria) {
        Specification<SessionEntity> spec = Specification.where(null);

        spec = spec.and(CommonSpecifications.likeIfNotNull("title", criteria.getTitle()))
                .and(CommonSpecifications.equalsIfNotNull("sessionType", criteria.getSessionType()))
                .and(CommonSpecifications.greaterThanOrEqualToIfNotNull("sessionTimeStart", criteria.getStartDate()))
                .and(CommonSpecifications.lessThanOrEqualToIfNotNull("sessionTimeEnd", criteria.getEndDate()))
                .and(CommonSpecifications.equalsIfNotNull("teacher.id", criteria.getTeacherId()))
                .and(CommonSpecifications.equalsIfNotNull("group.id", criteria.getGroupId()))
                .and(CommonSpecifications.equalsIfNotNull("isFinished", criteria.getIsFinished()))
                .and(CommonSpecifications.equalsIfNotNull("room.id", criteria.getRoomId()));

        return onlyActive(sessionRepository.findAll(spec));
    }

    @Transactional
    public SessionEntity markSessionAsFinished(Long sessionId) {
        SessionEntity session = sessionRepository.findById(Objects.requireNonNull(sessionId))
                .orElseThrow(() -> new CustomServiceException(SESSION_NOT_FOUND_MESSAGE + sessionId));
        session.setIsFinished(true);
        return sessionRepository.save(session);
    }

    public SessionEntity markSessionAsUnfinished(Long sessionId) {
        SessionEntity session = sessionRepository.findById(Objects.requireNonNull(sessionId))
                .orElseThrow(() -> new CustomServiceException(SESSION_NOT_FOUND_MESSAGE + sessionId));
        session.setIsFinished(false);
        return sessionRepository.save(session);
    }

    public List<SessionEntity> getSessionsBySeriesId(Long sessionSeriesId) {
        return onlyActive(sessionRepository.findBySessionSeriesId(sessionSeriesId));
    }

    public List<SessionDTO> findSessionsInRange(LocalDateTime start, LocalDateTime end) {
        return onlyActive(sessionRepository.findBySessionTimeStartBetween(start, end)).stream()
                .map(sessionMapper::sessionEntityToSessionDto)
                .toList();
    }

    public List<SessionEntity> getSessionsByGroupIdAndDateRange(Long groupId, LocalDateTime start, LocalDateTime end) {
        return onlyActive(sessionRepository.findByGroupIdAndSessionTimeStartBetween(groupId, start, end));
    }

    public List<SessionDTO> findByGroupIdAndSessionTimeStartBetween(Long groupId, LocalDateTime start,
            LocalDateTime end) {
        return onlyActive(sessionRepository.findByGroupIdAndSessionTimeStartBetween(groupId, start, end))
                .stream()
                .map(sessionMapper::sessionEntityToSessionDto)
                .toList();
    }

    // ========== NOUVELLES MÉTHODES AJOUTÉES ==========

    /**
     * Désactive une session et TOUS ses éléments associés.
     *
     * IMPORTANT : Cette méthode désactive :
     * 1. La session elle-même (session.active = false)
     * 2. Toutes les attendances associées (attendance.active = false)
     * 3. Tous les PaymentDetails associés (paymentDetail.active = false) ← CRITIQUE
     * !
     *
     * POURQUOI C'EST IMPORTANT :
     * Si vous ne désactivez pas les PaymentDetails, ils continuent à être comptés
     * dans les calculs de paiement, ce qui cause l'erreur :
     * "Le montant payé dépasse le coût total"
     *
     * UTILISATION :
     * Appelez cette méthode au lieu de simplement mettre session.active = false
     *
     * @param sessionId l'ID de la session à désactiver
     */
    @Transactional
    public void deactivateSession(Long sessionId) {
        LOGGER.info("Deactivating session: {}", sessionId);

        // 1. Désactiver la session
        SessionEntity session = sessionRepository.findById(Objects.requireNonNull(sessionId))
                .orElseThrow(() -> new CustomServiceException(SESSION_NOT_FOUND_MESSAGE + sessionId));

        // Refuse la désactivation d'une séance rattachée à une année passée (Exigence 9.2).
        // Ce garde-fou existait déjà sur la création, la modification et la suppression :
        // sans lui, la désactivation offrait un contournement de la lecture seule.
        readOnlyYearGuard.assertSessionMutable(session);

        // Règle métier : une séance dont les présences sont validées ne peut pas être
        // supprimée. Les présences alimentent le nombre de séances suivies, donc le montant
        // dû par l'étudiant : les effacer fausserait les soldes. Il faut d'abord dévalider.
        assertAttendanceNotValidated(session);

        session.setActive(false);
        sessionRepository.save(session);
        LOGGER.debug("Session {} deactivated", sessionId);

        // 2. Désactiver les attendances
        attendanceService.deactivateBySessionId(sessionId);
        LOGGER.debug("Attendances deactivated for session {}", sessionId);

        // 3. Désactiver les PaymentDetails (LA PARTIE CRITIQUE !)
        int deactivatedPayments = paymentDetailDeactivationService
                .deactivatePaymentDetailsBySessionId(sessionId);

        LOGGER.info("Session {} fully deactivated ({} payment details deactivated)",
                sessionId, deactivatedPayments);
    }

    /**
     * Réactive une session et tous ses éléments associés.
     *
     * Utilisez cette méthode pour annuler une dévalidation de session.
     *
     * @param sessionId l'ID de la session à réactiver
     */
    @Transactional
    public void reactivateSession(Long sessionId) {
        LOGGER.info("Reactivating session: {}", sessionId);

        // 1. Réactiver la session
        SessionEntity session = sessionRepository.findById(Objects.requireNonNull(sessionId))
                .orElseThrow(() -> new CustomServiceException(SESSION_NOT_FOUND_MESSAGE + sessionId));

        session.setActive(true);
        sessionRepository.save(session);
        LOGGER.debug("Session {} reactivated", sessionId);

        // 2. Réactiver les PaymentDetails
        int reactivatedPayments = paymentDetailDeactivationService
                .reactivatePaymentDetailsBySessionId(sessionId);

        LOGGER.info("Session {} fully reactivated ({} payment details reactivated)",
                sessionId, reactivatedPayments);
    }
}