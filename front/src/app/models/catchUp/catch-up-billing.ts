/**
 * Modèles de la facturation des rattrapages à préciser.
 *
 * Alignés 1:1 sur les DTO du serveur (`dto/catchup`). Le routage entre « vrai rattrapage » et
 * « facturée sur place » est décidé côté serveur : le client n'a rien à recalculer ici, il affiche
 * et transmet des décisions.
 */

/** État de facturation d'une présence de rattrapage. */
export type CatchUpBillingState = 'PENDING' | 'RESOLVED' | 'HOST_BILLED';

/** Rattrapage en attente de décision, tel qu'affiché dans la liste. */
export interface PendingCatchUp {
  attendanceId: number;
  studentId: number | null;
  studentName: string | null;
  sessionId: number | null;
  sessionTitle: string | null;
  sessionDate: string | null;
  hostGroupId: number | null;
  hostGroupName: string | null;
  /** Niveau du groupe d'accueil : l'une des deux dimensions du test de routage. */
  levelName: string | null;
  /** Matière du groupe d'accueil : seconde dimension du test. */
  subjectName: string | null;
  seriesId: number | null;
  seriesName: string | null;
}

/**
 * Corps de la résolution.
 *
 * `alreadyPaid` est volontairement non optionnel : la décision doit être prise. Un champ optionnel
 * laisserait le client l'omettre, et le serveur le refuserait — autant que le type l'interdise.
 */
export interface ResolveCatchUpRequest {
  missedSessionId: number;
  alreadyPaid: boolean;
  comment?: string;
}

/**
 * Corps de la correction : les deux champs sont optionnels, `undefined` signifiant « ne pas
 * toucher à cette décision ». Une correction sans aucun champ est refusée par le serveur.
 */
export interface CorrectCatchUpRequest {
  missedSessionId?: number;
  alreadyPaid?: boolean;
  comment?: string;
}

/** Entrée de la piste d'audit d'un rattrapage. */
export interface CatchUpBillingAudit {
  id: number;
  field: 'MISSED_SESSION' | 'ALREADY_PAID';
  oldValue: string | null;
  newValue: string | null;
  performedBy: string;
  performedAt: string;
  sequenceRank: number;
  comment: string | null;
}
