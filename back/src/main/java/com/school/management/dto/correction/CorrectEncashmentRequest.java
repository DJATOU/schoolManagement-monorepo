package com.school.management.dto.correction;

import java.math.BigDecimal;

/**
 * Corriger un Encaissement : l'Encaissement tel qu'il aurait dû être saisi (spec
 * admin-corrections, exigence 3.1). L'écran part des valeurs actuelles ; aucun champ absent ne veut
 * dire « inchangé ».
 *
 * @param amount         montant reçu
 * @param studentId      étudiant qui a versé
 * @param groupId        groupe
 * @param targetSeriesId Série visée
 * @param paymentMethod  mode de paiement, vide si aucun
 * @param notes          note, vide si aucune
 * @param reasonType     Motif, parmi ceux de {@code GET /api/encashments/correction-reasons}
 * @param reasonText     explication, obligatoire pour {@code OTHER}
 * @param previewToken   jeton de l'Aperçu lu ; ignoré en Aperçu, obligatoire en confirmation
 */
public record CorrectEncashmentRequest(BigDecimal amount, Long studentId, Long groupId, Long targetSeriesId,
                                       String paymentMethod, String notes, String reasonType, String reasonText,
                                       String previewToken) {
}
