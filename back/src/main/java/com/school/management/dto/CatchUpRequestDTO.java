package com.school.management.dto;

/**
 * DTO minimal de création d'une demande de rattrapage (catch-up).
 *
 * <p>Porte les données strictement nécessaires à la création d'une demande, alignées
 * sur le payload envoyé par le front {@code catch-up.service.ts} :
 * l'étudiant, la séance manquée d'origine, le groupe d'origine et la fiche de présence
 * (absence) à l'origine de la demande. Un champ {@code notes} facultatif est accepté.</p>
 *
 * <p>La validation métier (droit au rattrapage, séance manquée payée, compatibilité de
 * groupe) est assurée par {@code CatchUpService}. Le mapper MapStruct complet ainsi que
 * le DTO de réponse seront ajoutés ultérieurement (tâche 14.1) ; cette requête reste
 * volontairement focalisée sur la création.</p>
 *
 * @param studentId            identifiant de l'étudiant concerné
 * @param originalSessionId    identifiant de la séance manquée d'origine
 * @param originalGroupId       identifiant du groupe d'origine (facultatif ; à défaut, le
 *                              groupe de la séance manquée est utilisé)
 * @param originalAttendanceId identifiant de la fiche de présence (absence) d'origine
 * @param notes                notes libres facultatives
 */
public record CatchUpRequestDTO(
        Long studentId,
        Long originalSessionId,
        Long originalGroupId,
        Long originalAttendanceId,
        String notes) {
}
