package com.school.management.service;

import com.school.management.persistance.StudentStatus;

/**
 * Résultat de la décision de fin d'année pour un étudiant.
 *
 * <p>Produit par {@link PromotionCalculator#decide}, cet enregistrement décrit le niveau cible à
 * appliquer, le statut résultant de l'étudiant, et un indicateur de revue administrative.</p>
 *
 * @param targetLevelId identifiant du niveau à affecter à l'étudiant. Il vaut soit le niveau
 *                      courant, soit le niveau suivant, jamais un niveau fabriqué.
 * @param status        statut résultant de l'étudiant ({@link StudentStatus#ACTIVE} ou
 *                      {@link StudentStatus#INACTIVE}).
 * @param needsReview   {@code true} lorsqu'un étudiant déjà au niveau le plus élevé a fait l'objet
 *                      d'une demande de promotion : son cas doit être revu par l'administrateur.
 */
public record PromotionOutcome(
        Long targetLevelId,
        StudentStatus status,
        boolean needsReview) {
}
