/**
 * Paie des enseignants (spec teacher-payroll) : contrats du serveur, montants en DA à deux décimales.
 *
 * <p>La paie d'un enseignant est une part de ce que la série de son groupe a réellement encaissé,
 * au pourcentage d'un taux choisi au moment de payer. Une paie enregistrée ne change plus : l'argent
 * arrivé ou rendu après coup donne une régularisation, une erreur se corrige par annulation ou
 * remplacement.</p>
 */

/** Taux de rémunération du catalogue. Contrat serveur : `TeacherPayRateDTO`. */
export interface TeacherPayRate {
  id: number;
  label: string;
  /** Part de l'enseignant, en %, strictement entre 0 et 100. */
  teacherPercent: number;
  /** Part de l'école : 100 − part de l'enseignant. */
  schoolPercent: number;
  active: boolean;
}

/** Création ou modification d'un taux. */
export interface TeacherPayRateRequest {
  label: string;
  teacherPercent: number;
}

/**
 * État d'une série dans « À payer » : à payer, en cours (séances à valider), sans enseignant, sans
 * encaissé, ou payée mais à régulariser.
 */
export type PayableState = 'PAYABLE' | 'NOT_FINISHED' | 'NO_TEACHER' | 'NOTHING_COLLECTED' | 'TO_REGULARIZE';

/** Une ligne de « À payer ». Contrat serveur : `PayableSeriesDTO`. */
export interface PayableSeries {
  seriesId: number;
  seriesName: string;
  groupId: number;
  groupName: string;
  teacherId: number | null;
  teacherName: string | null;
  state: PayableState;
  activeSessions: number;
  validatedSessions: number;
  collectedGross: number;
  refunded: number;
  collectedNet: number;
  /** Série payée : numéro de sa paie initiale, pourcentage figé, déjà versé, écart. */
  initialPayoutNumber: string | null;
  teacherPercent: number | null;
  teacherPaid: number | null;
  gap: number | null;
}

export type PayoutKind = 'INITIAL' | 'REGULARIZATION';
export type PayoutStatus = 'ACTIVE' | 'CANCELLED';

/** Une paie enregistrée, copie figée. Contrat serveur : `PayoutDTO`. */
export interface Payout {
  id: number;
  payoutNumber: string;
  kind: PayoutKind;
  initialPayoutNumber: string | null;
  teacherId: number;
  teacherName: string;
  groupId: number;
  groupName: string;
  seriesId: number;
  seriesName: string;
  rateLabel: string;
  teacherPercent: number;
  collectedGross: number;
  refunded: number;
  collectedNet: number;
  /** Encaissé que la paie partage : tout l'encaissé pour une paie initiale, l'écart pour une régularisation. */
  baseDelta: number;
  /** Négative pour une retenue. */
  teacherAmount: number;
  schoolAmount: number;
  note: string | null;
  paidAt: string;
  paidBy: string;
  status: PayoutStatus;
  cancelledAt: string | null;
  cancelledBy: string | null;
  cancelReasonType: string | null;
  cancelReasonText: string | null;
  replacesNumber: string | null;
  replacedByNumber: string | null;
}

/** Paies d'un filtre, et totaux des seules paies actives. Contrat serveur : `PayoutListDTO`. */
export interface PayoutList {
  payouts: Payout[];
  teacherTotal: number;
  schoolTotal: number;
}

/** Filtres de « Paies versées » ; dates au format `yyyy-MM-dd`, bornes incluses. */
export interface PayoutFilters {
  teacherId?: number | null;
  groupId?: number | null;
  status?: PayoutStatus | null;
  from?: string | null;
  to?: string | null;
}

/**
 * Aperçu d'une paie ou d'une régularisation, scellé par un jeton. Contrat serveur :
 * `PayoutPreviewDTO`.
 */
export interface PayoutPreview {
  seriesId: number;
  seriesName: string;
  groupId: number;
  groupName: string;
  teacherId: number;
  teacherName: string;
  kind: PayoutKind;
  rateId: number | null;
  rateLabel: string;
  teacherPercent: number;
  collectedGross: number;
  refunded: number;
  collectedNet: number;
  /** Encaissé déjà couvert par la dernière paie ; zéro pour une paie initiale. */
  netCovered: number;
  baseDelta: number;
  teacherAmount: number;
  schoolAmount: number;
  /** Déjà versé à l'enseignant sur la série ; zéro pour une paie initiale. */
  teacherPaid: number;
  previewToken: string;
}

/** Une impression du bordereau d'une paie. Contrat serveur : `PayoutSlipDTO`. */
export interface PayoutSlip {
  payout: Payout;
  /** 1 pour l'original, 2 et au-delà pour un duplicata. */
  issuanceRank: number;
  issuedAt: string;
  issuedBy: string;
  fileName: string;
}

/** Résultat d'un remplacement : l'originale annulée, et sa remplaçante. */
export interface PayoutCorrection {
  original: Payout;
  replacement: Payout;
}

/**
 * Refus d'une paie, avec le nouvel Aperçu quand les montants ont changé depuis celui lu (409
 * `STALE_PREVIEW`).
 */
export class PayoutError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly errorCode: string | null = null,
    readonly preview: PayoutPreview | null = null,
    readonly previewToken: string | null = null
  ) {
    super(message);
    this.name = 'PayoutError';
  }

  /** Les montants ont changé depuis l'Aperçu : un nouvel Aperçu est joint. */
  get stale(): boolean {
    return this.errorCode === 'STALE_PREVIEW' && this.preview !== null && this.previewToken !== null;
  }
}

/** Une paie de type régularisation à montant négatif : une retenue. */
export function isDeduction(payout: Pick<Payout, 'kind' | 'teacherAmount'>): boolean {
  return payout.kind === 'REGULARIZATION' && payout.teacherAmount < 0;
}

/** Nature d'une paie, pour l'afficher : paie, complément ou retenue. */
export function payoutNature(payout: Pick<Payout, 'kind' | 'teacherAmount'>): 'INITIAL' | 'COMPLEMENT' | 'DEDUCTION' {
  if (payout.kind === 'INITIAL') {
    return 'INITIAL';
  }
  return payout.teacherAmount < 0 ? 'DEDUCTION' : 'COMPLEMENT';
}
