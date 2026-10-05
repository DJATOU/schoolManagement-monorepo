export interface DashboardStats {
  from: string;
  to: string;

  totalStudents: number;
  newStudentsInPeriod: number;
  leavingStudents: number;
  maleStudents: number;
  femaleStudents: number;

  totalTeachers: number;
  totalGroups: number;
  /** Groupes actifs ayant au moins une inscription active (sous-ensemble de totalGroups). */
  activeGroups: number;

  sessionsValidated: number;
  sessionsScheduled: number;
  sessionsDeactivated: number;
  catchUpSessions: number;

  presentCount: number;
  justifiedAbsences: number;
  unjustifiedAbsences: number;
}
