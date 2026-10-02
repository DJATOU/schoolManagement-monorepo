package com.school.management.dto.correction;

/**
 * Annuler un Encaissement (spec admin-corrections, exigence 2.1).
 *
 * @param reasonType   Motif, parmi ceux de {@code GET /api/encashments/correction-reasons}
 * @param reasonText   explication, obligatoire pour {@code OTHER}
 * @param previewToken jeton de l'Aperçu lu ; ignoré en Aperçu, obligatoire en confirmation
 */
public record CancelEncashmentRequest(String reasonType, String reasonText, String previewToken) {
}
