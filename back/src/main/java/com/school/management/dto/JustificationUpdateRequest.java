package com.school.management.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corps de requête de modification de la justification d'une absence (exigence 4.1).
 *
 * <p><strong>Ce record est volontairement fermé à deux champs.</strong> Un {@code PATCH} générique
 * existait auparavant sur les présences : il projetait une {@code Map} arbitraire du client sur
 * l'entité via ModelMapper, ce qui rendait <em>n'importe quel</em> champ d'une présence écrasable —
 * l'étudiant, la séance, le statut de présence. Il a été retiré pour cette raison. N'exposer que la
 * valeur demandée et un commentaire ferme définitivement cette porte : aucun autre champ n'est
 * atteignable par ce point d'entrée.</p>
 *
 * @param justified valeur demandée de la justification. Obligatoire : l'absence de valeur serait
 *                  ambiguë entre « passer à non justifié » et « ne rien changer »
 * @param comment   commentaire libre expliquant la correction, facultatif, au plus 500 caractères.
 *                  Conservé dans la piste d'audit pour répondre à une contestation ultérieure
 */
public record JustificationUpdateRequest(
        @NotNull Boolean justified,
        @Size(max = 500) String comment) {
}
