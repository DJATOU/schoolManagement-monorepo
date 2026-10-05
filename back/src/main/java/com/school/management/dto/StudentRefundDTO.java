package com.school.management.dto;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Un remboursement, tel que l'historique le nomme : sous sa série dans l'historique de l'étudiant,
 * sous son versement dans l'écran « Gestion des paiements ».
 *
 * <p>Le numéro de pièce et le motif sont ce qui répond à une famille qui conteste un versé en
 * baisse : « 400 DA vous ont été rendus le 5 octobre, pièce REMB-2026-0007, trop-perçu ». L'identifiant
 * sert à réimprimer le reçu ({@code POST /api/refunds/{id}/receipts}).</p>
 *
 * @param refundId     identifiant du remboursement
 * @param refundNumber numéro de pièce, {@code REMB-AAAA-NNNN}
 * @param refundDate   date de la sortie de caisse
 * @param amount       montant rendu, échelle 2
 * @param reason       motif ; nul pour un remboursement antérieur à la traçabilité
 * @param seriesId     série du versement remboursé, nulle si le versement n'en porte pas
 * @param seriesName   nom de cette série
 * @param groupId      groupe du versement remboursé, nul si le versement n'en porte pas
 * @param groupName    nom de ce groupe
 * @param paymentId    versement remboursé
 * @param recordedBy   administrateur qui a enregistré le remboursement, ou mention de repli — la
 *                     même que celle du reçu
 */
public record StudentRefundDTO(
        Long refundId,
        String refundNumber,
        Date refundDate,
        BigDecimal amount,
        String reason,
        Long seriesId,
        String seriesName,
        Long groupId,
        String groupName,
        Long paymentId,
        String recordedBy) {
}
