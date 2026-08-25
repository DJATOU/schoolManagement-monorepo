package com.school.management.service;

import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.SchoolYearRepository;
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
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de propriété (jqwik) pour le filtrage des groupes par année scolaire
 * ({@link GroupRepository#findBySchoolYearId(Long)}), en intégration H2 réelle.
 *
 * <p>Pour tout ensemble de groupes répartis sur plusieurs années scolaires et toute année
 * spécifiée, la requête de filtrage par année retourne exactement les groupes dont
 * l'année scolaire est égale à l'année spécifiée, et aucun autre.</p>
 *
 * <p>jqwik s'exécute sur son propre moteur JUnit Platform : les tranches de test Spring
 * ({@code @DataJpaTest}) ne s'appliquent pas aux méthodes {@code @Property}. Un contexte Spring
 * ciblé (mêmes auto-configurations que {@code @DataJpaTest} : datasource, JPA/Hibernate, dépôts,
 * transactions) est donc amorcé une seule fois par conteneur via {@link BeforeContainer}, sur une
 * base H2 en mémoire. La base est vidée au début de chaque essai pour repartir d'un état propre
 * (le libellé d'année scolaire portant une contrainte d'unicité, on génère des libellés distincts
 * par essai en repartant d'une base vierge).</p>
 *
 * <p>Feature: school-year, Property 8: For any set of Groups spread across School Years and any
 * specified year, the filter-by-year query returns exactly the Groups whose schoolYear equals the
 * specified year and no others.</p>
 *
 * <p><b>Validates: Requirements 10.4, 10.5</b></p>
 */
class GroupFilteringBySchoolYearPropertyTest {

    private static ConfigurableApplicationContext context;
    private static SchoolYearRepository schoolYearRepository;
    private static GroupRepository groupRepository;

    // ------------------------------------------------------------------
    // Cycle de vie : contexte Spring + H2 amorcé une fois par conteneur
    // ------------------------------------------------------------------

    @BeforeContainer
    static void startContext() {
        // Les arguments de ligne de commande priment sur l'application.properties du module
        // principal (PostgreSQL) : on force ici une base H2 en mémoire dédiée au test.
        context = new SpringApplicationBuilder(GroupFilteringTestContext.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.datasource.url=jdbc:h2:mem:group-filter-pbt;DB_CLOSE_DELAY=-1",
                        "--spring.datasource.driverClassName=org.h2.Driver",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                        "--spring.jpa.hibernate.ddl-auto=create-drop",
                        "--spring.jpa.show-sql=false",
                        "--spring.main.banner-mode=off");

        schoolYearRepository = context.getBean(SchoolYearRepository.class);
        groupRepository = context.getBean(GroupRepository.class);
    }

    @AfterContainer
    static void stopContext() {
        if (context != null) {
            context.close();
        }
    }

    // ------------------------------------------------------------------
    // Property 8 — Group filtering by School Year
    // ------------------------------------------------------------------

    // Feature: school-year, Property 8: For any set of Groups spread across School Years and any specified year, the filter-by-year query returns exactly the Groups whose schoolYear equals the specified year and no others.
    @Property(tries = 100)
    void property8_groupFilteringBySchoolYear(
            @ForAll @IntRange(min = 1, max = 5) int yearCount,
            @ForAll("groupAssignments") List<Integer> groupYearIndices) {

        // --- Arrange : repartir d'une base vierge (libellés uniques par essai) ---
        resetDatabase();

        // Créer N années scolaires distinctes (libellés uniques imposés par la contrainte).
        List<SchoolYearEntity> years = new ArrayList<>();
        for (int i = 0; i < yearCount; i++) {
            int startYear = 2000 + i;
            SchoolYearEntity year = SchoolYearEntity.builder()
                    .label(startYear + "-" + (startYear + 1))
                    .startDate(date(startYear, 9, 1))
                    .endDate(date(startYear + 1, 6, 30))
                    .isCurrent(i == 0)
                    .build();
            years.add(schoolYearRepository.save(year));
        }

        // Assigner chaque groupe généré à l'une des années (certaines années peuvent
        // rester sans aucun groupe : l'indice n'y renvoie jamais).
        // On mémorise, par année, l'ensemble des identifiants de groupes attendus.
        int groupIndex = 0;
        for (Integer rawIndex : groupYearIndices) {
            int yearIdx = rawIndex % yearCount;
            SchoolYearEntity assignedYear = years.get(yearIdx);
            GroupEntity group = GroupEntity.builder()
                    .name("Groupe " + groupIndex++)
                    .schoolYear(assignedYear)
                    .build();
            groupRepository.save(group);
        }

        // --- Act & Assert : pour chaque année, le filtre retourne exactement ses groupes ---
        for (SchoolYearEntity year : years) {
            // Ensemble attendu : identifiants des groupes réellement rattachés à cette année.
            Set<Long> expectedGroupIds = groupRepository.findAll().stream()
                    .filter(g -> g.getSchoolYear() != null
                            && g.getSchoolYear().getId().equals(year.getId()))
                    .map(GroupEntity::getId)
                    .collect(Collectors.toSet());

            List<GroupEntity> filtered = groupRepository.findBySchoolYearId(year.getId());

            // (1) Le filtre retourne exactement l'ensemble attendu (ni plus, ni moins).
            assertThat(filtered)
                    .as("le filtre par année %s doit retourner exactement les groupes de cette année", year.getLabel())
                    .extracting(GroupEntity::getId)
                    .containsExactlyInAnyOrderElementsOf(expectedGroupIds);

            // (2) Aucun groupe retourné n'appartient à une autre année.
            assertThat(filtered)
                    .as("aucun groupe retourné ne doit appartenir à une autre année")
                    .allSatisfy(g -> {
                        assertThat(g.getSchoolYear()).isNotNull();
                        assertThat(g.getSchoolYear().getId()).isEqualTo(year.getId());
                    });
        }
    }

    // ------------------------------------------------------------------
    // Nettoyage : vider les tables dans l'ordre respectant les clés étrangères
    // ------------------------------------------------------------------

    private void resetDatabase() {
        // Les groupes d'abord (FK vers school_year), puis les années scolaires.
        groupRepository.deleteAll();
        schoolYearRepository.deleteAll();
    }

    // ------------------------------------------------------------------
    // Fabrique de dates
    // ------------------------------------------------------------------

    private static Date date(int year, int month, int day) {
        return Date.from(LocalDate.of(year, month, day)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant());
    }

    // ------------------------------------------------------------------
    // Générateurs
    // ------------------------------------------------------------------

    /**
     * Listes d'indices d'année (bruts) pour l'affectation des groupes, y compris la liste vide
     * (aucun groupe). Chaque indice est ramené modulo le nombre d'années dans le test ; certaines
     * années peuvent ainsi ne recevoir aucun groupe.
     */
    @Provide
    Arbitrary<List<Integer>> groupAssignments() {
        return Arbitraries.integers().between(0, 1000).list().ofMaxSize(12);
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
    static class GroupFilteringTestContext {
    }
}
