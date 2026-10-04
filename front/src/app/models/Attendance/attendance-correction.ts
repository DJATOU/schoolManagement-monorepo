/**
 * Présence telle qu'une correction la laisse (spec admin-corrections, exigence 8). Contrat serveur :
 * `AttendanceCorrection`.
 */
export interface AttendanceCorrection {
  attendanceId: number;
  studentId: number;
  sessionId: number;
  present: boolean;
  /** Absence justifiée ; toujours faux pour une présence. */
  justified: boolean;
  /** Faux pour une ligne retirée, qui reste en base désactivée. */
  active: boolean;
}

/** L'état voulu d'une ligne : présent, absent, ou absent justifié. */
export interface AttendanceState {
  present: boolean;
  justified: boolean;
}
