package com.school.management.service;

import com.school.management.dto.StudentAbsenceDTO;
import com.school.management.dto.catchup.CatchUpBillingAuditDTO;
import com.school.management.dto.catchup.PendingCatchUpDTO;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.CatchUpBillingAuditEntity;
import com.school.management.persistance.CatchUpBillingAuditField;
import com.school.management.persistance.CatchUpBillingState;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.CatchUpBillingAuditRepository;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.service.exception.CustomServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.AuditorAware;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Résolution et correction de la facturation d'un rattrapage à préciser.
 *
 * <h2>Deux décisions, aucune valeur par défaut</h2>
 * Un rattrapage {@code PENDING} exige <strong>deux</strong> décisions pour être résolu : quelle
 * séance manquée il rattrape, et si cette séance était <strong>déjà payée</strong> dans sa série
 * d'origine. Aucune des deux n'a de défaut, et c'est délibéré : un défaut juste dans la plupart des
 * cas n'est jamais relu, et le jour où il est faux l'erreur passe inaperçue — c'est-à-dire
 * exactement le scénario de double facturation que cette fonctionnalité existe pour empêcher.
 *
 * <h2>Ce service ne calcule aucun montant</h2>
 * Il enregistre des décisions. Les montants restent résolus <strong>par série</strong> par
 * {@code BillableSessionsResolver}, qui lit la décision stockée. Dupliquer ici le moindre calcul
 * ferait réapparaître la divergence que la facturation au prorata a coûté cher à supprimer.
 *
 * <h2>La qualification par dates n'est pas court-circuitée</h2>
 * {@code CatchUpBillingQualifier} continue de comparer les dates ; la décision stockée s'y
 * superpose, elle ne la remplace pas. Une décision prise à un instant donné est une
 * <strong>donnée</strong>, comme une date : elle n'introduit aucune récursion entre séries et rend
 * le résultat indépendant de l'ordre d'évaluation, ce qu'une relecture de l'état de paiement au
 * moment du calcul ne permettrait pas.
 */
@Service
public class CatchUpBillingResolutionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatchUpBillingResolutionService.class);

    /** Auteur retenu lorsque aucun utilisateur n'est authentifié (tâche planifiée, amorçage). */
    private static final String SYSTEM_AUTHOR = "system";

    private final AttendanceRepository attendanceRepository;
    private final SessionRepository sessionRepository;
    private final GroupRepository groupRepository;
    private final CatchUpBillingAuditRepository auditRepository;
    private final AuditorAware<String> auditorAware;

    /**
     * Réutilisé pour la liste des absences éligibles, plutôt que d'en recopier les règles ici : le
     * droit au rattrapage révoqué et les demandes déjà en cours y sont déjà pris en compte.
     */
    private final CatchUpService catchUpService;

    public CatchUpBillingResolutionService(AttendanceRepository attendanceRepository,
                                           SessionRepository sessionRepository,
                                           GroupRepository groupRepository,
                                           CatchUpBillingAuditRepository auditRepository,
                                           AuditorAware<String> auditorAware,
                                           CatchUpService catchUpService) {
        this.attendanceRepository = attendanceRepository;
        this.sessionRepository = sessionRepository;
        this.groupRepository = groupRepository;
        this.auditRepository = auditRepository;
        this.auditorAware = auditorAware;
        this.catchUpService = catchUpService;
    }

    /**
     * Rattrapages restant à préciser, dans l'ordre chronologique des séances.
     *
     * <p>Chaque ligne est une séance consommée que personne ne facture encore : la liste est faite
     * pour être vidée.</p>
     */
    @Transactional(readOnly = true)
    public List<AttendanceEntity> findPending() {
        return attendanceRepository.findPendingCatchUps();
    }

    /**
     * Même liste, aplatie pour l'affichage.
     *
     * <p>L'aplatissement a lieu <strong>dans la transaction</strong> : les relations sont chargées
     * en {@code LAZY}, et les résoudre côté contrôleur lèverait une exception de session fermée.
     * Même parti que {@code CatchUpService.getAllRequests}.</p>
     */
    @Transactional(readOnly = true)
    public List<PendingCatchUpDTO> findPendingForDisplay() {
        return findPending().stream().map(this::toPendingDto).toList();
    }

    /**
     * Vue aplatie d'un rattrapage, quel que soit son état.
     *
     * <p>Renvoyée après une résolution ou une correction : l'appelant voit l'objet tel qu'il est
     * désormais enregistré, plutôt que de devoir relire la liste pour vérifier son propre effet.</p>
     *
     * @throws CustomServiceException 404 si la présence est introuvable
     */
    @Transactional(readOnly = true)
    public PendingCatchUpDTO pendingViewOf(Long attendanceId) {
        return toPendingDto(loadAttendance(attendanceId));
    }

    /** Piste d'audit d'un rattrapage, aplatie pour l'affichage. */
    @Transactional(readOnly = true)
    public List<CatchUpBillingAuditDTO> auditTrailForDisplay(Long attendanceId) {
        return auditTrail(attendanceId).stream()
                .map(entry -> new CatchUpBillingAuditDTO(
                        entry.getId(),
                        // `field` est NOT NULL en base et toujours posé par trace() : un garde
                        // null ici serait une branche inatteignable, donc non testable.
                        entry.getField().name(),
                        entry.getOldValue(),
                        entry.getNewValue(),
                        entry.getPerformedBy(),
                        entry.getPerformedAt(),
                        entry.getSequenceRank(),
                        entry.getComment()))
                .toList();
    }

    private PendingCatchUpDTO toPendingDto(AttendanceEntity attendance) {
        SessionEntity session = attendance.getSession();
        GroupEntity hostGroup = attendance.getGroup() != null
                ? attendance.getGroup()
                : (session != null ? session.getGroup() : null);

        return new PendingCatchUpDTO(
                attendance.getId(),
                attendance.getStudent() == null ? null : attendance.getStudent().getId(),
                fullName(attendance),
                session == null ? null : session.getId(),
                session == null ? null : session.getTitle(),
                session == null ? null : session.getSessionTimeStart(),
                hostGroup == null ? null : hostGroup.getId(),
                hostGroup == null ? null : hostGroup.getName(),
                // Niveau et matière extraits par un helper : écrits en ligne, ils cumulaient deux
                // conditions dont la combinaison « groupe présent mais niveau nul » n'est atteinte
                // par aucun jeu de données réaliste, laissant une branche non testable.
                levelName(hostGroup),
                subjectName(hostGroup),
                attendance.getSessionSeries() == null ? null : attendance.getSessionSeries().getId(),
                attendance.getSessionSeries() == null ? null : attendance.getSessionSeries().getName());
    }

    /**
     * Niveau du groupe d'accueil, nul si le groupe est absent ou n'a pas de niveau.
     *
     * <p>Les deux cas sont réels et distincts : une présence peut ne porter aucun groupe (donnée
     * abîmée), et un groupe peut ne pas avoir de niveau (référentiel incomplet). L'affichage doit
     * survivre aux deux, sans quoi une seule ligne défectueuse ferait échouer toute la liste.</p>
     */
    private String levelName(GroupEntity hostGroup) {
        if (hostGroup == null) {
            return null;
        }
        return hostGroup.getLevel() == null ? null : hostGroup.getLevel().getName();
    }

    /** Matière du groupe d'accueil, nulle si le groupe est absent ou n'a pas de matière. */
    private String subjectName(GroupEntity hostGroup) {
        if (hostGroup == null) {
            return null;
        }
        return hostGroup.getSubject() == null ? null : hostGroup.getSubject().getName();
    }

    /** Nom complet de l'étudiant, nul si la présence n'en porte pas. */
    private String fullName(AttendanceEntity attendance) {
        if (attendance.getStudent() == null) {
            return null;
        }
        String first = attendance.getStudent().getFirstName();
        String last = attendance.getStudent().getLastName();
        String name = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
        return name.isEmpty() ? null : name;
    }

    /**
     * Séances manquées proposables pour résoudre un rattrapage : absences éligibles de l'étudiant
     * dans les groupes de <strong>même niveau et même matière</strong> que le groupe d'accueil, dans
     * la même année scolaire.
     *
     * <p>Le sélecteur applique exactement le test qui a décidé du routage. Proposer une absence d'un
     * autre niveau ou d'une autre matière laisserait désigner une séance que le modèle n'autorise pas
     * à rattraper : cette présence aurait été classée « facturée sur place », et lui rattacher une
     * séance manquée produirait un rattrapage que rien ne justifie.</p>
     *
     * <p>Le groupe d'accueil est exclu : on ne rattrape pas une séance dans son propre groupe.</p>
     *
     * @param studentId   identifiant de l'étudiant
     * @param hostGroupId groupe de la séance suivie, exclu des résultats
     * @return les absences proposables, dans l'ordre fourni par {@code CatchUpService}
     * @throws CustomServiceException 404 si le groupe d'accueil est introuvable
     */
    @Transactional(readOnly = true)
    public List<StudentAbsenceDTO> eligibleMissedSessions(Long studentId, Long hostGroupId) {
        GroupEntity hostGroup = groupRepository.findById(Objects.requireNonNull(hostGroupId))
                .orElseThrow(() -> new CustomServiceException(
                        "Groupe introuvable pour l'identifiant : " + hostGroupId,
                        HttpStatus.NOT_FOUND));

        return catchUpService.getEligibleAbsences(studentId).stream()
                .filter(absence -> matchesHostGroup(absence.groupId(), hostGroup))
                .toList();
    }

    /**
     * Le groupe de l'absence partage-t-il niveau, matière et année scolaire avec le groupe
     * d'accueil, sans être ce groupe lui-même ?
     *
     * <p>Un groupe introuvable, ou dont l'une des trois caractéristiques est absente, ne peut fonder
     * aucune correspondance : l'absence est écartée du sélecteur plutôt que proposée sur une
     * hypothèse.</p>
     */
    private boolean matchesHostGroup(Long absenceGroupId, GroupEntity hostGroup) {
        // Une absence sans groupe ne peut correspondre à rien, et le groupe d'accueil ne fonde pas
        // son propre rattrapage. Les deux cas sont écrits séparément : réunis par un `||`, la
        // combinaison laissait une branche qu'aucun jeu de données ne pouvait atteindre.
        if (absenceGroupId == null) {
            return false;
        }
        if (absenceGroupId.equals(hostGroup.getId())) {
            return false;
        }
        return groupRepository.findById(absenceGroupId)
                .map(candidate -> sameId(hostGroup.getLevel() == null ? null : hostGroup.getLevel().getId(),
                                candidate.getLevel() == null ? null : candidate.getLevel().getId())
                        && sameId(hostGroup.getSubject() == null ? null : hostGroup.getSubject().getId(),
                                candidate.getSubject() == null ? null : candidate.getSubject().getId())
                        && sameId(hostGroup.getSchoolYear() == null ? null : hostGroup.getSchoolYear().getId(),
                                candidate.getSchoolYear() == null ? null : candidate.getSchoolYear().getId()))
                .orElse(false);
    }

    /** Deux identifiants égaux et tous deux renseignés. Un nul ne correspond à rien. */
    private boolean sameId(Long a, Long b) {
        return a != null && a.equals(b);
    }

    /**
     * Résout un rattrapage à préciser : désigne la séance manquée et tranche « déjà payée ».
     *
     * @param attendanceId    présence de rattrapage à résoudre
     * @param missedSessionId séance manquée rattrapée, obligatoire
     * @param alreadyPaid     la séance manquée était-elle déjà payée dans sa série d'origine ?
     *                        obligatoire — {@code null} est refusé, faute de quoi l'absence de
     *                        décision serait enregistrée comme une décision
     * @param comment         commentaire libre, conservé dans la trace
     * @return la présence résolue
     * @throws CustomServiceException 404 si la présence ou la séance manquée est introuvable ;
     *                                400 si la décision est absente, si la présence n'est pas un
     *                                rattrapage à préciser, ou si la séance manquée n'a pas de
     *                                série ; 409 si cette séance manquée est déjà rattrapée
     */
    @Transactional
    public AttendanceEntity resolve(Long attendanceId, Long missedSessionId, Boolean alreadyPaid,
                                    String comment) {
        Objects.requireNonNull(attendanceId, "L'identifiant de la présence ne doit pas être nul.");

        AttendanceEntity attendance = loadAttendance(attendanceId);

        // Un rattrapage facturé sur place n'a aucune séance manquée à désigner, et un rattrapage
        // déjà résolu se corrige par correct() : le distinguer évite qu'une résolution écrase une
        // décision antérieure sans laisser de trace.
        if (attendance.getCatchUpBillingState() != CatchUpBillingState.PENDING) {
            throw new CustomServiceException(String.format(
                    "La présence %d n'est pas un rattrapage à préciser (état : %s) : "
                            + "utilisez la correction pour modifier une décision déjà prise.",
                    attendanceId, attendance.getCatchUpBillingState()),
                    HttpStatus.BAD_REQUEST);
        }

        Boolean decision = requireDecision(alreadyPaid);
        SessionEntity missedSession = loadMissedSession(missedSessionId);
        requireNoOtherCatchUp(attendance, missedSession);

        String previousMissed = idAsText(attendance.getMissedSession());

        attendance.setMissedSession(missedSession);
        attendance.setMissedSessionAlreadyPaid(decision);
        attendance.setCatchUpBillingState(CatchUpBillingState.RESOLVED);
        AttendanceEntity saved = attendanceRepository.save(attendance);

        // La résolution est tracée comme une correction : elle décide où va l'argent, et cette
        // décision doit être attribuable même si la présence disparaît ensuite.
        trace(saved.getId(), CatchUpBillingAuditField.MISSED_SESSION,
                previousMissed, String.valueOf(missedSession.getId()), comment);
        trace(saved.getId(), CatchUpBillingAuditField.ALREADY_PAID,
                null, String.valueOf(decision), comment);

        LOGGER.info("Rattrapage {} résolu : séance manquée {}, déjà payée = {}.",
                attendanceId, missedSession.getId(), decision);

        return saved;
    }

    /**
     * Corrige la séance manquée et/ou la décision « déjà payée » d'un rattrapage déjà résolu.
     *
     * <p>Chaque changement <strong>effectif</strong> écrit une entrée de trace ; réenregistrer une
     * valeur identique n'en écrit aucune, sans quoi le journal se remplirait de lignes sans
     * information et la dernière décision réelle deviendrait difficile à retrouver.</p>
     *
     * @param attendanceId    présence de rattrapage à corriger
     * @param missedSessionId nouvelle séance manquée, ou {@code null} pour ne pas la changer
     * @param alreadyPaid     nouvelle décision, ou {@code null} pour ne pas la changer
     * @param comment         motif de la correction, conservé dans la trace
     * @return la présence corrigée
     * @throws CustomServiceException 404 si la présence ou la séance est introuvable ; 400 si la
     *                                présence n'est pas un rattrapage résolu, ou si aucun
     *                                changement n'est demandé ; 409 si la séance manquée visée est
     *                                déjà rattrapée par une autre présence
     */
    @Transactional
    public AttendanceEntity correct(Long attendanceId, Long missedSessionId, Boolean alreadyPaid,
                                    String comment) {
        Objects.requireNonNull(attendanceId, "L'identifiant de la présence ne doit pas être nul.");

        if (missedSessionId == null && alreadyPaid == null) {
            throw new CustomServiceException(
                    "Aucune correction demandée : indiquez une nouvelle séance manquée, "
                            + "une nouvelle décision de facturation, ou les deux.",
                    HttpStatus.BAD_REQUEST);
        }

        AttendanceEntity attendance = loadAttendance(attendanceId);

        if (attendance.getCatchUpBillingState() != CatchUpBillingState.RESOLVED) {
            throw new CustomServiceException(String.format(
                    "La présence %d n'est pas un rattrapage résolu (état : %s) : "
                            + "il n'y a aucune décision à corriger.",
                    attendanceId, attendance.getCatchUpBillingState()),
                    HttpStatus.BAD_REQUEST);
        }

        if (missedSessionId != null) {
            SessionEntity newMissed = loadMissedSession(missedSessionId);
            requireNoOtherCatchUp(attendance, newMissed);

            String before = idAsText(attendance.getMissedSession());
            String after = String.valueOf(newMissed.getId());
            if (!Objects.equals(before, after)) {
                attendance.setMissedSession(newMissed);
                trace(attendanceId, CatchUpBillingAuditField.MISSED_SESSION, before, after, comment);
            }
        }

        if (alreadyPaid != null) {
            String before = booleanAsText(attendance.getMissedSessionAlreadyPaid());
            String after = String.valueOf(alreadyPaid);
            if (!Objects.equals(before, after)) {
                attendance.setMissedSessionAlreadyPaid(alreadyPaid);
                trace(attendanceId, CatchUpBillingAuditField.ALREADY_PAID, before, after, comment);
            }
        }

        LOGGER.info("Rattrapage {} corrigé par {}.", attendanceId, currentAuthor());
        return attendanceRepository.save(attendance);
    }

    /** Piste d'audit d'un rattrapage, de la correction la plus récente à la plus ancienne. */
    @Transactional(readOnly = true)
    public List<CatchUpBillingAuditEntity> auditTrail(Long attendanceId) {
        return auditRepository.findByAttendanceIdOrderByPerformedAtDescSequenceRankDesc(attendanceId);
    }

    // ------------------------------------------------------------------
    // Invariants partagés
    // ------------------------------------------------------------------

    private AttendanceEntity loadAttendance(Long attendanceId) {
        return attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new CustomServiceException(
                        "Présence introuvable pour l'identifiant : " + attendanceId,
                        HttpStatus.NOT_FOUND));
    }

    /**
     * La décision doit être prise explicitement.
     *
     * <p>C'est le seul garde qui traduit « aucune valeur par défaut » en comportement : sans lui, un
     * client omettant le champ enregistrerait « non déjà payée », donc une facturation, sans que
     * personne ne l'ait choisi.</p>
     */
    private Boolean requireDecision(Boolean alreadyPaid) {
        if (alreadyPaid == null) {
            throw new CustomServiceException(
                    "La décision de facturation est obligatoire : indiquez si la séance manquée "
                            + "était déjà payée. Aucune valeur par défaut n'est appliquée, "
                            + "car elle déciderait à votre place de qui facture cette séance.",
                    HttpStatus.BAD_REQUEST);
        }
        return alreadyPaid;
    }

    /**
     * Charge la séance manquée et exige qu'elle appartienne à une série.
     *
     * <p>Même exigence que {@code CatchUpService.complete} : la facturation est indexée par série,
     * une séance manquée hors série serait invisible pour le calcul. La règle vit ici et là parce
     * que les deux chemins créent la même situation, et une seule des deux vérifications laisserait
     * un passage ouvert.</p>
     */
    private SessionEntity loadMissedSession(Long missedSessionId) {
        if (missedSessionId == null) {
            throw new CustomServiceException(
                    "La séance manquée est obligatoire pour résoudre un rattrapage : "
                            + "c'est elle qui porte la facturation, dans son groupe d'origine.",
                    HttpStatus.BAD_REQUEST);
        }
        SessionEntity missedSession = sessionRepository.findById(missedSessionId)
                .orElseThrow(() -> new CustomServiceException(
                        "Séance introuvable pour l'identifiant : " + missedSessionId,
                        HttpStatus.NOT_FOUND));
        if (missedSession.getSessionSeries() == null) {
            throw new CustomServiceException(String.format(
                    "La séance manquée %d n'est rattachée à aucune série : elle ne peut porter "
                            + "aucune facturation. Rattacher cette séance à une série, puis réessayer.",
                    missedSessionId),
                    HttpStatus.BAD_REQUEST);
        }
        return missedSession;
    }

    /**
     * Une séance manquée n'est rattrapée qu'une fois.
     *
     * <p>Deux rattrapages actifs de la même séance rendraient indéterminé lequel compense l'absence,
     * donc le décompte des séances suivies — et le seuil de retard qui en dépend. La présence en
     * cours est exclue du contrôle : se redésigner soi-même ne crée aucun doublon.</p>
     */
    private void requireNoOtherCatchUp(AttendanceEntity attendance, SessionEntity missedSession) {
        Long studentId = attendance.getStudent() == null ? null : attendance.getStudent().getId();
        if (studentId == null) {
            throw new CustomServiceException(String.format(
                    "La présence %d n'est rattachée à aucun étudiant : résolution impossible.",
                    attendance.getId()),
                    HttpStatus.BAD_REQUEST);
        }
        boolean alreadyCaughtUp = attendanceRepository.existsOtherCatchUpForMissedSession(
                studentId, missedSession.getId(), attendance.getId());
        if (alreadyCaughtUp) {
            throw new CustomServiceException(String.format(
                    "Un rattrapage est déjà enregistré pour la séance manquée %d : "
                            + "un seul rattrapage par séance manquée est possible.",
                    missedSession.getId()),
                    HttpStatus.CONFLICT);
        }
    }

    // ------------------------------------------------------------------
    // Trace
    // ------------------------------------------------------------------

    private void trace(Long attendanceId, CatchUpBillingAuditField field,
                       String oldValue, String newValue, String comment) {
        auditRepository.save(CatchUpBillingAuditEntity.builder()
                .attendanceId(attendanceId)
                .field(field)
                .oldValue(oldValue)
                .newValue(newValue)
                .performedBy(currentAuthor())
                .performedAt(LocalDateTime.now())
                .sequenceRank(auditRepository.nextSequenceRank(attendanceId))
                .comment(comment)
                .build());
    }

    /** Utilisateur courant, {@code system} en l'absence d'authentification. */
    private String currentAuthor() {
        return auditorAware.getCurrentAuditor().orElse(SYSTEM_AUTHOR);
    }

    /**
     * Identifiant de séance rendu en texte pour la trace, {@code null} lorsqu'aucune séance n'était
     * désignée — ce qui est le cas d'une première résolution.
     *
     * <p>Une séance persistée porte toujours un identifiant : le tester en plus produirait une
     * branche qu'aucun test ne peut atteindre, et que le seuil de couverture rejette à juste
     * titre.</p>
     */
    private String idAsText(SessionEntity session) {
        return session == null ? null : String.valueOf(session.getId());
    }

    /** Décision rendue en texte, {@code null} lorsqu'elle n'a jamais été tranchée. */
    private String booleanAsText(Boolean value) {
        return value == null ? null : String.valueOf(value);
    }
}
