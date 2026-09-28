package com.school.management.persistance;

import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Tests d'intégration (Spring Boot Test, H2 en mémoire) des valeurs par défaut et
 * des contraintes de persistance des entités introduites par la fonctionnalité
 * année scolaire.
 *
 * <p>Couvre :</p>
 * <ul>
 *   <li>{@link SchoolYearEntity#getIsCurrent()} vaut {@code false} par défaut à la
 *       persistance (Exigences 1.1, 2.1) ;</li>
 *   <li>{@link StudentEntity#getStatus()} vaut {@code ACTIVE} par défaut à la
 *       persistance (Exigence 7.1) ;</li>
 *   <li>l'insertion d'un second {@link SchoolYearEntity} portant le même
 *       {@code label} est rejetée par la contrainte d'unicité
 *       {@code uk_school_year_label} (Exigence 1.4) ;</li>
 *   <li>la colonne {@code school_year_id} de {@link GroupEntity} est nullable :
 *       un groupe sans année scolaire est persistable (Exigence 3.1, niveau colonne).</li>
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
class SchoolYearPersistenceIntegrationTest {

    @Autowired
    private TestEntityManager em;

    // ------------------------------------------------------------------
    // Fabriques de fixtures minimales
    // ------------------------------------------------------------------

    private SchoolYearEntity schoolYear(String label) {
        return SchoolYearEntity.builder()
                .label(label)
                .startDate(new Date())
                .endDate(new Date())
                .build();
    }

    // ==================================================================
    // Exigences 1.1, 2.1 — isCurrent par défaut à false
    // ==================================================================
    @Nested
    @DisplayName("SchoolYearEntity : isCurrent par défaut à false")
    class IsCurrentDefault {

        @Test
        @DisplayName("Persistance sans renseigner isCurrent → false")
        void isCurrentDefaultsToFalseOnPersist() {
            SchoolYearEntity persisted = em.persistFlushFind(schoolYear("2025-2026"));

            assertThat(persisted.getIsCurrent())
                    .as("isCurrent doit valoir false par défaut à la persistance")
                    .isFalse();
        }
    }

    // ==================================================================
    // Exigence 7.1 — status par défaut à ACTIVE
    // ==================================================================
    @Nested
    @DisplayName("StudentEntity : status par défaut à ACTIVE")
    class StatusDefault {

        @Test
        @DisplayName("Persistance sans renseigner status → ACTIVE")
        void statusDefaultsToActiveOnPersist() {
            StudentEntity student = StudentEntity.builder()
                    .firstName("Amina")
                    .lastName("Test")
                    .build();

            StudentEntity persisted = em.persistFlushFind(student);

            assertThat(persisted.getStatus())
                    .as("status doit valoir ACTIVE par défaut à la persistance")
                    .isEqualTo(StudentStatus.ACTIVE);
        }
    }

    // ==================================================================
    // Exigence 1.4 — label unique
    // ==================================================================
    @Nested
    @DisplayName("SchoolYearEntity : contrainte d'unicité sur label")
    class UniqueLabel {

        @Test
        @DisplayName("Deux années scolaires avec le même label → rejet à la contrainte d'unicité")
        void duplicateLabelIsRejected() {
            em.persist(schoolYear("2025-2026"));
            em.flush();

            SchoolYearEntity duplicate = schoolYear("2025-2026");

            assertThatThrownBy(() -> {
                em.persist(duplicate);
                em.flush();
            }).isInstanceOf(PersistenceException.class);
        }

        @Test
        @DisplayName("Deux libellés distincts → acceptés")
        void distinctLabelsAccepted() {
            em.persist(schoolYear("2025-2026"));
            em.persist(schoolYear("2026-2027"));

            assertDoesNotThrow(em::flush);
        }
    }

    // ==================================================================
    // Exigence 3.1 (niveau colonne) — school_year_id nullable sur Group
    // ==================================================================
    @Nested
    @DisplayName("GroupEntity : schoolYear nullable au niveau colonne")
    class GroupSchoolYearNullable {

        @Test
        @DisplayName("Persistance d'un groupe sans année scolaire → accepté")
        void groupWithoutSchoolYearIsPersistable() {
            GroupEntity group = GroupEntity.builder()
                    .name("Groupe sans année")
                    .build();

            GroupEntity persisted = em.persistFlushFind(group);

            assertThat(persisted.getId())
                    .as("un groupe sans année scolaire doit être persistable (colonne nullable)")
                    .isNotNull();
            assertThat(persisted.getSchoolYear())
                    .as("schoolYear reste null quand aucune année n'est associée")
                    .isNull();
        }
    }
}
