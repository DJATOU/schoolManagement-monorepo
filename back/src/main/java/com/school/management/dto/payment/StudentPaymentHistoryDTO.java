package com.school.management.dto.payment;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * Historique de paiement d'un étudiant, reports compris (exigences 6.2, 6.4).
 *
 * <p>Depuis le report, une série peut avoir été créditée sans qu'aucun versement n'ait jamais été
 * saisi sur elle : l'argent vient du surplus d'une autre série. Un historique qui n'expose que
 * {@code payments.amount_paid} présente donc un montant sans origine, impossible à justifier lors
 * d'un contrôle.</p>
 *
 * <p>Chaque ligne sépare donc les deux provenances, et la liste {@link #carryOvers()} restitue
 * chaque report avec son montant et ses deux séries.</p>
 *
 * @param studentId  l'étudiant concerné
 * @param series     une ligne par série créditée, de la plus récente à la plus ancienne
 * @param carryOvers les reports produits par les versements de cet étudiant, par identifiant
 *                   croissant
 */
public record StudentPaymentHistoryDTO(
        Long studentId,
        List<SeriesPaymentHistoryDTO> series,
        List<PaymentCarryOverDTO> carryOvers) {

    /**
     * Une ligne de paiement de série, avec la provenance de son cumul.
     *
     * <p><strong>Distinguer imputation directe et report (exigence 6.4).</strong>
     * {@code amountReceivedByCarryOver} est la somme des reports actifs pointant cette ligne ;
     * {@code amountAllocatedDirectly} est le reste, c'est-à-dire ce qui a été saisi sur cette
     * série. Une ligne sans report a donc {@code amountReceivedByCarryOver} nul, et l'égalité
     * {@code amountPaid = amountAllocatedDirectly + amountReceivedByCarryOver} tient toujours.</p>
     *
     * @param paymentId                 identifiant de la ligne de paiement
     * @param seriesId                  la série créditée, nulle pour un versement hors série
     * @param seriesName                le nom de cette série
     * @param amountPaid                le cumul enregistré sur la série
     * @param amountAllocatedDirectly   la part saisie directement sur cette série
     * @param amountReceivedByCarryOver la part reçue par report depuis une autre série
     * @param status                    le statut de la ligne de paiement
     * @param paymentDate               la date du dernier encaissement porté par la ligne
     */
    public record SeriesPaymentHistoryDTO(
            Long paymentId,
            Long seriesId,
            String seriesName,
            BigDecimal amountPaid,
            BigDecimal amountAllocatedDirectly,
            BigDecimal amountReceivedByCarryOver,
            String status,
            Date paymentDate) {
    }
}
