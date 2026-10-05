package com.school.management.dto.payroll;

/**
 * Annulation d'une paie, en Aperçu comme en confirmation.
 *
 * @param reasonType   Motif, parmi les motifs d'une correction de paie
 * @param reasonText   précision, obligatoire pour « Autre »
 * @param previewToken jeton de l'Aperçu lu ; obligatoire en confirmation
 */
public record CancelPayoutRequest(String reasonType, String reasonText, String previewToken) {
}
