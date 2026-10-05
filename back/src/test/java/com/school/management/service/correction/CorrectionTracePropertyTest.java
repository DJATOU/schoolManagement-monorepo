package com.school.management.service.correction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.CatchUpBillingState;
import com.school.management.persistance.CatchUpRequestEntity;
import com.school.management.persistance.CatchUpStatus;
import com.school.management.persistance.CorrectionReasonType;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PricingEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.CatchUpRequestRepository;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.PricingRepository;
import com.school.management.repository.SchoolYearRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.repository.StudentGroupRepository;
import com.school.management.repository.StudentRepository;
import com.school.management.service.CatchUpRoutingService;
import com.school.management.service.DiscountService;
import com.school.management.service.ReadOnlyYearGuard;
import com.school.management.service.RefundNumberService;
import com.school.management.service.RefundService;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.payment.BillableSessionsResolverImpl;
import com.school.management.service.payment.CatchUpBillingQualifierImpl;
import com.school.management.service.payment.EncashmentQueryService;
import com.school.management.service.payment.EncashmentService;
import com.school.management.service.payment.PaymentAllocationService;
import com.school.management.service.payment.PaymentCarryOverService;
import com.school.management.service.payment.PaymentCostResolver;
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
import net.jqwik.api.lifecycle.AfterTry;
import net.jqwik.api.lifecycle.BeforeContainer;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Propriété P7 — une Trace par changement effectif (spec admin-corrections, D.6 ; exigences 11.3 à
 * 11.5).
 *
 * <p>Quelle que soit la suite de tentatives — annuler ou corriger un versement, corriger une arrivée,
 * un départ, rouvrir, changer, ajouter ou retirer une présence, dévalider une séance —, chacune en
 * Aperçu, confirmée, confirmée avec un jeton périmé ou sans jeton, avec un Motif juste ou non :</p>
 *
 * <ul>
 *   <li><b>refus ou Aperçu</b> : aucune Trace, et rien d'autre n'a bougé (11.5) ;</li>
 *   <li><b>confirmée</b> : au moins une Trace, et une seule par objet changé. Les objets sont relus en
 *       SQL, indépendamment du code testé : chaque versement, inscription, présence ou séance dont
 *       l'état a changé a exactement une Trace qui le nomme, une présence créée aussi ; un versement de
 *       remplacement n'a la sienne que s'il change d'élève — le Journal du nouvel élève doit le
 *       montrer. Aucune Trace ne nomme un objet resté tel quel : une correction sans changement
 *       effectif est refusée (11.3) ;</li>
 *   <li>chaque Trace est attribuée à l'administrateur authentifié pour cette tentative, porte son
 *       Motif, une phrase, des valeurs avant et après différentes, et un rang supérieur à toute Trace
 *       antérieure (11.4).</li>
 * </ul>
 *
 * <p>Ce qui suit un changement sans en être un objet — ventilation déplacée, statut recalculé,
 * demande de rattrapage annulée, compteur de reçus — est dit par la Trace de la correction, pas par
 * une Trace à lui.</p>
 *
 * <p>Même harnais que les autres propriétés : un contexte ciblé sur H2, amorcé une fois, base vidée à
 * chaque essai. La garde d'année laisse tout passer : les années closes ne sont pas l'objet de P7.</p>
 */
class CorrectionTracePropertyTest {

    private static final double PRICE_PER_SESSION = 2000.0;
    private static final int SERIES = 2;
    private static final int SESSIONS_PER_SERIES = 3;
    private static final int SESSIONS = SERIES * SESSIONS_PER_SERIES;
    private static final LocalDate FIRST_MONDAY = LocalDate.of(2030, 1, 7);
    private static final List<String> USERS = List.of("directrice", "secretaire");
    /** Jeton bien formé, qu'aucun Aperçu ne rend : la confirmation est périmée. */
    private static final String STALE_TOKEN = "0".repeat(64);

    private static ConfigurableApplicationContext context;
    private static JdbcTemplate jdbc;
    private static EncashmentCorrectionService encashmentCorrections;
    private static EnrolmentCorrectionService enrolmentCorrections;
    private static AttendanceCorrectionService attendanceCorrections;
    private static PaymentProcessingService processing;
    private static SchoolYearRepository schoolYearRepository;
    private static PricingRepository pricingRepository;
    private static GroupRepository groupRepository;
    private static SessionSeriesRepository seriesRepository;
    private static SessionRepository sessionRepository;
    private static StudentRepository studentRepository;
    private static StudentGroupRepository studentGroupRepository;
    private static AttendanceRepository attendanceRepository;
    private static CatchUpRequestRepository catchUpRequestRepository;

    @BeforeContainer
    static void startContext() {
        context = new SpringApplicationBuilder(TraceTestContext.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.datasource.url=jdbc:h2:mem:correction-trace-properties;DB_CLOSE_DELAY=-1",
                        "--spring.datasource.driverClassName=org.h2.Driver",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                        "--spring.jpa.hibernate.ddl-auto=create-drop",
                        "--spring.jpa.show-sql=false",
                        "--spring.main.banner-mode=off",
                        "--logging.level.com.school.management=WARN");
        jdbc = context.getBean(JdbcTemplate.class);
        encashmentCorrections = context.getBean(EncashmentCorrectionService.class);
        enrolmentCorrections = context.getBean(EnrolmentCorrectionService.class);
        attendanceCorrections = context.getBean(AttendanceCorrectionService.class);
        processing = context.getBean(PaymentProcessingService.class);
        schoolYearRepository = context.getBean(SchoolYearRepository.class);
        pricingRepository = context.getBean(PricingRepository.class);
        groupRepository = context.getBean(GroupRepository.class);
        seriesRepository = context.getBean(SessionSeriesRepository.class);
        sessionRepository = context.getBean(SessionRepository.class);
        studentRepository = context.getBean(StudentRepository.class);
        studentGroupRepository = context.getBean(StudentGroupRepository.class);
        attendanceRepository = context.getBean(AttendanceRepository.class);
        catchUpRequestRepository = context.getBean(CatchUpRequestRepository.class);
    }

    @AfterContainer
    static void stopContext() {
        SecurityContextHolder.clearContext();
        if (context != null) {
            context.close();
        }
    }

    @AfterTry
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------
    // Scénarios
    // ------------------------------------------------------------------

    enum Kind { CANCEL, CORRECT, ARRIVAL, DEPARTURE, REOPEN, CHANGE, ADD, REMOVE, UNVALIDATE }

    /** Aperçu seul ; Aperçu puis confirmation de son jeton ; confirmation au jeton périmé ; sans jeton. */
    enum Mode { PREVIEW, CONFIRM, STALE, NO_TOKEN }

    /** Ligne de présence posée avant les corrections. */
    enum Mark { NONE, PRESENT, ABSENT, JUSTIFIED }

    /**
     * Une tentative de correction. {@code pick} désigne la cible parmi celles du moment, {@code value}
     * l'état voulu ; {@code validReason} faux prend un Motif sans rapport, refusé.
     */
    record Attempt(Kind kind, int pick, int value, boolean flag, Mode mode, boolean validReason, int user) {
    }

    record Payment(int student, int series, int units) {
    }

    /**
     * État de départ : séances validées, lignes de présence des deux inscrits, départ éventuel du
     * second (rang de la séance, son jour compris), versements ; puis les tentatives.
     */
    record Scenario(List<Boolean> validated, List<Mark> marks, List<Integer> arrivals, Integer departure,
                    List<Payment> payments, List<Attempt> attempts) {
    }

    /** Arrivées possibles : avant toute séance, ou entre deux, pour qu'une correction en fasse entrer. */
    private static final List<LocalDate> ARRIVALS = List.of(LocalDate.of(2029, 9, 1), FIRST_MONDAY.plusDays(3),
            FIRST_MONDAY.plusWeeks(1).plusDays(3), FIRST_MONDAY.plusWeeks(3).minusDays(2));

    @Provide
    Arbitrary<Scenario> scenarios() {
        Arbitrary<Mark> mark = Arbitraries.frequency(Tuple.of(2, Mark.NONE), Tuple.of(3, Mark.PRESENT),
                Tuple.of(2, Mark.ABSENT), Tuple.of(1, Mark.JUSTIFIED));
        Arbitrary<Payment> payment = Combinators.combine(Arbitraries.integers().between(0, 1),
                Arbitraries.integers().between(0, SERIES - 1), Arbitraries.integers().between(1, 4)).as(Payment::new);
        Arbitrary<Attempt> attempt = Combinators.combine(
                Arbitraries.of(Kind.values()),
                Arbitraries.integers().between(0, 11),
                Arbitraries.integers().between(0, 31),
                Arbitraries.of(true, false),
                Arbitraries.frequency(Tuple.of(2, Mode.PREVIEW), Tuple.of(6, Mode.CONFIRM), Tuple.of(1, Mode.STALE),
                        Tuple.of(1, Mode.NO_TOKEN)),
                Arbitraries.frequency(Tuple.of(6, true), Tuple.of(1, false)),
                Arbitraries.integers().between(0, USERS.size() - 1)).as(Attempt::new);
        return Combinators.combine(
                Arbitraries.frequency(Tuple.of(2, true), Tuple.of(1, false)).list().ofSize(SESSIONS),
                mark.list().ofSize(SESSIONS * 2),
                Arbitraries.frequency(Tuple.of(2, 0), Tuple.of(1, 1), Tuple.of(1, 2), Tuple.of(1, 3)).list().ofSize(2),
                Arbitraries.integers().between(0, SESSIONS - 1).injectNull(0.5),
                payment.list().ofMaxSize(3),
                attempt.list().ofMinSize(3).ofMaxSize(10)).as(Scenario::new);
    }

    // ------------------------------------------------------------------
    // P7
    // ------------------------------------------------------------------

    // 200 essais, seuils bas : la couverture doit dire qu'un cas n'est jamais tiré, pas échouer au hasard.
    @Property(tries = 200)
    void oneTracePerEffectiveChange(@ForAll("scenarios") Scenario scenario) {
        World world = persist(scenario);

        for (Attempt attempt : scenario.attempts()) {
            String user = USERS.get(attempt.user());
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    user, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            CorrectionReason reason = reason(attempt);
            BiFunction<CorrectionMode, String, CorrectionOutcome<?>> correction = correction(world, attempt, reason);
            if (correction == null) {
                Statistics.label("P7").collect(attempt.kind() + " sans cible");
                continue;
            }
            State before = state();
            long lastRank = lastRank();

            String outcome;
            try {
                outcome = run(attempt, correction);
            } catch (CustomServiceException refused) {
                outcome = noChange(refused) ? "sans changement, refusée" : "refusée";
            }
            Statistics.label("P7").collect(attempt.kind() + " " + outcome);
            Statistics.label("P7 issue").collect(outcome);

            State after = state();
            List<Map<String, Object>> traces = jdbc.queryForList(
                    "SELECT * FROM correction_audit WHERE id > ? ORDER BY id", lastRank);
            if (!outcome.equals("confirmée")) {
                assertThat(traces).as("%s %s : aucune Trace", attempt, outcome).isEmpty();
                assertThat(after).as("%s %s : rien n'a bougé", attempt, outcome).isEqualTo(before);
                continue;
            }
            assertTracesMatchChanges(attempt, reason, user, before, after, traces);
            if (attempt.kind() == Kind.ARRIVAL || attempt.kind() == Kind.DEPARTURE || attempt.kind() == Kind.REOPEN) {
                boolean noted = traces.stream().anyMatch(trace -> "ATTENDANCE_ADDED".equals(trace.get("ACTION")));
                Statistics.label("P7 dates").collect(noted ? "présence notée" : "sans présence notée");
            }
        }

        Statistics.label("P7 issue").coverage(c -> {
            c.check("confirmée").percentage(p -> p >= 10);
            c.check("aperçu").percentage(p -> p >= 5);
            c.check("refusée").percentage(p -> p >= 5);
            c.check("sans changement, refusée").percentage(p -> p >= 3);
            c.check("périmée").percentage(p -> p >= 1);
            c.check("sans jeton").percentage(p -> p >= 1);
        });
        Statistics.label("P7").coverage(c -> {
            for (Kind kind : Kind.values()) {
                c.check(kind + " confirmée").percentage(p -> p >= 0.3);
            }
            c.check("CORRECT sans changement, refusée").percentage(p -> p >= 0.3);
        });
        // Une correction de dates qui fait entrer une séance où l'élève est noté : la présence créée a sa Trace.
        Statistics.label("P7 dates").coverage(c -> c.check("présence notée").percentage(p -> p >= 3));
    }

    /** Exécute la tentative selon son mode ; rend son issue, ou laisse passer le refus. */
    private static String run(Attempt attempt, BiFunction<CorrectionMode, String, CorrectionOutcome<?>> correction) {
        switch (attempt.mode()) {
            case PREVIEW -> {
                correction.apply(CorrectionMode.PREVIEW, null);
                return "aperçu";
            }
            case STALE -> {
                // Un refus de la correction elle-même passe avant le jeton : il remonte comme tel.
                try {
                    correction.apply(CorrectionMode.CONFIRM, STALE_TOKEN);
                } catch (StalePreviewException stale) {
                    return "périmée";
                }
                return fail("%s : un jeton qu'aucun Aperçu n'a rendu a été accepté", attempt);
            }
            case NO_TOKEN -> {
                try {
                    correction.apply(CorrectionMode.CONFIRM, null);
                } catch (CustomServiceException refused) {
                    if (refused.getMessage().startsWith("Confirmation sans aperçu")) {
                        return "sans jeton";
                    }
                    throw refused;
                }
                return fail("%s : confirmation sans jeton acceptée", attempt);
            }
            default -> {
                String token = correction.apply(CorrectionMode.PREVIEW, null).previewToken();
                try {
                    correction.apply(CorrectionMode.CONFIRM, token);
                } catch (CustomServiceException refused) {
                    return fail("%s : confirmation refusée juste après son Aperçu : %s", attempt, refused.getMessage());
                }
                return "confirmée";
            }
        }
    }

    /**
     * Une Trace par objet changé, aucune pour un objet resté tel quel, chacune bien formée et
     * attribuée.
     */
    private static void assertTracesMatchChanges(Attempt attempt, CorrectionReason reason, String user, State before,
                                                 State after, List<Map<String, Object>> traces) {
        assertThat(traces).as("%s confirmée : au moins une Trace", attempt).isNotEmpty();

        Map<String, Integer> traced = new TreeMap<>();
        for (Map<String, Object> trace : traces) {
            traced.merge(trace.get("DOMAIN") + "#" + trace.get("ENTITY_ID"), 1, Integer::sum);
            assertThat(trace.get("PERFORMED_BY")).as("%s : auteur de %s", attempt, trace).isEqualTo(user);
            assertThat(trace.get("REASON_TYPE")).as("%s : Motif de %s", attempt, trace).isEqualTo(reason.type().name());
            assertThat(trace.get("REASON_TEXT")).as("%s : texte du Motif", attempt).isEqualTo(reason.text());
            assertThat((String) trace.get("SUMMARY")).as("%s : phrase", attempt).isNotBlank();
            if (trace.get("OLD_VALUE") != null) {
                assertThat(trace.get("OLD_VALUE")).as("%s : avant et après identiques", attempt)
                        .isNotEqualTo(trace.get("NEW_VALUE"));
            }
        }
        assertThat(traced.values()).as("%s : un objet tracé deux fois %s", attempt, traced).allMatch(n -> n == 1);

        Set<String> expected = new TreeSet<>();
        Set<String> allowed = new TreeSet<>();
        changed("ENROLMENT", before.enrolments(), after.enrolments(), expected, allowed);
        changed("ATTENDANCE", before.attendances(), after.attendances(), expected, allowed);
        changed("SESSION", before.sessions(), after.sessions(), expected, allowed);
        for (Map.Entry<Long, Map<String, Object>> row : after.encashments().entrySet()) {
            Map<String, Object> was = before.encashments().get(row.getKey());
            String key = "ENCASHMENT#" + row.getKey();
            if (was == null) {
                // Remplacement : sa propre Trace seulement s'il change d'élève.
                Map<String, Object> original = after.encashments().get(asLong(row.getValue().get("REPLACES_ID")));
                assertThat(original).as("%s : versement créé sans en remplacer un", attempt).isNotNull();
                if (!Objects.equals(original.get("STUDENT_ID"), row.getValue().get("STUDENT_ID"))) {
                    expected.add(key);
                    allowed.add(key);
                }
            } else if (!was.equals(row.getValue())) {
                expected.add(key);
                allowed.add(key);
            }
        }

        assertThat(traced.keySet()).as("%s : objets changés sans Trace", attempt).containsAll(expected);
        assertThat(allowed).as("%s : Traces d'objets restés tels quels", attempt).containsAll(traced.keySet());
    }

    /** Les lignes changées ou créées d'une table : chacune attend sa Trace. */
    private static void changed(String domain, Map<Long, Map<String, Object>> before,
                                Map<Long, Map<String, Object>> after, Set<String> expected, Set<String> allowed) {
        for (Map.Entry<Long, Map<String, Object>> row : after.entrySet()) {
            if (!row.getValue().equals(before.get(row.getKey()))) {
                expected.add(domain + "#" + row.getKey());
                allowed.add(domain + "#" + row.getKey());
            }
        }
    }

    /** Refus d'une correction qui ne changerait rien (11.3). */
    private static boolean noChange(CustomServiceException refused) {
        String message = Objects.requireNonNullElse(refused.getMessage(), "");
        return message.contains("sans changement") || message.contains("rien à") || message.contains("déjà");
    }

    // ------------------------------------------------------------------
    // Tentatives
    // ------------------------------------------------------------------

    /** Le Motif : un de ceux de la correction, ou, si demandé, un Motif sans rapport. */
    private static CorrectionReason reason(Attempt attempt) {
        List<CorrectionReasonType> allowed = switch (attempt.kind()) {
            case CANCEL, CORRECT -> List.copyOf(EncashmentCorrectionService.ENCASHMENT_REASONS);
            case ARRIVAL -> EnrolmentCorrectionService.ARRIVAL_REASONS;
            case DEPARTURE -> EnrolmentCorrectionService.DEPARTURE_REASONS;
            case REOPEN -> EnrolmentCorrectionService.REOPEN_REASONS;
            case UNVALIDATE -> AttendanceCorrectionService.UNVALIDATION_REASONS;
            default -> AttendanceCorrectionService.REASONS;
        };
        List<CorrectionReasonType> pool = attempt.validReason() ? new ArrayList<>(allowed)
                : new ArrayList<>(List.of(CorrectionReasonType.values()).stream().filter(t -> !allowed.contains(t))
                        .toList());
        pool.sort(Enum::compareTo);
        CorrectionReasonType type = pool.get(attempt.value() % pool.size());
        return new CorrectionReason(type, type == CorrectionReasonType.OTHER ? "Saisie à reprendre " + attempt.value()
                : null);
    }

    /** La correction tentée, sur la cible du moment ; {@code null} s'il n'y en a aucune. */
    private static BiFunction<CorrectionMode, String, CorrectionOutcome<?>> correction(World world, Attempt attempt,
                                                                                         CorrectionReason reason) {
        int value = attempt.value();
        return switch (attempt.kind()) {
            case CANCEL -> {
                Long encashment = pick(ids("SELECT id FROM encashment ORDER BY id"), attempt.pick());
                yield encashment == null ? null
                        : (mode, token) -> encashmentCorrections.cancel(encashment, reason, mode, token);
            }
            case CORRECT -> {
                Long encashment = pick(ids("SELECT id FROM encashment ORDER BY id"), attempt.pick());
                if (encashment == null) {
                    yield null;
                }
                EncashmentChanges changes = changes(world, encashment, value);
                yield (mode, token) -> encashmentCorrections.correct(encashment, changes, reason, mode, token);
            }
            case ARRIVAL -> {
                Long enrolment = world.enrolments().get(attempt.pick() % 2);
                LocalDate day = day(enrolment, value, true);
                Map<Long, Boolean> marks = marks(world, attempt, enrolment, day, window(enrolment)[1]);
                yield (mode, token) -> enrolmentCorrections.correctArrival(enrolment, day, marks, reason, mode, token);
            }
            case DEPARTURE -> {
                Long enrolment = world.enrolments().get(attempt.pick() % 2);
                LocalDate day = day(enrolment, value, false);
                Map<Long, Boolean> marks = marks(world, attempt, enrolment, window(enrolment)[0], day);
                yield (mode, token) -> enrolmentCorrections.setDeparture(enrolment, day, attempt.flag(), marks, reason,
                        mode, token);
            }
            case REOPEN -> {
                Long enrolment = world.enrolments().get(attempt.pick() % 2);
                Map<Long, Boolean> marks = marks(world, attempt, enrolment, window(enrolment)[0], null);
                yield (mode, token) -> enrolmentCorrections.reopen(enrolment, marks, reason, mode, token);
            }
            case CHANGE -> {
                Long line = pick(ids("SELECT id FROM attendance WHERE active = TRUE ORDER BY id"), attempt.pick());
                boolean present = value % 3 == 0;
                Boolean justified = value % 3 == 2;
                yield line == null ? null : (mode, token) -> attendanceCorrections.changePresence(line, present,
                        justified, reason, mode, token);
            }
            case ADD -> {
                // Une fois sur deux, une ligne qui manque vraiment : séance validée, inscrit sans ligne.
                // Sinon n'importe quel couple, le venu d'ailleurs compris : le plus souvent refusé.
                List<Long[]> missing = new ArrayList<>();
                for (Map<String, Object> row : jdbc.queryForList("SELECT s.id AS session_id, sg.student_id "
                        + "FROM session s CROSS JOIN student_groups sg WHERE s.is_finished = TRUE AND NOT EXISTS ("
                        + "SELECT 1 FROM attendance a WHERE a.session_id = s.id AND a.student_id = sg.student_id "
                        + "AND a.active = TRUE) ORDER BY s.id, sg.student_id")) {
                    missing.add(new Long[] { asLong(row.get("SESSION_ID")), asLong(row.get("STUDENT_ID")) });
                }
                Long[] target = attempt.flag() && !missing.isEmpty() ? missing.get(attempt.pick() % missing.size())
                        : new Long[] { world.sessions().get(attempt.pick() % SESSIONS),
                                world.students().get(value % world.students().size()) };
                boolean present = (value / 3) % 3 == 0;
                Boolean justified = (value / 3) % 3 == 2;
                yield (mode, token) -> attendanceCorrections.add(target[0], target[1], present, justified, reason, mode,
                        token);
            }
            case REMOVE -> {
                Long line = pick(ids("SELECT id FROM attendance WHERE active = TRUE ORDER BY id"), attempt.pick());
                yield line == null ? null : (mode, token) -> attendanceCorrections.remove(line, reason, mode, token);
            }
            case UNVALIDATE -> {
                Long session = world.sessions().get(attempt.pick() % SESSIONS);
                yield (mode, token) -> attendanceCorrections.unvalidate(session, reason, mode, token);
            }
        };
    }

    /**
     * L'état voulu d'un versement, champ par champ selon les bits de {@code value} : montant, élève,
     * série, mode, note. Aucun bit : rien ne change, la correction doit être refusée.
     */
    private static EncashmentChanges changes(World world, Long encashment, int pick) {
        // Une fois sur huit, rien ne change : la correction doit être refusée, sans Trace (11.3).
        int value = pick % 8 == 0 ? 0 : pick;
        Map<String, Object> row = jdbc.queryForMap("SELECT amount_received, student_id, group_id, target_series_id, "
                + "payment_method, notes FROM encashment WHERE id = ?", encashment);
        BigDecimal amount = (BigDecimal) row.get("AMOUNT_RECEIVED");
        Long student = asLong(row.get("STUDENT_ID"));
        Long series = asLong(row.get("TARGET_SERIES_ID"));
        String method = (String) row.get("PAYMENT_METHOD");
        String notes = (String) row.get("NOTES");
        if ((value & 1) != 0) {
            amount = amount.compareTo(BigDecimal.valueOf(500)) > 0 ? amount.subtract(BigDecimal.valueOf(500))
                    : amount.add(BigDecimal.valueOf(500));
        }
        if ((value & 2) != 0) {
            student = world.students().get(0).equals(student) ? world.students().get(1) : world.students().get(0);
        }
        if ((value & 4) != 0) {
            series = world.series().get(0).equals(series) ? world.series().get(1) : world.series().get(0);
        }
        if ((value & 8) != 0) {
            method = "cash".equals(method) ? "cheque" : "cash";
        }
        if ((value & 16) != 0) {
            notes = notes == null ? "Versement du père" : null;
        }
        return new EncashmentChanges(amount, student, world.groupId(), series, method, notes);
    }

    /**
     * Le jour visé : l'actuel (rien à corriger), dans l'année, ou hors de l'année (refusé). Les jours
     * des séances y sont, pour faire entrer ou sortir une séance de la période.
     */
    private static LocalDate day(Long enrolment, int value, boolean arrival) {
        Map<String, Object> row = jdbc.queryForMap("SELECT date_assigned, date_left FROM student_groups WHERE id = ?",
                enrolment);
        Object current = row.get(arrival ? "DATE_ASSIGNED" : "DATE_LEFT");
        List<LocalDate> days = new ArrayList<>(List.of(LocalDate.of(2029, 9, 1), FIRST_MONDAY.minusDays(1),
                FIRST_MONDAY.plusDays(3), FIRST_MONDAY.plusWeeks(1), FIRST_MONDAY.plusWeeks(2).plusDays(1),
                FIRST_MONDAY.plusWeeks(4), LocalDate.of(2030, 7, 15)));
        if (current != null) {
            days.add(((java.sql.Timestamp) current).toLocalDateTime().toLocalDate());
        }
        return days.get(value % days.size());
    }

    /**
     * Présences à noter depuis l'Aperçu. Avec {@code flag}, une pour chaque séance validée que la
     * période voulue fait entrer sans ligne de l'élève — elles deviennent des présences créées, chacune
     * sa Trace. Sinon, de temps en temps, une séance quelconque, le plus souvent refusée.
     */
    private static Map<Long, Boolean> marks(World world, Attempt attempt, Long enrolment, LocalDate arrival,
                                            LocalDate departure) {
        if (!attempt.flag()) {
            return attempt.value() % 4 == 0 ? Map.of(world.sessions().get(attempt.pick() % SESSIONS), true) : Map.of();
        }
        LocalDate[] current = window(enrolment);
        Long student = jdbc.queryForObject("SELECT student_id FROM student_groups WHERE id = ?", Long.class, enrolment);
        Map<Long, Boolean> marks = new TreeMap<>();
        for (Map<String, Object> row : jdbc.queryForList("SELECT s.id, s.session_time_start FROM session s "
                + "WHERE s.is_finished = TRUE AND NOT EXISTS (SELECT 1 FROM attendance a WHERE a.session_id = s.id "
                + "AND a.student_id = ? AND a.active = TRUE) ORDER BY s.id", student)) {
            LocalDate day = ((java.sql.Timestamp) row.get("SESSION_TIME_START")).toLocalDateTime().toLocalDate();
            if (!within(day, current[0], current[1]) && within(day, arrival, departure)) {
                marks.put(asLong(row.get("ID")), attempt.value() % 2 == 0);
            }
        }
        return marks;
    }

    /** Arrivée et départ d'une inscription, au jour ; départ nul si elle est ouverte. */
    private static LocalDate[] window(Long enrolment) {
        Map<String, Object> row = jdbc.queryForMap("SELECT date_assigned, date_left FROM student_groups WHERE id = ?",
                enrolment);
        return new LocalDate[] { dayOf(row.get("DATE_ASSIGNED")), dayOf(row.get("DATE_LEFT")) };
    }

    private static boolean within(LocalDate day, LocalDate arrival, LocalDate departure) {
        return arrival != null && !day.isBefore(arrival) && (departure == null || !day.isAfter(departure));
    }

    private static LocalDate dayOf(Object timestamp) {
        return timestamp == null ? null : ((java.sql.Timestamp) timestamp).toLocalDateTime().toLocalDate();
    }

    private static List<Long> ids(String sql) {
        return jdbc.queryForList(sql, Long.class);
    }

    private static Long pick(List<Long> ids, int pick) {
        return ids.isEmpty() ? null : ids.get(pick % ids.size());
    }

    // ------------------------------------------------------------------
    // États relus en SQL
    // ------------------------------------------------------------------

    /**
     * Les objets d'une correction, colonne par colonne, et tout le reste, ligne entière : un refus ne
     * doit rien toucher, pas même la ventilation, les reports ou le compteur de reçus.
     */
    private record State(Map<Long, Map<String, Object>> encashments, Map<Long, Map<String, Object>> enrolments,
                         Map<Long, Map<String, Object>> attendances, Map<Long, Map<String, Object>> sessions,
                         Map<String, List<Map<String, Object>>> rest) {
    }

    private static State state() {
        Map<String, List<Map<String, Object>>> rest = new LinkedHashMap<>();
        for (String table : List.of("encashment", "encashment_allocation", "payments", "payment_detail",
                "payment_carry_over", "attendance", "student_groups", "session", "catch_up_request", "receipt_counter")) {
            rest.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"));
        }
        return new State(
                rows("SELECT id, status, amount_received, student_id, group_id, target_series_id, payment_method, notes, "
                        + "replaces_id FROM encashment"),
                rows("SELECT id, date_assigned, date_left, active FROM student_groups"),
                rows("SELECT id, active, status, is_justified FROM attendance"),
                rows("SELECT id, is_finished FROM session"),
                rest);
    }

    private static Map<Long, Map<String, Object>> rows(String sql) {
        Map<Long, Map<String, Object>> rows = new TreeMap<>();
        for (Map<String, Object> row : jdbc.queryForList(sql)) {
            rows.put(asLong(row.get("ID")), row);
        }
        return rows;
    }

    private static long lastRank() {
        return jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM correction_audit", Long.class);
    }

    private static Long asLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    // ------------------------------------------------------------------
    // Données de l'essai
    // ------------------------------------------------------------------

    /**
     * @param students  les deux inscrits, puis un élève d'un autre groupe, venu en rattrapage
     * @param enrolments une inscription par inscrit
     */
    private record World(Long groupId, List<Long> series, List<Long> sessions, List<Long> students,
                         List<Long> enrolments) {
    }

    private World persist(Scenario scenario) {
        for (String table : List.of("correction_audit", "catch_up_request", "payment_idempotency", "payment_carry_over",
                "payment_detail", "encashment_allocation", "encashment", "receipt_counter", "payments", "attendance",
                "session", "session_series", "student_groups", "groups", "student", "price", "school_year")) {
            jdbc.update("DELETE FROM " + table);
        }
        SchoolYearEntity year = schoolYearRepository.save(SchoolYearEntity.builder().label("2029-2030")
                .startDate(at(LocalDate.of(2029, 9, 1))).endDate(at(LocalDate.of(2030, 6, 30))).isCurrent(true).build());
        PricingEntity price = pricingRepository.save(PricingEntity.builder().price(PRICE_PER_SESSION).build());
        GroupEntity group = groupRepository.save(GroupEntity.builder().name("Math 1ère A").price(price)
                .schoolYear(year).sessionNumberPerSerie(SESSIONS_PER_SERIES).build());

        List<Long> series = new ArrayList<>();
        List<SessionEntity> sessions = new ArrayList<>();
        int week = 0;
        for (int s = 0; s < SERIES; s++) {
            SessionSeriesEntity entity = seriesRepository.save(SessionSeriesEntity.builder().name("Série " + (s + 1))
                    .group(group).totalSessions(SESSIONS_PER_SERIES).serieTimeStart(at(FIRST_MONDAY.plusWeeks(week)))
                    .build());
            for (int k = 0; k < SESSIONS_PER_SERIES; k++) {
                boolean validated = scenario.validated().get(sessions.size());
                sessions.add(sessionRepository.save(SessionEntity.builder().title("Séance " + (k + 1)).group(group)
                        .sessionSeries(entity).sessionTimeStart(at(FIRST_MONDAY.plusWeeks(week++)))
                        .isFinished(validated).build()));
            }
            series.add(entity.getId());
        }

        StudentEntity amine = student("Amine", "Belkacem");
        StudentEntity lina = student("Lina", "Haddad");
        StudentEntity sami = student("Sami", "Kaci");
        LocalDate[] arrivals = { ARRIVALS.get(scenario.arrivals().get(0)), ARRIVALS.get(scenario.arrivals().get(1)) };
        LocalDate departure = scenario.departure() == null ? null : FIRST_MONDAY.plusWeeks(scenario.departure());
        if (departure != null && departure.isBefore(arrivals[1])) {
            departure = null;
        }
        Long first = enrol(amine, group, arrivals[0], null);
        Long second = enrol(lina, group, arrivals[1], departure);

        // Lignes dans la période seulement : une absence hors période serait refusée à la saisie.
        for (int k = 0; k < SESSIONS; k++) {
            SessionEntity session = sessions.get(k);
            LocalDate day = FIRST_MONDAY.plusWeeks(k);
            if (!Boolean.TRUE.equals(session.getIsFinished())) {
                continue;
            }
            if (!day.isBefore(arrivals[0])) {
                line(amine, session, group, scenario.marks().get(2 * k));
            }
            if (!day.isBefore(arrivals[1]) && (departure == null || !day.isAfter(departure))) {
                line(lina, session, group, scenario.marks().get(2 * k + 1));
            }
        }
        // Sami vient d'ailleurs, sans place réservée nulle part : sa séance est facturée sur place.
        SessionEntity hosted = sessions.get(1);
        if (Boolean.TRUE.equals(hosted.getIsFinished())) {
            attendanceRepository.save(AttendanceEntity.builder().student(sami).session(hosted)
                    .sessionSeries(hosted.getSessionSeries()).group(group).isPresent(true).isJustified(false)
                    .isCatchUp(true).catchUpBillingState(CatchUpBillingState.HOST_BILLED).build());
            catchUpRequestRepository.save(CatchUpRequestEntity.builder().student(sami).catchUpSession(hosted)
                    .catchUpGroup(group).status(CatchUpStatus.COMPLETED).build());
        }

        List<Long> students = List.of(amine.getId(), lina.getId(), sami.getId());
        for (Payment payment : scenario.payments()) {
            try {
                processing.processPayment(students.get(payment.student()), group.getId(), series.get(payment.series()),
                        payment.units() * 1000.0);
            } catch (CustomServiceException refused) {
                // Au-delà de ce que le groupe peut recevoir : l'essai continue sans lui.
            }
        }
        return new World(group.getId(), series, sessions.stream().map(SessionEntity::getId).toList(), students,
                List.of(first, second));
    }

    private static StudentEntity student(String firstName, String lastName) {
        return studentRepository.save(StudentEntity.builder().firstName(firstName).lastName(lastName).build());
    }

    private static Long enrol(StudentEntity student, GroupEntity group, LocalDate arrival, LocalDate departure) {
        StudentGroupEntity enrolment = studentGroupRepository.save(StudentGroupEntity.builder().student(student)
                .group(group).dateAssigned(at(arrival)).build());
        if (departure != null) {
            enrolment.setActive(false);
            enrolment.setDateLeft(at(departure));
            enrolment = studentGroupRepository.save(enrolment);
        }
        return enrolment.getId();
    }

    private static void line(StudentEntity student, SessionEntity session, GroupEntity group, Mark mark) {
        if (mark == Mark.NONE) {
            return;
        }
        attendanceRepository.save(AttendanceEntity.builder().student(student).session(session)
                .sessionSeries(session.getSessionSeries()).group(group).isPresent(mark == Mark.PRESENT)
                .isJustified(mark == Mark.JUSTIFIED).isCatchUp(false).build());
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
            CorrectionAuditService.class, CorrectionRunner.class, EncashmentCorrectionService.class,
            EnrolmentCorrectionService.class, AttendanceCorrectionService.class, VentilationMover.class,
            SeriesSettlement.class, AbsenceWindowGuard.class, RollCallService.class, CatchUpRoutingService.class,
            com.school.management.service.payroll.PaidSeriesGuard.class })
    static class TraceTestContext {

        @Bean
        AuditorAware<String> auditorAware() {
            return () -> Optional.of("admin-test");
        }

        /** Les années closes ne sont pas l'objet de P7 : la garde laisse tout passer. */
        @Bean
        ReadOnlyYearGuard readOnlyYearGuard() {
            return Mockito.mock(ReadOnlyYearGuard.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
