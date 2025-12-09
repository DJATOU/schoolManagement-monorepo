import { BaseModel } from "../baseModel";

export interface StudentGroup extends BaseModel {
  id?: number;
  groupId: number;
  studentId: number;

  enrollmentDate: Date;
  exitDate?: Date;
  exitReason?: 'TRANSFER' | 'DROPOUT' | 'COMPLETED' | 'OTHER';

  transferredToGroupId?: number;
  transferredFromGroupId?: number;

  isActive: boolean;
}
