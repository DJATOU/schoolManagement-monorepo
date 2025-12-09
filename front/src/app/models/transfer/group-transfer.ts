export interface GroupTransfer {
  id?: number;
  studentId: number;
  studentName?: string;

  fromGroupId: number;
  fromGroupName?: string;
  toGroupId: number;
  toGroupName?: string;

  transferDate: Date;
  reason?: string;

  sessionsCompletedInOldGroup: number;
  amountPaidOldGroup: number;
  amountDueNewGroup: number;
  financialAdjustment: number;

  status: 'PENDING' | 'COMPLETED' | 'CANCELLED';
  createdBy?: string;
  createdDate?: Date;
}

export interface TransferImpactCalculation {
  fromGroup: {
    groupId: number;
    groupName: string;
    pricePerSession: number;
    sessionsCompleted: number;
    sessionsPaid: number;
    amountPaid: number;
  };
  toGroup: {
    groupId: number;
    groupName: string;
    pricePerSession: number;
    sessionsRemaining: number;
    amountDue: number;
  };
  priceDifference: number;
  totalAdjustment: number;
  recommendation: string;
}
