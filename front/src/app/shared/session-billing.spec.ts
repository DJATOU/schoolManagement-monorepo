import {
  countBillableSessions,
  countExcludedSessions,
  isBillableUnpaid,
  isCatchUpBilled,
  isExcludedSession
} from './session-billing';
import { SessionHistoryDTO } from '../models/session/SessionHistoryDTO';
import { SeriesHistoryDTO } from '../models/sessionSerie/SeriesHistoryDTO';

/**
 * Lisibilité du prorata dans les historiques (exigences 11.3 à 11.6).
 *
 * <p>L'enjeu de ces tests est une confusion d'administrateur, pas un calcul : une séance
 * écartée du prorata ne doit jamais être classée avec les séances dues, sans quoi l'étudiant
 * paraît en retard alors qu'il est à jour.</p>
 */
describe('session-billing', () => {

  const session = (overrides: Partial<SessionHistoryDTO> = {}): SessionHistoryDTO => ({
    catchUpSession: false,
    sessionId: 1,
    sessionName: 'Séance 1',
    sessionDate: '2024-01-10',
    attendanceStatus: 'PRESENT',
    isJustified: false,
    description: '',
    paymentStatus: 'PAID',
    amountPaid: 30,
    paymentDate: '2024-01-10',
    ...overrides
  });

  describe('isExcludedSession', () => {
    it('trusts the server reason above every other signal', () => {
      expect(isExcludedSession(session({ inclusionReason: 'EXCLUDED' }))).toBeTrue();
      expect(isExcludedSession(session({ inclusionReason: 'AFTER_ENROLMENT' }))).toBeFalse();
      expect(isExcludedSession(session({ inclusionReason: 'ATTENDED_BEFORE_ENROLMENT' }))).toBeFalse();
    });

    it('bills a session held after enrolment even without an attendance sheet', () => {
      // Le cas que l'approximation classait à tort non facturée : séance postérieure à
      // l'inscription dont la présence n'est simplement pas encore renseignée. Elle est due.
      const afterEnrolmentNotYetTaken = session({
        inclusionReason: 'AFTER_ENROLMENT',
        attendanceStatus: 'UNKNOWN',
        amountPaid: 0,
        paymentStatus: 'UNPAID'
      });
      expect(isExcludedSession(afterEnrolmentNotYetTaken)).toBeFalse();
      expect(isBillableUnpaid(afterEnrolmentNotYetTaken)).toBeTrue();
      // L'ancien repli, sans motif, se trompait sur la même séance.
      expect(isExcludedSession(session({ attendanceStatus: 'UNKNOWN', amountPaid: 0 }))).toBeTrue();
    });

    it('trusts the server flag when the reason is absent', () => {
      expect(isExcludedSession(session({ billable: false }))).toBeTrue();
      // Le drapeau serveur prime sur l'assiduité : une séance facturable sans feuille de
      // présence reste facturable.
      expect(isExcludedSession(session({ billable: true, attendanceStatus: 'UNKNOWN', amountPaid: 0 })))
        .toBeFalse();
    });

    it('falls back to attendance when neither reason nor flag is present', () => {
      expect(isExcludedSession(session({ attendanceStatus: 'UNKNOWN', amountPaid: 0 }))).toBeTrue();
      expect(isExcludedSession(session({ attendanceStatus: 'PRESENT' }))).toBeFalse();
      expect(isExcludedSession(session({ attendanceStatus: 'ABSENT', amountPaid: 0 }))).toBeFalse();
    });

    it('keeps a session carrying an allocated amount billable', () => {
      // Un montant affecté prouve que la séance a été facturée : l'afficher « non facturée »
      // contredirait la ligne de paiement.
      expect(isExcludedSession(session({ attendanceStatus: 'UNKNOWN', amountPaid: 30 }))).toBeFalse();
    });
  });

  describe('isBillableUnpaid', () => {
    it('flags only billable sessions left to pay', () => {
      expect(isBillableUnpaid(session({ paymentStatus: 'UNPAID' }))).toBeTrue();
      expect(isBillableUnpaid(session({ paymentStatus: 'PARTIAL' }))).toBeTrue();
      expect(isBillableUnpaid(session({ paymentStatus: 'PAID' }))).toBeFalse();
    });

    it('never flags an excluded session as a debt', () => {
      const excluded = session({ billable: false, paymentStatus: 'UNPAID', amountPaid: 0 });
      expect(isBillableUnpaid(excluded)).toBeFalse();
    });

    it('never flags an exempted session as a debt', () => {
      expect(isBillableUnpaid(session({ isExempted: true, paymentStatus: 'UNPAID' }))).toBeFalse();
    });
  });

  describe('isCatchUpBilled', () => {
    it('labels a session attended before enrolment', () => {
      // Le seul cas dont la facturation demande une explication (exigence 11.5).
      expect(isCatchUpBilled(session({ inclusionReason: 'ATTENDED_BEFORE_ENROLMENT' }))).toBeTrue();
    });

    it('does not label a catch-up held after enrolment', () => {
      // `catchUpSession` étiquette tous les rattrapages ; celui-ci est facturé parce qu'il est
      // postérieur à l'inscription, ce qui n'a rien à expliquer à l'administrateur.
      expect(isCatchUpBilled(session({ catchUpSession: true, inclusionReason: 'AFTER_ENROLMENT' })))
        .toBeFalse();
    });

    it('does not label an excluded session as a billed catch-up', () => {
      expect(isCatchUpBilled(session({ catchUpSession: true, inclusionReason: 'EXCLUDED' })))
        .toBeFalse();
      expect(isCatchUpBilled(session({ catchUpSession: true, billable: false }))).toBeFalse();
    });

    it('falls back to the attendance-sheet flag when the reason is absent', () => {
      expect(isCatchUpBilled(session({ catchUpSession: true }))).toBeTrue();
      expect(isCatchUpBilled(session({ catchUpSession: false }))).toBeFalse();
    });
  });

  describe('countBillableSessions', () => {
    const series = (sessions: SessionHistoryDTO[], overrides: Partial<SeriesHistoryDTO> = {}):
      SeriesHistoryDTO => ({
      seriesId: 10,
      seriesName: 'Série A',
      paymentStatus: 'PARTIAL',
      totalAmountPaid: 30,
      totalCost: 60,
      sessions,
      ...overrides
    });

    it('prefers the server count when present', () => {
      expect(countBillableSessions(series([], { billableSessions: 4 }))).toBe(4);
    });

    it('derives the count from the displayed sessions otherwise', () => {
      const sessions = [
        session({ sessionId: 1, billable: true }),
        session({ sessionId: 2, billable: false }),
        session({ sessionId: 3, billable: false })
      ];
      expect(countBillableSessions(series(sessions))).toBe(1);
      expect(countExcludedSessions(series(sessions))).toBe(2);
    });

    it('derives the count from the server reasons when the series count is absent', () => {
      const sessions = [
        session({ sessionId: 1, inclusionReason: 'AFTER_ENROLMENT' }),
        session({ sessionId: 2, inclusionReason: 'ATTENDED_BEFORE_ENROLMENT' }),
        session({ sessionId: 3, inclusionReason: 'EXCLUDED' })
      ];
      expect(countBillableSessions(series(sessions))).toBe(2);
      expect(countExcludedSessions(series(sessions))).toBe(1);
    });

    it('handles a series without sessions', () => {
      expect(countBillableSessions(series([]))).toBe(0);
      expect(countExcludedSessions(series([]))).toBe(0);
    });
  });
});
