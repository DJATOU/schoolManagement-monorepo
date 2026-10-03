package com.school.management.service.correction;

import com.school.management.domain.valueobject.EnrolmentWindow;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.CorrectionAction;
import com.school.management.persistance.CorrectionDomain;
import com.school.management.persistance.CorrectionReasonType;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.PaymentRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.repository.StudentGroupRepository;
import com.school.management.service.EnrolmentDates;
import com.school.management.service.ReadOnlyYearGuard;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.payment.BillableSessionsResolver;
import com.school.management.service.payment.EncashmentService;
import com.school.management.service.payment.PaymentCostResolver;
import com.school.management.service.session.RollCallService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Corriger les dates d'une inscription : arrivée, départ, réouverture (spec admin-corrections,
 * exigences 5.5 à 5.9, 6.1, 6.3 à 6.5, D5, D6).
 *
 * <p>Les trois corrections changent la même chose — la Fenêtre_Inscription — et en tirent les mêmes
 * conséquences, par un seul code :</p>
 * <ul>
 *   <li><strong>Séances qui sortent de la période</strong> : leurs absences sont retirées, aucune
 *       absence ne subsistant hors fenêtre (propriété P5) ; leurs présences ordinaires restent,
 *       facturées comme séances consommées, et l'Aperçu le dit (5.6) — sauf, pour un départ, si
 *       l'administratrice demande de les retirer (6.3). Une présence de rattrapage n'est pas
 *       touchée (6.4).</li>
 *   <li><strong>Séances validées qui entrent dans la période sans présence</strong> : listées,
 *       facturables, place réservée ; l'administratrice peut y noter l'étudiant présent ou absent
 *       dans la même opération (5.7).</li>
 *   <li><strong>Ventilation</strong> : celle d'une séance devenue non facturable passe sur les autres
 *       séances facturables de sa série, sans changer de série ni d'Encaissement (5.9, D6) ; un
 *       trop-perçu apparu est annoncé, ni reporté ni remboursé.</li>
 * </ul>
 * <p>Chaque correction passe par le {@link CorrectionRunner} : Aperçu exécuté puis annulé,
 * confirmation refusée si la liste a changé depuis (5.8).</p>
 */
@Service
public class EnrolmentCorrectionService {

    /** Corriger la date d'arrivée, le motif le plus probable en tête : l'ordre est celui de l'écran. */
    public static final List<CorrectionReasonType> ARRIVAL_REASONS = List.of(
            CorrectionReasonType.ARRIVAL_DATE_CORRECTED,
            CorrectionReasonType.DATA_ENTRY_ERROR,
            CorrectionReasonType.OTHER);

    /** Enregistrer ou corriger un départ. */
    public static final List<CorrectionReasonType> DEPARTURE_REASONS = List.of(
            CorrectionReasonType.STUDENT_LEFT,
            CorrectionReasonType.DATA_ENTRY_ERROR,
            CorrectionReasonType.OTHER);

    /** Rouvrir une inscription close : un départ saisi à tort. */
    public static final List<CorrectionReasonType> REOPEN_REASONS = List.of(
            CorrectionReasonType.DATA_ENTRY_ERROR,
            CorrectionReasonType.OTHER);

    /** Nature de la correction. */
    enum Operation { ARRIVAL, DEPARTURE, REOPEN }

    private final CorrectionRunner runner;
    private final StudentGroupRepository studentGroupRepository;
    private final SessionSeriesRepository seriesRepository;
    private final SessionRepository sessionRepository;
    private final AttendanceRepository attendanceRepository;
    private final PaymentRepository paymentRepository;
    private final BillableSessionsResolver billableSessionsResolver;
    private final PaymentCostResolver costResolver;
    private final EncashmentService encashmentService;
    private final VentilationMover ventilationMover;
    private final ReadOnlyYearGuard readOnlyYearGuard;

    public EnrolmentCorrectionService(CorrectionRunner runner,
                                      StudentGroupRepository studentGroupRepository,
                                      SessionSeriesRepository seriesRepository,
                                      SessionRepository sessionRepository,
                                      AttendanceRepository attendanceRepository,
                                      PaymentRepository paymentRepository,
                                      BillableSessionsResolver billableSessionsResolver,
                                      PaymentCostResolver costResolver,
                                      EncashmentService encashmentService,
                                      VentilationMover ventilationMover,
                                      ReadOnlyYearGuard readOnlyYearGuard) {
        this.runner = runner;
        this.studentGroupRepository = studentGroupRepository;
        this.seriesRepository = seriesRepository;
        this.sessionRepository = sessionRepository;
        this.attendanceRepository = attendanceRepository;
        this.paymentRepository = paymentRepository;
        this.billableSessionsResolver = billableSessionsResolver;
        this.costResolver = costResolver;
        this.encashmentService = encashmentService;
        this.ventilationMover = ventilationMover;
        this.readOnlyYearGuard = readOnlyYearGuard;
    }

    /**
     * Corrige la date d'arrivée (exigences 5.5 à 5.9).
     *
     * @param attendances présence ({@code true}) ou absence ({@code false}) à noter sur des séances
     *                    validées que la nouvelle arrivée fait entrer dans la période, sans présence
     */
    public CorrectionOutcome<EnrolmentCorrection> correctArrival(Long enrolmentId, LocalDate arrival,
                                                                 Map<Long, Boolean> attendances,
                                                                 CorrectionReason reason, CorrectionMode mode,
                                                                 String previewToken) {
        requireReason(reason, ARRIVAL_REASONS, "corriger une date d'arrivée");
        if (arrival == null) {
            throw new CustomServiceException("Date d'arrivée obligatoire.", HttpStatus.BAD_REQUEST);
        }
        return runner.run(new WindowCommand(Operation.ARRIVAL, enrolmentId, arrival, null, false,
                attendances, reason), mode, previewToken);
    }

    /**
     * Enregistre le départ d'une inscription ouverte, ou corrige celui d'une inscription close
     * (exigences 6.1, 6.3, 6.5).
     *
     * @param removePresencesAfter retirer aussi les présences ordinaires postérieures au départ ;
     *                             sinon elles restent, facturées comme séances consommées
     */
    public CorrectionOutcome<EnrolmentCorrection> setDeparture(Long enrolmentId, LocalDate departure,
                                                               boolean removePresencesAfter,
                                                               Map<Long, Boolean> attendances,
                                                               CorrectionReason reason, CorrectionMode mode,
                                                               String previewToken) {
        requireReason(reason, DEPARTURE_REASONS, "enregistrer ou corriger un départ");
        if (departure == null) {
            throw new CustomServiceException("Date de départ obligatoire.", HttpStatus.BAD_REQUEST);
        }
        return runner.run(new WindowCommand(Operation.DEPARTURE, enrolmentId, null, departure,
                removePresencesAfter, attendances, reason), mode, previewToken);
    }

    /** Rouvre une inscription close : son départ avait été saisi à tort (exigence 6.5). */
    public CorrectionOutcome<EnrolmentCorrection> reopen(Long enrolmentId, Map<Long, Boolean> attendances,
                                                         CorrectionReason reason, CorrectionMode mode,
                                                         String previewToken) {
        requireReason(reason, REOPEN_REASONS, "rouvrir une inscription");
        return runner.run(new WindowCommand(Operation.REOPEN, enrolmentId, null, null, false,
                attendances, reason), mode, previewToken);
    }

    private static void requireReason(CorrectionReason reason, List<CorrectionReasonType> allowed, String what) {
        Objects.requireNonNull(reason, "reason");
        if (!allowed.contains(reason.type())) {
            throw new CustomServiceException("Motif « " + reason.type() + " » sans rapport avec la correction : "
                    + what + ".", HttpStatus.BAD_REQUEST);
        }
    }

    // ------------------------------------------------------------------
    // Commande
    // ------------------------------------------------------------------

    /** Une correction de la Fenêtre_Inscription. */
    private final class WindowCommand implements CorrectionCommand<EnrolmentCorrection> {

        private final Operation operation;
        private final Long enrolmentId;
        private final LocalDate arrival;
        private final LocalDate departure;
        private final boolean removePresencesOutside;
        private final SortedMap<Long, Boolean> decisions;
        private final CorrectionReason reason;

        private WindowCommand(Operation operation, Long enrolmentId, LocalDate arrival, LocalDate departure,
                              boolean removePresencesOutside, Map<Long, Boolean> decisions,
                              CorrectionReason reason) {
            this.operation = operation;
            this.enrolmentId = Objects.requireNonNull(enrolmentId, "enrolmentId");
            this.arrival = arrival;
            this.departure = departure;
            this.removePresencesOutside = removePresencesOutside;
            this.decisions = new TreeMap<>(decisions == null ? Map.of() : decisions);
            this.reason = reason;
        }

        @Override
        public String fingerprint() {
            String marks = decisions.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining(";"));
            return EncashmentCorrectionService.canonical("ENROLMENT_" + operation, enrolmentId, arrival, departure,
                    removePresencesOutside, marks, reason.type(), reason.text());
        }

        /** Toutes les séries du groupe, vues par l'étudiant : le coût de chacune peut changer. */
        @Override
        public CorrectionScope scope() {
            StudentGroupEntity enrolment = load(enrolmentId);
            return CorrectionScope.empty().group(enrolment.getStudent().getId(), enrolment.getGroup().getId());
        }

        @Override
        public CorrectionExecution<EnrolmentCorrection> execute() {
            StudentGroupEntity enrolment = load(enrolmentId);
            StudentEntity student = enrolment.getStudent();
            GroupEntity group = enrolment.getGroup();
            readOnlyYearGuard.assertGroupMutable(group);

            EnrolmentWindow current = enrolment.window();
            EnrolmentWindow target = target(enrolment, current, group);
            assertNoOverlap(enrolment, target, student, group);

            List<SessionSeriesEntity> seriesList = seriesRepository.findByGroupId(group.getId()).stream()
                    .sorted(Comparator.comparing(SessionSeriesEntity::getId))
                    .toList();
            List<EnrolmentWindow> windowsBefore = windowsOf(student, group);
            Map<Long, Set<Long>> billableBefore = billable(student, seriesList);
            Map<Long, BigDecimal> excessBefore = excess(student, seriesList);

            enrolment.setDateAssigned(EnrolmentWindow.startOfDay(target.arrival()));
            enrolment.setDateLeft(EnrolmentWindow.startOfDay(target.departure()));
            enrolment.setActive(target.departure() == null);
            studentGroupRepository.saveAndFlush(enrolment);
            List<EnrolmentWindow> windowsAfter = windowsOf(student, group);

            List<CorrectionEffect> effects = new ArrayList<>();
            List<AuditDraft> audits = new ArrayList<>();
            String change = describeChange(student, group, current, target);
            effects.add(new CorrectionEffect(CorrectionEffectType.ENROLMENT_WINDOW_CHANGED, change));
            audits.add(AuditDraft.builder()
                    .domain(CorrectionDomain.ENROLMENT)
                    .action(action(current))
                    .entityId(enrolmentId)
                    .studentId(student.getId())
                    .groupId(group.getId())
                    .oldValue(values(current))
                    .newValue(values(target))
                    .summary(change)
                    .reason(reason)
                    .build());

            applyToSessions(student, group, windowsBefore, windowsAfter, effects, audits);
            attendanceRepository.flush();

            Set<SeriesKey> touched = new HashSet<>();
            for (SessionSeriesEntity series : seriesList) {
                Set<Long> lost = new HashSet<>(billableBefore.get(series.getId()));
                lost.removeAll(billableIds(student, series));
                if (!lost.isEmpty()) {
                    VentilationMover.Move move = ventilationMover.move(student.getId(), series, lost);
                    effects.addAll(move.effects());
                    if (move.moved()) {
                        touched.add(new SeriesKey(student.getId(), series.getId()));
                    }
                }
                paymentRepository.findByStudentIdAndGroupIdAndSessionSeriesId(student.getId(), group.getId(),
                        series.getId()).ifPresent(payment -> {
                            // Le coût a pu changer : le statut stocké de la ligne de paiement suit.
                            encashmentService.refreshSeriesCumul(payment);
                            touched.add(new SeriesKey(student.getId(), series.getId()));
                        });
                announceExcess(student, series, excessBefore.get(series.getId()), effects);
            }

            return new CorrectionExecution<>(new EnrolmentCorrection(enrolmentId, student.getId(), group.getId(),
                    target.arrival(), target.departure(), target.departure() == null),
                    effects, touched, audits);
        }

        /** Fenêtre voulue, après les contrôles propres à chaque correction. */
        private EnrolmentWindow target(StudentGroupEntity enrolment, EnrolmentWindow current, GroupEntity group) {
            String who = fullName(enrolment.getStudent());
            return switch (operation) {
                case ARRIVAL -> {
                    if (arrival.equals(current.arrival())) {
                        throw new CustomServiceException("L'arrivée de " + who + " est déjà le "
                                + EnrolmentWindow.format(arrival) + " : rien à corriger.", HttpStatus.BAD_REQUEST);
                    }
                    EnrolmentDates.assertWithinSchoolYear(group, arrival, "La date d'arrivée");
                    if (current.departure() != null && arrival.isAfter(current.departure())) {
                        throw new CustomServiceException("L'arrivée du " + EnrolmentWindow.format(arrival)
                                + " suivrait le départ de " + who + " du " + EnrolmentWindow.format(current.departure())
                                + " : corrigez d'abord le départ.", HttpStatus.CONFLICT);
                    }
                    yield new EnrolmentWindow(arrival, current.departure());
                }
                case DEPARTURE -> {
                    if (departure.equals(current.departure())) {
                        throw new CustomServiceException("Le départ de " + who + " est déjà le "
                                + EnrolmentWindow.format(departure) + " : rien à corriger.", HttpStatus.BAD_REQUEST);
                    }
                    EnrolmentDates.assertWithinSchoolYear(group, departure, "La date de départ");
                    if (current.arrival() != null && departure.isBefore(current.arrival())) {
                        throw new CustomServiceException("Le départ du " + EnrolmentWindow.format(departure)
                                + " précéderait l'arrivée de " + who + " du " + EnrolmentWindow.format(current.arrival())
                                + " : corrigez d'abord l'arrivée.", HttpStatus.CONFLICT);
                    }
                    yield new EnrolmentWindow(current.arrival(), departure);
                }
                case REOPEN -> {
                    if (current.departure() == null) {
                        throw new CustomServiceException("L'inscription de " + who + " au groupe « " + group.getName()
                                + " » est déjà ouverte : rien à rouvrir.", HttpStatus.CONFLICT);
                    }
                    yield new EnrolmentWindow(current.arrival(), null);
                }
            };
        }

        /**
         * Deux inscriptions d'un étudiant au même groupe ne se recouvrent jamais : une même séance le
         * concernerait deux fois.
         */
        private void assertNoOverlap(StudentGroupEntity enrolment, EnrolmentWindow target, StudentEntity student,
                                     GroupEntity group) {
            for (StudentGroupEntity other : studentGroupRepository.findByGroupIdAndStudentId(group.getId(),
                    student.getId())) {
                if (Objects.equals(other.getId(), enrolment.getId())) {
                    continue;
                }
                EnrolmentWindow window = other.window();
                boolean overlaps = window.arrival() != null
                        && (target.departure() == null || !window.arrival().isAfter(target.departure()))
                        && (window.departure() == null || !window.departure().isBefore(target.arrival()));
                if (overlaps) {
                    throw new CustomServiceException("La période " + target.describe() + " chevaucherait l'autre "
                            + "inscription de " + fullName(student) + " au groupe « " + group.getName() + " » ("
                            + window.describe() + ") : une même séance le concernerait deux fois.",
                            HttpStatus.CONFLICT);
                }
            }
        }

        /** Présences des séances qui sortent de la période ou y entrent. */
        private void applyToSessions(StudentEntity student, GroupEntity group, List<EnrolmentWindow> before,
                                     List<EnrolmentWindow> after, List<CorrectionEffect> effects,
                                     List<AuditDraft> audits) {
            Map<Long, List<AttendanceEntity>> attendanceBySession = new HashMap<>();
            for (AttendanceEntity attendance : attendanceRepository.findByStudentIdAndActiveTrue(student.getId())) {
                SessionEntity session = attendance.getSession();
                GroupEntity host = session == null ? null : RollCallService.groupOf(session);
                if (host != null && Objects.equals(host.getId(), group.getId())) {
                    attendanceBySession.computeIfAbsent(session.getId(), id -> new ArrayList<>()).add(attendance);
                }
            }

            List<SessionEntity> sessions = sessionRepository.findByGroupId(group.getId()).stream()
                    .filter(session -> !Boolean.FALSE.equals(session.getActive()))
                    .sorted(Comparator.comparing(SessionEntity::getSessionTimeStart,
                                    Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(SessionEntity::getId))
                    .toList();

            Map<Long, SessionEntity> entering = new LinkedHashMap<>();
            for (SessionEntity session : sessions) {
                boolean wasIn = contains(before, session);
                boolean isIn = contains(after, session);
                List<AttendanceEntity> marks = attendanceBySession.getOrDefault(session.getId(), List.of());
                if (wasIn && !isIn) {
                    leaving(student, group, session, marks, effects, audits);
                } else if (!wasIn && isIn && Boolean.TRUE.equals(session.getIsFinished()) && marks.isEmpty()) {
                    entering.put(session.getId(), session);
                }
            }

            Set<Long> unknown = new java.util.TreeSet<>(decisions.keySet());
            unknown.removeAll(entering.keySet());
            if (!unknown.isEmpty()) {
                throw new CustomServiceException("Séance(s) " + unknown + " : rien à y noter dans cette correction. "
                        + "Seules les séances validées que la correction fait entrer dans la période, sans présence "
                        + "de l'étudiant, attendent une présence ou une absence.", HttpStatus.BAD_REQUEST);
            }
            for (SessionEntity session : entering.values()) {
                Boolean present = decisions.get(session.getId());
                if (present == null) {
                    effects.add(new CorrectionEffect(CorrectionEffectType.SESSION_BECAME_BILLABLE,
                            "Séance du " + day(session) + " (« " + seriesName(session) + " ») validée sans présence de "
                                    + fullName(student) + " : facturable, sa place était réservée"));
                } else {
                    record(student, group, session, present, effects, audits);
                }
            }
        }

        /** Une séance sort de la période : son absence part, sa présence reste ou part. */
        private void leaving(StudentEntity student, GroupEntity group, SessionEntity session,
                             List<AttendanceEntity> marks, List<CorrectionEffect> effects, List<AuditDraft> audits) {
            for (AttendanceEntity mark : marks) {
                if (Boolean.TRUE.equals(mark.getIsCatchUp())) {
                    continue;
                }
                boolean presence = Boolean.TRUE.equals(mark.getIsPresent());
                String what = (presence ? "Présence" : "Absence") + " de " + fullName(student) + " le " + day(session)
                        + " (« " + seriesName(session) + " »)";
                if (presence && !removePresencesOutside) {
                    effects.add(new CorrectionEffect(CorrectionEffectType.PRESENCE_KEPT, what
                            + " hors de la nouvelle période : maintenue, facturée comme séance consommée"));
                    continue;
                }
                mark.setActive(false);
                attendanceRepository.save(mark);
                String removed = what + " retirée : hors de la nouvelle période";
                effects.add(new CorrectionEffect(presence ? CorrectionEffectType.PRESENCE_REMOVED
                        : CorrectionEffectType.ABSENCE_REMOVED, removed));
                audits.add(attendanceTrace(CorrectionAction.ATTENDANCE_REMOVED, mark, student, group, session,
                        Map.of("present", presence, "active", true), Map.of("present", presence, "active", false),
                        removed));
            }
        }

        /** Présence ou absence notée sur une séance validée qui entre dans la période. */
        private void record(StudentEntity student, GroupEntity group, SessionEntity session, boolean present,
                            List<CorrectionEffect> effects, List<AuditDraft> audits) {
            AttendanceEntity mark = attendanceRepository.save(AttendanceEntity.builder()
                    .student(student)
                    .session(session)
                    .sessionSeries(session.getSessionSeries())
                    .group(group)
                    .isPresent(present)
                    .isJustified(false)
                    .isCatchUp(false)
                    .build());
            String recorded = (present ? "Présence" : "Absence") + " de " + fullName(student) + " le " + day(session)
                    + " (« " + seriesName(session) + " ») enregistrée";
            effects.add(new CorrectionEffect(CorrectionEffectType.ATTENDANCE_RECORDED, recorded));
            audits.add(attendanceTrace(CorrectionAction.ATTENDANCE_ADDED, mark, student, group, session,
                    null, Map.of("present", present, "active", true), recorded));
        }

        private AuditDraft attendanceTrace(CorrectionAction action, AttendanceEntity mark, StudentEntity student,
                                           GroupEntity group, SessionEntity session, Map<String, ?> oldValue,
                                           Map<String, ?> newValue, String summary) {
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

        private CorrectionAction action(EnrolmentWindow current) {
            return switch (operation) {
                case ARRIVAL -> CorrectionAction.ARRIVAL_DATE_CORRECTED;
                case DEPARTURE -> current.departure() == null ? CorrectionAction.DEPARTURE_RECORDED
                        : CorrectionAction.DEPARTURE_CORRECTED;
                case REOPEN -> CorrectionAction.ENROLMENT_REOPENED;
            };
        }

        /** La correction en une phrase, pour l'Aperçu et le Journal. */
        private String describeChange(StudentEntity student, GroupEntity group, EnrolmentWindow current,
                                      EnrolmentWindow target) {
            String who = fullName(student);
            String where = "« " + group.getName() + " »";
            return switch (operation) {
                case ARRIVAL -> "Arrivée de " + who + " dans " + where + " : " + dayOrNone(current.arrival())
                        + " → " + EnrolmentWindow.format(target.arrival());
                case DEPARTURE -> current.departure() == null
                        ? "Départ de " + who + " de " + where + " enregistré au " + EnrolmentWindow.format(target.departure())
                        : "Départ de " + who + " de " + where + " : " + EnrolmentWindow.format(current.departure())
                                + " → " + EnrolmentWindow.format(target.departure());
                case REOPEN -> "Inscription de " + who + " à " + where + " rouverte : départ du "
                        + EnrolmentWindow.format(current.departure()) + " annulé";
            };
        }
    }

    // ------------------------------------------------------------------
    // Lectures
    // ------------------------------------------------------------------

    private StudentGroupEntity load(Long enrolmentId) {
        return studentGroupRepository.findById(enrolmentId)
                .orElseThrow(() -> new CustomServiceException("Inscription introuvable : " + enrolmentId,
                        HttpStatus.NOT_FOUND));
    }

    private List<EnrolmentWindow> windowsOf(StudentEntity student, GroupEntity group) {
        return studentGroupRepository.findByGroupIdAndStudentId(group.getId(), student.getId()).stream()
                .map(StudentGroupEntity::window)
                .toList();
    }

    private static boolean contains(List<EnrolmentWindow> windows, SessionEntity session) {
        return windows.stream().anyMatch(window -> window.contains(session.getSessionTimeStart()));
    }

    private Map<Long, Set<Long>> billable(StudentEntity student, List<SessionSeriesEntity> seriesList) {
        Map<Long, Set<Long>> billable = new HashMap<>();
        seriesList.forEach(series -> billable.put(series.getId(), billableIds(student, series)));
        return billable;
    }

    private Set<Long> billableIds(StudentEntity student, SessionSeriesEntity series) {
        return billableSessionsResolver.resolve(student.getId(), series.getId()).billable().stream()
                .map(SessionEntity::getId)
                .collect(Collectors.toSet());
    }

    /** Excédent du versé sur le coût, par série ; zéro sans excédent. */
    private Map<Long, BigDecimal> excess(StudentEntity student, List<SessionSeriesEntity> seriesList) {
        Map<Long, BigDecimal> excess = new HashMap<>();
        seriesList.forEach(series -> excess.put(series.getId(), excessOf(student, series)));
        return excess;
    }

    private BigDecimal excessOf(StudentEntity student, SessionSeriesEntity series) {
        PaymentCostResolver.PaymentStatusResult status = costResolver.resolve(student.getId(), series.getId());
        return money(status.amountPaid().subtract(status.monthTotalCost()).max(BigDecimal.ZERO));
    }

    /** Trop-perçu apparu ou accru par la correction : annoncé, jamais traité par elle (5.9). */
    private void announceExcess(StudentEntity student, SessionSeriesEntity series, BigDecimal before,
                                List<CorrectionEffect> effects) {
        BigDecimal after = excessOf(student, series);
        if (after.compareTo(before) > 0) {
            effects.add(new CorrectionEffect(CorrectionEffectType.EXCESS_LEFT, "Trop-perçu de "
                    + AmountEffectWriter.money(after) + " DA sur « " + series.getName() + " » : ni reporté ni "
                    + "remboursé par cette correction"));
        }
    }

    // ------------------------------------------------------------------
    // Rédaction
    // ------------------------------------------------------------------

    private static Map<String, Object> values(EnrolmentWindow window) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("arrival", window.arrival() == null ? null : window.arrival().toString());
        values.put("departure", window.departure() == null ? null : window.departure().toString());
        values.put("active", window.departure() == null);
        return values;
    }

    private static String day(SessionEntity session) {
        return dayOrNone(EnrolmentWindow.dayOf(session.getSessionTimeStart()));
    }

    private static String dayOrNone(LocalDate day) {
        return day == null ? "non datée" : EnrolmentWindow.format(day);
    }

    private static String seriesName(SessionEntity session) {
        return session.getSessionSeries() == null ? "sans série" : session.getSessionSeries().getName();
    }

    private static String fullName(StudentEntity student) {
        return Stream.of(student.getFirstName(), student.getLastName())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
