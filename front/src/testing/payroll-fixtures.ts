import { PayableSeries, Payout, PayoutPreview, PayoutSlip, TeacherPayRate } from '../app/models/payroll/payroll';

/**
 * Jeu de données des specs de la Paie des enseignants : la série « Octobre » du groupe « Maths 4 AM A »,
 * 74 000 DA versés dont 2 000 rendus, payée à Nadia Aït Ahmed au taux Standard de 60 %.
 */

export function payoutPreview(overrides: Partial<PayoutPreview> = {}): PayoutPreview {
  return {
    seriesId: 12, seriesName: 'Octobre', groupId: 3, groupName: 'Maths 4 AM A', teacherId: 5,
    teacherName: 'Nadia Aït Ahmed', kind: 'INITIAL', rateId: 7, rateLabel: 'Standard', teacherPercent: 60,
    collectedGross: 74000, refunded: 2000, collectedNet: 72000, netCovered: 0, baseDelta: 72000,
    teacherAmount: 43200, schoolAmount: 28800, teacherPaid: 0, previewToken: 'jeton-1',
    ...overrides
  };
}

export function payout(overrides: Partial<Payout> = {}): Payout {
  return {
    id: 40, payoutNumber: 'PAIE-2030-0001', kind: 'INITIAL', initialPayoutNumber: null, teacherId: 5,
    teacherName: 'Nadia Aït Ahmed', groupId: 3, groupName: 'Maths 4 AM A', seriesId: 12, seriesName: 'Octobre',
    rateLabel: 'Standard', teacherPercent: 60, collectedGross: 74000, refunded: 2000, collectedNet: 72000,
    baseDelta: 72000, teacherAmount: 43200, schoolAmount: 28800, note: null, paidAt: '2030-02-01T10:30:00',
    paidBy: 'directrice', status: 'ACTIVE', cancelledAt: null, cancelledBy: null, cancelReasonType: null,
    cancelReasonText: null, replacesNumber: null, replacedByNumber: null,
    ...overrides
  };
}

export function payableSeries(overrides: Partial<PayableSeries> = {}): PayableSeries {
  return {
    seriesId: 12, seriesName: 'Octobre', groupId: 3, groupName: 'Maths 4 AM A', teacherId: 5,
    teacherName: 'Nadia Aït Ahmed', state: 'PAYABLE', activeSessions: 8, validatedSessions: 8,
    plannedSessions: 8, missingSessions: 0, collectedGross: 74000, refunded: 2000, collectedNet: 72000, initialPayoutNumber: null, teacherPercent: null,
    teacherPaid: null, gap: null,
    ...overrides
  };
}

export function slip(overrides: Partial<PayoutSlip> = {}): PayoutSlip {
  return {
    payout: payout(), issuanceRank: 1, issuedAt: '2030-02-01T10:31:00', issuedBy: 'directrice',
    fileName: 'paie-2030-0001_nadia_ait_ahmed.pdf',
    ...overrides
  };
}

export function rate(overrides: Partial<TeacherPayRate> = {}): TeacherPayRate {
  return { id: 7, label: 'Standard', teacherPercent: 60, schoolPercent: 40, active: true, ...overrides };
}
