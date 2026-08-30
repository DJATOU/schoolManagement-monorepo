import { SessionHistoryDTO } from '../models/session/SessionHistoryDTO';
import { SeriesHistoryDTO } from '../models/sessionSerie/SeriesHistoryDTO';
import {
  countBillableSessions,
  countExcludedSessions,
  isBilledAtHostGroup,
  isBillableUnpaid,
  isExcludedSession,
  isPendingCatchUp
} from './session-billing';

/**
 * Un rattrapage à préciser est une **troisième** catégorie, à côté de « facturée » et « écartée ».
 *
 * <p>Les trois vues qui présentent l'historique (fenêtre de paiement, historique complet, PDF)
 * lisent ce module : si le classement y était faux, l'erreur apparaîtrait partout à la fois. Ce
 * qui est vérifié est donc qu'un rattrapage à préciser n'est <strong>ni une dette ni une gratuité
 * acquise</strong> — le classer d'un côté ou de l'autre annoncerait une décision que personne n'a
 * prise.</p>
 */
describe('session-billing — rattrapage à préciser', () => {

  const session = (over: Partial<SessionHistoryDTO> = {}): SessionHistoryDTO => ({
    catchUpSession: false,
    sessionId: 1,
    sessionName: 'Séance',
    sessionDate: '2026-09-14T18:30:00',
    attendanceStatus: 'PRESENT',
    isJustified: false,
    description: '',
    paymentStatus: 'UNPAID',
    amountPaid: 0,
    paymentDate: '',
    ...over
  });

  describe('isPendingCatchUp', () => {
    it('reconnaît un rattrapage à préciser', () => {
      expect(isPendingCatchUp(session({ catchUpBillingState: 'PENDING' }))).toBeTrue();
    });

    it('ne confond pas les états décidés avec une décision en attente', () => {
      expect(isPendingCatchUp(session({ catchUpBillingState: 'RESOLVED' }))).toBeFalse();
      expect(isPendingCatchUp(session({ catchUpBillingState: 'HOST_BILLED' }))).toBeFalse();
      expect(isPendingCatchUp(session())).toBeFalse();
    });
  });

  describe('isExcludedSession', () => {
    it('n\'écarte pas un rattrapage à préciser', () => {
      // L'écarter annoncerait « non facturée », donc une gratuité décidée : l'administrateur
      // n'aurait plus rien à trancher et la recette disparaîtrait silencieusement.
      expect(isExcludedSession(session({ catchUpBillingState: 'PENDING', inclusionReason: 'EXCLUDED' })))
        .toBeFalse();
      expect(isExcludedSession(session({ catchUpBillingState: 'PENDING', billable: false })))
        .toBeFalse();
    });

    it('continue d\'écarter une séance réellement exclue', () => {
      expect(isExcludedSession(session({ inclusionReason: 'EXCLUDED' }))).toBeTrue();
    });
  });

  describe('isBillableUnpaid', () => {
    it('ne compte pas un rattrapage à préciser comme une dette', () => {
      // Le statut renvoyé par le serveur est « UNPAID » faute de mieux : le prendre au mot
      // afficherait une dette que personne n'a établie.
      expect(isBillableUnpaid(session({ catchUpBillingState: 'PENDING', paymentStatus: 'UNPAID' })))
        .toBeFalse();
    });

    it('compte une séance facturable impayée', () => {
      expect(isBillableUnpaid(session({ paymentStatus: 'UNPAID' }))).toBeTrue();
    });
  });

  describe('isBilledAtHostGroup', () => {
    it('reconnaît le Cas 2, par le drapeau comme par l\'état', () => {
      expect(isBilledAtHostGroup(session({ billedAtHostGroup: true }))).toBeTrue();
      expect(isBilledAtHostGroup(session({ catchUpBillingState: 'HOST_BILLED' }))).toBeTrue();
    });

    it('ne s\'applique pas à un rattrapage à préciser', () => {
      expect(isBilledAtHostGroup(session({ catchUpBillingState: 'PENDING' }))).toBeFalse();
    });
  });

  describe('décomptes de série', () => {
    const series = (sessions: SessionHistoryDTO[]): SeriesHistoryDTO => ({
      seriesId: 53,
      seriesName: 'Série',
      paymentStatus: 'PARTIAL',
      totalAmountPaid: 0,
      totalCost: 0,
      sessions
    } as SeriesHistoryDTO);

    it('exclut un rattrapage à préciser des deux décomptes du repli', () => {
      // Ni facturable, ni écartée : l'inclure dans l'un des deux gonflerait un total que la
      // décision n'a pas encore établi.
      const s = series([
        session({ sessionId: 1 }),
        session({ sessionId: 2, catchUpBillingState: 'PENDING' }),
        session({ sessionId: 3, inclusionReason: 'EXCLUDED' })
      ]);

      expect(countBillableSessions(s)).toBe(1);
      expect(countExcludedSessions(s)).toBe(1);
    });

    it('privilégie le décompte du serveur quand il est fourni', () => {
      const s = series([session({ catchUpBillingState: 'PENDING' })]);
      s.billableSessions = 4;
      expect(countBillableSessions(s)).toBe(4);
    });
  });
});
