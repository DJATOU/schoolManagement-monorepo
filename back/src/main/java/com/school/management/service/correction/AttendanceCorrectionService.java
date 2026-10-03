package com.school.management.service.correction;

import com.school.management.domain.valueobject.EnrolmentWindow;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.CatchUpBillingState;
import com.school.management.persistance.CatchUpRequestEntity;
import com.school.management.persistance.CatchUpStatus;
import com.school.management.persistance.CorrectionAction;
import com.school.management.persistance.CorrectionDomain;
import com.school.management.persistance.CorrectionReasonType;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.CatchUpRequestRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.repository.StudentGroupRepository;
import com.school.management.repository.StudentRepository;
import com.school.management.service.ReadOnlyYearGuard;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.session.AbsenceWindowGuard;
import com.school.management.service.session.RollCallService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Corriger la présence d'un étudiant sur une Séance validée, sans toucher aux autres (spec
 * admin-corrections, exigence 8).
 *
 * <p>Trois corrections, chacune avec un Motif et après Aperçu ({@link CorrectionRunner}) :</p>
 * <ul>
 *   <li><strong>présent ↔ absent</strong> : une absence peut être justifiée dans la même action
 *       (8.2) ; une présence efface la justification, et la Trace dit ce qui est effacé (8.3) ;</li>
 *   <li><strong>ajouter</strong> la présence ou l'absence d'un étudiant inscrit au groupe, sans
 *       ligne sur la feuille ;</li>
 *   <li><strong>retirer</strong> une ligne, par désactivation : elle reste en base (8.4).</li>
 * </ul>
 *
 * <p>Refus : une absence sur une Séance_Non_Concernée (8.5, par {@link AbsenceWindowGuard}) ; une
 * année close (8.6) ; une séance non validée, dont la feuille se modifie directement. Si la séance
 * est rattrapée ou en voie de l'être, elle ne peut devenir suivie ni perdre sa ligne : le rattrapage
 * compenserait une séance qui n'est plus manquée. Il se retire d'abord.</p>
 *
 * <p>Une présence de rattrapage ne passe jamais à absent ; elle se retire, et ce retrait rouvre le
 * droit de rattraper la séance manquée qu'elle compensait (exigence 9, D9).</p>
 *
 * <p>Ce qui en découle pour l'argent est tiré par {@link SeriesSettlement}, comme pour une
 * correction de dates : une présence retirée hors de la période rend sa séance non facturable, et
 * sa ventilation passe sur une autre séance de la série.</p>
 */
@Service
public class AttendanceCorrectionService {

    /** Motifs d'une correction de présence, le plus probable en tête : l'ordre est celui de l'écran. */
    public static final List<CorrectionReasonType> REASONS = List.of(
            CorrectionReasonType.DATA_ENTRY_ERROR,
            CorrectionReasonType.DOCUMENT_RECEIVED,
            CorrectionReasonType.OTHER);

    /** Demandes de rattrapage en cours : la séance manquée attend d'être rattrapée. */
    private static final Set<CatchUpStatus> OPEN_REQUESTS = EnumSet.of(CatchUpStatus.PENDING, CatchUpStatus.SCHEDULED);

    private final CorrectionRunner runner;
    private final AttendanceRepository attendanceRepository;
    private final SessionRepository sessionRepository;
    private final StudentRepository studentRepository;
    private final StudentGroupRepository studentGroupRepository;
    private final SessionSeriesRepository seriesRepository;
    private final CatchUpRequestRepository catchUpRequestRepository;
    private final AbsenceWindowGuard absenceWindowGuard;
    private final ReadOnlyYearGuard readOnlyYearGuard;
    private final SeriesSettlement settlement;

    public AttendanceCorrectionService(CorrectionRunner runner,
                                       AttendanceRepository attendanceRepository,
                                       SessionRepository sessionRepository,
                                       StudentRepository studentRepository,
                                       StudentGroupRepository studentGroupRepository,
                                       SessionSeriesRepository seriesRepository,
                                       CatchUpRequestRepository catchUpRequestRepository,
                                       AbsenceWindowGuard absenceWindowGuard,
                                       ReadOnlyYearGuard readOnlyYearGuard,
                                       SeriesSettlement settlement) {
        this.runner = runner;
        this.attendanceRepository = attendanceRepository;
        this.sessionRepository = sessionRepository;
        this.studentRepository = studentRepository;
        this.studentGroupRepository = studentGroupRepository;
        this.seriesRepository = seriesRepository;
        this.catchUpRequestRepository = catchUpRequestRepository;
        this.absenceWindowGuard = absenceWindowGuard;
        this.readOnlyYearGuard = readOnlyYearGuard;
        this.settlement = settlement;
    }

    /**
     * Passe une présence à absent, ou une absence à présent (exigences 8.1 à 8.3).
     *
     * @param present   l'état voulu
     * @param justified pour une absence, justifiée ou non ; une présence ne se justifie pas
     */
    public CorrectionOutcome<AttendanceCorrection> changePresence(Long attendanceId, Boolean present, Boolean justified,
                                                                  CorrectionReason reason, CorrectionMode mode,
                                                                  String previewToken) {
        requireReason(reason);
        boolean target = requirePresence(present, justified);
        return runner.run(new ChangeCommand(attendanceId, target, justifiedAfter(target, justified), reason),
                mode, previewToken);
    }

    /** Ajoute la présence ou l'absence manquante d'un étudiant inscrit au groupe (exigence 8.1). */
    public CorrectionOutcome<AttendanceCorrection> add(Long sessionId, Long studentId, Boolean present,
                                                       Boolean justified, CorrectionReason reason,
                                                       CorrectionMode mode, String previewToken) {
        requireReason(reason);
        boolean target = requirePresence(present, justified);
        if (studentId == null) {
            throw new CustomServiceException("Étudiant à préciser.", HttpStatus.BAD_REQUEST);
        }
        return runner.run(new AddCommand(Objects.requireNonNull(sessionId, "sessionId"), studentId, target,
                justifiedAfter(target, justified), reason), mode, previewToken);
    }

    /** Retire une présence ou une absence, par désactivation (exigences 8.1, 8.4). */
    public CorrectionOutcome<AttendanceCorrection> remove(Long attendanceId, CorrectionReason reason,
                                                          CorrectionMode mode, String previewToken) {
        requireReason(reason);
        return runner.run(new RemoveCommand(attendanceId, reason), mode, previewToken);
    }

    private static void requireReason(CorrectionReason reason) {
        Objects.requireNonNull(reason, "reason");
        if (!REASONS.contains(reason.type())) {
            throw new CustomServiceException("Motif « " + reason.type() + " » sans rapport avec une correction de "
                    + "présence.", HttpStatus.BAD_REQUEST);
        }
    }

    /** L'état voulu est dit, et une présence n'est jamais justifiée. */
    private static boolean requirePresence(Boolean present, Boolean justified) {
        if (present == null) {
            throw new CustomServiceException("Présent ou absent : à préciser.", HttpStatus.BAD_REQUEST);
        }
        if (present && Boolean.TRUE.equals(justified)) {
            throw new CustomServiceException("Une présence ne se justifie pas : seule une absence peut l'être.",
                    HttpStatus.BAD_REQUEST);
        }
        return present;
    }

    private static boolean justifiedAfter(boolean present, Boolean justified) {
        return !present && Boolean.TRUE.equals(justified);
    }

    // ------------------------------------------------------------------
    // Commandes
    // ------------------------------------------------------------------

    /** Ce que les trois corrections partagent : la portée, et la Trace. */
    private abstract class AttendanceCommand implements CorrectionCommand<AttendanceCorrection> {

        protected final CorrectionReason reason;

        AttendanceCommand(CorrectionReason reason) {
            this.reason = reason;
        }

        /** L'étudiant et la séance corrigés, relus dans la transaction de la correction. */
        abstract StudentEntity student();

        abstract SessionEntity session();

        /** Toutes les séries du groupe de la séance, vues par l'étudiant : le coût de chacune peut changer. */
        @Override
        public CorrectionScope scope() {
            return CorrectionScope.empty().group(student().getId(), groupOf(session()).getId());
        }

        SeriesSettlement.Before capture(StudentEntity student, GroupEntity group) {
            return settlement.capture(student.getId(), seriesRepository.findByGroupId(group.getId()));
        }

        AuditDraft trace(CorrectionAction action, AttendanceEntity mark, StudentEntity student, GroupEntity group,
                         SessionEntity session, Map<String, ?> oldValue, Map<String, ?> newValue, String summary) {
            return AuditDraft.builder()
                    .domain(CorrectionDomain.ATTENDANCE)
                    .action(action)
                    .entityId(mark.getId())
                    .studentId(student.getId())
                    .groupId(group.getId())
                    .sessionId(session.getId())
                    .seriesId(session.getSessionSeries() == null ? null : session.getSessionSeries().getId())
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .summary(summary)
                    .reason(reason)
                    .build();
        }
    }

    /** Présent ↔ absent. */
    private final class ChangeCommand extends AttendanceCommand {

        private final Long attendanceId;
        private final boolean present;
        private final boolean justified;

        private ChangeCommand(Long attendanceId, boolean present, boolean justified, CorrectionReason reason) {
            super(reason);
            this.attendanceId = Objects.requireNonNull(attendanceId, "attendanceId");
            this.present = present;
            this.justified = justified;
        }

        @Override
        public String fingerprint() {
            return EncashmentCorrectionService.canonical("ATTENDANCE_CHANGE", attendanceId, present, justified,
                    reason.type(), reason.text());
        }

        @Override
        StudentEntity student() {
            return correctable(attendanceId).getStudent();
        }

        @Override
        SessionEntity session() {
            return correctable(attendanceId).getSession();
        }

        @Override
        public CorrectionExecution<AttendanceCorrection> execute() {
            AttendanceEntity mark = correctable(attendanceId);
            StudentEntity student = mark.getStudent();
            SessionEntity session = mark.getSession();
            GroupEntity group = groupOf(session);
            boolean wasPresent = Boolean.TRUE.equals(mark.getIsPresent());
            boolean wasJustified = Boolean.TRUE.equals(mark.getIsJustified());
            if (present == wasPresent) {
                throw new CustomServiceException(fullName(student) + " est déjà noté " + (present ? "présent" : "absent")
                        + " le " + day(session) + (present ? " : rien à corriger."
                                : " : sa justification se modifie par « Justifier »."), HttpStatus.BAD_REQUEST);
            }
            if (present) {
                assertNotCaughtUp(student, session);
            }
            SeriesSettlement.Before before = capture(student, group);

            mark.setIsPresent(present);
            mark.setIsJustified(justified);
            if (!present) {
                // Une absence hors de la période de l'étudiant est refusée, ligne nommée (8.5).
                absenceWindowGuard.assertSubmittedAbsencesConcerned(List.of(mark));
            }
            attendanceRepository.saveAndFlush(mark);

            String change = where(session, group) + " : " + fullName(student) + " " + state(wasPresent, wasJustified)
                    + " → " + state(present, justified);
            List<CorrectionEffect> effects = new ArrayList<>();
            effects.add(new CorrectionEffect(CorrectionEffectType.ATTENDANCE_CHANGED, change));
            List<AuditDraft> audits = List.of(trace(CorrectionAction.PRESENCE_CHANGED, mark, student, group, session,
                    values(wasPresent, wasJustified, true), values(present, justified, true), change));
            Set<SeriesKey> touched = settlement.settle(before, effects);
            return new CorrectionExecution<>(new AttendanceCorrection(mark.getId(), student.getId(), session.getId(),
                    present, justified, true), effects, touched, audits);
        }
    }

    /** Présence ou absence manquante. */
    private final class AddCommand extends AttendanceCommand {

        private final Long sessionId;
        private final Long studentId;
        private final boolean present;
        private final boolean justified;

        private AddCommand(Long sessionId, Long studentId, boolean present, boolean justified, CorrectionReason reason) {
            super(reason);
            this.sessionId = sessionId;
            this.studentId = studentId;
            this.present = present;
            this.justified = justified;
        }

        @Override
        public String fingerprint() {
            return EncashmentCorrectionService.canonical("ATTENDANCE_ADD", sessionId, studentId, present, justified,
                    reason.type(), reason.text());
        }

        @Override
        StudentEntity student() {
            return studentRepository.findById(studentId)
                    .orElseThrow(() -> new CustomServiceException("Étudiant introuvable : " + studentId,
                            HttpStatus.NOT_FOUND));
        }

        @Override
        SessionEntity session() {
            return sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new CustomServiceException("Séance introuvable : " + sessionId,
                            HttpStatus.NOT_FOUND));
        }

        @Override
        public CorrectionExecution<AttendanceCorrection> execute() {
            SessionEntity session = session();
            StudentEntity student = student();
            GroupEntity group = groupOf(session);
            assertCorrectable(session);
            if (studentGroupRepository.findByGroupIdAndStudentId(group.getId(), student.getId()).isEmpty()) {
                throw new CustomServiceException(fullName(student) + " n'est pas inscrit au groupe « " + group.getName()
                        + " » : un rattrapage s'enregistre par une demande de rattrapage.", HttpStatus.CONFLICT);
            }
            if (attendanceRepository.existsByStudentIdAndSessionIdAndActiveTrue(student.getId(), session.getId())) {
                throw new CustomServiceException(fullName(student) + " a déjà une ligne le " + day(session)
                        + " : corrigez-la plutôt que d'en ajouter une.", HttpStatus.CONFLICT);
            }
            if (present) {
                assertNotCaughtUp(student, session);
            }
            SeriesSettlement.Before before = capture(student, group);

            AttendanceEntity mark = AttendanceEntity.builder()
                    .student(student)
                    .session(session)
                    .sessionSeries(session.getSessionSeries())
                    .group(group)
                    .isPresent(present)
                    .isJustified(justified)
                    .isCatchUp(false)
                    .build();
            if (!present) {
                absenceWindowGuard.assertSubmittedAbsencesConcerned(List.of(mark));
            }
            mark = attendanceRepository.saveAndFlush(mark);

            String added = where(session, group) + " : " + line(present, justified) + " de " + fullName(student)
                    + " ajoutée";
            List<CorrectionEffect> effects = new ArrayList<>();
            effects.add(new CorrectionEffect(CorrectionEffectType.ATTENDANCE_RECORDED, added));
            List<AuditDraft> audits = List.of(trace(CorrectionAction.ATTENDANCE_ADDED, mark, student, group, session,
                    null, values(present, justified, true), added));
            Set<SeriesKey> touched = settlement.settle(before, effects);
            return new CorrectionExecution<>(new AttendanceCorrection(mark.getId(), student.getId(), session.getId(),
                    present, justified, true), effects, touched, audits);
        }
    }

    /** Ligne retirée, par désactivation. */
    private final class RemoveCommand extends AttendanceCommand {

        private final Long attendanceId;

        private RemoveCommand(Long attendanceId, CorrectionReason reason) {
            super(reason);
            this.attendanceId = Objects.requireNonNull(attendanceId, "attendanceId");
        }

        @Override
        public String fingerprint() {
            return EncashmentCorrectionService.canonical("ATTENDANCE_REMOVE", attendanceId, reason.type(),
                    reason.text());
        }

        @Override
        StudentEntity student() {
            return active(attendanceId).getStudent();
        }

        @Override
        SessionEntity session() {
            return active(attendanceId).getSession();
        }

        /**
         * Une présence de rattrapage touche deux groupes : celui qui l'accueille, et celui de la séance
         * manquée qu'elle compensait, où cette séance comptait comme suivie.
         */
        @Override
        public CorrectionScope scope() {
            AttendanceEntity mark = active(attendanceId);
            if (!Boolean.TRUE.equals(mark.getIsCatchUp())) {
                return super.scope();
            }
            CorrectionScope scope = CorrectionScope.empty().group(mark.getStudent().getId(),
                    groupOf(mark.getSession()).getId());
            return mark.getMissedSession() == null ? scope
                    : scope.group(mark.getStudent().getId(), groupOf(mark.getMissedSession()).getId());
        }

        @Override
        public CorrectionExecution<AttendanceCorrection> execute() {
            AttendanceEntity mark = active(attendanceId);
            if (Boolean.TRUE.equals(mark.getIsCatchUp())) {
                return removeCatchUp(mark);
            }
            mark = correctable(attendanceId);
            StudentEntity student = mark.getStudent();
            SessionEntity session = mark.getSession();
            GroupEntity group = groupOf(session);
            boolean present = Boolean.TRUE.equals(mark.getIsPresent());
            boolean justified = Boolean.TRUE.equals(mark.getIsJustified());
            assertNotCaughtUp(student, session);
            SeriesSettlement.Before before = capture(student, group);

            mark.setActive(false);
            attendanceRepository.saveAndFlush(mark);

            String removed = where(session, group) + " : " + line(present, justified) + " de " + fullName(student)
                    + " retirée";
            List<CorrectionEffect> effects = new ArrayList<>();
            effects.add(new CorrectionEffect(present ? CorrectionEffectType.PRESENCE_REMOVED
                    : CorrectionEffectType.ABSENCE_REMOVED, removed));
            List<AuditDraft> audits = List.of(trace(CorrectionAction.ATTENDANCE_REMOVED, mark, student, group, session,
                    values(present, justified, true), values(present, justified, false), removed));
            Set<SeriesKey> touched = settlement.settle(before, effects);
            return new CorrectionExecution<>(new AttendanceCorrection(mark.getId(), student.getId(), session.getId(),
                    present, justified, false), effects, touched, audits);
        }

        /**
         * Retire une présence de rattrapage saisie à tort (exigences 9.1 à 9.3, D9).
         *
         * <p>Deux verrous empêchaient de rattraper de nouveau la séance manquée : la présence de
         * rattrapage active qui la désigne, et la demande de rattrapage non annulée qui porte son
         * absence. Les deux sautent ensemble : la présence est désactivée, la demande qui l'a produite
         * passe à {@code CANCELLED}. Le droit au rattrapage de l'absence, lui, n'a jamais été touché.</p>
         *
         * <p>La séance n'a pas à être validée : un rattrapage enregistré par sa demande existe avant
         * la validation, et la feuille de présence ne sait pas le retirer.</p>
         */
        private CorrectionExecution<AttendanceCorrection> removeCatchUp(AttendanceEntity mark) {
            StudentEntity student = mark.getStudent();
            SessionEntity host = mark.getSession();
            GroupEntity hostGroup = groupOf(host);
            SessionEntity missed = mark.getMissedSession();
            GroupEntity originGroup = missed == null ? null : groupOf(missed);
            assertOpen(host);
            if (missed != null) {
                // La séance manquée cesse de compter comme suivie : son année doit être ouverte aussi.
                readOnlyYearGuard.assertSessionMutable(missed);
            }
            List<CatchUpRequestEntity> requests = catchUpRequestRepository.findByStudentId(student.getId()).stream()
                    .filter(request -> request.getStatus() != CatchUpStatus.CANCELLED)
                    .filter(request -> isSession(request.getCatchUpSession(), host))
                    .filter(request -> missed == null || isSession(request.getOriginalSession(), missed))
                    .sorted(Comparator.comparing(CatchUpRequestEntity::getId))
                    .toList();

            // Seule la série d'accueil peut perdre une séance facturable, donc sa ventilation. À
            // l'origine, la séance manquée reste facturable (sa place était réservée) : seul le dû à
            // ce jour baisse, et l'Aperçu le montre par la portée.
            SeriesSettlement.Before before = settlement.capture(student.getId(),
                    seriesRepository.findByGroupId(hostGroup.getId()));

            Map<String, Object> oldValue = catchUpValues(mark, true, requests);
            mark.setActive(false);
            attendanceRepository.saveAndFlush(mark);
            for (CatchUpRequestEntity request : requests) {
                request.setStatus(CatchUpStatus.CANCELLED);
                request.setCancellationReason("Présence de rattrapage retirée par correction (" + reason.type()
                        + (reason.text() == null ? "" : " : " + reason.text()) + ")");
                catchUpRequestRepository.save(request);
            }
            Map<String, Object> newValue = catchUpValues(mark, false, requests);

            String removed = "Rattrapage de " + fullName(student) + " du " + day(host) + " (" + hostGroup.getName()
                    + ") retiré : " + (missed == null ? withoutMissedSession(mark)
                            : "séance manquée du " + day(missed) + " (" + originGroup.getName() + "), déjà payée : "
                                    + alreadyPaid(mark.getMissedSessionAlreadyPaid()));
            List<CorrectionEffect> effects = new ArrayList<>();
            effects.add(new CorrectionEffect(CorrectionEffectType.CATCH_UP_REMOVED, removed));
            if (missed != null) {
                effects.add(new CorrectionEffect(CorrectionEffectType.CATCH_UP_REOPENED, where(missed, originGroup)
                        + " : de nouveau à rattraper pour " + fullName(student)));
            }
            for (int i = 0; i < requests.size(); i++) {
                effects.add(new CorrectionEffect(CorrectionEffectType.CATCH_UP_REQUEST_CANCELLED,
                        "Demande de rattrapage sur la séance du " + day(host) + " annulée"));
            }
            List<AuditDraft> audits = List.of(trace(CorrectionAction.CATCH_UP_REMOVED, mark, student, hostGroup, host,
                    oldValue, newValue, removed));
            Set<SeriesKey> touched = settlement.settle(before, effects);
            return new CorrectionExecution<>(new AttendanceCorrection(mark.getId(), student.getId(), host.getId(),
                    true, false, false), effects, touched, audits);
        }
    }

    /**
     * Ce que portait la présence de rattrapage, conservé par la Trace (9.3) : la séance manquée et la
     * décision « déjà payée », l'état de facturation, et les demandes qui l'avaient produite.
     */
    private static Map<String, Object> catchUpValues(AttendanceEntity mark, boolean active,
                                                     List<CatchUpRequestEntity> requests) {
        SessionEntity missed = mark.getMissedSession();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("present", true);
        values.put("catchUp", true);
        values.put("active", active);
        values.put("billingState", mark.getCatchUpBillingState() == null ? null : mark.getCatchUpBillingState().name());
        values.put("missedSessionId", missed == null ? null : missed.getId());
        values.put("missedSessionDay", missed == null ? null : day(missed));
        values.put("missedSessionAlreadyPaid", mark.getMissedSessionAlreadyPaid());
        values.put("catchUpRequests", requests.stream().map(AttendanceCorrectionService::requestValues).toList());
        return values;
    }

    /** Une demande de rattrapage dans une Trace : identifiant, puis statut, dans cet ordre. */
    private static Map<String, Object> requestValues(CatchUpRequestEntity request) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", request.getId());
        values.put("status", request.getStatus().name());
        return values;
    }

    /** Un rattrapage sans séance manquée : facturé sur place, ou encore à préciser. */
    private static String withoutMissedSession(AttendanceEntity mark) {
        return mark.getCatchUpBillingState() == CatchUpBillingState.HOST_BILLED
                ? "facturé sur place, sans séance manquée"
                : "séance manquée non renseignée";
    }

    private static String alreadyPaid(Boolean decision) {
        return decision == null ? "non tranché" : decision ? "oui" : "non";
    }

    // ------------------------------------------------------------------
    // Contrôles
    // ------------------------------------------------------------------

    /** Une ligne active et complète : un étudiant, une séance. */
    private AttendanceEntity active(Long attendanceId) {
        AttendanceEntity mark = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new CustomServiceException("Présence introuvable : " + attendanceId,
                        HttpStatus.NOT_FOUND));
        if (!Boolean.TRUE.equals(mark.getActive())) {
            throw new CustomServiceException("Cette ligne a déjà été retirée : rien à corriger.", HttpStatus.CONFLICT);
        }
        if (mark.getStudent() == null || mark.getSession() == null) {
            throw new CustomServiceException("Ligne de présence sans étudiant ou sans séance : elle ne peut pas être "
                    + "corrigée ici.", HttpStatus.CONFLICT);
        }
        return mark;
    }

    /** Une ligne active, ordinaire, complète, sur une séance validée d'une année ouverte. */
    private AttendanceEntity correctable(Long attendanceId) {
        AttendanceEntity mark = active(attendanceId);
        if (Boolean.TRUE.equals(mark.getIsCatchUp())) {
            // Un rattrapage absent n'a pas de sens : l'élève venu d'ailleurs n'était attendu nulle part ici.
            throw new CustomServiceException("Présence de rattrapage du " + day(mark.getSession()) + " : elle ne passe "
                    + "pas à absent. Si l'élève n'est pas venu, retirez-la : la séance manquée redevient à rattraper.",
                    HttpStatus.CONFLICT);
        }
        assertCorrectable(mark.getSession());
        return mark;
    }

    /** Année ouverte (8.6), séance non supprimée. */
    private void assertOpen(SessionEntity session) {
        readOnlyYearGuard.assertSessionMutable(session);
        if (Boolean.FALSE.equals(session.getActive())) {
            throw new CustomServiceException("La séance du " + day(session) + " a été supprimée : ses présences ne se "
                    + "corrigent plus.", HttpStatus.CONFLICT);
        }
    }

    /** Année ouverte, séance validée et non supprimée. */
    private void assertCorrectable(SessionEntity session) {
        assertOpen(session);
        if (!Boolean.TRUE.equals(session.getIsFinished())) {
            throw new CustomServiceException("La séance du " + day(session) + " n'est pas validée : sa feuille de "
                    + "présence se modifie directement, sans correction.", HttpStatus.CONFLICT);
        }
    }

    /**
     * La séance n'est ni rattrapée ni en voie de l'être : sinon elle ne peut devenir suivie ni perdre
     * sa ligne, le rattrapage compenserait une séance qui n'est plus manquée.
     */
    private void assertNotCaughtUp(StudentEntity student, SessionEntity session) {
        for (AttendanceEntity catchUp : attendanceRepository.findByStudentIdAndIsCatchUpTrueAndActiveTrue(student.getId())) {
            if (isSession(catchUp.getMissedSession(), session)) {
                throw new CustomServiceException("La séance du " + day(session) + " a été rattrapée par "
                        + fullName(student) + " le " + day(catchUp.getSession()) + " : retirez d'abord ce rattrapage.",
                        HttpStatus.CONFLICT);
            }
        }
        for (CatchUpRequestEntity request : catchUpRequestRepository.findByStudentId(student.getId())) {
            if (OPEN_REQUESTS.contains(request.getStatus()) && isSession(request.getOriginalSession(), session)) {
                throw new CustomServiceException("Une demande de rattrapage de la séance du " + day(session)
                        + " est en cours pour " + fullName(student) + " : annulez-la d'abord.", HttpStatus.CONFLICT);
            }
        }
    }

    private static boolean isSession(SessionEntity candidate, SessionEntity session) {
        return candidate != null && Objects.equals(candidate.getId(), session.getId());
    }

    /** Groupe de la séance : celui où s'apprécie l'inscription de l'étudiant. */
    private static GroupEntity groupOf(SessionEntity session) {
        GroupEntity group = RollCallService.groupOf(session);
        if (group == null) {
            throw new CustomServiceException("La séance du " + day(session) + " n'appartient à aucun groupe : aucune "
                    + "inscription ne peut la concerner.", HttpStatus.CONFLICT);
        }
        return group;
    }

    // ------------------------------------------------------------------
    // Rédaction
    // ------------------------------------------------------------------

    /** « Séance du 14/01/2030 (Math 1ère A) ». */
    private static String where(SessionEntity session, GroupEntity group) {
        return "Séance du " + day(session) + " (" + group.getName() + ")";
    }

    private static String state(boolean present, boolean justified) {
        return present ? "présent" : justified ? "absent (justifié)" : "absent";
    }

    private static String line(boolean present, boolean justified) {
        return present ? "présence" : justified ? "absence justifiée" : "absence";
    }

    private static Map<String, Object> values(boolean present, boolean justified, boolean active) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("present", present);
        values.put("justified", justified);
        values.put("active", active);
        return values;
    }

    private static String day(SessionEntity session) {
        java.time.LocalDate day = EnrolmentWindow.dayOf(session.getSessionTimeStart());
        return day == null ? "(non datée)" : EnrolmentWindow.format(day);
    }

    private static String fullName(StudentEntity student) {
        return Stream.of(student.getFirstName(), student.getLastName())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
    }
}
