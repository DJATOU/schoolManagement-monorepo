export interface CatchUpRequest {
  id?: number;
  studentId: number;
  studentName?: string;

  originalSessionId: number;
  originalSessionName?: string;
  originalGroupId: number;
  originalGroupName?: string;
  originalAttendanceId: number;
  originalSessionDate?: Date;

  catchUpSessionId?: number;
  catchUpSessionName?: string;
  catchUpGroupId?: number;
  catchUpGroupName?: string;
  catchUpSessionDate?: Date;

  status: 'PENDING' | 'SCHEDULED' | 'COMPLETED' | 'CANCELLED';
  requestDate: Date;
  scheduledDate?: Date;
  completedDate?: Date;

  notes?: string;
  createdBy?: string;
  updatedBy?: string;
}
