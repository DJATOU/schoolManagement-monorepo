package com.school.management.dto.session;

import java.util.Date;
import java.util.List;

/**
 * Compte rendu d'une création de séances récurrentes.
 *
 * @param created    nombre de séances créées
 * @param skipped    nombre d'occurrences écartées (créneau déjà occupé)
 * @param sessionIds identifiants des séances créées, dans l'ordre chronologique
 * @param seriesIds  identifiants des séries touchées ou créées par la génération
 * @param conflicts  occurrences écartées, avec leur motif
 */
public record RecurringSessionResultDTO(
        int created,
        int skipped,
        List<Long> sessionIds,
        List<Long> seriesIds,
        List<Conflict> conflicts) {

    /**
     * Occurrence non créée.
     *
     * @param start  début du créneau refusé
     * @param reason code de motif : {@code ROOM_BUSY} ou {@code TEACHER_BUSY}
     * @param detail nom de la ressource en cause (salle ou enseignant)
     */
    public record Conflict(Date start, String reason, String detail) {
    }
}
