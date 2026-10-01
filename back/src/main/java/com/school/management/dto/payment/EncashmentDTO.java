package com.school.management.dto.payment;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * Un Encaissement tel qu'il a eu lieu, pour le reçu, sa réimpression et l'historique de l'élève
 * (spec admin-corrections, exigences 1.1 et 1.2).
 *
 * <p>Tout ce qui s'imprime vient du serveur : numéro de reçu, date et heure, auteur. L'écran ne
 * reconstruit plus rien — une référence fabriquée côté client à partir du cumul de la série et
 * de sa date, réécrite ensuite, ne permettait de rapprocher un reçu d'aucun versement.</p>
 *
 * @param id                      identifiant de l'Encaissement
 * @param receiptNumber           numéro de reçu, {@code RECU-AAAA-NNNN}
 * @param status                  {@code ACTIVE} ou {@code CANCELLED}
 * @param kind                    {@code REGULAR} ou {@code CATCH_UP}
 * @param amountReceived          montant reçu, tel qu'encaissé
 * @param paymentMethod           mode de paiement saisi
 * @param notes                   note saisie
 * @param receivedAt              date et heure d'encaissement, fixées par le serveur
 * @param receivedBy              auteur de l'encaissement
 * @param studentId               étudiant
 * @param studentName             prénom et nom de l'étudiant
 * @param groupId                 groupe
 * @param groupName               nom du groupe
 * @param targetSeriesId          série visée à la saisie
 * @param targetSeriesName        nom de la série visée
 * @param allocations             répartition sur les séries, dans l'ordre de création
 * @param cancelledAt             date de l'annulation, nulle si actif
 * @param cancelledBy             auteur de l'annulation
 * @param cancelReasonType        motif de l'annulation
 * @param cancelReasonText        texte du motif « Autre »
 * @param replacesReceiptNumber   reçu que celui-ci remplace, nul sinon
 * @param replacedByReceiptNumber reçu qui remplace celui-ci, nul sinon
 */
public record EncashmentDTO(
        Long id,
        String receiptNumber,
        String status,
        String kind,
        BigDecimal amountReceived,
        String paymentMethod,
        String notes,
        Date receivedAt,
        String receivedBy,
        Long studentId,
        String studentName,
        Long groupId,
        String groupName,
        Long targetSeriesId,
        String targetSeriesName,
        List<AllocationDTO> allocations,
        Date cancelledAt,
        String cancelledBy,
        String cancelReasonType,
        String cancelReasonText,
        String replacesReceiptNumber,
        String replacedByReceiptNumber) {

    /**
     * Part de l'Encaissement imputée sur une série.
     *
     * @param seriesId    série créditée
     * @param seriesName  nom de la série
     * @param amount      montant imputé
     * @param carriedOver vrai si la série n'est pas la série visée (report)
     * @param active      faux si l'Encaissement a été annulé
     */
    public record AllocationDTO(Long seriesId, String seriesName, BigDecimal amount, boolean carriedOver,
                                boolean active) {
    }
}
