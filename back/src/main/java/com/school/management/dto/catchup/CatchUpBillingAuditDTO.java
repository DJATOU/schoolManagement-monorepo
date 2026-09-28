package com.school.management.dto.catchup;

import java.time.LocalDateTime;

/**
 * Entrée de la piste d'audit des décisions de facturation d'un rattrapage.
 *
 * <p>Sert à répondre à une contestation : qui a décidé quoi, quand, et à partir de quelle valeur.
 * Les valeurs sont rendues en texte, {@code null} conservant son sens propre — « aucune séance
 * désignée » ou « décision non tranchée ».</p>
 *
 * @param id           identifiant de l'entrée
 * @param field        décision modifiée (séance manquée ou indicateur « déjà payée »)
 * @param oldValue     valeur avant la modification, nulle si aucune
 * @param newValue     valeur après la modification
 * @param performedBy  auteur de la modification
 * @param performedAt  horodatage de la modification
 * @param sequenceRank rang de séquence, qui départage deux entrées de même horodatage
 * @param comment      commentaire de l'auteur
 */
public record CatchUpBillingAuditDTO(
        Long id,
        String field,
        String oldValue,
        String newValue,
        String performedBy,
        LocalDateTime performedAt,
        Long sequenceRank,
        String comment) {
}
