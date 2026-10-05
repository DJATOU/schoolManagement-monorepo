import { Encashment } from '../payment/encashment';

/**
 * Motif d'une correction (spec admin-corrections, D10). Contrat serveur : `CorrectionReasonType`.
 * La liste proposée pour un type de correction vient du serveur (`GET …/correction-reasons`).
 */
export type CorrectionReasonType =
  | 'DATA_ENTRY_ERROR'
  | 'DOCUMENT_RECEIVED'
  | 'ARRIVAL_DATE_CORRECTED'
  | 'STUDENT_LEFT'
  | 'WRONG_STUDENT'
  | 'WRONG_AMOUNT'
  | 'OTHER';

/** Motif choisi par l'administratrice ; `text` obligatoire pour `OTHER`. */
export interface CorrectionReason {
  type: CorrectionReasonType;
  text?: string | null;
}

/** Montants d'une Série pour un élève, à un instant. Contrat serveur : `AmountSnapshot`. */
export interface AmountSnapshot {
  cost: number;
  dueSoFar: number;
  paid: number;
  remaining: number;
  late: boolean;
}

/** Une Série dont un montant change. Contrat serveur : `SeriesAmountChange`. */
export interface SeriesAmountChange {
  studentId: number;
  studentName: string;
  seriesId: number;
  seriesName: string;
  groupName?: string | null;
  before: AmountSnapshot;
  after: AmountSnapshot;
}

/** Un effet autre qu'un montant, déjà rédigé en français par le serveur. */
export interface CorrectionEffect {
  type: string;
  description: string;
  /**
   * Séance sur laquelle l'administratrice peut agir depuis l'Aperçu : une séance validée qui entre
   * dans la période sans présence, ou la présence notée à sa place (exigence 5.7). Absente sinon.
   */
  sessionId?: number | null;
}

/** Présent (`true`) ou absent (`false`) noté sur des séances désignées par l'Aperçu, par identifiant. */
export type AttendanceMarks = Record<number, boolean>;

/** Ce qu'une correction change, mesuré en l'exécutant. Contrat serveur : `CorrectionPreview`. */
export interface CorrectionPreview {
  series: SeriesAmountChange[];
  effects: CorrectionEffect[];
  amountsUnchanged: boolean;
}

/**
 * Réponse d'une correction : Aperçu, jeton qui permet de confirmer exactement cet Aperçu, et
 * résultat de la correction confirmée (`null` en Aperçu). Contrat serveur : `CorrectionResponse`.
 */
export interface CorrectionResponse<T> {
  preview: CorrectionPreview;
  previewToken: string;
  result: T | null;
}

/** Issue d'une correction d'Encaissement. Contrat serveur : `EncashmentCorrection`. */
export interface EncashmentCorrection {
  original: Encashment;
  /** Reçu de remplacement, à imprimer ; nul si seuls le mode et la note changeaient. */
  replacement: Encashment | null;
}

/** L'Encaissement tel qu'il aurait dû être saisi : l'état voulu complet. */
export interface EncashmentChanges {
  amount: number;
  studentId: number;
  groupId: number;
  targetSeriesId: number;
  paymentMethod?: string | null;
  notes?: string | null;
}

/** Un remboursement qui empêche une correction (409 `REFUND_FLOOR`). */
export interface BlockingRefund {
  refundNumber: string;
  refundDate: string;
  amount: number;
  seriesName: string;
}
