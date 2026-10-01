/**
 * Part d'un Encaissement imputée sur une série.
 *
 * <p>Contrat serveur : `EncashmentDTO.AllocationDTO`.</p>
 */
export interface EncashmentAllocation {
  seriesId: number;
  seriesName: string;
  amount: number;
  /** Vrai si la série n'est pas la série visée à la saisie : la part est un report. */
  carriedOver: boolean;
  /** Faux si l'Encaissement a été annulé. */
  active: boolean;
}

/**
 * Un versement tel qu'il a eu lieu : ce qu'imprime le reçu et ce que liste l'historique de
 * l'élève (spec admin-corrections, exigences 1.1 et 1.2).
 *
 * <p>Numéro, date, heure et auteur sont fixés par le serveur : l'écran ne fabrique plus aucune
 * référence. Contrat serveur : `EncashmentDTO`, renvoyé par `GET /api/encashments/{id}`, par
 * `GET /api/students/{id}/encashments` et dans la réponse d'un encaissement.</p>
 */
export interface Encashment {
  id: number;
  /** Numéro de reçu, `RECU-AAAA-NNNN`. */
  receiptNumber: string;
  status: 'ACTIVE' | 'CANCELLED';
  kind: 'REGULAR' | 'CATCH_UP';
  /** Montant reçu, tel qu'encaissé : jamais modifié, même après annulation. */
  amountReceived: number;
  paymentMethod?: string | null;
  notes?: string | null;
  /** Date et heure d'encaissement, fixées par le serveur (ISO). */
  receivedAt: string;
  /** Compte qui a encaissé. */
  receivedBy: string;
  studentId: number;
  studentName: string;
  groupId: number;
  groupName: string;
  targetSeriesId: number;
  targetSeriesName: string;
  allocations: EncashmentAllocation[];
  cancelledAt?: string | null;
  cancelledBy?: string | null;
  cancelReasonType?: string | null;
  cancelReasonText?: string | null;
  replacesReceiptNumber?: string | null;
  replacedByReceiptNumber?: string | null;
}
