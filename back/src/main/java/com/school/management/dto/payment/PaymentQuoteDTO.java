package com.school.management.dto.payment;

import java.math.BigDecimal;

/**
 * Devis de paiement d'un étudiant pour une série : tout ce qu'il faut pour saisir un
 * versement sans recalculer quoi que ce soit côté client.
 *
 * <p>Le formulaire de saisie calculait auparavant le coût dans le navigateur à partir du
 * tarif catalogue, sans appliquer la réduction. Le montant proposé était donc surévalué et
 * le garde-fou anti-trop-perçu laissait passer des versements supérieurs au dû réel.</p>
 *
 * <h2>Les deux quantités à ne pas confondre (business-rules.md, audit H5)</h2>
 * <ul>
 *   <li>{@code monthTotalCost} = séances facturables × prix net : ce que coûte la série
 *       pour cet étudiant, donc le plafond de ce qui peut être encaissé ;</li>
 *   <li>{@code amountDueSoFar} = séances suivies × prix net : le seuil de retard.</li>
 * </ul>
 *
 * <h2>Prorata (exigences 3.4, 3.5)</h2>
 * Le coût n'est plus calculé sur {@code series.total_sessions} mais sur les seules
 * <b>séances facturables</b> de l'étudiant : une séance tenue avant son arrivée dans le groupe
 * et à laquelle il n'a pas assisté ne lui est pas due. Le devis expose donc
 * {@code billableSessions} et {@code excludedSessions} pour que l'écran puisse justifier un
 * montant inférieur au coût nominal de la série.
 *
 * <p>{@code existingExcess} rend visible l'excédent des couples étudiant/série encaissés avant
 * l'entrée en vigueur du prorata : aucune reprise de données n'est prévue, ces montants restent
 * affichés comme excédent existant et le plafond encaissable tombe à zéro.</p>
 *
 * @param studentId            identifiant de l'étudiant
 * @param seriesId             identifiant de la série
 * @param plannedSessions      <b>déprécié</b> : porte désormais le décompte des séances
 *                             facturables, identique à {@code billableSessions}. Conservé sous
 *                             ce nom le temps que le front et ses libellés migrent vers
 *                             {@code billableSessions} ; à retirer dans un second temps pour ne
 *                             pas mêler un renommage à un changement de règle
 * @param billableSessions     nombre de séances facturables à l'étudiant sur la série
 * @param excludedSessions     nombre de séances écartées : antérieures à l'inscription et non
 *                             suivies, donc ni facturées ni dues
 * @param attendedSessions     nombre de séances facturables effectivement suivies (présent)
 * @param grossPricePerSession tarif catalogue d'une séance, avant réduction
 * @param discountRate         taux de réduction résolu, dans [0.00, 1.00]
 * @param netPricePerSession   prix d'une séance après réduction
 * @param monthTotalCost       coût de la série complète, après réduction
 * @param amountDueSoFar       montant dû à ce jour, après réduction
 * @param amountPaid           montant déjà versé (paiements non annulés − remboursements)
 * @param remainingToPay       reste à payer sur la série, jamais négatif
 * @param maxPayable           montant maximal encaissable maintenant, plafond du formulaire
 * @param existingExcess       excédent déjà encaissé au-delà du coût au prorata, jamais négatif ;
 *                             strictement positif, il force {@code maxPayable} à zéro
 * @param exempted             vrai lorsque la réduction est totale (100 %)
 * @param catchUpOnly          vrai lorsque l'étudiant n'est présent qu'en rattrapage sur la série
 */
public record PaymentQuoteDTO(
        Long studentId,
        Long seriesId,
        int plannedSessions,
        int billableSessions,
        int excludedSessions,
        int attendedSessions,
        BigDecimal grossPricePerSession,
        BigDecimal discountRate,
        BigDecimal netPricePerSession,
        BigDecimal monthTotalCost,
        BigDecimal amountDueSoFar,
        BigDecimal amountPaid,
        BigDecimal remainingToPay,
        BigDecimal maxPayable,
        BigDecimal existingExcess,
        boolean exempted,
        boolean catchUpOnly) {
}
