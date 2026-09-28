package com.school.management.service;

import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.SchoolYearRepository;
import com.school.management.repository.StudentRepository;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests d'intégration (Spring Boot Test, H2 en mémoire) du {@link SchoolYearMigrationRunner}
 * portant sur deux garanties :
 *
 * <ol>
 *   <li><strong>Idempotence de la migration (Exigence 12.1)</strong> : ré-exécuter le runner ne
 *       crée aucune année scolaire en double ; l'unique année courante reste la même.</li>
 *   <li><strong>Invariant « pas d'année directe » (Exigences 3.5, 12.3)</strong> : les séries,
 *       séances, paiements et présences ne portent aucune colonne / aucun champ d'année scolaire ;
 *       leur année est uniquement joignable via leur groupe (session → série → groupe → année).</li>
 * </ol>
 *
 * <p>{@code @DataJpaTest} remplace la source de données par une base H2 embarquée et exécute
 * chaque test dans une transaction annulée en fin de test. Le {@code @TestPropertySource} force
 * le dialecte H2 et {@code create-drop} pour que Hibernate génère le schéma à partir des entités,
 * sans conflit avec la configuration PostgreSQL du module principal.</p>
 *
 * <p>Le {@link SchoolYearMigrationRunner} est <strong>instancié manuellement</strong> à partir
 * des dépôts injectés, et non déclaré comme bean : cela évite que son {@code ApplicationRunner}
 * ne s'exécute au démarrage du contexte (et ne valide une année hors de la transaction de test).
 * Son {@code migrate()} s'exécute alors dans la transaction ambiante du test (rejointe par
 * défaut), qui est annulée à la fin — parfait pour l'isolation.</p>
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class SchoolYearMigrationIdempotencyIntegrationTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private SchoolYearRepository schoolYearRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private StudentRepository studentRepository;

    private SchoolYearMigrationRunner migrationRunner;

    @BeforeEach
    void setUp() {
        // Instanciation directe : pas de bean ApplicationRunner, donc aucune migration au
        // démarrage du contexte. migrate() s'exécute dans la transaction de test (annulée ensuite).
        migrationRunner = new SchoolYearMigrationRunner(
                schoolYearRepository, groupRepository, studentRepository);
    }

    // ==================================================================
    // Exigence 12.1 — idempotence : aucune année en double
    // ==================================================================
    @Nested
    @DisplayName("Migration idempotente (Exigence 12.1)")
    class Idempotency {

        @Test
        @DisplayName("Ré-exécuter le runner ne crée aucune année scolaire en double")
        void reRunningCreatesNoDuplicateYear() {
            // --- Arrange : état pré-migration minimal (des groupes sans année) ---
            em.persist(GroupEntity.builder().name("Groupe A").build());
            em.persist(GroupEntity.builder().name("Groupe B").build());
            em.flush();

            // --- Act 1 : première migration ---
            SchoolYearEntity created = migrationRunner.migrate();
            em.flush();

            // --- Assert 1 : exactement une année scolaire, marquée courante ---
            assertThat(created)
                    .as("la première migration crée l'année scolaire initiale")
                    .isNotNull();
            assertThat(schoolYearRepository.count())
                    .as("une seule année scolaire après la première migration")
                    .isEqualTo(1L);

            SchoolYearEntity currentAfterFirst = schoolYearRepository.findByIsCurrentTrue().orElseThrow();
            Long idAfterFirst = currentAfterFirst.getId();
            String labelAfterFirst = currentAfterFirst.getLabel();

            // --- Act 2 : ré-exécution du runner ---
            SchoolYearEntity secondResult = migrationRunner.migrate();
            em.flush();

            // --- Assert 2 : aucune année en double, la même année reste courante ---
            assertThat(secondResult)
                    .as("la seconde exécution est ignorée (idempotence) et ne renvoie aucune année")
                    .isNull();
            assertThat(schoolYearRepository.count())
                    .as("le nombre d'années scolaires reste inchangé après ré-exécution")
                    .isEqualTo(1L);

            long currentCount = schoolYearRepository.findAll().stream()
                    .filter(sy -> Boolean.TRUE.equals(sy.getIsCurrent()))
                    .count();
            assertThat(currentCount)
                    .as("il reste exactement une année scolaire courante")
                    .isEqualTo(1L);

            SchoolYearEntity currentAfterSecond = schoolYearRepository.findByIsCurrentTrue().orElseThrow();
            assertThat(currentAfterSecond.getId())
                    .as("l'année courante est toujours la même instance (aucun doublon créé)")
                    .isEqualTo(idAfterFirst);
            assertThat(currentAfterSecond.getLabel())
                    .as("le libellé de l'année courante est inchangé")
                    .isEqualTo(labelAfterFirst);
        }
    }

    // ==================================================================
    // Exigences 3.5, 12.3 — invariant « pas d'année directe »
    // ==================================================================
    @Nested
    @DisplayName("Invariant « pas d'année directe » (Exigences 3.5, 12.3)")
    class NoDirectYearInvariant {

        /**
         * Assertion structurelle (réflexion) : aucune des entités enfant ne doit déclarer de champ
         * de type {@link SchoolYearEntity}, ni de colonne / jointure nommée {@code school_year_id}.
         * L'année scolaire n'est donc joignable que via le groupe (Exigences 3.5, 12.3).
         */
        @Test
        @DisplayName("Série, séance, paiement et présence ne déclarent aucun champ/colonne d'année scolaire")
        void childEntitiesDeclareNoDirectSchoolYearColumn() {
            List<Class<?>> childEntities = List.of(
                    SessionSeriesEntity.class,
                    SessionEntity.class,
                    PaymentEntity.class,
                    AttendanceEntity.class);

            for (Class<?> entity : childEntities) {
                for (Field field : entity.getDeclaredFields()) {
                    // (1) Aucun champ de type SchoolYearEntity.
                    assertThat(field.getType())
                            .as("%s ne doit déclarer aucun champ de type SchoolYearEntity", entity.getSimpleName())
                            .isNotEqualTo(SchoolYearEntity.class);

                    // (2) Aucune colonne/jointure « school_year_id ».
                    JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
                    if (joinColumn != null) {
                        assertThat(joinColumn.name())
                                .as("%s.%s ne doit pas mapper la colonne school_year_id",
                                        entity.getSimpleName(), field.getName())
                                .isNotEqualToIgnoringCase("school_year_id");
                    }
                    Column column = field.getAnnotation(Column.class);
                    if (column != null) {
                        assertThat(column.name())
                                .as("%s.%s ne doit pas mapper la colonne school_year_id",
                                        entity.getSimpleName(), field.getName())
                                .isNotEqualToIgnoringCase("school_year_id");
                    }
                }
            }
        }

        /**
         * Assertion comportementale : l'année scolaire d'une série, d'une séance, d'un paiement et
         * d'une présence se résout exclusivement en remontant jusqu'au groupe (Exigence 3.4/3.5).
         */
        @Test
        @DisplayName("L'année d'une série/séance/paiement/présence se résout via le groupe")
        void childYearResolvesThroughGroup() {
            // --- Arrange : une année, un groupe rattaché, et une grappe d'enfants ---
            SchoolYearEntity year = SchoolYearEntity.builder()
                    .label("2025-2026")
                    .startDate(new Date())
                    .endDate(new Date())
                    .isCurrent(true)
                    .build();
            em.persist(year);

            GroupEntity group = GroupEntity.builder()
                    .name("Groupe résolution")
                    .schoolYear(year)
                    .build();
            em.persist(group);

            SessionSeriesEntity series = SessionSeriesEntity.builder()
                    .name("Série 1")
                    .group(group)
                    .build();
            em.persist(series);

            SessionEntity session = SessionEntity.builder()
                    .title("Séance 1")
                    .group(group)
                    .sessionSeries(series)
                    .build();
            em.persist(session);

            StudentEntity student = StudentEntity.builder()
                    .firstName("Amina")
                    .lastName("Test")
                    .build();
            em.persist(student);

            PaymentEntity payment = PaymentEntity.builder()
                    .student(student)
                    .session(session)
                    .sessionSeries(series)
                    .group(group)
                    .amountPaid(100.0)
                    .build();
            em.persist(payment);

            AttendanceEntity attendance = AttendanceEntity.builder()
                    .student(student)
                    .session(session)
                    .sessionSeries(series)
                    .group(group)
                    .isPresent(true)
                    .build();
            em.persist(attendance);

            em.flush();
            em.clear();

            // --- Assert : chaque enfant retrouve la même année via son groupe ---
            SessionSeriesEntity reloadedSeries = em.find(SessionSeriesEntity.class, series.getId());
            assertThat(reloadedSeries.getGroup().getSchoolYear().getId())
                    .as("l'année d'une série est celle de son groupe")
                    .isEqualTo(year.getId());

            SessionEntity reloadedSession = em.find(SessionEntity.class, session.getId());
            assertThat(reloadedSession.getGroup().getSchoolYear().getId())
                    .as("l'année d'une séance est celle de son groupe")
                    .isEqualTo(year.getId());
            assertThat(reloadedSession.getSessionSeries().getGroup().getSchoolYear().getId())
                    .as("l'année d'une séance se résout aussi via série → groupe")
                    .isEqualTo(year.getId());

            PaymentEntity reloadedPayment = em.find(PaymentEntity.class, payment.getId());
            assertThat(reloadedPayment.getGroup().getSchoolYear().getId())
                    .as("l'année d'un paiement est celle de son groupe")
                    .isEqualTo(year.getId());

            AttendanceEntity reloadedAttendance = em.find(AttendanceEntity.class, attendance.getId());
            assertThat(reloadedAttendance.getGroup().getSchoolYear().getId())
                    .as("l'année d'une présence est celle de son groupe")
                    .isEqualTo(year.getId());
        }
    }
}
