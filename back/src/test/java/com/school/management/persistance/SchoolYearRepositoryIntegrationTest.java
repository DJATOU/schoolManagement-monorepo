package com.school.management.persistance;

import com.school.management.repository.GroupRepository;
import com.school.management.repository.LevelRepository;
import com.school.management.repository.SchoolYearRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests d'intégration (Spring Boot Test, H2 en mémoire) des requêtes des dépôts
 * introduits ou étendus par la fonctionnalité année scolaire.
 *
 * <p>Vérifie, contre des données amorcées via {@link TestEntityManager} :</p>
 * <ul>
 *   <li>{@link SchoolYearRepository#findAllByOrderByStartDateDesc()} retourne les
 *       années scolaires triées par date de début décroissante (Exigence 1.6) ;</li>
 *   <li>{@link SchoolYearRepository#findByIsCurrentTrue()} retourne l'unique année
 *       courante (Exigence 2.5) ;</li>
 *   <li>{@link SchoolYearRepository#findByLabel(String)} retourne l'année portant
 *       le libellé demandé (Exigence 2.5) ;</li>
 *   <li>{@link GroupRepository#findBySchoolYearIsNull()} ne retourne que les groupes
 *       sans année scolaire (Exigence 12.2) ;</li>
 *   <li>{@link LevelRepository#findAllByOrderByLevelSequenceAsc()} retourne les
 *       niveaux triés par rang croissant (Exigence 8.3).</li>
 * </ul>
 *
 * <p>{@code @DataJpaTest} remplace la source de données par une base H2 embarquée.
 * Le {@code @TestPropertySource} force le dialecte H2 et {@code create-drop} pour
 * que Hibernate génère le schéma à partir des entités, sans conflit avec la
 * configuration PostgreSQL du module principal.</p>
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class SchoolYearRepositoryIntegrationTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private SchoolYearRepository schoolYearRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private LevelRepository levelRepository;

    // ------------------------------------------------------------------
    // Fabriques de fixtures minimales
    // ------------------------------------------------------------------

    private static Date date(int year, int month, int day) {
        return Date.from(LocalDate.of(year, month, day)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant());
    }

    private SchoolYearEntity schoolYear(String label, Date startDate, boolean isCurrent) {
        return SchoolYearEntity.builder()
                .label(label)
                .startDate(startDate)
                .endDate(new Date())
                .isCurrent(isCurrent)
                .build();
    }

    private LevelEntity level(String name, Integer sequence) {
        return LevelEntity.builder()
                .name(name)
                .levelSequence(sequence)
                .build();
    }

    // ==================================================================
    // Exigence 1.6 — tri par date de début décroissante
    // ==================================================================
    @Nested
    @DisplayName("SchoolYearRepository : findAllByOrderByStartDateDesc")
    class OrderByStartDateDesc {

        @Test
        @DisplayName("Les années scolaires sont retournées de la plus récente à la plus ancienne")
        void ordersByStartDateDescending() {
            // Amorçage volontairement dans le désordre
            em.persist(schoolYear("2024-2025", date(2024, 9, 1), false));
            em.persist(schoolYear("2026-2027", date(2026, 9, 1), true));
            em.persist(schoolYear("2025-2026", date(2025, 9, 1), false));
            em.flush();

            List<SchoolYearEntity> result = schoolYearRepository.findAllByOrderByStartDateDesc();

            assertThat(result)
                    .extracting(SchoolYearEntity::getLabel)
                    .containsExactly("2026-2027", "2025-2026", "2024-2025");
        }
    }

    // ==================================================================
    // Exigence 2.5 — année courante unique
    // ==================================================================
    @Nested
    @DisplayName("SchoolYearRepository : findByIsCurrentTrue")
    class FindByIsCurrentTrue {

        @Test
        @DisplayName("Retourne l'unique année dont isCurrent vaut true")
        void returnsTheSingleCurrentYear() {
            em.persist(schoolYear("2024-2025", date(2024, 9, 1), false));
            em.persist(schoolYear("2025-2026", date(2025, 9, 1), true));
            em.persist(schoolYear("2023-2024", date(2023, 9, 1), false));
            em.flush();

            Optional<SchoolYearEntity> current = schoolYearRepository.findByIsCurrentTrue();

            assertThat(current)
                    .as("une seule année courante doit être retournée")
                    .isPresent()
                    .get()
                    .extracting(SchoolYearEntity::getLabel)
                    .isEqualTo("2025-2026");
        }
    }

    // ==================================================================
    // Exigence 2.5 — recherche par libellé
    // ==================================================================
    @Nested
    @DisplayName("SchoolYearRepository : findByLabel")
    class FindByLabel {

        @Test
        @DisplayName("Retourne l'année scolaire portant le libellé demandé")
        void returnsTheMatchingYear() {
            em.persist(schoolYear("2024-2025", date(2024, 9, 1), false));
            em.persist(schoolYear("2025-2026", date(2025, 9, 1), true));
            em.flush();

            Optional<SchoolYearEntity> found = schoolYearRepository.findByLabel("2024-2025");

            assertThat(found)
                    .isPresent()
                    .get()
                    .extracting(SchoolYearEntity::getLabel)
                    .isEqualTo("2024-2025");
        }

        @Test
        @DisplayName("Retourne vide quand aucun libellé ne correspond")
        void returnsEmptyWhenNoMatch() {
            em.persist(schoolYear("2025-2026", date(2025, 9, 1), true));
            em.flush();

            assertThat(schoolYearRepository.findByLabel("1999-2000")).isEmpty();
        }
    }

    // ==================================================================
    // Exigence 12.2 — groupes sans année scolaire
    // ==================================================================
    @Nested
    @DisplayName("GroupRepository : findBySchoolYearIsNull")
    class FindBySchoolYearIsNull {

        @Test
        @DisplayName("Ne retourne que les groupes dont l'année scolaire est null")
        void returnsOnlyGroupsWithoutSchoolYear() {
            SchoolYearEntity year = schoolYear("2025-2026", date(2025, 9, 1), true);
            em.persist(year);

            GroupEntity withYear = GroupEntity.builder()
                    .name("Groupe avec année")
                    .schoolYear(year)
                    .build();
            GroupEntity withoutYearA = GroupEntity.builder()
                    .name("Groupe sans année A")
                    .build();
            GroupEntity withoutYearB = GroupEntity.builder()
                    .name("Groupe sans année B")
                    .build();

            em.persist(withYear);
            em.persist(withoutYearA);
            em.persist(withoutYearB);
            em.flush();

            List<GroupEntity> orphans = groupRepository.findBySchoolYearIsNull();

            assertThat(orphans)
                    .extracting(GroupEntity::getName)
                    .containsExactlyInAnyOrder("Groupe sans année A", "Groupe sans année B");
        }
    }

    // ==================================================================
    // Exigence 8.3 — tri des niveaux par rang croissant
    // ==================================================================
    @Nested
    @DisplayName("LevelRepository : findAllByOrderByLevelSequenceAsc")
    class OrderByLevelSequenceAsc {

        @Test
        @DisplayName("Les niveaux sont retournés triés par level_sequence croissant")
        void ordersByLevelSequenceAscending() {
            // Amorçage volontairement dans le désordre
            em.persist(level("3ème", 3));
            em.persist(level("1ère", 1));
            em.persist(level("2ème", 2));
            em.flush();

            List<LevelEntity> result = levelRepository.findAllByOrderByLevelSequenceAsc();

            assertThat(result)
                    .extracting(LevelEntity::getLevelSequence)
                    .containsExactly(1, 2, 3);
            assertThat(result)
                    .extracting(LevelEntity::getName)
                    .containsExactly("1ère", "2ème", "3ème");
        }
    }
}
