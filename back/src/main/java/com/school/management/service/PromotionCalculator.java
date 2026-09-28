package com.school.management.service;

import com.school.management.persistance.StudentStatus;

import java.util.Optional;

/**
 * Calculateur de promotion <strong>pur</strong> (sans Spring, sans I/O).
 *
 * <p>Décide, pour un étudiant, le niveau cible et le statut résultant à partir d'un triplet
 * (niveau courant, niveau suivant éventuel, décision). Aucune entité n'est chargée et aucun
 * effet de bord n'est produit : la logique est donc trivialement testable par propriétés.</p>
 *
 * <p>Le niveau cible retourné est toujours le niveau courant ou le niveau suivant fourni, jamais
 * un identifiant fabriqué, garantissant qu'aucun niveau absent du Level_Sequence n'est produit.</p>
 */
public final class PromotionCalculator {

    /**
     * Décide l'issue de fin d'année pour un étudiant.
     *
     * <p>Règles appliquées :</p>
     * <ul>
     *   <li>{@link PromotionDecision#PROMOTION} avec un niveau suivant présent → niveau suivant,
     *       statut {@link StudentStatus#ACTIVE}, {@code needsReview = false}.</li>
     *   <li>{@link PromotionDecision#PROMOTION} sans niveau suivant (niveau le plus élevé) →
     *       niveau courant inchangé, statut {@link StudentStatus#ACTIVE},
     *       {@code needsReview = true} (à revoir par l'administrateur).</li>
     *   <li>{@link PromotionDecision#REDOUBLEMENT} → niveau courant inchangé, statut
     *       {@link StudentStatus#ACTIVE}, {@code needsReview = false}.</li>
     *   <li>{@link PromotionDecision#DEPARTURE} → niveau courant inchangé, statut
     *       {@link StudentStatus#INACTIVE}, {@code needsReview = false}.</li>
     * </ul>
     *
     * @param currentLevelId identifiant du niveau courant de l'étudiant (non nul).
     * @param nextLevelId    identifiant du niveau suivant, ou {@link Optional#empty()} si le
     *                       niveau courant est le plus élevé (non nul).
     * @param decision       la décision appliquée (non nulle).
     * @return l'issue calculée.
     * @throws IllegalArgumentException si {@code currentLevelId}, {@code nextLevelId} ou
     *                                  {@code decision} est nul.
     */
    public PromotionOutcome decide(Long currentLevelId,
                                   Optional<Long> nextLevelId,
                                   PromotionDecision decision) {
        if (currentLevelId == null) {
            throw new IllegalArgumentException("Le niveau courant ne peut pas être nul.");
        }
        if (nextLevelId == null) {
            throw new IllegalArgumentException("L'option du niveau suivant ne peut pas être nulle.");
        }
        if (decision == null) {
            throw new IllegalArgumentException("La décision ne peut pas être nulle.");
        }

        return switch (decision) {
            case PROMOTION -> nextLevelId
                    .map(next -> new PromotionOutcome(next, StudentStatus.ACTIVE, false))
                    .orElseGet(() -> new PromotionOutcome(currentLevelId, StudentStatus.ACTIVE, true));
            case REDOUBLEMENT -> new PromotionOutcome(currentLevelId, StudentStatus.ACTIVE, false);
            case DEPARTURE -> new PromotionOutcome(currentLevelId, StudentStatus.INACTIVE, false);
        };
    }
}
