package com.school.management.service;

import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentStatus;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.SchoolYearRepository;
import com.school.management.repository.StudentRepository;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de propriété (jqwik) pour la complétude de la migration « année scolaire »
 * ({@link SchoolYearMigrationRunner}), en intégration H2 réelle.
 *
 * <p>Pour tout état pré-migration de groupes (sans année scolaire) et d'étudiants (statut nul
 * ou déjà {@code ACTIVE}), après exécution de la migration :</p>
 * <ol>
 *   <li>il existe une (unique) année scolaire courante (Exigence 12.1 / 12.2) ;</li>
 *   <li>aucun groupe n'a d'année scolaire nulle (Exigences 12.2, 12.5) ;</li>
 *   <li>tout étudiant a le statut {@code ACTIVE} (Exigence 12.4).</li>
 * </ol>
 *
 * <p>jqwik s'exécute sur son propre moteur JUnit Platform : les tranches de test Spring
 * ({@code @DataJpaTest}) ne s'appliquent pas aux méthodes {@code @Property}. Un contexte Spring
 * ciblé (mêmes auto-configurations que {@code @DataJpaTest} : datasource, JPA/Hibernate, dépôts,
 * transactions) est donc amorcé une seule fois par conteneur via {@link BeforeContainer}, sur une
 * base H2 en mémoire. Le {@link SchoolYearMigrationRunner} est déclaré comme bean afin que son
 * {@code @Transactional migrate()} bénéficie du proxy transactionnel. La base est vidée au début
 * de chaque essai pour repartir d'un état propre (la migration étant idempotente, une année
 * scolaire résiduelle la rendrait inopérante).</p>
 */
class SchoolYearMigrationCompletenessPropertyTest {

    private static ConfigurableApplicationContext context;
    private static SchoolYearRepository schoolYearRepository;
    private static GroupRepository groupRepository;
    private static StudentRepository studentRepository;
    private static SchoolYearMigrationRunner migrationRunner;

    // ------------------------------------------------------------------
    // Cycle de vie : contexte Spring + H2 amorcé une fois par conteneur
    // ------------------------------------------------------------------

    @BeforeContainer
    static void startContext() {
        // Les arguments de ligne de commande priment sur l'application.properties du module
        // principal (PostgreSQL) : on force ici une base H2 en mémoire dédiée au test.
        context = new SpringApplicationBuilder(MigrationTestContext.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.datasource.url=jdbc:h2:mem:migration-pbt;DB_CLOSE_DELAY=-1",
                        "--spring.datasource.driverClassName=org.h2.Driver",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                        "--spring.jpa.hibernate.ddl-auto=create-drop",
                        "--spring.jpa.show-sql=false",
                        "--spring.main.banner-mode=off");

        schoolYearRepository = context.getBean(SchoolYearRepository.class);
        groupRepository = context.getBean(GroupRepository.class);
        studentRepository = context.getBean(StudentRepository.class);
        migrationRunner = context.getBean(SchoolYearMigrationRunner.class);
    }

    @AfterContainer
    static void stopContext() {
        if (context != null) {
            context.close();
        }
    }

    // ------------------------------------------------------------------
    // Property 13 — Migration completeness
    // ------------------------------------------------------------------

    // Feature: school-year, Property 13: For any pre-migration state of Groups and Students, after migration there is a current School Year, no Group has a null School Year, and every Student has status ACTIVE.
    @Property(tries = 100)
    void property13_migrationCompleteness(
            @ForAll @IntRange(min = 0, max = 8) int groupCount,
            @ForAll("preMigrationStatuses") List<StudentStatus> studentStatuses) {

        // --- Arrange : repartir d'une base vierge (migration idempotente) ---
        resetDatabase();

        // Groupes pré-migration : aucun n'a d'année scolaire (school_year_id null).
        for (int i = 0; i < groupCount; i++) {
            groupRepository.save(GroupEntity.builder()
                    .name("Groupe pré-migration " + i)
                    .build());
        }

        // Étudiants pré-migration : statut nul (colonne non renseignée) ou déjà ACTIVE.
        for (int i = 0; i < studentStatuses.size(); i++) {
            StudentEntity student = StudentEntity.builder()
                    .firstName("Étudiant " + i)
                    .lastName("Test")
                    .build();
            // Le builder positionne ACTIVE par défaut : on force explicitement le statut généré
            // (y compris null) pour reproduire fidèlement un état pré-migration.
            student.setStatus(studentStatuses.get(i));
            studentRepository.save(student);
        }

        // --- Act : exécuter la migration ---
        migrationRunner.migrate();

        // --- Assert : les trois post-conditions de la Property 13 ---

        // (1) Il existe exactement une année scolaire courante.
        assertThat(schoolYearRepository.findByIsCurrentTrue())
                .as("après migration, une année scolaire courante doit exister")
                .isPresent();
        long currentCount = schoolYearRepository.findAll().stream()
                .filter(sy -> Boolean.TRUE.equals(sy.getIsCurrent()))
                .count();
        assertThat(currentCount)
                .as("exactement une année scolaire courante")
                .isEqualTo(1L);

        // (2) Aucun groupe n'a d'année scolaire nulle.
        assertThat(groupRepository.findBySchoolYearIsNull())
                .as("après migration, aucun groupe ne doit avoir d'année scolaire nulle")
                .isEmpty();
        SchoolYearEntity currentYear = schoolYearRepository.findByIsCurrentTrue().orElseThrow();
        assertThat(groupRepository.findAll())
                .as("tout groupe est rattaché à l'année scolaire initiale (courante)")
                .allSatisfy(group -> {
                    assertThat(group.getSchoolYear()).isNotNull();
                    assertThat(group.getSchoolYear().getId()).isEqualTo(currentYear.getId());
                });

        // (3) Tout étudiant a le statut ACTIVE.
        assertThat(studentRepository.findAll())
                .as("après migration, tout étudiant doit avoir le statut ACTIVE")
                .allSatisfy(student ->
                        assertThat(student.getStatus()).isEqualTo(StudentStatus.ACTIVE));
    }

    // ------------------------------------------------------------------
    // Nettoyage : vider les tables dans l'ordre respectant les clés étrangères
    // ------------------------------------------------------------------

    private void resetDatabase() {
        // Les étudiants d'abord (côté propriétaire du lien student_groups), puis les groupes
        // (FK vers school_year), enfin les années scolaires.
        studentRepository.deleteAll();
        groupRepository.deleteAll();
        schoolYearRepository.deleteAll();
    }

    // ------------------------------------------------------------------
    // Générateurs
    // ------------------------------------------------------------------

    /**
     * Statuts d'étudiants représentatifs d'un état pré-migration : soit {@code null} (colonne non
     * renseignée avant la migration), soit {@code ACTIVE} (valeur par défaut). Aucun {@code INACTIVE}
     * n'est généré car ce statut ne peut apparaître qu'après le déploiement de la fonctionnalité.
     */
    @Provide
    Arbitrary<StudentStatus> preMigrationStatus() {
        return Arbitraries.just(StudentStatus.ACTIVE).injectNull(0.5);
    }

    /** Listes de statuts pré-migration (mélange de null et d'ACTIVE), y compris la liste vide. */
    @Provide
    Arbitrary<List<StudentStatus>> preMigrationStatuses() {
        return preMigrationStatus().list().ofMaxSize(8);
    }

    // ------------------------------------------------------------------
    // Contexte Spring ciblé (équivalent aux auto-configurations de @DataJpaTest)
    // ------------------------------------------------------------------

    @Configuration
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class,
            TransactionAutoConfiguration.class
    })
    @EntityScan("com.school.management.persistance")
    @EnableJpaRepositories("com.school.management.repository")
    static class MigrationTestContext {

        /**
         * Déclare le runner comme bean pour que son {@code @Transactional migrate()} soit
         * intercepté par le proxy transactionnel. Le libellé initial n'est pas injecté ici
         * (constructeur direct) : le runner dérive alors un libellé par défaut de la date du jour.
         */
        @Bean
        SchoolYearMigrationRunner schoolYearMigrationRunner(SchoolYearRepository schoolYearRepository,
                                                            GroupRepository groupRepository,
                                                            StudentRepository studentRepository) {
            return new SchoolYearMigrationRunner(schoolYearRepository, groupRepository, studentRepository);
        }
    }
}
