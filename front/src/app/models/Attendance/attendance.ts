export interface Attendance {
  id: number;
  studentId: number;
  sessionId: number;
  groupId: number;
  originalGroupId?: number;
  sessionSeriesId?: number;

  isPresent: boolean;
  isJustified: boolean;
  justificationReason?: string;

  isCatchUp: boolean;
  catchUpForSessionId?: number;
  catchUpFromGroupId?: number;

  paymentStatus: 'PENDING' | 'PAID' | 'EXEMPTED' | 'COVERED_BY_CATCHUP';

  description?: string;
  dateCreation: Date;
  dateUpdate: Date;
  createdBy: string;
  updatedBy: string;
  active: boolean;
}
