/**
 * Devis de paiement d'un étudiant pour une série, calculé par le serveur.
 *
 * Le formulaire de saisie recalculait le coût dans le navigateur à partir du tarif
 * catalogue, sans appliquer la réduction de l'étudiant : le montant proposé était surévalué
 * et le contrôle de dépassement inopérant. Tous les montants viennent désormais d'ici.
 *
 * `monthTotalCost` et `amountDueSoFar` répondent à deux questions différentes et ne doivent
 * pas être confondus : le premier est le coût de la série pour cet étudiant, le second le seuil
 * de retard (séances effectivement suivies).
 *
 * Le coût est calculé au prorata : une séance tenue avant l'arrivée de l'étudiant dans le
 * groupe et à laquelle il n'a pas assisté ne lui est pas facturée. `billableSessions` et
 * `excludedSessions` permettent de justifier à l'écran un montant inférieur au coût nominal de
 * la série.
 */
export interface PaymentQuote {
  studentId: number;
  seriesId: number;
  /**
   * @deprecated Porte désormais le décompte des séances facturables, identique à
   * `billableSessions`. Conservé le temps que les libellés migrent ; utiliser
   * `billableSessions`.
   */
  plannedSessions: number;
  /** Nombre de séances facturables à l'étudiant sur cette série. */
  billableSessions: number;
  /** Séances écartées : antérieures à l'inscription et non suivies, donc non facturées. */
  excludedSessions: number;
  attendedSessions: number;
  /** Tarif catalogue d'une séance, avant réduction. */
  grossPricePerSession: number;
  /** Taux de réduction résolu, entre 0 et 1. */
  discountRate: number;
  /** Prix d'une séance après réduction. */
  netPricePerSession: number;
  /** Coût de la série complète, après réduction. */
  monthTotalCost: number;
  /** Montant dû à ce jour, après réduction. */
  amountDueSoFar: number;
  /** Montant déjà versé (paiements non annulés moins remboursements). */
  amountPaid: number;
  /** Reste à payer sur la série, jamais négatif. */
  remainingToPay: number;
  /** Montant maximal encaissable maintenant : plafond du formulaire. */
  maxPayable: number;
  /**
   * Excédent déjà encaissé au-delà du coût au prorata, jamais négatif. Strictement positif, le
   * plafond `maxPayable` est nul : la série a été sur-encaissée avant l'entrée en vigueur du
   * prorata et aucune reprise de données n'est prévue.
   */
  existingExcess: number;
  /** Réduction totale (100 %) : rien n'est dû. */
  exempted: boolean;
  /** L'étudiant n'est présent qu'en rattrapage sur cette série. */
  catchUpOnly: boolean;
}
