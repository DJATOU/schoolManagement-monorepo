export interface Payment {
  id?: number;
  studentId: number;
  sessionSeriesId?: number;
  sessionId?: number;
  groupId: number;

  amountPaid: number;
  amountDue: number;

  discountType?: 'PERCENTAGE' | 'FIXED' | 'NONE';
  discountValue?: number;
  discountReason?: string;

  paymentType: 'SERIES' | 'SESSION' | 'PARTIAL';

  paymentMethod?: string;
  paymentDescription?: string;
  paymentForMonth: Date;
  paymentDate?: Date;
  status: 'PENDING' | 'PARTIAL' | 'PAID' | 'OVERPAID' | 'REFUNDED';

  totalSeriesCost?: number;
  totalPaidForSeries?: number;
  amountOwed?: number;
  seriesPrice?: number;
}
