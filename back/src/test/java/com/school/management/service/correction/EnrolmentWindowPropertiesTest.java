package com.school.management.service.correction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.management.mapper.AttendanceMapper;
import com.school.management.mapper.SessionMapper;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.CorrectionReasonType;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PricingEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.PricingRepository;
import com.school.management.repository.RoomRepository;
import com.school.management.repository.SchoolYearRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.repository.StudentGroupRepository;
import com.school.management.repository.StudentRepository;
import com.school.management.repository.TeacherRepository;
import com.school.management.service.AttendanceService;
import com.school.management.service.CatchUpRoutingService;
import com.school.management.service.DiscountService;
import com.school.management.service.ReadOnlyYearGuard;
import com.school.management.service.RefundNumberService;
import com.school.management.service.RefundService;
import com.school.management.service.SeriesRolloverService;
import com.school.management.service.SessionService;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.payment.BillableSessionsResolver;
import com.school.management.service.payment.BillableSessionsResolverImpl;
import com.school.management.service.payment.CatchUpBillingQualifierImpl;
import com.school.management.service.payment.EncashmentQueryService;
import com.school.management.service.payment.EncashmentService;
import com.school.management.service.payment.PaymentAllocationService;
import com.school.management.service.payment.PaymentCarryOverService;
import com.school.management.service.payment.PaymentCostResolver;
import com.school.management.service.payment.PaymentDetailDeactivationService;
import com.school.management.service.payment.PaymentDistributionService;
import com.school.management.service.payment.PaymentIdempotencyService;
import com.school.management.service.payment.PaymentProcessingService;
import com.school.management.service.payment.PaymentQuoteService;
import com.school.management.service.payment.ReceiptNumberService;
import com.school.management.service.session.AbsenceWindowGuard;
import com.school.management.service.session.RollCallService;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;
import net.jqwik.api.lifecycle.BeforeTry;
import net.jqwik.api.statistics.Statistics;
import org.mockito.Mockito;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Propriétés de la Fenêtre_Inscription (spec admin-corrections, C.7) : P5 et P6.
 *
 * <ul>
 *   <li><b>P5 — fenêtre respectée.</b> Quelle que soit la suite d'opérations — feuilles de présence,
 *       présences unitaires, corrections d'arrivée, de départ, réouvertures, séances déplacées —,
 *       aucune absence active ne subsiste hors d'une Fenêtre_Inscription de son étudiant, et la
 *       Feuille_Appel de chaque séance est exactement l'ensemble des étudiants dont une fenêtre
 *       contient son jour, départs compris. Une opération refusée ne change rien. (6.2, 7.1, 7.3)</li>
 *   <li><b>P6 — déplacer la ventilation ne change aucun montant.</b> Une correction de dates laisse
 *       Encaissements, Imputations, reports et cumuls tels quels ; la ventilation ne reste que sur
 *       des séances facturables, ne grossit jamais, et ne perd de l'argent que si aucune séance
 *       facturable de la série ne peut plus le recevoir. (5.9, D6)</li>
 * </ul>
 *
 * <p>Les oracles sont indépendants du code testé : fenêtres, absences et dates relues en SQL,
 * comparées en jours. Même harnais que {@code CorrectionPropertiesTest} : un contexte ciblé sur H2,
 * amorcé une fois, base vidée à chaque essai.</p>
 */
class EnrolmentWindowPropertiesTest {

    private static final double PRICE_PER_SESSION = 2000.0;
    private static final int STUDENTS = 3;
    private static final int SESSIONS_PER_SERIES = 3;
    private static final int SERIES = 2;
    /** Lundi de la première séance ; les séances suivent de semaine en semaine. */
    private static final LocalDate FIRST_MONDAY = LocalDate.of(2030, 1, 7);
    private static final CorrectionReason ARRIVAL = CorrectionReason.of(CorrectionReasonType.ARRIVAL_DATE_CORRECTED);
    private static final CorrectionReason LEFT = CorrectionReason.of(CorrectionReasonType.STUDENT_LEFT);
    private static final CorrectionReason MISTAKE = CorrectionReason.of(CorrectionReasonType.DATA_ENTRY_ERROR);

    private static ConfigurableApplicationContext context;
    private static JdbcTemplate jdbc;
    private static AttendanceService attendanceService;
    private static SessionService sessionService;
    private static RollCallService rollCallService;
    private static EnrolmentCorrectionService corrections;
    private static PaymentProcessingService processing;
    private static BillableSessionsResolver billableSessionsResolver;
    private static PaymentQuoteService quoteService;
    private static PaymentCostResolver costResolver;
    private static SchoolYearRepository schoolYearRepository;
    private static PricingRepository pricingRepository;
    private static GroupRepository groupRepository;
    private static SessionSeriesRepository seriesRepository;
    private static SessionRepository sessionRepository;
    private static StudentRepository studentRepository;
    private static StudentGroupRepository studentGroupRepository;
    private static AttendanceRepository attendanceRepository;

    @BeforeContainer
    static void startContext() {
        context = new SpringApplicationBuilder(WindowTestContext.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.datasource.url=jdbc:h2:mem:enrolment-window-properties;DB_CLOSE_DELAY=-1",
                        "--spring.datasource.driverClassName=org.h2.Driver",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                        "--spring.jpa.hibernate.ddl-auto=create-drop",
                        "--spring.jpa.show-sql=false",
                        "--spring.main.banner-mode=off",
                        "--logging.level.com.school.management=WARN");
        jdbc = context.getBean(JdbcTemplate.class);
        attendanceService = context.getBean(AttendanceService.class);
        sessionService = context.getBean(SessionService.class);
        rollCallService = context.getBean(RollCallService.class);
        corrections = context.getBean(EnrolmentCorrectionService.class);
        processing = context.getBean(PaymentProcessingService.class);
        billableSessionsResolver = context.getBean(BillableSessionsResolver.class);
        quoteService = context.getBean(PaymentQuoteService.class);
        costResolver = context.getBean(PaymentCostResolver.class);
        schoolYearRepository = context.getBean(SchoolYearRepository.class);
        pricingRepository = context.getBean(PricingRepository.class);
        groupRepository = context.getBean(GroupRepository.class);
        seriesRepository = context.getBean(SessionSeriesRepository.class);
        sessionRepository = context.getBean(SessionRepository.class);
        studentRepository = context.getBean(StudentRepository.class);
        studentGroupRepository = context.getBean(StudentGroupRepository.class);
        attendanceRepository = context.getBean(AttendanceRepository.class);
    }

    @AfterContainer
    static void stopContext() {
        SecurityContextHolder.clearContext();
        if (context != null) {
            context.close();
        }
    }

    @BeforeTry
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin-test", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    // ------------------------------------------------------------------
    // Scénarios
    // ------------------------------------------------------------------

    /**
     * Inscriptions d'un étudiant au groupe, en jours depuis le premier lundi : une première fenêtre,
     * ouverte ou close ; si elle est close, éventuellement un retour, ouvert ou clos.
     */
    record Plan(int arrival, Integer stay, Integer gap, Integer secondStay) {
    }

    enum Mark { NONE, PRESENT, ABSENT, UNSET }

    sealed interface Step permits Sheet, Single, Arrival, Departure, Reopen, Move {
    }

    /** Feuille de présence d'une séance, une marque par étudiant. */
    record Sheet(int session, List<Mark> marks) implements Step {
    }

    /** Présence unitaire. */
    record Single(int session, int student, Mark mark) implements Step {
    }

    record Arrival(int student, int pick, int day) implements Step {
    }

    record Departure(int student, int pick, int day, boolean removePresences) implements Step {
    }

    record Reopen(int student, int pick) implements Step {
    }

    /** Séance déplacée de quelques jours. */
    record Move(int session, int shift) implements Step {
    }

    record Scenario(List<Plan> plans, List<Step> steps) {
    }

    record Payment(int student, int series, int amount) {
    }

    /**
     * P6 : une correction qui <em>réduit</em> la période — arrivée plus tardive ou départ plus tôt,
     * de {@code shift} jours — c'est elle qui fait perdre des séances ventilées.
     */
    record Shrink(int student, int pick, boolean arrival, int shift) {
    }

    record VentilationScenario(List<Plan> plans, List<Single> presences, List<Payment> payments, Shrink correction) {
    }

    private static Arbitrary<Plan> plan() {
        return Combinators.combine(
                Arbitraries.integers().between(-14, 30),
                Arbitraries.integers().between(0, 35).injectNull(0.4),
                Arbitraries.integers().between(1, 15).injectNull(0.5),
                Arbitraries.integers().between(0, 20).injectNull(0.6)).as(Plan::new);
    }

    private static Arbitrary<Integer> day() {
        return Arbitraries.integers().between(-14, 42);
    }

    private static Arbitrary<Mark> mark() {
        return Arbitraries.frequency(Tuple.of(1, Mark.NONE), Tuple.of(3, Mark.PRESENT), Tuple.of(3, Mark.ABSENT),
                Tuple.of(1, Mark.UNSET));
    }

    private static Arbitrary<Integer> sessionIndex() {
        return Arbitraries.integers().between(0, SERIES * SESSIONS_PER_SERIES - 1);
    }

    private static Arbitrary<Integer> student() {
        return Arbitraries.integers().between(0, STUDENTS - 1);
    }

    private static Arbitrary<Step> correction() {
        Arbitrary<Step> arrival = Combinators.combine(student(), Arbitraries.integers().between(0, 1), day())
                .as(Arrival::new);
        Arbitrary<Step> departure = Combinators.combine(student(), Arbitraries.integers().between(0, 1), day(),
                Arbitraries.of(true, false)).as(Departure::new);
        Arbitrary<Step> reopen = Combinators.combine(student(), Arbitraries.integers().between(0, 1)).as(Reopen::new);
        return Arbitraries.frequencyOf(Tuple.of(3, arrival), Tuple.of(3, departure), Tuple.of(1, reopen));
    }

    @Provide
    Arbitrary<Scenario> scenarios() {
        Arbitrary<Step> sheet = Combinators.combine(sessionIndex(), mark().list().ofSize(STUDENTS)).as(Sheet::new);
        Arbitrary<Step> single = Combinators.combine(sessionIndex(), student(), mark()).as(Single::new);
        Arbitrary<Step> move = Combinators.combine(sessionIndex(), Arbitraries.integers().between(-10, 10)).as(Move::new);
        Arbitrary<Step> step = Arbitraries.frequencyOf(Tuple.of(4, sheet), Tuple.of(2, single), Tuple.of(4, correction()),
                Tuple.of(1, move));
        return Combinators.combine(plan().list().ofSize(STUDENTS), step.list().ofMinSize(3).ofMaxSize(12))
                .as(Scenario::new);
    }

    @Provide
    Arbitrary<VentilationScenario> ventilationScenarios() {
        Arbitrary<Single> presence = Combinators.combine(sessionIndex(), student(), mark()).as(Single::new);
        // Versements surtout modestes : une série qu'aucun versement ne remplit laisse de la place où
        // déplacer. Tirés uniformément de 500 à 4 000 DA, ils remplissaient trop souvent la série :
        // le déplacement sans reliquat ne sortait que dans 2 à 5 % des scénarios, sous le seuil exigé
        // un lancement sur quelques-uns. Les gros versements restent, pour le reliquat.
        Arbitrary<Integer> amount = Arbitraries.frequencyOf(
                Tuple.of(2, Arbitraries.integers().between(1, 3)),
                Tuple.of(1, Arbitraries.integers().between(4, 8))).map(units -> units * 500);
        Arbitrary<Payment> payment = Combinators.combine(student(), Arbitraries.integers().between(0, SERIES - 1),
                amount).as(Payment::new);
        Arbitrary<Shrink> shrink = Combinators.combine(student(), Arbitraries.integers().between(0, 1),
                Arbitraries.of(true, false), Arbitraries.integers().between(1, 21)).as(Shrink::new);
        return Combinators.combine(plan().list().ofSize(STUDENTS), presence.list().ofMaxSize(6),
                payment.list().ofMinSize(1).ofMaxSize(4), shrink).as(VentilationScenario::new);
    }

    // ------------------------------------------------------------------
    // P5 — fenêtre respectée
    // ------------------------------------------------------------------

    @Property(tries = 100)
    void noEntryPointLeavesAnAbsenceOutsideTheWindow(@ForAll("scenarios") Scenario scenario) {
        World world = persist(scenario.plans());
        assertWindowRespected(world);

        for (Step step : scenario.steps()) {
            Snapshot before = snapshot();
            String outcome;
            try {
                outcome = apply(world, step);
            } catch (CustomServiceException refused) {
                assertThat(snapshot()).as("%s refusé : rien n'est écrit", step).isEqualTo(before);
                outcome = name(step) + " refusée";
            }
            Statistics.label("P5").collect(outcome);
            assertWindowRespected(world);
        }

        Statistics.label("P5").coverage(c -> {
            c.check("feuille acceptée").percentage(p -> p >= 5);
            c.check("feuille refusée").percentage(p -> p >= 2);
            c.check("arrivée acceptée").percentage(p -> p >= 3);
            c.check("arrivée refusée").percentage(p -> p >= 1);
            c.check("départ acceptée").percentage(p -> p >= 3);
            c.check("réouverture acceptée").percentage(p -> p >= 0.5);
            c.check("séance déplacée acceptée").percentage(p -> p >= 1);
        });
    }

    /** Les deux moitiés de P5, sur l'état courant. */
    private static void assertWindowRespected(World world) {
        Map<Long, List<LocalDate[]>> windows = windows();
        for (Map<String, Object> row : jdbc.queryForList("SELECT a.student_id, s.session_time_start FROM attendance a "
                + "JOIN session s ON a.session_id = s.id WHERE a.active = TRUE AND (a.status IS NULL OR a.status = FALSE)")) {
            Long studentId = ((Number) row.get("STUDENT_ID")).longValue();
            LocalDate day = ((Timestamp) row.get("SESSION_TIME_START")).toLocalDateTime().toLocalDate();
            assertThat(inWindow(windows.getOrDefault(studentId, List.of()), day))
                    .as("absence active de l'étudiant %d le %s, hors de ses fenêtres", studentId, day).isTrue();
        }
        for (Map<String, Object> row : jdbc.queryForList("SELECT id, session_time_start FROM session")) {
            Long sessionId = ((Number) row.get("ID")).longValue();
            LocalDate day = ((Timestamp) row.get("SESSION_TIME_START")).toLocalDateTime().toLocalDate();
            Set<Long> expected = world.students().stream()
                    .filter(id -> inWindow(windows.getOrDefault(id, List.of()), day))
                    .collect(Collectors.toCollection(TreeSet::new));
            Set<Long> rollCall = rollCallService.rollCall(sessionId).students().stream()
                    .map(student -> student.id())
                    .collect(Collectors.toCollection(TreeSet::new));
            assertThat(rollCall).as("feuille d'appel du %s", day).isEqualTo(expected);
        }
    }

    // ------------------------------------------------------------------
    // P6 — déplacer la ventilation ne change aucun montant
    // ------------------------------------------------------------------

    // 200 essais : les deux issues rares (déplacée, avec ou sans reliquat) restent chacune bien au-dessus
    // de leur seuil de couverture, qui ne doit pas échouer au hasard.
    @Property(tries = 200)
    void movingTheVentilationChangesNoAmount(@ForAll("ventilationScenarios") VentilationScenario scenario) {
        World world = persist(scenario.plans());
        for (Single presence : scenario.presences()) {
            markQuietly(world, presence);
        }
        for (Payment payment : scenario.payments()) {
            try {
                processing.processPayment(world.students().get(payment.student()), world.groupId(),
                        world.series().get(payment.series()), payment.amount());
            } catch (CustomServiceException refused) {
                // Au-delà de ce que le groupe peut encore recevoir : l'essai continue sans lui.
            }
        }

        Money moneyBefore = money();
        Map<String, BigDecimal> paidBefore = paid(world);
        Map<Long, BigDecimal> linesBefore = linesPerAllocation();
        long inactiveBefore = jdbc.queryForObject("SELECT COUNT(*) FROM payment_detail WHERE active = FALSE", Long.class);
        try {
            shrink(world, scenario.correction());
        } catch (CustomServiceException refused) {
            assertThat(money()).isEqualTo(moneyBefore);
            Statistics.label("P6").collect("correction refusée");
            return;
        }

        assertThat(money()).as("Encaissements, Imputations, reports et cumuls inchangés").isEqualTo(moneyBefore);
        assertThat(paid(world)).as("versé de chaque série inchangé").isEqualTo(paidBefore);

        Map<Long, BigDecimal> linesAfter = linesPerAllocation();
        boolean unplaced = false;
        for (Map.Entry<Long, BigDecimal> entry : linesBefore.entrySet()) {
            BigDecimal after = linesAfter.getOrDefault(entry.getKey(), BigDecimal.ZERO.setScale(2));
            assertThat(after).as("la ventilation d'une Imputation ne grossit jamais").isLessThanOrEqualTo(entry.getValue());
            if (after.compareTo(entry.getValue()) < 0) {
                unplaced = true;
                assertSeriesFull(entry.getKey());
            }
        }
        assertThat(linesAfter.keySet()).as("aucune ventilation n'apparaît sur une autre Imputation")
                .isSubsetOf(linesBefore.keySet());
        assertLinesOnBillableSessionsOnly();
        assertNoSessionAboveNetPrice();

        boolean moved = jdbc.queryForObject("SELECT COUNT(*) FROM payment_detail WHERE active = FALSE", Long.class)
                > inactiveBefore;
        Statistics.label("P6").collect(!moved ? "rien à déplacer" : unplaced ? "déplacée, avec reliquat" : "déplacée");
        Statistics.label("P6").coverage(c -> {
            // Relevé sur 200 essais : 6 à 9 % chacune. 2 % (4 scénarios) reste à plus de 3 écarts-types
            // sous ces taux.
            c.check("déplacée").percentage(p -> p >= 2);
            c.check("déplacée, avec reliquat").percentage(p -> p >= 2);
        });
    }

    /** Toute ligne active est sur une séance que le résolveur dit facturable à son étudiant. */
    private static void assertLinesOnBillableSessionsOnly() {
        for (Map<String, Object> row : jdbc.queryForList("SELECT p.student_id, p.session_series_id, d.session_id "
                + "FROM payment_detail d JOIN payments p ON d.payment_id = p.id WHERE d.active = TRUE")) {
            Long studentId = ((Number) row.get("STUDENT_ID")).longValue();
            Long seriesId = ((Number) row.get("SESSION_SERIES_ID")).longValue();
            Long sessionId = ((Number) row.get("SESSION_ID")).longValue();
            assertThat(billableSessionsResolver.resolve(studentId, seriesId).billable())
                    .as("ventilation sur une séance non facturable").extracting(SessionEntity::getId).contains(sessionId);
        }
    }

    /** Une séance ne porte jamais plus que son prix net, tous Encaissements confondus (1.6). */
    private static void assertNoSessionAboveNetPrice() {
        for (Map<String, Object> row : jdbc.queryForList("SELECT p.student_id, p.session_series_id, d.session_id, "
                + "SUM(d.amount_paid) AS total FROM payment_detail d JOIN payments p ON d.payment_id = p.id "
                + "WHERE d.active = TRUE GROUP BY p.student_id, p.session_series_id, d.session_id")) {
            Long studentId = ((Number) row.get("STUDENT_ID")).longValue();
            Long seriesId = ((Number) row.get("SESSION_SERIES_ID")).longValue();
            assertThat(money(row.get("TOTAL"))).as("séance ventilée au-delà de son prix net")
                    .isLessThanOrEqualTo(quoteService.netPricePerSession(studentId, seriesId)
                            .setScale(2, RoundingMode.HALF_UP));
        }
    }

    /** Un reliquat n'est admis que si chaque séance facturable de la série est déjà couverte au prix net. */
    private static void assertSeriesFull(Long allocationId) {
        Map<String, Object> allocation = jdbc.queryForMap("SELECT a.payment_id, p.student_id, p.session_series_id "
                + "FROM encashment_allocation a JOIN payments p ON a.payment_id = p.id WHERE a.id = ?", allocationId);
        Long paymentId = ((Number) allocation.get("PAYMENT_ID")).longValue();
        Long studentId = ((Number) allocation.get("STUDENT_ID")).longValue();
        Long seriesId = ((Number) allocation.get("SESSION_SERIES_ID")).longValue();
        BigDecimal netPrice = quoteService.netPricePerSession(studentId, seriesId).setScale(2, RoundingMode.HALF_UP);
        for (SessionEntity session : billableSessionsResolver.resolve(studentId, seriesId).billable()) {
            BigDecimal covered = money(jdbc.queryForObject("SELECT COALESCE(SUM(amount_paid), 0) FROM payment_detail "
                    + "WHERE active = TRUE AND payment_id = ? AND session_id = ?", Object.class, paymentId, session.getId()));
            assertThat(covered).as("reliquat laissé alors qu'une séance facturable a encore de la place")
                    .isGreaterThanOrEqualTo(netPrice);
        }
    }

    // ------------------------------------------------------------------
    // Exécution
    // ------------------------------------------------------------------

    private record World(Long groupId, List<Long> series, List<Long> sessions, List<Long> students) {
    }

    /** Applique une étape ; renvoie son issue si elle est acceptée, lève le refus sinon. */
    private static String apply(World world, Step step) {
        return switch (step) {
            case Sheet sheet -> submitSheet(world, sheet);
            case Single single -> {
                AttendanceEntity line = line(world, single.session(), single.student(), single.mark());
                if (line == null) {
                    yield "présence sans objet";
                }
                attendanceService.createAttendance(line);
                yield "présence acceptée";
            }
            case Arrival arrival -> {
                Long enrolment = enrolment(world, arrival.student(), arrival.pick());
                confirm((mode, token) -> corrections.correctArrival(enrolment, day(arrival.day()), Map.of(), ARRIVAL,
                        mode, token));
                yield "arrivée acceptée";
            }
            case Departure departure -> {
                Long enrolment = enrolment(world, departure.student(), departure.pick());
                confirm((mode, token) -> corrections.setDeparture(enrolment, day(departure.day()),
                        departure.removePresences(), Map.of(), LEFT, mode, token));
                yield "départ acceptée";
            }
            case Reopen reopen -> {
                Long enrolment = enrolment(world, reopen.student(), reopen.pick());
                confirm((mode, token) -> corrections.reopen(enrolment, Map.of(), MISTAKE, mode, token));
                yield "réouverture acceptée";
            }
            case Move move -> {
                Long sessionId = world.sessions().get(move.session());
                Timestamp start = jdbc.queryForObject("SELECT session_time_start FROM session WHERE id = ?",
                        Timestamp.class, sessionId);
                Map<String, Object> updates = new HashMap<>();
                updates.put("sessionTimeStart", Date.from(start.toLocalDateTime().plusDays(move.shift())
                        .atZone(ZoneId.systemDefault()).toInstant()));
                sessionService.updateSession(sessionId, updates);
                yield "séance déplacée acceptée";
            }
        };
    }

    private static String name(Step step) {
        return switch (step) {
            case Sheet ignored -> "feuille";
            case Single ignored -> "présence";
            case Arrival ignored -> "arrivée";
            case Departure ignored -> "départ";
            case Reopen ignored -> "réouverture";
            case Move ignored -> "séance déplacée";
        };
    }

    private static String submitSheet(World world, Sheet sheet) {
        List<AttendanceEntity> lines = new ArrayList<>();
        for (int s = 0; s < STUDENTS; s++) {
            AttendanceEntity line = line(world, sheet.session(), s, sheet.marks().get(s));
            if (line != null) {
                lines.add(line);
            }
        }
        if (lines.isEmpty()) {
            return "feuille sans objet";
        }
        attendanceService.saveAll(lines);
        jdbc.update("UPDATE session SET is_finished = TRUE WHERE id = ?", world.sessions().get(sheet.session()));
        return "feuille acceptée";
    }

    /** Ligne de présence à soumettre, ou {@code null} sans marque ou si l'étudiant est déjà pointé. */
    private static AttendanceEntity line(World world, int session, int student, Mark mark) {
        Long sessionId = world.sessions().get(session);
        Long studentId = world.students().get(student);
        if (mark == Mark.NONE || jdbc.queryForObject("SELECT COUNT(*) FROM attendance WHERE active = TRUE "
                + "AND session_id = ? AND student_id = ?", Long.class, sessionId, studentId) > 0) {
            return null;
        }
        SessionEntity entity = sessionRepository.findById(sessionId).orElseThrow();
        return AttendanceEntity.builder()
                .student(studentRepository.findById(studentId).orElseThrow())
                .session(entity)
                .sessionSeries(entity.getSessionSeries())
                .group(groupRepository.findById(world.groupId()).orElseThrow())
                .isPresent(mark == Mark.UNSET ? null : mark == Mark.PRESENT)
                .isJustified(false)
                .isCatchUp(false)
                .build();
    }

    /** P6 : une présence posée par le chemin ordinaire, ignorée si elle est refusée. */
    private static void markQuietly(World world, Single presence) {
        AttendanceEntity line = line(world, presence.session(), presence.student(), presence.mark());
        if (line == null) {
            return;
        }
        try {
            attendanceService.createAttendance(line);
        } catch (CustomServiceException refused) {
            // Absence hors fenêtre : refusée, comme elle le doit.
        }
    }

    /**
     * P6 : arrivée repoussée, ou départ avancé — enregistré une semaine après la dernière séance pour
     * une inscription ouverte —, de {@code shift} jours.
     */
    private static void shrink(World world, Shrink shrink) {
        Long enrolment = enrolment(world, shrink.student(), shrink.pick());
        Map<String, Object> row = jdbc.queryForMap("SELECT date_assigned, date_left FROM student_groups WHERE id = ?",
                enrolment);
        LocalDate arrival = ((Timestamp) row.get("DATE_ASSIGNED")).toLocalDateTime().toLocalDate();
        Timestamp left = (Timestamp) row.get("DATE_LEFT");
        if (shrink.arrival()) {
            confirm((mode, token) -> corrections.correctArrival(enrolment, arrival.plusDays(shrink.shift()), Map.of(),
                    ARRIVAL, mode, token));
        } else {
            LocalDate departure = left == null ? FIRST_MONDAY.plusWeeks(SERIES * SESSIONS_PER_SERIES)
                    : left.toLocalDateTime().toLocalDate();
            confirm((mode, token) -> corrections.setDeparture(enrolment, departure.minusDays(shrink.shift()), false,
                    Map.of(), LEFT, mode, token));
        }
    }

    /** Inscription choisie par rang parmi celles de l'étudiant. */
    private static Long enrolment(World world, int student, int pick) {
        List<Long> enrolments = jdbc.queryForList("SELECT id FROM student_groups WHERE student_id = ? ORDER BY id",
                Long.class, world.students().get(student));
        return enrolments.get(pick % enrolments.size());
    }

    /** L'Aperçu, puis sa confirmation avec le jeton qu'il a rendu : rien ne change entre les deux. */
    private static void confirm(BiFunction<CorrectionMode, String, CorrectionOutcome<?>> correction) {
        String token = correction.apply(CorrectionMode.PREVIEW, null).previewToken();
        correction.apply(CorrectionMode.CONFIRM, token);
    }

    // ------------------------------------------------------------------
    // États relus en SQL
    // ------------------------------------------------------------------

    /** Ce qu'une opération refusée ne doit pas toucher. */
    private record Snapshot(List<Map<String, Object>> attendances, List<Map<String, Object>> enrolments,
                            List<Map<String, Object>> sessions, List<Map<String, Object>> lines, Long traces) {
    }

    private static Snapshot snapshot() {
        return new Snapshot(
                jdbc.queryForList("SELECT id, active, status FROM attendance ORDER BY id"),
                jdbc.queryForList("SELECT id, active, date_assigned, date_left FROM student_groups ORDER BY id"),
                jdbc.queryForList("SELECT id, session_time_start, is_finished FROM session ORDER BY id"),
                jdbc.queryForList("SELECT id, active, amount_paid FROM payment_detail ORDER BY id"),
                jdbc.queryForObject("SELECT COUNT(*) FROM correction_audit", Long.class));
    }

    /** L'argent compté : rien de ceci ne bouge quand la ventilation se déplace. */
    private record Money(List<Map<String, Object>> encashments, List<Map<String, Object>> allocations,
                         List<Map<String, Object>> carryOvers, List<Map<String, Object>> payments) {
    }

    private static Money money() {
        return new Money(
                jdbc.queryForList("SELECT id, status, amount_received FROM encashment ORDER BY id"),
                jdbc.queryForList("SELECT id, active, amount, series_id FROM encashment_allocation ORDER BY id"),
                jdbc.queryForList("SELECT id, active, amount FROM payment_carry_over ORDER BY id"),
                jdbc.queryForList("SELECT id, amount_paid FROM payments ORDER BY id"));
    }

    private static Map<String, BigDecimal> paid(World world) {
        Map<String, BigDecimal> paid = new TreeMap<>();
        for (Long studentId : world.students()) {
            for (Long seriesId : world.series()) {
                paid.put(studentId + "/" + seriesId,
                        costResolver.resolve(studentId, seriesId).amountPaid().setScale(2, RoundingMode.HALF_UP));
            }
        }
        return paid;
    }

    private static Map<Long, BigDecimal> linesPerAllocation() {
        Map<Long, BigDecimal> lines = new TreeMap<>();
        for (Map<String, Object> row : jdbc.queryForList("SELECT encashment_allocation_id, SUM(amount_paid) AS total "
                + "FROM payment_detail WHERE active = TRUE GROUP BY encashment_allocation_id")) {
            lines.put(((Number) row.get("ENCASHMENT_ALLOCATION_ID")).longValue(), money(row.get("TOTAL")));
        }
        return lines;
    }

    /** Fenêtres de chaque étudiant, [arrivée, départ] ; départ {@code null} si ouverte. */
    private static Map<Long, List<LocalDate[]>> windows() {
        Map<Long, List<LocalDate[]>> windows = new HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList("SELECT student_id, date_assigned, date_left FROM student_groups")) {
            Timestamp arrival = (Timestamp) row.get("DATE_ASSIGNED");
            Timestamp left = (Timestamp) row.get("DATE_LEFT");
            windows.computeIfAbsent(((Number) row.get("STUDENT_ID")).longValue(), id -> new ArrayList<>())
                    .add(new LocalDate[] { arrival == null ? null : arrival.toLocalDateTime().toLocalDate(),
                            left == null ? null : left.toLocalDateTime().toLocalDate() });
        }
        return windows;
    }

    private static boolean inWindow(List<LocalDate[]> windows, LocalDate day) {
        return windows.stream().anyMatch(w -> w[0] != null && !day.isBefore(w[0]) && (w[1] == null || !day.isAfter(w[1])));
    }

    private static BigDecimal money(Object value) {
        BigDecimal amount = value instanceof BigDecimal decimal ? decimal
                : BigDecimal.valueOf(((Number) value).doubleValue());
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    // Données de l'essai
    // ------------------------------------------------------------------

    private World persist(List<Plan> plans) {
        for (String table : List.of("correction_audit", "payment_idempotency", "payment_carry_over", "payment_detail",
                "encashment_allocation", "encashment", "receipt_counter", "payments", "attendance", "session",
                "session_series", "student_groups", "groups", "student", "price", "school_year")) {
            jdbc.update("DELETE FROM " + table);
        }
        SchoolYearEntity year = schoolYearRepository.save(SchoolYearEntity.builder().label("2029-2030")
                .startDate(at(LocalDate.of(2029, 9, 1))).endDate(at(LocalDate.of(2030, 6, 30))).isCurrent(true).build());
        PricingEntity price = pricingRepository.save(PricingEntity.builder().price(PRICE_PER_SESSION).build());
        GroupEntity group = groupRepository.save(GroupEntity.builder().name("Math 1ère A").price(price)
                .schoolYear(year).sessionNumberPerSerie(SESSIONS_PER_SERIES).build());

        List<Long> series = new ArrayList<>();
        List<Long> sessions = new ArrayList<>();
        int week = 0;
        for (int s = 0; s < SERIES; s++) {
            SessionSeriesEntity entity = seriesRepository.save(SessionSeriesEntity.builder().name("Série " + (s + 1))
                    .group(group).totalSessions(SESSIONS_PER_SERIES).serieTimeStart(at(FIRST_MONDAY.plusWeeks(week)))
                    .build());
            for (int k = 0; k < SESSIONS_PER_SERIES; k++) {
                sessions.add(sessionRepository.save(SessionEntity.builder().title("Séance " + (k + 1)).group(group)
                        .sessionSeries(entity).sessionTimeStart(at(FIRST_MONDAY.plusWeeks(week++))).build()).getId());
            }
            series.add(entity.getId());
        }

        List<Long> students = new ArrayList<>();
        for (int i = 0; i < STUDENTS; i++) {
            StudentEntity student = studentRepository.save(StudentEntity.builder()
                    .firstName("Élève " + (i + 1)).lastName("Test").build());
            Plan plan = plans.get(i);
            LocalDate arrival = day(plan.arrival());
            LocalDate departure = plan.stay() == null ? null : arrival.plusDays(plan.stay());
            enrol(student, group, arrival, departure);
            if (departure != null && plan.gap() != null) {
                LocalDate back = departure.plusDays(plan.gap());
                enrol(student, group, back, plan.secondStay() == null ? null : back.plusDays(plan.secondStay()));
            }
            students.add(student.getId());
        }
        return new World(group.getId(), series, sessions, students);
    }

    private static void enrol(StudentEntity student, GroupEntity group, LocalDate arrival, LocalDate departure) {
        StudentGroupEntity enrolment = studentGroupRepository.save(StudentGroupEntity.builder().student(student)
                .group(group).dateAssigned(at(arrival)).build());
        if (departure != null) {
            enrolment.setActive(false);
            enrolment.setDateLeft(at(departure));
            studentGroupRepository.save(enrolment);
        }
    }

    private static LocalDate day(int offset) {
        return FIRST_MONDAY.plusDays(offset);
    }

    /** Séances à 10:00 ; dates d'inscription ramenées au jour par l'entité. */
    private static Date at(LocalDate day) {
        return Date.from(day.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant());
    }

    // ------------------------------------------------------------------
    // Contexte
    // ------------------------------------------------------------------

    @Configuration
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class,
            TransactionAutoConfiguration.class
    })
    @EntityScan("com.school.management.persistance")
    @EnableJpaRepositories("com.school.management.repository")
    @Import({ BillableSessionsResolverImpl.class, CatchUpBillingQualifierImpl.class, DiscountService.class,
            PaymentCostResolver.class, PaymentQuoteService.class, PaymentAllocationService.class,
            PaymentDistributionService.class, PaymentCarryOverService.class, PaymentIdempotencyService.class,
            EncashmentService.class, ReceiptNumberService.class, PaymentProcessingService.class,
            EncashmentQueryService.class, RefundService.class, RefundNumberService.class,
            CorrectionAuditService.class, CorrectionRunner.class, EnrolmentCorrectionService.class,
            VentilationMover.class, SeriesSettlement.class, AbsenceWindowGuard.class, RollCallService.class,
            CatchUpRoutingService.class })
    static class WindowTestContext {

        @Bean
        AuditorAware<String> auditorAware() {
            return () -> Optional.of("admin-test");
        }

        /** L'année scolaire n'est pas l'objet de ces propriétés : la garde laisse tout passer. */
        @Bean
        ReadOnlyYearGuard readOnlyYearGuard() {
            return Mockito.mock(ReadOnlyYearGuard.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        /** Le mapper ne sert qu'aux requêtes HTTP : les étapes passent des entités. */
        @Bean
        AttendanceService attendanceService(AttendanceRepository attendances, StudentRepository students,
                                            SessionRepository sessions, SessionSeriesRepository series,
                                            GroupRepository groups, StudentGroupRepository enrolments,
                                            CatchUpRoutingService routing, AbsenceWindowGuard guard,
                                            ReadOnlyYearGuard yearGuard) {
            return new AttendanceService(attendances, (AttendanceMapper) null, students, sessions, series, groups,
                    enrolments, routing, guard, yearGuard);
        }

        /** Seul le déplacement d'une séance est exercé : le reste du service est simulé. */
        @Bean
        SessionService sessionService(SessionRepository sessions, GroupRepository groups, RoomRepository rooms,
                                      TeacherRepository teachers, SessionSeriesRepository series,
                                      AttendanceService attendanceService, ReadOnlyYearGuard guard) {
            return new SessionService(sessions, groups, Mockito.mock(SessionMapper.class), rooms, teachers, series,
                    Mockito.mock(PaymentDetailDeactivationService.class), attendanceService, guard,
                    Mockito.mock(SeriesRolloverService.class));
        }
    }
}
