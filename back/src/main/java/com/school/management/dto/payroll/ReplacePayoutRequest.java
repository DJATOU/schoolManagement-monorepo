package com.school.management.dto.payroll;

/**
 * Remplacement d'une paie initiale par une paie à un autre taux, en Aperçu comme en confirmation.
 *
 * @param rateId       taux de la paie de remplacement, actif et différent de celui de l'originale
 * @param note         note de la remplaçante ; absente, celle de l'originale est reprise
 * @param reasonType   Motif, parmi les motifs d'une correction de paie
 * @param reasonText   précision, obligatoire pour « Autre »
 * @param previewToken jeton de l'Aperçu lu ; obligatoire en confirmation
 */
public record ReplacePayoutRequest(Long rateId, String note, String reasonType, String reasonText,
                                   String previewToken) {
}
