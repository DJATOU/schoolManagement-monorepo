package com.school.management.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * Données d'un reçu de remboursement, prêtes à imprimer (exigence 8).
 *
 * <h2>Tous les replis sont résolus ici, côté serveur</h2>
 * « Hors série », « Hors groupe » et « Administrateur non identifié » sont calculés par le service
 * et non laissés au client. L'exigence 8.6 impose que deux productions du même reçu affichent
 * exactement les mêmes valeurs : si le client décidait des replis, une évolution de son code
 * suffirait à faire divergerdeux impressions d'une même pièce comptable.
 *
 * <h2>Le duplicata est une donnée, pas un choix d'affichage</h2>
 * {@link #issuanceRank} et {@link #issuedAt} viennent du journal des émissions. Le client ne peut
 * pas savoir si un reçu a déjà été produit : un reçu de caisse réimprimé peut servir deux fois, et
 * c'est précisément ce que la mention « Duplicata » signale.
 *
 * @param refundId       identifiant du remboursement
 * @param refundNumber   numéro de pièce, de la forme {@code REMB-AAAA-NNNN}
 * @param refundDate     date du remboursement
 * @param amount         montant remboursé
 * @param reason         motif du remboursement
 * @param studentFirstName prénom du bénéficiaire
 * @param studentLastName  nom du bénéficiaire
 * @param paymentDate    date du versement d'origine
 * @param amountPaid     montant du versement d'origine
 * @param groupName      nom du groupe du versement, ou « Hors groupe » (exigence 8.12)
 * @param seriesName     nom de la série du versement, ou « Hors série » (exigence 8.3)
 * @param recordedBy     administrateur ayant enregistré le remboursement, ou la mention de repli
 *                       lorsqu'il n'est pas identifiable (exigence 8.11)
 * @param issuanceRank   rang de cette production : 1 pour l'original, au-delà pour un duplicata
 * @param issuedAt       date de cette production précise
 * @param fileName       nom de fichier proposé, stable d'une production à l'autre (exigence 8.8)
 */
public record RefundReceiptDTO(
        Long refundId,
        String refundNumber,
        Date refundDate,
        BigDecimal amount,
        String reason,
        String studentFirstName,
        String studentLastName,
        Date paymentDate,
        BigDecimal amountPaid,
        String groupName,
        String seriesName,
        String recordedBy,
        int issuanceRank,
        LocalDateTime issuedAt,
        String fileName) {

    /** Vrai à partir de la deuxième production : le document doit alors porter « Duplicata ». */
    public boolean isDuplicate() {
        return issuanceRank > 1;
    }
}
