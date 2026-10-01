package com.school.management.migration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Les migrations Flyway, appliquées à un vrai PostgreSQL, produisent le schéma que les entités
 * attendent.
 *
 * <p><b>Pourquoi ce test existe.</b> Toute la suite tourne sur H2 avec Flyway désactivé : le
 * schéma y est fabriqué par Hibernate depuis les entités. Le SQL des migrations n'est donc
 * jamais exécuté par les tests, et une migration fausse — ou une entité qui ne correspond plus à
 * sa table — n'apparaissait qu'au démarrage en production, comme la panne de baseline sur
 * Render l'a montré.</p>
 *
 * <p>Ce test crée une base jetable, y applique V1 à la dernière migration, puis démarre
 * Hibernate en mode {@code validate} sur <em>toutes</em> les entités : le démarrage échoue si une
 * colonne attendue manque ou n'a pas le bon type. Il vérifie ensuite, en SQL, les règles que V6
 * confie au stockage plutôt qu'au seul code.</p>
 *
 * <p><b>Prérequis.</b> Un PostgreSQL joignable, avec le droit de créer une base. Paramètres :
 * {@code PG_TEST_URL} (défaut {@code jdbc:postgresql://localhost:5432/postgres}),
 * {@code PG_TEST_USER} et {@code PG_TEST_PASSWORD} (défaut {@code postgres}). Sans serveur, le
 * test est ignoré et le dit, plutôt que d'échouer sur un poste qui n'en a pas.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Migrations Flyway sur PostgreSQL")
class MigrationSchemaPostgresIntegrationTest {

    private static final String ADMIN_URL = env("PG_TEST_URL", "jdbc:postgresql://localhost:5432/postgres");
    private static final String USER = env("PG_TEST_USER", "postgres");
    private static final String PASSWORD = env("PG_TEST_PASSWORD", "postgres");

    private final String database = "school_schema_check_" + ProcessHandle.current().pid();
    private final AtomicInteger receiptCounter = new AtomicInteger();

    private ConfigurableApplicationContext context;
    private JdbcTemplate jdbc;

    private long studentId;
    private long groupId;
    private long seriesId;
    private long otherSeriesId;
    private long paymentId;

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    // ------------------------------------------------------------------
    // Base jetable : création, migrations, validation Hibernate
    // ------------------------------------------------------------------

    @BeforeAll
    void migrateFreshDatabase() throws SQLException {
        Assumptions.assumeTrue(serverReachable(),
                "PostgreSQL injoignable sur " + ADMIN_URL + " : test des migrations ignoré.");

        execAsAdmin("DROP DATABASE IF EXISTS " + database);
        execAsAdmin("CREATE DATABASE " + database);

        // Démarrer ce contexte EST le premier test : Flyway applique toutes les migrations, puis
        // Hibernate valide chaque entité contre le schéma obtenu. Un écart lève une exception ici.
        context = new SpringApplicationBuilder(SchemaCheckContext.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.datasource.url=" + databaseUrl(),
                        "--spring.datasource.username=" + USER,
                        "--spring.datasource.password=" + PASSWORD,
                        "--spring.datasource.driver-class-name=org.postgresql.Driver",
                        "--spring.flyway.enabled=true",
                        "--spring.flyway.baseline-on-migrate=false",
                        "--spring.jpa.hibernate.ddl-auto=validate",
                        "--spring.jpa.open-in-view=false",
                        "--spring.main.banner-mode=off");
        jdbc = context.getBean(JdbcTemplate.class);

        studentId = insertReturningId("INSERT INTO student (first_name, last_name) VALUES ('Amine', 'Belkacem')");
        groupId = insertReturningId("INSERT INTO groups (name) VALUES ('Math 1ère A')");
        seriesId = insertReturningId("INSERT INTO session_series (name, group_id) VALUES ('Série 1', ?)", groupId);
        otherSeriesId = insertReturningId("INSERT INTO session_series (name, group_id) VALUES ('Série 2', ?)", groupId);
        paymentId = insertReturningId("INSERT INTO payments (amount_paid, student_id, group_id, session_series_id) "
                + "VALUES (0, ?, ?, ?)", studentId, groupId, seriesId);
    }

    @AfterAll
    void dropDatabase() throws SQLException {
        if (context != null) {
            context.close();
        }
        if (serverReachable()) {
            execAsAdmin("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
        }
    }

    private String databaseUrl() {
        return ADMIN_URL.substring(0, ADMIN_URL.lastIndexOf('/') + 1) + database;
    }

    private static boolean serverReachable() {
        try (Connection ignored = DriverManager.getConnection(ADMIN_URL, USER, PASSWORD)) {
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    private static void execAsAdmin(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(ADMIN_URL, USER, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private long insertReturningId(String sql, Object... args) {
        return jdbc.queryForObject(sql + " RETURNING id", Long.class, args);
    }

    // ------------------------------------------------------------------
    // Fabriques de lignes
    // ------------------------------------------------------------------

    /** Encaissement valide par défaut ; chaque appel reçoit un numéro de reçu neuf. */
    private Map<String, Object> encashment() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("receipt_number", "RECU-2030-" + String.format("%04d", receiptCounter.incrementAndGet()));
        row.put("student_id", studentId);
        row.put("group_id", groupId);
        row.put("target_series_id", seriesId);
        row.put("amount_received", new java.math.BigDecimal("2000.00"));
        row.put("kind", "REGULAR");
        row.put("received_at", java.sql.Timestamp.valueOf("2030-01-07 10:00:00"));
        row.put("received_by", "admin");
        row.put("status", "ACTIVE");
        return row;
    }

    private long insert(String table, Map<String, Object> row) {
        String columns = String.join(", ", row.keySet());
        String placeholders = String.join(", ", row.keySet().stream().map(c -> "?").toList());
        return insertReturningId("INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ")",
                row.values().toArray());
    }

    private Map<String, Object> cancelled(Map<String, Object> row) {
        row.put("status", "CANCELLED");
        row.put("cancelled_at", java.sql.Timestamp.valueOf("2030-01-08 09:00:00"));
        row.put("cancelled_by", "admin");
        row.put("cancel_reason_type", "WRONG_AMOUNT");
        return row;
    }

    private Map<String, Object> allocation(long encashmentId, long series, String amount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("encashment_id", encashmentId);
        row.put("series_id", series);
        row.put("payment_id", paymentId);
        row.put("amount", new java.math.BigDecimal(amount));
        row.put("carried_over", false);
        row.put("active", true);
        return row;
    }

    private Map<String, Object> audit() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("domain", "ENCASHMENT");
        row.put("action", "ENCASHMENT_CANCELLED");
        row.put("entity_id", 1L);
        row.put("summary", "Reçu RECU-2030-0001 de 2 000,00 DA annulé");
        row.put("reason_type", "WRONG_AMOUNT");
        row.put("performed_by", "admin");
        row.put("performed_at", java.sql.Timestamp.valueOf("2030-01-08 09:00:00"));
        return row;
    }

    // ------------------------------------------------------------------
    // Application des migrations
    // ------------------------------------------------------------------

    @Test
    @DisplayName("V1 à V6 appliquées sur une base vide, et chaque entité correspond à sa table")
    void allMigrationsApplyAndEntitiesValidate() {
        // Le démarrage du contexte en mode validate a déjà vérifié les entités ; reste à s'assurer
        // que toutes les migrations ont réussi, dans l'ordre, sans en sauter aucune.
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class);
        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE NOT success", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Liens vers l'Encaissement facultatifs tant que le code ne les écrit pas")
    void linksOnExistingTablesAreNullableForNow() {
        // Cette assertion bascule quand la migration qui les rend obligatoires est livrée, avec le
        // code qui les écrit. Les rendre obligatoires avant ferait échouer tout encaissement.
        Map<String, String> nullability = new LinkedHashMap<>();
        for (String[] column : new String[][] {
                { "payment_detail", "encashment_allocation_id" },
                { "payment_carry_over", "encashment_allocation_id" },
                { "payment_idempotency", "encashment_id" } }) {
            nullability.put(column[0] + "." + column[1], jdbc.queryForObject(
                    "SELECT is_nullable FROM information_schema.columns WHERE table_name = ? AND column_name = ?",
                    String.class, column[0], column[1]));
        }
        assertThat(nullability).containsOnlyKeys(
                "payment_detail.encashment_allocation_id",
                "payment_carry_over.encashment_allocation_id",
                "payment_idempotency.encashment_id");
        assertThat(nullability.values()).containsOnly("YES");
    }

    // ------------------------------------------------------------------
    // Règles portées par le stockage
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Encaissement")
    class Encaissement {

        @Test
        @DisplayName("encaissement valide accepté")
        void validEncashmentIsAccepted() {
            assertThatCode(() -> insert("encashment", encashment())).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("montant nul ou négatif refusé")
        void nonPositiveAmountIsRejected() {
            for (String amount : List.of("0.00", "-1.00")) {
                Map<String, Object> row = encashment();
                row.put("amount_received", new java.math.BigDecimal(amount));
                assertThatThrownBy(() -> insert("encashment", row))
                        .isInstanceOf(DataIntegrityViolationException.class)
                        .hasMessageContaining("ck_encashment_amount_positive");
            }
        }

        @Test
        @DisplayName("statut et type inconnus refusés")
        void unknownStatusAndKindAreRejected() {
            Map<String, Object> badStatus = encashment();
            badStatus.put("status", "DELETED");
            assertThatThrownBy(() -> insert("encashment", badStatus))
                    .hasMessageContaining("ck_encashment_status");

            Map<String, Object> badKind = encashment();
            badKind.put("kind", "REFUND");
            assertThatThrownBy(() -> insert("encashment", badKind))
                    .hasMessageContaining("ck_encashment_kind");
        }

        @Test
        @DisplayName("numéro de reçu unique")
        void receiptNumberIsUnique() {
            Map<String, Object> first = encashment();
            insert("encashment", first);

            Map<String, Object> duplicate = encashment();
            duplicate.put("receipt_number", first.get("receipt_number"));
            assertThatThrownBy(() -> insert("encashment", duplicate))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("uk_encashment_receipt_number");
        }

        @Test
        @DisplayName("annulation sans date, auteur ou motif refusée")
        void cancellationMustSayWhenWhoAndWhy() {
            for (String missing : List.of("cancelled_at", "cancelled_by", "cancel_reason_type")) {
                Map<String, Object> row = cancelled(encashment());
                row.put(missing, null);
                assertThatThrownBy(() -> insert("encashment", row))
                        .as("annulation sans %s", missing)
                        .hasMessageContaining("ck_encashment_cancellation");
            }
            assertThatCode(() -> insert("encashment", cancelled(encashment()))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("encaissement actif portant une trace d'annulation refusé")
        void activeEncashmentCarriesNoCancellation() {
            Map<String, Object> row = encashment();
            row.put("cancelled_at", java.sql.Timestamp.valueOf("2030-01-08 09:00:00"));
            assertThatThrownBy(() -> insert("encashment", row))
                    .hasMessageContaining("ck_encashment_cancellation");
        }

        @Test
        @DisplayName("motif « Autre » sans texte refusé")
        void otherReasonRequiresText() {
            for (String text : new String[] { null, "   " }) {
                Map<String, Object> row = cancelled(encashment());
                row.put("cancel_reason_type", "OTHER");
                row.put("cancel_reason_text", text);
                assertThatThrownBy(() -> insert("encashment", row))
                        .hasMessageContaining("ck_encashment_cancel_reason_other");
            }
            Map<String, Object> withText = cancelled(encashment());
            withText.put("cancel_reason_type", "OTHER");
            withText.put("cancel_reason_text", "Parent venu deux fois");
            assertThatCode(() -> insert("encashment", withText)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("encaissement remplacé mais encore actif refusé ; remplacement dans les deux sens accepté")
        void replacedEncashmentMustBeCancelled() {
            long replacement = insert("encashment", encashment());

            Map<String, Object> stillActive = encashment();
            stillActive.put("replaced_by_id", replacement);
            assertThatThrownBy(() -> insert("encashment", stillActive))
                    .hasMessageContaining("ck_encashment_replaced_is_cancelled");

            Map<String, Object> original = cancelled(encashment());
            original.put("replaced_by_id", replacement);
            long originalId = insert("encashment", original);
            jdbc.update("UPDATE encashment SET replaces_id = ? WHERE id = ?", originalId, replacement);

            assertThat(jdbc.queryForObject("SELECT replaces_id FROM encashment WHERE id = ?", Long.class,
                    replacement)).isEqualTo(originalId);
        }

        @Test
        @DisplayName("encaissement qui se remplace lui-même refusé")
        void encashmentCannotReplaceItself() {
            long id = insert("encashment", cancelled(encashment()));
            assertThatThrownBy(() -> jdbc.update("UPDATE encashment SET replaced_by_id = id WHERE id = ?", id))
                    .hasMessageContaining("ck_encashment_not_self");
        }
    }

    @Nested
    @DisplayName("Imputation")
    class Imputation {

        @Test
        @DisplayName("montant nul refusé")
        void nonPositiveAmountIsRejected() {
            long encashmentId = insert("encashment", encashment());
            assertThatThrownBy(() -> insert("encashment_allocation", allocation(encashmentId, seriesId, "0.00")))
                    .hasMessageContaining("ck_allocation_amount_positive");
        }

        @Test
        @DisplayName("une série créditée au plus une fois par encaissement")
        void seriesCreditedAtMostOncePerEncashment() {
            long encashmentId = insert("encashment", encashment());
            insert("encashment_allocation", allocation(encashmentId, seriesId, "2000.00"));

            assertThatThrownBy(() -> insert("encashment_allocation", allocation(encashmentId, seriesId, "500.00")))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("uk_allocation_encashment_series");
            // Une autre série du même encaissement reste possible : c'est le report.
            assertThatCode(() -> insert("encashment_allocation", allocation(encashmentId, otherSeriesId, "500.00")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("ligne de ventilation et report désignent une Imputation existante")
        void detailAndCarryOverReferenceAnExistingAllocation() {
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO payment_detail (amount_paid, payment_id, encashment_allocation_id) VALUES (100, ?, ?)",
                    paymentId, 999_999L))
                    .hasMessageContaining("fk_payment_detail_allocation");

            long encashmentId = insert("encashment", encashment());
            long allocationId = insert("encashment_allocation", allocation(encashmentId, seriesId, "2000.00"));
            assertThatCode(() -> jdbc.update(
                    "INSERT INTO payment_detail (amount_paid, payment_id, encashment_allocation_id) VALUES (100, ?, ?)",
                    paymentId, allocationId)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Trace de correction")
    class Trace {

        @Test
        @DisplayName("motif « Autre » sans texte refusé")
        void otherReasonRequiresText() {
            Map<String, Object> row = audit();
            row.put("reason_type", "OTHER");
            assertThatThrownBy(() -> insert("correction_audit", row))
                    .hasMessageContaining("ck_correction_reason_other");
        }

        @Test
        @DisplayName("trace acceptée même si la donnée décrite n'existe pas : elle lui survit")
        void traceSurvivesTheTracedData() {
            Map<String, Object> row = audit();
            row.put("entity_id", 999_999L);
            row.put("student_id", 999_999L);
            row.put("session_id", 999_999L);
            assertThatCode(() -> insert("correction_audit", row)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("rang strictement croissant dans l'ordre des écritures")
        void rankIsStrictlyIncreasing() {
            long first = insert("correction_audit", audit());
            long second = insert("correction_audit", audit());
            long third = insert("correction_audit", audit());
            assertThat(List.of(first, second, third)).isSorted().doesNotHaveDuplicates();
        }
    }

    // ------------------------------------------------------------------
    // Contexte minimal : base de données, Flyway, Hibernate en validation
    // ------------------------------------------------------------------

    @Configuration
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class
    })
    @EntityScan("com.school.management.persistance")
    static class SchemaCheckContext {
    }
}
