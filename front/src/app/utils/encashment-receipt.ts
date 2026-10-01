import { Encashment } from '../models/payment/encashment';
import { PaymentReceiptData } from '../services/payment-receipt-pdf.service';

/**
 * Reçu d'un Encaissement enregistré, tel qu'il se réimprime depuis l'historique (A.9).
 *
 * <p>Tout vient de l'Encaissement : numéro, date, heure, auteur, montant et répartition. Le reçu
 * réimprimé porte donc les mêmes mentions que l'original, quel que soit le poste et l'heure de
 * la réimpression.</p>
 *
 * <p>La situation de la série (coût, cumul, reste à payer) n'est pas reprise : elle décrivait la
 * série <strong>au moment</strong> du versement et n'est pas conservée. La recalculer
 * aujourd'hui imprimerait sous un ancien numéro des montants que l'original ne portait pas.</p>
 *
 * @param encashment         l'Encaissement relu du serveur
 * @param paymentMethodLabel libellé déjà traduit de son mode de règlement
 */
export function receiptFromEncashment(encashment: Encashment, paymentMethodLabel: string): PaymentReceiptData {
  const allocations = encashment.allocations ?? [];
  // Part imputée sur la série visée : nulle si celle-ci était déjà soldée, tout étant reporté.
  const amountAllocated = allocations
    .filter(allocation => !allocation.carriedOver)
    .reduce((sum, allocation) => sum + allocation.amount, 0);

  return {
    reference: encashment.receiptNumber,
    issuedAt: new Date(encashment.receivedAt),
    studentName: encashment.studentName,
    groupName: encashment.groupName,
    seriesName: encashment.targetSeriesName,
    amountPaid: encashment.amountReceived,
    paymentMethodLabel,
    description: encashment.notes ?? undefined,
    isCatchUp: encashment.kind === 'CATCH_UP',
    amountAllocated: Math.round(amountAllocated * 100) / 100,
    carryOvers: allocations
      .filter(allocation => allocation.carriedOver)
      .map(allocation => ({ seriesName: allocation.seriesName, amount: allocation.amount })),
    adminUsername: encashment.receivedBy
  };
}
