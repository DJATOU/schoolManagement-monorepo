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

  sessionsValidated: number;
  sessionsScheduled: number;
  sessionsDeactivated: number;
  catchUpSessions: number;

  presentCount: number;
  justifiedAbsences: number;
  unjustifiedAbsences: number;
}
