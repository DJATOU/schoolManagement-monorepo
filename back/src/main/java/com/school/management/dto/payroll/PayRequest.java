package com.school.management.dto.payroll;

/**
 * Paie d'une série, en aperçu ou en confirmation.
 *
 * @param rateId       taux choisi ; ignoré pour une régularisation, qui reprend celui de la paie
 *                     initiale
 * @param note         note libre, imprimée sur le bordereau
 * @param previewToken jeton de l'aperçu lu ; obligatoire à la confirmation
 */
public record PayRequest(Long rateId, String note, String previewToken) {
}
