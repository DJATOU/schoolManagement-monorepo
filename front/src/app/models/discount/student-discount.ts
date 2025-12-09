export interface StudentDiscount {
  id?: number;
  studentId: number;
  studentName?: string;

  discountType: 'PERCENTAGE' | 'FIXED';
  discountValue: number;
  reason: string;

  groupId?: number;
  groupName?: string;

  startDate: Date;
  endDate?: Date;
  isActive: boolean;

  dateCreation?: Date;
  createdBy?: string;
}

export interface PriceCalculation {
  basePrice: number;
  appliedDiscounts: AppliedDiscount[];
  totalDiscountAmount: number;
  finalPrice: number;
}

export interface AppliedDiscount {
  discountId: number;
  reason: string;
  type: 'PERCENTAGE' | 'FIXED';
  value: number;
  amountDeducted: number;
}
