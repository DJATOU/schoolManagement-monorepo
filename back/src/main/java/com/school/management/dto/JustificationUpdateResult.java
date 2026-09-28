package com.school.management.dto;

/**
 * Résultat d'une demande de modification de la justification (exigences 4.2, 4.3).
 *
 * @param attendanceId présence visée
 * @param justified    valeur courante après traitement
 * @param changed      faux lorsque la valeur demandée égalait déjà la valeur courante. La demande
 *                     est alors un succès sans écriture ni entrée d'audit : la piste ne consigne
 *                     que les changements réels, sinon elle se remplirait de lignes sans information
 */
public record JustificationUpdateResult(
        Long attendanceId,
        Boolean justified,
        boolean changed) {
}
