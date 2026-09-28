/**
 * Jours de la semaine, dans la nomenclature de {@code java.time.DayOfWeek} attendue par
 * le backend.
 */
export type WeekDay =
  | 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY'
  | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

/** Demande de séances récurrentes : un créneau fixe répété sur une période. */
export interface RecurringSessionRequest {
  groupId: number;
  teacherId?: number | null;
  roomId?: number | null;
  title?: string;
  sessionType?: string;
  /** Format yyyy-MM-dd. */
  startDate: string;
  /** Format yyyy-MM-dd. */
  endDate: string;
  daysOfWeek: WeekDay[];
  /** Format HH:mm. */
  startTime: string;
  /** Format HH:mm. */
  endTime: string;
  /** Ignorer les créneaux déjà occupés au lieu de refuser toute la demande. */
  skipConflicts: boolean;
  numberTitles: boolean;
}

/** Occurrence écartée faute de créneau libre. */
export interface RecurringConflict {
  start: string;
  /** `ROOM_BUSY` ou `TEACHER_BUSY`. */
  reason: string;
  /** Nom de la salle ou de l'enseignant en cause. */
  detail: string;
}

/** Compte rendu d'une simulation ou d'une génération. */
export interface RecurringSessionResult {
  created: number;
  skipped: number;
  sessionIds: number[];
  seriesIds: number[];
  conflicts: RecurringConflict[];
}
