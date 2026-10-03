package com.school.management.service.payment;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.management.service.correction.CorrectionAuditService;
import com.school.management.service.correction.CorrectionMode;
import com.school.management.service.correction.CorrectionReason;
import com.school.management.service.correction.CorrectionRunner;
import com.school.management.service.correction.EncashmentChanges;
import com.school.management.service.correction.EncashmentCorrection;
import com.school.management.service.correction.EncashmentCorrectionService;
import com.school.management.service.exception.CustomServiceException;
import net.jqwik.api.lifecycle.BeforeTry;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.statistics.Statistics;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;
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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Propriété P1 — conservation de l'argent (spec admin-corrections, exigences 1.4 et 2.2).
 *
 * <p><b>Énoncé.</b> Pour toute suite d'encaissements, acceptés ou refusés, d'annulations et de
 * remplacements (Aperçu puis confirmation, acceptés ou refusés — B.6), après chaque étape :</p>
 * <ol>
 *   <li>le cumul de chaque ligne de paiement vaut la somme de ses Imputations actives ;</li>
 *   <li>la somme des Encaissements actifs vaut la somme des cumuls : l'argent au registre est
 *       exactement l'argent reçu et non annulé ;</li>
 *   <li>un Encaissement actif est imputé en entier — jamais partiellement — et un Encaissement
 *       annulé ne compte plus nulle part : ni Imputation, ni ligne de ventilation, ni report
 *       actifs ;</li>
 *   <li>la ventilation d'une Imputation active égale son montant, et chaque Imputation reportée a
 *       exactement une trace de report du même montant ;</li>
 *   <li>aucun cumul ne dépasse le coût de sa série ;</li>
 *   <li>les numéros de reçu sont consécutifs, sans trou : un refus n'en consomme aucun ;</li>
 *   <li>un versement refusé ne change rien.</li>
 * </ol>
 *
 * <p><b>Pourquoi une propriété sur une vraie base.</b> Chaque règle ci-dessus est éprouvée par un
 * exemple ailleurs. Ce qui ne l'est pas, ce sont leurs combinaisons : un report vers une série
 * ensuite soldée, l'annulation du premier de trois versements, un refus entre deux encaissements.
 * C'est là que l'argent se perd ou se crée. Les invariants sont relus en SQL, sans passer par les
 * entités, pour ne pas dépendre du code qu'ils vérifient.</p>
 *
 * <p>jqwik s'exécute sur son propre moteur JUnit Platform : un contexte ciblé est amorcé une fois
 * sur une base H2 en mémoire, vidée à chaque essai — même harnais que
 * {@code JustificationNeutralityPropertyTest}.</p>
 */
class MoneyConservationPropertyTest {

    private static final double PRICE_PER_SESSION = 2000.0;
    private static final int SERIES_COUNT = 3;
    private static final int STUDENT_COUNT = 2;

    private static ConfigurableApplicationContext context;
    private static PaymentProcessingService processing;
    private static EncashmentService encashments;
    private static EncashmentCorrectionService corrections;
    private static JdbcTemplate jdbc;
    private static StudentRepository studentRepository;
    private static GroupRepository groupRepository;
    private static PricingRepository pricingRepository;
    private static SessionSeriesRepository seriesRepository;
    private static SessionRepository sessionRepository;
    private static StudentGroupRepository studentGroupRepository;

    @BeforeContainer
    static void startContext() {
        context = new SpringApplicationBuilder(ConservationTestContext.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.datasource.url=jdbc:h2:mem:money-conservation;DB_CLOSE_DELAY=-1",
                        "--spring.datasource.driverClassName=org.h2.Driver",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                        "--spring.jpa.hibernate.ddl-auto=create-drop",
                        "--spring.jpa.show-sql=false",
                        "--spring.main.banner-mode=off",
                        "--logging.level.com.school.management=WARN");

        processing = context.getBean(PaymentProcessingService.class);
        encashments = context.getBean(EncashmentService.class);
        corrections = context.getBean(EncashmentCorrectionService.class);
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

    /** Un remplacement est tracé au nom de l'administrateur authentifié (spec admin-corrections, 11.4). */
    @BeforeTry
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin-test", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    // ------------------------------------------------------------------
    // Scénario généré
    // ------------------------------------------------------------------

    /** Une étape du scénario. */
    sealed interface Step permits Pay, Cancel, Replace {
    }

    /**
     * Remplacement d'un encaissement déjà enregistré, choisi par son rang, par un versement de
     * l'élève, de la Série et du montant donnés — Aperçu puis confirmation (spec admin-corrections,
     * B.6). Il peut être refusé : remplacement non plaçable, encaissement déjà annulé, rien de changé.
     */
    record Replace(int pick, int student, int series, int amount) implements Step {
    }

    /**
     * Versement d'un étudiant sur une série, d'un montant multiple de 500 DA. Il peut être refusé :
     * série visée sans séance, surplus que plus aucune série ne peut recevoir, série soldée.
     */
    record Pay(int student, int series, int amount) implements Step {
    }

    /** Annulation d'un encaissement déjà enregistré, choisi par son rang ; sans effet s'il n'y en a pas. */
    record Cancel(int pick) implements Step {
    }

    /**
     * @param sessionsPerSeries nombre de séances de chaque série, de 0 (série non ouverte) à 3
     * @param steps             suite d'étapes, dans l'ordre
     */
    record Scenario(List<Integer> sessionsPerSeries, List<Step> steps) {
    }

    @Provide
    Arbitrary<Scenario> scenarios() {
        Arbitrary<Step> pay = Combinators.combine(
                        Arbitraries.integers().between(0, STUDENT_COUNT - 1),
                        Arbitraries.integers().between(0, SERIES_COUNT - 1),
                        Arbitraries.integers().between(1, 18).map(units -> units * 500))
                .as(Pay::new);
        Arbitrary<Step> cancel = Arbitraries.integers().between(0, 20).map(Cancel::new);
        Arbitrary<Step> replace = Combinators.combine(
                        Arbitraries.integers().between(0, 20),
                        Arbitraries.integers().between(0, STUDENT_COUNT - 1),
                        Arbitraries.integers().between(0, SERIES_COUNT - 1),
                        Arbitraries.integers().between(1, 18).map(units -> units * 500))
                .as(Replace::new);
        // Annulations aussi fréquentes qu'avant l'ajout des remplacements : leur couverture est exigée.
        Arbitrary<Step> step = Arbitraries.frequencyOf(
                net.jqwik.api.Tuple.of(6, pay),
                net.jqwik.api.Tuple.of(3, cancel),
                net.jqwik.api.Tuple.of(2, replace));
        return Combinators.combine(
                        Arbitraries.integers().between(0, 3).list().ofSize(SERIES_COUNT),
                        step.list().ofMinSize(1).ofMaxSize(12))
                .as(Scenario::new);
    }

    // ------------------------------------------------------------------
    // Propriété
    // ------------------------------------------------------------------

    @Property(tries = 100)
    void moneyIsNeverCreatedNorDestroyed(@ForAll("scenarios") Scenario scenario) {
        Fixture fixture = persist(scenario);
        List<Long> encashmentIds = new ArrayList<>();
        int refusals = 0;
        int cancellations = 0;
        int replacements = 0;

        int index = 0;
        for (Step step : scenario.steps()) {
            String where = "étape " + (++index) + " " + step;

            if (step instanceof Pay pay) {
                Ledger before = ledger();
                try {
                    PaymentAllocationResult result = processing.processPayment(
                            fixture.students().get(pay.student()), fixture.groupId(),
                            fixture.series().get(pay.series()), pay.amount());
                    encashmentIds.add(result.encashment().getId());
                } catch (CustomServiceException refused) {
                    refusals++;
                    assertThat(refused.getStatus()).as(where + " : un refus est une erreur de saisie")
                            .isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ledger()).as(where + " : un versement refusé ne change rien").isEqualTo(before);
                }
            } else if (step instanceof Cancel cancel && !encashmentIds.isEmpty()) {
                Long id = encashmentIds.get(cancel.pick() % encashmentIds.size());
                BigDecimal before = activeEncashmentsTotal();
                BigDecimal amount = jdbc.queryForObject(
                        "SELECT amount_received FROM encashment WHERE id = ?", BigDecimal.class, id);
                boolean active = "ACTIVE".equals(jdbc.queryForObject(
                        "SELECT status FROM encashment WHERE id = ?", String.class, id));
                if (active) {
                    cancellations++;
                    encashments.neutralize(id, CorrectionReason.of(CorrectionReasonType.WRONG_AMOUNT));
                    assertThat(activeEncashmentsTotal()).as(where + " : l'annulation retire exactement le montant reçu")
                            .isEqualByComparingTo(before.subtract(amount));
                }
            } else if (step instanceof Replace replace && !encashmentIds.isEmpty()) {
                Long id = encashmentIds.get(replace.pick() % encashmentIds.size());
                BigDecimal before = activeEncashmentsTotal();
                Ledger ledgerBefore = ledger();
                BigDecimal amount = jdbc.queryForObject(
                        "SELECT amount_received FROM encashment WHERE id = ?", BigDecimal.class, id);
                EncashmentChanges changes = new EncashmentChanges(BigDecimal.valueOf(replace.amount()),
                        fixture.students().get(replace.student()), fixture.groupId(),
                        fixture.series().get(replace.series()), null, null);
                CorrectionReason reason = CorrectionReason.of(CorrectionReasonType.WRONG_AMOUNT);
                try {
                    String token = corrections.correct(id, changes, reason, CorrectionMode.PREVIEW, null).previewToken();
                    EncashmentCorrection done = corrections.correct(id, changes, reason, CorrectionMode.CONFIRM, token)
                            .result();
                    replacements++;
                    encashmentIds.add(done.replacement().id());
                    assertThat(activeEncashmentsTotal()).as(where + " : le remplacement échange A contre B, exactement")
                            .isEqualByComparingTo(before.subtract(amount).add(BigDecimal.valueOf(replace.amount())));
                } catch (CustomServiceException refused) {
                    assertThat(refused.getStatus()).as(where + " : un refus est une erreur de saisie ou un conflit")
                            .isIn(HttpStatus.BAD_REQUEST, HttpStatus.CONFLICT);
                    assertThat(ledger()).as(where + " : un remplacement refusé ne change rien").isEqualTo(ledgerBefore);
                }
            }

            assertInvariants(where, scenario);
        }

        // Une propriété vraie sur des scénarios triviaux ne prouve rien : on exige que les cas
        // difficiles soient effectivement visités (vérifié par Statistics.coverage ci-dessous).
        Statistics.label("refus").collect(refusals > 0);
        Statistics.label("report").collect(count("payment_carry_over") > 0);
        Statistics.label("annulation").collect(cancellations > 0);
        Statistics.label("annulation d'un versement reporté").collect(cancellations > 0
                && jdbc.queryForObject("SELECT COUNT(*) FROM payment_carry_over WHERE active = FALSE", Long.class) > 0);
        Statistics.label("refus").coverage(c -> c.check(true).percentage(p -> p >= 20));
        Statistics.label("report").coverage(c -> c.check(true).percentage(p -> p >= 20));
        // 10 % et non 20 % : depuis l'ajout des remplacements (B.6), une annulation effective est
        // visitée dans 18 à 29 % des scénarios relevés. Un seuil à 20 % échouait au hasard, et jqwik
        // rejoue ensuite la graine fautive à chaque lancement. 10 % reste à plus de 3 écarts-types
        // sous la moyenne, et garantit une dizaine d'annulations par lancement.
        Statistics.label("annulation").coverage(c -> c.check(true).percentage(p -> p >= 10));
        Statistics.label("annulation d'un versement reporté").coverage(c -> c.check(true).percentage(p -> p >= 3));
        Statistics.label("remplacement").collect(replacements > 0);
        Statistics.label("remplacement").coverage(c -> c.check(true).percentage(p -> p >= 10));
    }

    // ------------------------------------------------------------------
    // Invariants, relus en SQL
    // ------------------------------------------------------------------

    private void assertInvariants(String where, Scenario scenario) {
        // 1. Cumul de chaque ligne = somme de ses Imputations actives.
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT p.id, p.amount_paid, COALESCE((SELECT SUM(a.amount) FROM encashment_allocation a "
                        + "WHERE a.payment_id = p.id AND a.active = TRUE), 0) AS imputed FROM payments p")) {
            assertThat(money(row.get("AMOUNT_PAID"))).as(where + " : cumul de la ligne " + row.get("ID"))
                    .isEqualByComparingTo(money(row.get("IMPUTED")));
        }

        // 2. Argent au registre = argent reçu et non annulé.
        BigDecimal cumuls = money(jdbc.queryForObject("SELECT COALESCE(SUM(amount_paid), 0) FROM payments", Object.class));
        assertThat(activeEncashmentsTotal()).as(where + " : encaissements actifs = somme des cumuls")
                .isEqualByComparingTo(cumuls);

        // 3. Encaissement actif imputé en entier ; annulé : plus rien d'actif nulle part.
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT e.id, e.status, e.amount_received, "
                        + "COALESCE((SELECT SUM(a.amount) FROM encashment_allocation a WHERE a.encashment_id = e.id "
                        + "AND a.active = TRUE), 0) AS imputed, "
                        + "(SELECT COUNT(*) FROM payment_detail d JOIN encashment_allocation a "
                        + "ON d.encashment_allocation_id = a.id WHERE a.encashment_id = e.id AND d.active = TRUE) AS lines, "
                        + "(SELECT COUNT(*) FROM payment_carry_over c JOIN encashment_allocation a "
                        + "ON c.encashment_allocation_id = a.id WHERE a.encashment_id = e.id AND c.active = TRUE) AS carries "
                        + "FROM encashment e")) {
            String label = where + " : encaissement " + row.get("ID");
            if ("ACTIVE".equals(row.get("STATUS"))) {
                assertThat(money(row.get("IMPUTED"))).as(label + " imputé en entier")
                        .isEqualByComparingTo(money(row.get("AMOUNT_RECEIVED")));
            } else {
                assertThat(money(row.get("IMPUTED"))).as(label + " annulé : aucune imputation active").isZero();
                assertThat(((Number) row.get("LINES")).longValue()).as(label + " annulé : aucune ligne active").isZero();
                assertThat(((Number) row.get("CARRIES")).longValue()).as(label + " annulé : aucun report actif").isZero();
            }
        }

        // 4. Ventilation complète de chaque Imputation active ; un report par Imputation reportée.
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT a.id, a.amount, a.carried_over, "
                        + "COALESCE((SELECT SUM(d.amount_paid) FROM payment_detail d "
                        + "WHERE d.encashment_allocation_id = a.id AND d.active = TRUE), 0) AS ventilated, "
                        + "(SELECT COUNT(*) FROM payment_carry_over c WHERE c.encashment_allocation_id = a.id "
                        + "AND c.active = TRUE AND c.amount = a.amount) AS carries "
                        + "FROM encashment_allocation a WHERE a.active = TRUE")) {
            String label = where + " : imputation " + row.get("ID");
            assertThat(money(row.get("VENTILATED"))).as(label + " ventilée en entier")
                    .isEqualByComparingTo(money(row.get("AMOUNT")));
            assertThat(((Number) row.get("CARRIES")).longValue()).as(label + " : traces de report")
                    .isEqualTo(Boolean.TRUE.equals(row.get("CARRIED_OVER")) ? 1L : 0L);
        }

        // 5. Aucun cumul au-delà du coût de sa série (toutes les séances sont facturables ici).
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT p.id, p.amount_paid, (SELECT COUNT(*) FROM session s WHERE s.session_series_id = p.session_series_id) "
                        + "AS sessions FROM payments p")) {
            BigDecimal cost = money(((Number) row.get("SESSIONS")).doubleValue() * PRICE_PER_SESSION);
            assertThat(money(row.get("AMOUNT_PAID"))).as(where + " : cumul de la ligne " + row.get("ID") + " ≤ coût")
                    .isLessThanOrEqualTo(cost);
        }

        // 6. Numéros de reçu consécutifs, sans trou.
        List<String> numbers = jdbc.queryForList("SELECT receipt_number FROM encashment ORDER BY id", String.class);
        for (int i = 0; i < numbers.size(); i++) {
            assertThat(numbers.get(i)).as(where + " : reçu n° " + (i + 1)).endsWith(String.format("-%04d", i + 1));
        }
    }

    private static BigDecimal activeEncashmentsTotal() {
        return money(jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount_received), 0) FROM encashment WHERE status = 'ACTIVE'", Object.class));
    }

    /** Ce qu'un refus ne doit pas toucher. */
    private record Ledger(long encashments, long allocations, long lines, long carries, BigDecimal cumuls) {
    }

    private static Ledger ledger() {
        return new Ledger(
                count("encashment"), count("encashment_allocation"), count("payment_detail"),
                count("payment_carry_over"),
                money(jdbc.queryForObject("SELECT COALESCE(SUM(amount_paid), 0) FROM payments", Object.class)));
    }

    private static long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private static BigDecimal money(Object value) {
        BigDecimal amount = value instanceof BigDecimal decimal ? decimal
                : BigDecimal.valueOf(((Number) value).doubleValue());
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    // Données de l'essai
    // ------------------------------------------------------------------

    private record Fixture(Long groupId, List<Long> series, List<Long> students) {
    }

    private Fixture persist(Scenario scenario) {
        for (String table : List.of("correction_audit", "payment_idempotency", "payment_carry_over", "payment_detail",
                "encashment_allocation", "encashment", "receipt_counter", "payments", "attendance", "session",
                "session_series", "student_groups", "groups", "student", "price")) {
            jdbc.update("DELETE FROM " + table);
        }

        PricingEntity price = pricingRepository.save(PricingEntity.builder().price(PRICE_PER_SESSION).build());
        GroupEntity group = groupRepository.save(GroupEntity.builder()
                .name("Math 1ère A").price(price).sessionNumberPerSerie(3).build());

        List<Long> series = new ArrayList<>();
        int week = 0;
        for (int s = 0; s < SERIES_COUNT; s++) {
            SessionSeriesEntity entity = seriesRepository.save(SessionSeriesEntity.builder()
                    .name("Série " + (s + 1)).group(group).totalSessions(3).serieTimeStart(sessionDate(week)).build());
            for (int k = 0; k < scenario.sessionsPerSeries().get(s); k++) {
                sessionRepository.save(SessionEntity.builder()
                        .title("Séance " + (k + 1)).group(group).sessionSeries(entity)
                        .sessionTimeStart(sessionDate(week++)).build());
            }
            series.add(entity.getId());
        }

        // Inscrits « maintenant », séances en 2030 : toutes leur sont facturables.
        List<Long> students = new ArrayList<>();
        for (int i = 0; i < STUDENT_COUNT; i++) {
            StudentEntity student = studentRepository.save(StudentEntity.builder()
                    .firstName("Élève " + (i + 1)).lastName("Test").build());
            studentGroupRepository.save(StudentGroupEntity.builder().student(student).group(group).build());
            students.add(student.getId());
        }
        return new Fixture(group.getId(), series, students);
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
            EncashmentQueryService.class, CorrectionAuditService.class, CorrectionRunner.class,
            EncashmentCorrectionService.class })
    static class ConservationTestContext {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        AuditorAware<String> auditorAware() {
            return () -> Optional.of("admin-test");
        }

        /** L'année scolaire n'est pas l'objet de cette propriété : la garde laisse tout passer. */
        @Bean
        ReadOnlyYearGuard readOnlyYearGuard() {
            return Mockito.mock(ReadOnlyYearGuard.class);
        }
    }
}
