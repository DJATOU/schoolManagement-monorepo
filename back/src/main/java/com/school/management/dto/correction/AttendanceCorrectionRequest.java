package com.school.management.dto.correction;

/**
 * Corriger une présence (spec admin-corrections, exigence 8).
 *
 * <p>Une seule forme pour les trois corrections ; chacune ne lit que ses champs :</p>
 * <ul>
 *   <li>présent ↔ absent : {@code present}, et {@code justified} pour une absence ;</li>
 *   <li>ajout : {@code studentId}, {@code present}, {@code justified} ;</li>
 *   <li>retrait : aucun.</li>
 * </ul>
 *
 * @param reasonType   Motif, parmi ceux de {@code GET /api/attendances/correction-reasons}
 * @param reasonText   explication, obligatoire pour {@code OTHER}
 * @param previewToken jeton de l'Aperçu lu ; ignoré en Aperçu, obligatoire en confirmation
 */
public record AttendanceCorrectionRequest(
        Long studentId,
        Boolean present,
        Boolean justified,
        String reasonType,
        String reasonText,
        String previewToken) {

    /** Corps absent : tous les champs nuls, chaque correction dit ce qui lui manque. */
    public static AttendanceCorrectionRequest orEmpty(AttendanceCorrectionRequest request) {
        return request == null ? new AttendanceCorrectionRequest(null, null, null, null, null, null) : request;
    }
}
