package com.school.management.dto.correction;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.util.List;

/**
 * Corriger les dates d'une inscription (spec admin-corrections, exigences 5 et 6).
 *
 * <p>Une seule forme pour les trois corrections ; chacune ne lit que ses champs :</p>
 * <ul>
 *   <li>arrivée : {@code arrival} ;</li>
 *   <li>départ : {@code departure}, et {@code removePresencesAfter} pour retirer aussi les présences
 *       ordinaires postérieures au départ ;</li>
 *   <li>réouverture : aucun.</li>
 * </ul>
 *
 * @param attendances  présence ou absence à noter sur des séances validées que la correction fait
 *                     entrer dans la période, sans présence de l'étudiant (exigence 5.7)
 * @param reasonType   Motif, parmi ceux de {@code GET /api/enrolments/correction-reasons}
 * @param reasonText   explication, obligatoire pour {@code OTHER}
 * @param previewToken jeton de l'Aperçu lu ; ignoré en Aperçu, obligatoire en confirmation
 */
public record EnrolmentCorrectionRequest(
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate arrival,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate departure,
        Boolean removePresencesAfter,
        List<AttendanceMark> attendances,
        String reasonType,
        String reasonText,
        String previewToken) {

    /** Présence ({@code present = true}) ou absence à noter sur une séance. */
    public record AttendanceMark(Long sessionId, Boolean present) {
    }
}
