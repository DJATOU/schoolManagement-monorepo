/**
 * Absence d'un étudiant éligible à une demande de rattrapage.
 * Aligné sur le DTO backend {@code StudentAbsenceDTO}.
 */
export interface StudentAbsence {
  attendanceId: number;
  sessionId: number;
  sessionTitle?: string;
  sessionDate?: Date;
  groupId?: number;
  groupName?: string;
  seriesId?: number;
  isJustified?: boolean;
  catchUpRight?: boolean;
}
