import { CorrectionReasonType } from '../correction/correction';

/**
 * Une inscription d'un élève à un groupe et sa période (spec admin-corrections, exigences 5 et 6).
 * Contrat serveur : `EnrolmentDTO`.
 *
 * Les dates sont des jours `yyyy-MM-dd`, sans heure ni fuseau : elles s'affichent telles quelles
 * (`formatCalendarDay`), jamais converties en `Date`, qui les décalerait d'un jour selon le fuseau.
 */
export interface Enrolment {
  id: number;
  studentId: number;
  groupId: number;
  groupName: string;
  schoolYearId: number | null;
  /** Date_Inscription. */
  arrival: string;
  /** Date_Sortie ; `null` tant que l'inscription est ouverte. */
  departure: string | null;
  active: boolean;
}

/** Les trois corrections de la période d'une inscription. */
export type EnrolmentCorrectionKind = 'ARRIVAL' | 'DEPARTURE' | 'REOPEN';

/** Motifs proposés par correction, dans l'ordre d'affichage. Contrat serveur : `GET /api/enrolments/correction-reasons`. */
export type EnrolmentCorrectionReasons = Record<EnrolmentCorrectionKind, CorrectionReasonType[]>;

/** L'inscription telle qu'une correction la laisse. Contrat serveur : `EnrolmentCorrection`. */
export interface EnrolmentCorrection {
  enrolmentId: number;
  studentId: number;
  groupId: number;
  arrival: string;
  departure: string | null;
  active: boolean;
}

/**
 * Ce que l'administratrice demande, hors Motif : la nouvelle date, et pour un départ, s'il faut
 * aussi retirer les présences ordinaires postérieures (exigence 6.3).
 */
export interface EnrolmentDateChange {
  kind: EnrolmentCorrectionKind;
  /** Jour `yyyy-MM-dd` ; sans objet pour une réouverture. */
  date: string | null;
  removePresencesAfter: boolean;
}
