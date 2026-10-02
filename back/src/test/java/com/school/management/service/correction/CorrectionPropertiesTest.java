package com.school.management.service.correction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.management.dto.RefundRequestDTO;
import com.school.management.persistance.CorrectionReasonType;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PricingEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.PricingRepository;
import com.school.management.repository.SessionRepository;
import com.school.management.repository.SessionSeriesRepository;
import com.school.management.repository.StudentGroupRepository;
import com.school.management.repository.StudentRepository;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Propriétés des corrections d'Encaissement (spec admin-corrections, B.6) : P2, P3 et P4.
 *
 * <ul>
 *   <li><b>P2a — corriger le dernier versement équivaut à l'avoir bien saisi.</b> Après un
 *       historique quelconque, encaisser A puis le remplacer par B donne exactement les montants,
 *       statuts, reports et ventilation d'un historique où B aurait été encaissé à la place de A ;
 *       et l'un est refusé si et seulement si l'autre l'est. (3.2, 3.4)</li>
 *   <li><b>P2b — corriger, c'est annuler puis encaisser.</b> Pour un versement A quelconque de
 *       l'historique, le remplacer par B donne le même état qu'annuler A puis encaisser B par le
 *       chemin ordinaire, quand les deux aboutissent.</li>
 *   <li><b>P3 — indivisibilité.</b> Toute correction refusée — à l'Aperçu, à la confirmation, ou
 *       parce que les données ont changé entre les deux — ne change rien : Encaissements,
 *       Imputations, ventilation, reports, cumuls, numéros de reçu, Traces. (3.5, 11.5)</li>
 *   <li><b>P4 — l'Aperçu ne ment pas.</b> Un Aperçu n'écrit rien ; confirmé aussitôt, il est
 *       accepté, et les montants de chaque Série valent alors exactement ses montants « après »,
 *       ceux des Séries qu'il ne liste pas restant inchangés. (4.1, 4.3)</li>
 * </ul>
 *
 * <p><b>Pourquoi P2 n'est pas énoncé pour un versement quelconque.</b> Un remplacement ne
 * recalcule pas la répartition des versements postérieurs à A : si A avait rempli Janvier et
 * qu'un versement suivant a été reporté sur Février à cause de lui, ce report reste. Seul A est
 * corrigé, et c'est voulu. L'équivalence avec « B saisi à la place de A » n'est donc exacte que pour
 * le dernier versement (P2a) ; pour les autres, la propriété exacte est P2b.</p>
 *
 * <p>Même harnais que {@code MoneyConservationPropertyTest} : un contexte ciblé sur H2, amorcé une
 * fois, base vidée à chaque essai ; états relus en SQL, comparés par rang d'élève, de Série et de
 * séance, jamais par identifiant.</p>
 */
class CorrectionPropertiesTest {

    private static final double PRICE_PER_SESSION = 2000.0;
    private static final int SERIES_COUNT = 3;
    private static final int STUDENT_COUNT = 2;
    private static final List<CorrectionReason> REASONS = List.of(
            CorrectionReason.of(CorrectionReasonType.DATA_ENTRY_ERROR),
            CorrectionReason.of(CorrectionReasonType.WRONG_STUDENT),
            CorrectionReason.of(CorrectionReasonType.WRONG_AMOUNT),
            new CorrectionReason(CorrectionReasonType.OTHER, "Erreur de guichet"));

    private static ConfigurableApplicationContext context;
    private static PaymentProcessingService processing;
    private static EncashmentCorrectionService corrections;
    private static RefundService refunds;
    private static PaymentCostResolver costResolver;
    private static JdbcTemplate jdbc;
    private static StudentRepository studentRepository;
    private static GroupRepository groupRepository;
    private static PricingRepository pricingRepository;
    private static SessionSeriesRepository seriesRepository;
    private static SessionRepository sessionRepository;
    private static StudentGroupRepository studentGroupRepository;

    @BeforeContainer
    static void startContext() {
        context = new SpringApplicationBuilder(CorrectionTestContext.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.datasource.url=jdbc:h2:mem:correction-properties;DB_CLOSE_DELAY=-1",
                        "--spring.datasource.driverClassName=org.h2.Driver",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                        "--spring.jpa.hibernate.ddl-auto=create-drop",
                        "--spring.jpa.show-sql=false",
                        "--spring.main.banner-mode=off",
                        "--logging.level.com.school.management=WARN");
        processing = context.getBean(PaymentProcessingService.class);
        corrections = context.getBean(EncashmentCorrectionService.class);
        refunds = context.getBean(RefundService.class);
        costResolver = context.getBean(PaymentCostResolver.class);
        jdbc = context.getBean(JdbcTemplate.class);
        studentRepository = context.getBean(StudentRepository.class);
        groupRepository = context.getBean(GroupRepository.class);
        pricingRepository = context.getBean(PricingRepository.class);
        seriesRepository = context.getBean(SessionSeriesRepository.class);
        sessionRepository = context.getBean(SessionRepository.class);
        studentGroupRepository = context.getBean(StudentGroupRepository.class);
    }

    @AfterContainer
    static void stopContext() {
        SecurityContextHolder.clearContext();
        if (context != null) {
            context.close();
        }
    }

    /** Chaque correction est tracée au nom de l'administrateur authentifié (exigence 11.4). */
    @BeforeTry
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin-test", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    // ------------------------------------------------------------------
    // Scénarios
    // ------------------------------------------------------------------

    sealed interface Step permits Pay, Refund {
    }

    /** Versement d'un élève sur une Série, multiple de 500 DA ; il peut être refusé. */
    record Pay(int student, int series, int amount) implements Step {
    }

    /** Remboursement sur la ligne d'un élève et d'une Série, s'il y en a une ; il peut être refusé. */
    record Refund(int student, int series, int amount) implements Step {
    }

    /** Nature de la correction générée. */
    enum Kind {
        /** Annulation. */
        CANCEL,
        /** Correction vers le montant, l'élève et la Série décrits : en général un Remplacement. */
        REPLACE,
        /** Mêmes montant, élève et Série : seuls le mode ou la note changent. */
        DETAILS
    }

    /**
     * Une correction de l'un des versements acceptés, choisi par son rang.
     *
     * @param method vrai pour changer aussi le mode de paiement
     * @param note   vrai pour changer aussi la note
     */
    record Correction(int pick, Kind kind, int amount, int student, int series, boolean method,
                      boolean note, int reason) {
    }

    /**
     * @param intervening versement survenu entre l'Aperçu et la confirmation, ou {@code null}
     */
    record Scenario(List<Integer> sessionsPerSeries, List<Step> history, Correction correction, Pay intervening) {
    }

    /** P2a : un historique, puis A, que l'on remplace par B — ou B saisi directement. */
    record LastScenario(List<Integer> sessionsPerSeries, List<Step> history, Pay typed, Pay intended, int reason) {
    }

    @Provide
    Arbitrary<Scenario> scenarios() {
        Arbitrary<Correction> correction = Combinators.combine(
                Arbitraries.integers().between(0, 20),
                Arbitraries.frequency(Tuple.of(2, Kind.CANCEL), Tuple.of(3, Kind.REPLACE), Tuple.of(1, Kind.DETAILS)),
                Arbitraries.integers().between(1, 14).map(units -> units * 500),
                Arbitraries.integers().between(0, STUDENT_COUNT - 1),
                Arbitraries.integers().between(0, SERIES_COUNT - 1),
                Arbitraries.of(true, false),
                Arbitraries.of(true, false),
                Arbitraries.integers().between(0, REASONS.size() - 1)).as(Correction::new);
        return Combinators.combine(sessionsPerSeries(), history(2, 8), correction, pay().injectNull(0.5))
                .as(Scenario::new);
    }

    @Provide
    Arbitrary<LastScenario> lastScenarios() {
        return Combinators.combine(sessionsPerSeries(), history(0, 6), pay(), pay(),
                        Arbitraries.integers().between(0, REASONS.size() - 1))
                .as(LastScenario::new);
    }

    /** Une Série sans séance n'est pas ouverte : présente, mais rare, pour que des versements aboutissent. */
    private static Arbitrary<List<Integer>> sessionsPerSeries() {
        return Arbitraries.frequency(Tuple.of(1, 0), Tuple.of(3, 1), Tuple.of(3, 2), Tuple.of(3, 3))
                .list().ofSize(SERIES_COUNT);
    }

    /** Versement de 500 à 5 000 DA : le plus souvent plaçable, parfois au-delà d'une Série, donc reporté. */
    private static Arbitrary<Pay> pay() {
        return Combinators.combine(
                        Arbitraries.integers().between(0, STUDENT_COUNT - 1),
                        Arbitraries.integers().between(0, SERIES_COUNT - 1),
                        Arbitraries.integers().between(1, 10).map(units -> units * 500))
                .as(Pay::new);
    }

    private static Arbitrary<List<Step>> history(int min, int max) {
        Arbitrary<Step> refund = Combinators.combine(
                        Arbitraries.integers().between(0, STUDENT_COUNT - 1),
                        Arbitraries.integers().between(0, SERIES_COUNT - 1),
                        Arbitraries.integers().between(1, 6).map(units -> units * 500))
                .as(Refund::new);
        return Arbitraries.frequencyOf(Tuple.of(2, pay().map(p -> (Step) p)), Tuple.of(1, refund))
                .list().ofMinSize(min).ofMaxSize(max);
    }

    // ------------------------------------------------------------------
    // P4 — l'Aperçu ne ment pas
    // ------------------------------------------------------------------

    @Property(tries = 100)
    void thePreviewNeverLies(@ForAll("scenarios") Scenario scenario) {
        Fixture fixture = persist(scenario.sessionsPerSeries());
        List<Accepted> accepted = apply(fixture, scenario.history());
        if (accepted.isEmpty()) {
            Statistics.label("P4").collect("sans versement");
            return;
        }
        Accepted target = accepted.get(scenario.correction().pick() % accepted.size());

        Ledger before = ledger();
        Map<String, AmountSnapshot> amountsBefore = amounts(fixture);
        CorrectionOutcome<?> preview;
        try {
            preview = run(fixture, target, scenario.correction(), CorrectionMode.PREVIEW, null);
        } catch (CustomServiceException refused) {
            assertThat(ledger()).as("refus à l'Aperçu : rien n'est écrit").isEqualTo(before);
            Statistics.label("P4").collect("refusée");
            return;
        }
        assertThat(ledger()).as("un Aperçu n'écrit rien").isEqualTo(before);

        CorrectionOutcome<?> confirmed = run(fixture, target, scenario.correction(), CorrectionMode.CONFIRM,
                preview.previewToken());
        assertThat(confirmed.preview()).as("la confirmation mesure ce que l'Aperçu annonçait")
                .isEqualTo(preview.preview());

        Map<String, AmountSnapshot> announcedBefore = new TreeMap<>(amountsBefore);
        Map<String, AmountSnapshot> announcedAfter = new TreeMap<>(amountsBefore);
        for (SeriesAmountChange change : preview.preview().series()) {
            String key = key(fixture, change.studentId(), change.seriesId());
            announcedBefore.put(key, change.before());
            announcedAfter.put(key, change.after());
        }
        assertThat(announcedBefore).as("les montants « avant » de l'Aperçu sont ceux d'avant").isEqualTo(amountsBefore);
        assertThat(amounts(fixture)).as("les montants après confirmation sont ceux annoncés").isEqualTo(announcedAfter);

        Statistics.label("P4").collect(scenario.correction().kind() + " confirmée");
        Statistics.label("P4 aucun montant").collect(preview.preview().amountsUnchanged());
        Statistics.label("P4").coverage(c -> {
            c.check("CANCEL confirmée").percentage(p -> p >= 10);
            c.check("REPLACE confirmée").percentage(p -> p >= 10);
            c.check("DETAILS confirmée").percentage(p -> p >= 5);
            c.check("refusée").percentage(p -> p >= 5);
        });
    }

    // ------------------------------------------------------------------
    // P3 — indivisibilité
    // ------------------------------------------------------------------

    @Property(tries = 200)
    void aRefusedCorrectionChangesNothing(@ForAll("scenarios") Scenario scenario) {
        Fixture fixture = persist(scenario.sessionsPerSeries());
        List<Accepted> accepted = apply(fixture, scenario.history());
        if (accepted.isEmpty()) {
            Statistics.label("P3").collect("sans versement");
            return;
        }
        Accepted target = accepted.get(scenario.correction().pick() % accepted.size());

        Ledger beforePreview = ledger();
        CorrectionOutcome<?> preview;
        try {
            preview = run(fixture, target, scenario.correction(), CorrectionMode.PREVIEW, null);
        } catch (CustomServiceException refused) {
            assertThat(ledger()).as("refus à l'Aperçu").isEqualTo(beforePreview);
            Statistics.label("P3").collect(refused instanceof RefundFloorException ? "plancher" : "refus à l'Aperçu");
            return;
        }
        // L'Aperçu que verrait l'administratrice au moment de confirmer : celui d'origine, ou un
        // nouveau si un versement est survenu entre-temps. Nul si la correction est devenue impossible.
        CorrectionOutcome<?> current = preview;
        if (scenario.intervening() != null) {
            // Versé par le même élève : ses Séries sont celles que l'Aperçu a mesurées.
            Pay intervening = scenario.intervening();
            payQuietly(fixture, new Pay(target.pay().student(), intervening.series(), intervening.amount()));
            try {
                current = run(fixture, target, scenario.correction(), CorrectionMode.PREVIEW, null);
            } catch (CustomServiceException impossible) {
                current = null;
            }
        }

        Ledger beforeConfirm = ledger();
        try {
            run(fixture, target, scenario.correction(), CorrectionMode.CONFIRM, preview.previewToken());
            assertThat(current).as("confirmée alors que la correction est devenue impossible").isNotNull();
            assertThat(current.previewToken()).as("confirmée alors que l'Aperçu a changé (4.3)")
                    .isEqualTo(preview.previewToken());
            Statistics.label("P3").collect("confirmée");
        } catch (CustomServiceException refused) {
            assertThat(ledger()).as("refus à la confirmation : rien n'est écrit").isEqualTo(beforeConfirm);
            if (refused instanceof StalePreviewException stale) {
                // Refusée parce que l'Aperçu a changé — et le refus porte exactement le nouvel Aperçu.
                assertThat(current).as("Aperçu dit périmé alors que la correction est impossible").isNotNull();
                assertThat(stale.getPreviewToken()).isEqualTo(current.previewToken())
                        .isNotEqualTo(preview.previewToken());
                assertThat(stale.getPreview()).isEqualTo(current.preview());
                Statistics.label("P3").collect("Aperçu périmé");
            } else {
                // Refusée pour une règle : seulement si la correction est devenue impossible entre-temps.
                assertThat(current).as("refus à la confirmation d'une correction encore possible : "
                        + refused.getMessage()).isNull();
                Statistics.label("P3").collect("refus à la confirmation");
            }
        }
        // Seuils bas mais non nuls : chaque sorte de refus doit être effectivement visitée.
        Statistics.label("P3").coverage(c -> {
            c.check("refus à l'Aperçu").percentage(p -> p >= 3);
            c.check("Aperçu périmé").percentage(p -> p >= 1);
            c.check("plancher").percentage(p -> p >= 0.5);
        });
    }

    // ------------------------------------------------------------------
    // P2a — corriger le dernier versement équivaut à l'avoir bien saisi
    // ------------------------------------------------------------------

    @Property(tries = 100)
    void correctingTheLastPaymentEqualsTypingItRight(@ForAll("lastScenarios") LastScenario scenario) {
        Pay intended = differentFrom(scenario.typed(), scenario.intended());

        // Monde 1 : A saisi, puis remplacé par B.
        Fixture typedWorld = persist(scenario.sessionsPerSeries());
        apply(typedWorld, scenario.history());
        Long typed = payQuietly(typedWorld, scenario.typed());
        if (typed == null) {
            Statistics.label("P2a").collect("A refusé");
            return;
        }
        Ledger beforeCorrection = ledger();
        Integer correctionStatus = null;
        try {
            correct(typedWorld, typed, intended, REASONS.get(scenario.reason()));
        } catch (CustomServiceException refused) {
            correctionStatus = refused.getStatus().value();
            assertThat(ledger()).as("remplacement refusé : rien n'est écrit").isEqualTo(beforeCorrection);
        }
        WorldState corrected = correctionStatus == null ? state(typedWorld) : null;

        // Monde 2 : B saisi directement.
        Fixture rightWorld = persist(scenario.sessionsPerSeries());
        apply(rightWorld, scenario.history());
        Integer directStatus = null;
        try {
            pay(rightWorld, intended);
        } catch (CustomServiceException refused) {
            directStatus = refused.getStatus().value();
        }

        assertThat(correctionStatus).as("refusé si et seulement si B saisi directement l'est").isEqualTo(directStatus);
        if (corrected != null) {
            assertThat(corrected).as("mêmes montants, statuts, reports et ventilation").isEqualTo(state(rightWorld));
            Statistics.label("P2a").collect(corrected.carryOvers().isEmpty() ? "comparé" : "comparé, avec report");
        } else {
            Statistics.label("P2a").collect("refusé des deux côtés");
        }
        Statistics.label("P2a").coverage(c -> {
            c.check("comparé").percentage(p -> p >= 10);
            c.check("comparé, avec report").percentage(p -> p >= 5);
            c.check("refusé des deux côtés").percentage(p -> p >= 1);
        });
    }

    // ------------------------------------------------------------------
    // P2b — corriger, c'est annuler puis encaisser
    // ------------------------------------------------------------------

    @Property(tries = 100)
    void correctingEqualsCancellingThenPaying(@ForAll("scenarios") Scenario scenario) {
        Correction correction = scenario.correction();

        Fixture replacedWorld = persist(scenario.sessionsPerSeries());
        List<Accepted> accepted = apply(replacedWorld, scenario.history());
        if (accepted.isEmpty()) {
            Statistics.label("P2b").collect("sans versement");
            return;
        }
        int pick = correction.pick() % accepted.size();
        // Un remplacement, quelle que soit la nature générée : P2 porte sur l'argent.
        Pay intended = differentFrom(accepted.get(pick).pay(),
                new Pay(correction.student(), correction.series(), correction.amount()));
        WorldState replaced;
        try {
            correct(replacedWorld, accepted.get(pick).encashmentId(), intended, REASONS.get(correction.reason()));
            replaced = state(replacedWorld);
        } catch (CustomServiceException refused) {
            Statistics.label("P2b").collect("remplacement refusé");
            return;
        }

        Fixture cancelledWorld = persist(scenario.sessionsPerSeries());
        Long target = apply(cancelledWorld, scenario.history()).get(pick).encashmentId();
        try {
            CorrectionReason reason = REASONS.get(correction.reason());
            String token = corrections.cancel(target, reason, CorrectionMode.PREVIEW, null).previewToken();
            corrections.cancel(target, reason, CorrectionMode.CONFIRM, token);
            pay(cancelledWorld, intended);
        } catch (CustomServiceException refused) {
            // L'annulation seule peut buter sur le plancher des remboursements, que le remplacement
            // respectait grâce à B : les deux chemins ne sont alors pas comparables.
            Statistics.label("P2b").collect("annulation seule refusée");
            return;
        }
        assertThat(replaced).as("remplacer = annuler puis encaisser").isEqualTo(state(cancelledWorld));
        Statistics.label("P2b").collect(replaced.carryOvers().isEmpty() ? "comparé" : "comparé, avec report");
        Statistics.label("P2b").coverage(c -> {
            c.check("comparé").percentage(p -> p >= 10);
            c.check("comparé, avec report").percentage(p -> p >= 10);
        });
    }

    // ------------------------------------------------------------------
    // Exécution
    // ------------------------------------------------------------------

    private record Fixture(Long groupId, List<Long> series, List<Long> students, List<Long> sessions) {
    }

    /** Un versement accepté de l'historique. */
    private record Accepted(Long encashmentId, Pay pay) {
    }

    private List<Accepted> apply(Fixture fixture, List<Step> history) {
        List<Accepted> accepted = new ArrayList<>();
        for (Step step : history) {
            if (step instanceof Pay pay) {
                Long id = payQuietly(fixture, pay);
                if (id != null) {
                    accepted.add(new Accepted(id, pay));
                }
            } else if (step instanceof Refund refund) {
                refundQuietly(fixture, refund);
            }
        }
        return accepted;
    }

    private static Long pay(Fixture fixture, Pay pay) {
        return processing.processPayment(fixture.students().get(pay.student()), fixture.groupId(),
                fixture.series().get(pay.series()), pay.amount()).encashment().getId();
    }

    private static Long payQuietly(Fixture fixture, Pay pay) {
        try {
            return pay(fixture, pay);
        } catch (CustomServiceException refused) {
            return null;
        }
    }

    private static void refundQuietly(Fixture fixture, Refund refund) {
        List<Long> lines = jdbc.queryForList("SELECT id FROM payments WHERE student_id = ? AND session_series_id = ?",
                Long.class, fixture.students().get(refund.student()), fixture.series().get(refund.series()));
        if (lines.isEmpty()) {
            return;
        }
        try {
            refunds.create(new RefundRequestDTO(lines.get(0), fixture.students().get(refund.student()),
                    BigDecimal.valueOf(refund.amount()), null, "Remboursement de test"));
        } catch (CustomServiceException refused) {
            // Au-delà du plafond remboursable : l'historique continue sans lui.
        }
    }

    /** Aperçu ou confirmation de la correction décrite, sur le versement visé. */
    private static CorrectionOutcome<?> run(Fixture fixture, Accepted target, Correction correction,
                                            CorrectionMode mode, String token) {
        CorrectionReason reason = REASONS.get(correction.reason());
        return switch (correction.kind()) {
            case CANCEL -> corrections.cancel(target.encashmentId(), reason, mode, token);
            case REPLACE -> corrections.correct(target.encashmentId(), changes(fixture,
                            new Pay(correction.student(), correction.series(), correction.amount()),
                            correction.method() ? "cheque" : null, correction.note() ? "Corrigé" : null),
                    reason, mode, token);
            // Au moins l'un des deux change : sinon la correction n'aurait rien à faire.
            case DETAILS -> corrections.correct(target.encashmentId(), changes(fixture, target.pay(),
                            correction.method() ? "cheque" : null,
                            correction.note() || !correction.method() ? "Corrigé" : null),
                    reason, mode, token);
        };
    }

    /** Remplacement complet, Aperçu puis confirmation. */
    private static void correct(Fixture fixture, Long encashmentId, Pay intended, CorrectionReason reason) {
        EncashmentChanges changes = changes(fixture, intended, null, null);
        String token = corrections.correct(encashmentId, changes, reason, CorrectionMode.PREVIEW, null).previewToken();
        corrections.correct(encashmentId, changes, reason, CorrectionMode.CONFIRM, token);
    }

    private static EncashmentChanges changes(Fixture fixture, Pay pay, String method, String note) {
        return new EncashmentChanges(BigDecimal.valueOf(pay.amount()), fixture.students().get(pay.student()),
                fixture.groupId(), fixture.series().get(pay.series()), method, note);
    }

    /** B diffère de A par l'argent : sinon la correction n'aurait rien à remplacer. */
    private static Pay differentFrom(Pay typed, Pay intended) {
        return intended.equals(typed)
                ? new Pay(intended.student(), intended.series(), intended.amount() + 500)
                : intended;
    }

    // ------------------------------------------------------------------
    // États relus en SQL
    // ------------------------------------------------------------------

    /** Tout ce qu'une correction refusée ne doit pas toucher. */
    private record Ledger(List<Map<String, Object>> encashments, List<Map<String, Object>> allocations,
                          List<Map<String, Object>> lines, List<Map<String, Object>> carryOvers,
                          List<Map<String, Object>> payments, List<Map<String, Object>> refunds,
                          List<Map<String, Object>> counter, Long traces) {
    }

    private static Ledger ledger() {
        return new Ledger(
                jdbc.queryForList("SELECT id, status, receipt_number, amount_received, payment_method, notes, "
                        + "cancel_reason_type, replaces_id, replaced_by_id FROM encashment ORDER BY id"),
                jdbc.queryForList("SELECT id, active, amount FROM encashment_allocation ORDER BY id"),
                jdbc.queryForList("SELECT id, active, amount_paid FROM payment_detail ORDER BY id"),
                jdbc.queryForList("SELECT id, active, amount FROM payment_carry_over ORDER BY id"),
                jdbc.queryForList("SELECT id, amount_paid, status FROM payments ORDER BY id"),
                jdbc.queryForList("SELECT id, amount, active FROM refund ORDER BY id"),
                jdbc.queryForList("SELECT counter_year, last_rank FROM receipt_counter"),
                jdbc.queryForObject("SELECT COUNT(*) FROM correction_audit", Long.class));
    }

    /**
     * État d'un monde, désigné par rangs : montants de chaque Série pour chaque élève, statut et
     * cumul de chaque ligne de paiement, reports actifs, ventilation active par séance.
     */
    private record WorldState(Map<String, AmountSnapshot> amounts, Map<String, String> lines,
                              List<String> carryOvers, Map<String, BigDecimal> ventilation) {
    }

    private static WorldState state(Fixture fixture) {
        // Une ligne de paiement dont tout l'argent a été annulé reste, à 0 et « en attente » : ses
        // Imputations neutralisées la désignent encore. Elle dit la même chose que l'absence de
        // ligne — rien n'a été versé — et n'entre donc pas dans la comparaison.
        Map<String, String> lines = new TreeMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT student_id, session_series_id, amount_paid, status FROM payments WHERE amount_paid <> 0")) {
            lines.put(key(fixture, id(row, "STUDENT_ID"), id(row, "SESSION_SERIES_ID")),
                    money(row.get("AMOUNT_PAID")) + " " + row.get("STATUS"));
        }
        List<String> carryOvers = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList("SELECT student_id, source_series_id, target_series_id, "
                + "amount FROM payment_carry_over WHERE active = TRUE")) {
            carryOvers.add("e" + fixture.students().indexOf(id(row, "STUDENT_ID"))
                    + " S" + fixture.series().indexOf(id(row, "SOURCE_SERIES_ID"))
                    + "→S" + fixture.series().indexOf(id(row, "TARGET_SERIES_ID"))
                    + " " + money(row.get("AMOUNT")));
        }
        carryOvers.sort(null);
        Map<String, BigDecimal> ventilation = new TreeMap<>();
        for (Map<String, Object> row : jdbc.queryForList("SELECT p.student_id, d.session_id, SUM(d.amount_paid) AS total "
                + "FROM payment_detail d JOIN payments p ON d.payment_id = p.id WHERE d.active = TRUE "
                + "GROUP BY p.student_id, d.session_id")) {
            ventilation.put("e" + fixture.students().indexOf(id(row, "STUDENT_ID"))
                    + " séance " + fixture.sessions().indexOf(id(row, "SESSION_ID")), money(row.get("TOTAL")));
        }
        return new WorldState(amounts(fixture), lines, carryOvers, ventilation);
    }

    private static Map<String, AmountSnapshot> amounts(Fixture fixture) {
        Map<String, AmountSnapshot> amounts = new TreeMap<>();
        for (Long studentId : fixture.students()) {
            for (Long seriesId : fixture.series()) {
                amounts.put(key(fixture, studentId, seriesId),
                        AmountSnapshot.of(costResolver.resolve(studentId, seriesId)));
            }
        }
        return amounts;
    }

    private static String key(Fixture fixture, Long studentId, Long seriesId) {
        return "e" + fixture.students().indexOf(studentId) + " S" + fixture.series().indexOf(seriesId);
    }

    private static Long id(Map<String, Object> row, String column) {
        return ((Number) row.get(column)).longValue();
    }

    private static BigDecimal money(Object value) {
        BigDecimal amount = value instanceof BigDecimal decimal ? decimal
                : BigDecimal.valueOf(((Number) value).doubleValue());
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    // Données de l'essai
    // ------------------------------------------------------------------

    private Fixture persist(List<Integer> sessionsPerSeries) {
        for (String table : List.of("correction_audit", "refund_receipt_issuance", "refund", "payment_idempotency",
                "payment_carry_over", "payment_detail", "encashment_allocation", "encashment", "receipt_counter",
                "payments", "attendance", "session", "session_series", "student_groups", "groups", "student", "price")) {
            jdbc.update("DELETE FROM " + table);
        }
        PricingEntity price = pricingRepository.save(PricingEntity.builder().price(PRICE_PER_SESSION).build());
        GroupEntity group = groupRepository.save(GroupEntity.builder()
                .name("Math 1ère A").price(price).sessionNumberPerSerie(3).build());

        List<Long> series = new ArrayList<>();
        List<Long> sessions = new ArrayList<>();
        int week = 0;
        for (int s = 0; s < SERIES_COUNT; s++) {
            SessionSeriesEntity entity = seriesRepository.save(SessionSeriesEntity.builder()
                    .name("Série " + (s + 1)).group(group).totalSessions(3).serieTimeStart(sessionDate(week)).build());
            for (int k = 0; k < sessionsPerSeries.get(s); k++) {
                sessions.add(sessionRepository.save(SessionEntity.builder()
                        .title("Séance " + (k + 1)).group(group).sessionSeries(entity)
                        .sessionTimeStart(sessionDate(week++)).build()).getId());
            }
            series.add(entity.getId());
        }
        List<Long> students = new ArrayList<>();
        for (int i = 0; i < STUDENT_COUNT; i++) {
            StudentEntity student = studentRepository.save(StudentEntity.builder()
                    .firstName("Élève " + (i + 1)).lastName("Test").build());
            studentGroupRepository.save(StudentGroupEntity.builder().student(student).group(group).build());
            students.add(student.getId());
        }
        return new Fixture(group.getId(), series, students, sessions);
    }

    private static Date sessionDate(int week) {
        return Date.from(LocalDate.of(2030, 1, 7).plusWeeks(week)
                .atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant());
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
            CorrectionAuditService.class, CorrectionRunner.class, EncashmentCorrectionService.class })
    static class CorrectionTestContext {

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
    }
}
