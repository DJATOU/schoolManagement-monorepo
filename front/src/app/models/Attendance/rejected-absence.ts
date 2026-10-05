import { EnrolmentWindow } from '../session/roll-call';

/**
 * Absence refusée à la validation : l'élève n'était pas concerné par la séance (spec
 * admin-corrections, exigences 7.3 et 7.5). Contrat serveur : `RejectedAbsenceDTO`.
 */
export interface RejectedAbsence {
  studentId: number;
  firstName: string;
  lastName: string;
  sessionId: number;
  /** Jour de la séance, `yyyy-MM-dd`. */
  sessionDay: string | null;
  reason: 'NOT_ENROLLED' | 'OUTSIDE_WINDOW';
  /** Ses périodes dans le groupe de la séance ; vide s'il n'y est pas inscrit. */
  windows: EnrolmentWindow[];
  /** La ligne en français, prête à afficher. */
  message: string;
}

/**
 * Refus d'une feuille de présence, avec ce que le serveur y joint : pour un 409
 * `ABSENCE_OUTSIDE_WINDOW`, les absences refusées, que l'écran retire en une action avant de
 * revalider.
 */
export class AttendanceSubmissionError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly errorCode: string | null = null,
    readonly rejected: RejectedAbsence[] = []
  ) {
    super(message);
    this.name = 'AttendanceSubmissionError';
  }

  /** La feuille contient des absences hors période, nommées dans `rejected`. */
  get outsideWindow(): boolean {
    return this.errorCode === 'ABSENCE_OUTSIDE_WINDOW' && this.rejected.length > 0;
  }
}
