package com.school.management.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * Entrée de la piste d'audit de la justification d'une absence (exigences 5.1, 5.7).
 *
 * @param id           identifiant de l'entrée
 * @param attendanceId présence auditée
 * @param oldValue     valeur avant modification. Nulle lorsque la justification n'avait jamais été
 *                     renseignée, ce qui est distinct d'un « non » explicite
 * @param newValue     valeur appliquée
 * @param performedBy  auteur, ou {@code system} en l'absence d'utilisateur authentifié
 * @param performedAt  horodatage à la milliseconde, {@code yyyy-MM-ddTHH:mm:ss.SSS} : sans format,
 *                     {@code @EnableWebMvc} le sérialise en tableau, que le pipe {@code date}
 *                     d'Angular refuse
 * @param comment      commentaire fourni, nul si aucun
 */
public record JustificationAuditDTO(
        Long id,
        Long attendanceId,
        Boolean oldValue,
        Boolean newValue,
        String performedBy,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
        LocalDateTime performedAt,
        String comment) {
}
