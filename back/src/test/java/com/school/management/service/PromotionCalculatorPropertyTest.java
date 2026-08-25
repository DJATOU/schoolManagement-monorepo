package com.school.management.service;

import com.school.management.persistance.StudentStatus;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de propriété (jqwik) pour {@link PromotionCalculator}.
 *
 * <p>Valide la logique pure de décision de promotion (niveau cible, statut, drapeau de revue)
 * sans base de données : les identifiants de niveaux et les décisions sont générés puis passés
 * directement au calculateur.</p>
 */
class PromotionCalculatorPropertyTest {

    private final PromotionCalculator calculator = new PromotionCalculator();

    // ------------------------------------------------------------------
    // Property 3 — Promotion decision correctness
    // ------------------------------------------------------------------

    // Feature: school-year, Property 3: For any Student current Level and any decision, decide yields next/ACTIVE for PROMOTION with next; unchanged/ACTIVE/needsReview for PROMOTION at highest; unchanged/ACTIVE for REDOUBLEMENT; unchanged/INACTIVE for DEPARTURE; defaulting to PROMOTION; outcome independent of enrollment.
    @Property(tries = 100)
    void property3_promotionDecisionCorrectness(
            @ForAll("levelIds") Long currentLevelId,
            @ForAll("optionalNextLevelIds") Optional<Long> nextLevelId,
            @ForAll("decisions") PromotionDecision decision,
            @ForAll boolean hasEnrollment) {

        // La décision effective : en l'absence de décision explicite, on retombe sur PROMOTION
        // (comportement par défaut appliqué par l'appelant, Requirement 5.7).
        PromotionDecision effectiveDecision = decision == null ? PromotionDecision.PROMOTION : decision;

        PromotionOutcome outcome = calculator.decide(currentLevelId, nextLevelId, effectiveDecision);

        switch (effectiveDecision) {
            case PROMOTION -> {
                if (nextLevelId.isPresent()) {
                    // Promotion avec niveau suivant : passe au niveau suivant, actif, sans revue.
                    assertThat(outcome.targetLevelId()).isEqualTo(nextLevelId.get());
                    assertThat(outcome.status()).isEqualTo(StudentStatus.ACTIVE);
                    assertThat(outcome.needsReview()).isFalse();
                } else {
                    // Promotion au niveau le plus élevé : niveau inchangé, actif, à revoir.
                    assertThat(outcome.targetLevelId()).isEqualTo(currentLevelId);
                    assertThat(outcome.status()).isEqualTo(StudentStatus.ACTIVE);
                    assertThat(outcome.needsReview()).isTrue();
                }
            }
            case REDOUBLEMENT -> {
                // Redoublement : niveau inchangé, actif, sans revue.
                assertThat(outcome.targetLevelId()).isEqualTo(currentLevelId);
                assertThat(outcome.status()).isEqualTo(StudentStatus.ACTIVE);
                assertThat(outcome.needsReview()).isFalse();
            }
            case DEPARTURE -> {
                // Départ : niveau inchangé, inactif, sans revue.
                assertThat(outcome.targetLevelId()).isEqualTo(currentLevelId);
                assertThat(outcome.status()).isEqualTo(StudentStatus.INACTIVE);
                assertThat(outcome.needsReview()).isFalse();
            }
        }

        // L'issue ne dépend que du (niveau courant, niveau suivant, décision) : elle est
        // identique quelle que soit l'existence d'une inscription (Requirement 14.3).
        PromotionOutcome outcomeIndependentOfEnrollment =
                calculator.decide(currentLevelId, nextLevelId, effectiveDecision);
        assertThat(outcome)
                .as("l'issue doit être indépendante de l'inscription (hasEnrollment=%s)", hasEnrollment)
                .isEqualTo(outcomeIndependentOfEnrollment);
    }

    // ------------------------------------------------------------------
    // Property 4 — Promotion never produces a non-existent Level
    // ------------------------------------------------------------------

    // Feature: school-year, Property 4: For any current Level, next-Level option, and decision, the targetLevelId returned by decide is always one of the supplied Level ids (current or next); never a Level absent from the Level_Sequence.
    @Property(tries = 100)
    void property4_promotionNeverProducesNonExistentLevel(
            @ForAll("levelIds") Long currentLevelId,
            @ForAll("optionalNextLevelIds") Optional<Long> nextLevelId,
            @ForAll("decisions") PromotionDecision decision) {

        PromotionOutcome outcome = calculator.decide(currentLevelId, nextLevelId, decision);

        // Le niveau cible est toujours l'un des identifiants fournis : le niveau courant ou,
        // s'il existe, le niveau suivant. Jamais un identifiant fabriqué (Requirement 8.3).
        if (nextLevelId.isPresent()) {
            assertThat(outcome.targetLevelId())
                    .as("targetLevelId doit être le niveau courant ou le niveau suivant fourni")
                    .isIn(currentLevelId, nextLevelId.get());
        } else {
            assertThat(outcome.targetLevelId())
                    .as("targetLevelId doit être le niveau courant fourni")
                    .isEqualTo(currentLevelId);
        }
    }

    // ------------------------------------------------------------------
    // Générateurs
    // ------------------------------------------------------------------

    /** Identifiants de niveaux non nuls, incluant des cas limites (1, valeurs élevées). */
    @Provide
    Arbitrary<Long> levelIds() {
        return Arbitraries.longs().between(1L, 1_000_000L);
    }

    /**
     * Option de niveau suivant : présente (identifiant distinct possible) ou vide (niveau le
     * plus élevé). Couvre les deux branches de la promotion.
     */
    @Provide
    Arbitrary<Optional<Long>> optionalNextLevelIds() {
        Arbitrary<Optional<Long>> present =
                Arbitraries.longs().between(1L, 1_000_000L).map(Optional::of);
        Arbitrary<Optional<Long>> empty = Arbitraries.just(Optional.empty());
        return Arbitraries.oneOf(present, empty);
    }

    /** Toutes les décisions de promotion possibles. */
    @Provide
    Arbitrary<PromotionDecision> decisions() {
        return Arbitraries.of(PromotionDecision.class);
    }
}
