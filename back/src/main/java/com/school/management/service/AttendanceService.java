package com.school.management.service;

import com.school.management.dto.AttendanceDTO;
import com.school.management.mapper.AttendanceMapper;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.CatchUpBillingState;
import com.school.management.repository.*;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.session.AbsenceWindowGuard;
import com.school.management.shared.mapper.MappingContext;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class AttendanceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AttendanceService.class);

    private final AttendanceRepository attendanceRepository;
    private final AttendanceMapper attendanceMapper;

    // PHASE 1 REFACTORING: Repositories pour MappingContext
    private final StudentRepository studentRepository;
    private final SessionRepository sessionRepository;
    private final SessionSeriesRepository sessionSeriesRepository;
    private final GroupRepository groupRepository;
    private final StudentGroupRepository studentGroupRepository;

    /**
     * Décide si une présence hors groupe est un vrai rattrapage (place réservée ailleurs) ou une
     * séance à facturer sur place. Le test porte sur le niveau et la matière, jamais sur le type
     * de groupe, qui désigne l'effectif.
     */
    private final CatchUpRoutingService catchUpRoutingService;

    /** Aucune absence hors Fenêtre_Inscription, sur chaque écriture (exigences 7.3 à 7.5). */
    private final AbsenceWindowGuard absenceWindowGuard;

    /** Aucune validation sur une année close (exigence 10.2). */
    private final ReadOnlyYearGuard readOnlyYearGuard;

    // MappingContext pour AttendanceMapper
    private MappingContext mappingContext;

    @Autowired
    public AttendanceService(AttendanceRepository attendanceRepository, AttendanceMapper attendanceMapper,
            StudentRepository studentRepository, SessionRepository sessionRepository,
            SessionSeriesRepository sessionSeriesRepository, GroupRepository groupRepository,
            StudentGroupRepository studentGroupRepository,
            CatchUpRoutingService catchUpRoutingService,
            AbsenceWindowGuard absenceWindowGuard,
            ReadOnlyYearGuard readOnlyYearGuard) {
        this.readOnlyYearGuard = readOnlyYearGuard;
        this.attendanceRepository = attendanceRepository;
        this.attendanceMapper = attendanceMapper;
        this.studentRepository = studentRepository;
        this.sessionRepository = sessionRepository;
        this.sessionSeriesRepository = sessionSeriesRepository;
        this.groupRepository = groupRepository;
        this.studentGroupRepository = studentGroupRepository;
        this.catchUpRoutingService = catchUpRoutingService;
        this.absenceWindowGuard = absenceWindowGuard;
    }

    /**
     * PHASE 1 REFACTORING: Initialise le MappingContext après injection des
     * dépendances
     */
    @PostConstruct
    private void initMappingContext() {
        this.mappingContext = MappingContext.of(
                null, // LevelRepository
                null, // TutorRepository
                null, // GroupTypeRepository
                null, // SubjectRepository
                null, // PricingRepository
                null, // TeacherRepository
                null, // SchoolYearRepository
                null, // RoomRepository
                groupRepository,
                sessionSeriesRepository,
                studentRepository,
                sessionRepository);
        LOGGER.debug("MappingContext initialized for AttendanceService");
    }

    /**
     * Retourne le MappingContext pour utilisation par les controllers
     */
    public MappingContext getMappingContext() {
        return mappingContext;
    }

    public List<AttendanceDTO> getAllAttendances() {
        List<AttendanceEntity> attendances = attendanceRepository.findAll();
        return attendances.stream()
                .map(attendanceMapper::attendanceToAttendanceDTO)
                .toList();
    }

    public AttendanceEntity getAttendanceById(Long id) {
        return attendanceRepository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new RuntimeException("Attendance not found")); // Customize this exception
    }

    public AttendanceEntity createAttendance(AttendanceEntity attendance) {
        assertYearsOpen(List.of(Objects.requireNonNull(attendance)));
        absenceWindowGuard.assertSubmittedAbsencesConcerned(List.of(attendance));
        return attendanceRepository.save(attendance);
    }

    /**
     * Une séance d'une année close ne se valide plus (exigence 10.2) : sa feuille de présence, comme
     * une présence isolée, est refusée avant toute écriture.
     *
     * <p>Une ligne sans séance est refusée ici (400) : son année ne se résout pas, et elle finissait
     * plus loin en erreur serveur.</p>
     */
    private void assertYearsOpen(List<AttendanceEntity> attendances) {
        attendances.stream()
                .map(AttendanceEntity::getSession)
                .distinct()
                .forEach(session -> {
                    if (session == null) {
                        throw new CustomServiceException("Présence sans séance : indiquez la séance concernée.",
                                HttpStatus.BAD_REQUEST);
                    }
                    readOnlyYearGuard.assertSessionMutable(session);
                });
    }

    // updateAttendance(Long) retiré : c'était un talon qui rechargeait la présence puis la
    // ré-enregistrait sans rien modifier. Il donnait l'illusion d'une mise à jour à tout appelant.
    // La modification de la justification passe par AttendanceJustificationService, dont le
    // périmètre est explicite et la trace obligatoire.

    // deleteAttendance(Long) et deleteBySessionId(Long) retirés (D.3) : suppressions définitives de
    // présences, sans Motif ni Trace (exigence 8.4). Une ligne se retire par désactivation, par
    // AttendanceCorrectionService.

    // save(AttendanceEntity) retiré : sans appelant, c'était une écriture de présence qui
    // contournait tous les contrôles, la fenêtre d'inscription comprise.

    /**
     * Feuille de présence d'une séance.
     *
     * <p>Refusée en bloc si une absence vise un étudiant que la séance ne concerne pas (7.5) : la
     * vérification porte sur toutes les lignes avant toute écriture, et chaque ligne refusée est
     * nommée, pour être retirée en une fois.</p>
     */
    public List<AttendanceEntity> saveAll(List<AttendanceEntity> attendances) {
        assertYearsOpen(attendances);
        absenceWindowGuard.assertSubmittedAbsencesConcerned(attendances);
        for (AttendanceEntity attendance : attendances) {
            if (attendanceRepository.existsByStudentIdAndSessionIdAndActiveTrue(attendance.getStudent().getId(),
                    attendance.getSession().getId())) {
                throw new IllegalArgumentException("Attendance already exists for student ID "
                        + attendance.getStudent().getId() + " and session ID " + attendance.getSession().getId());
            }
            // L'ordre compte : on retire d'abord le drapeau posé à tort sur un membre du groupe,
            // puis on classe ce qui reste un rattrapage. Router avant normaliserait un membre.
            normalizeCatchUpFlag(attendance);
            routeCatchUpBilling(attendance);
        }
        return attendanceRepository.saveAll(Objects.requireNonNull(attendances));
    }

    /**
     * Classe une présence de rattrapage : vrai rattrapage à préciser, ou séance facturée sur place.
     *
     * <p>C'est ici que se refermait le défaut d'origine. L'écran de validation marquait rattrapage
     * tout étudiant non membre du groupe, sans jamais demander quelle séance était rattrapée ; la
     * présence était donc enregistrée sans séance manquée, le qualificateur retombait sur « aucune
     * autre série ne facture cette séance » et le groupe d'accueil facturait — même lorsque
     * l'étudiant avait déjà payé la séance dans son propre groupe.</p>
     *
     * <p>Le classement est fait <strong>par le serveur</strong>, seul à connaître les inscriptions
     * de l'étudiant. Deux issues, aucune ambiguïté :</p>
     * <ul>
     *   <li>une place lui est réservée dans un groupe de même niveau et même matière → la présence
     *       est {@code PENDING} : elle ne facture rien tant que la séance manquée et la décision
     *       « déjà payée » ne sont pas renseignées, et n'empêche pas la validation de la séance
     *       pour les autres étudiants ;</li>
     *   <li>aucun groupe de même niveau et même matière → {@code HOST_BILLED} : la séance lui est
     *       facturée sur place, comme à un membre. Aucune séance manquée n'est attendue, et son
     *       absence est ici voulue.</li>
     * </ul>
     *
     * <p>Une présence déjà classée par le flux rattrapage dédié n'est pas reclassée : ce flux a posé
     * la séance manquée et la décision, cet écran n'a rien à en redire.</p>
     */
    private void routeCatchUpBilling(AttendanceEntity attendance) {
        if (!Boolean.TRUE.equals(attendance.getIsCatchUp())
                || attendance.getCatchUpBillingState() != null) {
            return;
        }

        Long studentId = attendance.getStudent() == null ? null : attendance.getStudent().getId();
        Long groupId = resolveHostGroupId(attendance);
        if (studentId == null || groupId == null) {
            // Sans étudiant ni groupe d'accueil, aucun test n'est possible. La présence reste sans
            // état : elle sera visible comme telle, plutôt que classée sur une hypothèse.
            LOGGER.warn("Présence de rattrapage sans étudiant ou sans groupe : classement impossible.");
            return;
        }

        CatchUpRoutingService.RoutingVerdict verdict = catchUpRoutingService.route(studentId, groupId);
        if (verdict == CatchUpRoutingService.RoutingVerdict.TRUE_CATCH_UP) {
            attendance.setCatchUpBillingState(CatchUpBillingState.PENDING);
            LOGGER.info("Rattrapage à préciser : étudiant {}, groupe d'accueil {} — la séance manquée "
                    + "et la décision « déjà payée » restent à renseigner.", studentId, groupId);
        } else {
            attendance.setCatchUpBillingState(CatchUpBillingState.HOST_BILLED);
            LOGGER.info("Séance facturée sur place : étudiant {}, groupe d'accueil {} — aucun groupe "
                    + "de même niveau et même matière, donc aucune séance manquée à rattraper.",
                    studentId, groupId);
        }
    }

    /** Groupe où la séance se déroule : celui de la présence, sinon celui de la séance. */
    private Long resolveHostGroupId(AttendanceEntity attendance) {
        if (attendance.getGroup() != null && attendance.getGroup().getId() != null) {
            return attendance.getGroup().getId();
        }
        if (attendance.getSession() != null && attendance.getSession().getGroup() != null) {
            return attendance.getSession().getGroup().getId();
        }
        return null;
    }

    /**
     * Un rattrapage est une séance suivie dans un groupe dont l'étudiant n'est pas membre.
     * Si l'étudiant est inscrit (affectation active) au groupe de la séance, la présence ne
     * peut donc pas être un rattrapage, quoi qu'annonce le client.
     *
     * <p>Sans cette normalisation, un étudiant ajouté à la main sur une feuille de présence
     * de son propre groupe (cas d'une inscription postérieure au début de la séance) était
     * enregistré comme rattrapage. Toute la série basculait alors en mode rattrapage dans le
     * calcul de paiement, avec un coût total et un seuil de retard différents.</p>
     *
     * <p>On ne fait que retirer le drapeau à tort : promouvoir une présence en rattrapage
     * reste du ressort du flux rattrapage dédié.</p>
     */
    private void normalizeCatchUpFlag(AttendanceEntity attendance) {
        if (!Boolean.TRUE.equals(attendance.getIsCatchUp())
                || attendance.getGroup() == null
                || attendance.getStudent() == null) {
            return;
        }

        Long groupId = attendance.getGroup().getId();
        Long studentId = attendance.getStudent().getId();
        if (groupId == null || studentId == null) {
            return;
        }

        if (studentGroupRepository.existsByGroupIdAndStudentIdAndActiveTrue(groupId, studentId)) {
            LOGGER.info("Présence marquée rattrapage alors que l'étudiant {} est inscrit au groupe {} : "
                    + "drapeau isCatchUp remis à false.", studentId, groupId);
            attendance.setIsCatchUp(false);
        }
    }

    /**
     * Une séance déplacée (jour ou groupe) ne doit laisser aucune de ses absences actives hors
     * Fenêtre_Inscription. Appelé par la modification de séance, la séance déjà modifiée en mémoire.
     */
    public void assertAbsencesStayConcerned(Long sessionId) {
        absenceWindowGuard.assertMovedSessionAbsencesConcerned(
                attendanceRepository.findBySessionIdAndActiveTrue(sessionId));
    }

    public void deactivateBySessionId(Long sessionId) {
        List<AttendanceEntity> attendances = attendanceRepository.findBySessionId(sessionId);
        for (AttendanceEntity attendance : attendances) {
            attendance.setActive(false);
        }
        attendanceRepository.saveAll(Objects.requireNonNull(attendances));
    }

    public List<AttendanceDTO> getAttendanceBySessionId(Long sessionId) {
        List<AttendanceEntity> activeAttendances = attendanceRepository.findBySessionIdAndActiveTrue(sessionId);
        return activeAttendances.stream()
                .map(attendanceMapper::attendanceToAttendanceDTO)
                .toList();
    }

    public List<AttendanceDTO> getAttendanceByStudentAndSeries(Long studentId, Long sessionSeriesId) {
        List<AttendanceEntity> attendanceEntities = attendanceRepository
                .findByStudentIdAndSessionSeriesIdAndActiveTrue(studentId, sessionSeriesId);
        return attendanceEntities.stream()
                .map(attendanceMapper::attendanceToAttendanceDTO)
                .toList();
    }

    // Additional methods as needed...
}
