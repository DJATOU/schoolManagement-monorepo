import { formatDayFirst, localDay, parseIsoDay, toIsoDay } from './iso-day';

/**
 * Jours du calendrier : aucune conversion UTC. Ces specs doivent passer dans tout fuseau
 * (la vérification les relance sous TZ=America/Sao_Paulo et TZ=Pacific/Kiritimati).
 */
describe('iso-day', () => {
  function expectLocalDay(date: Date | null, year: number, month: number, day: number): void {
    expect(date).not.toBeNull();
    expect(date!.getFullYear()).toBe(year);
    expect(date!.getMonth()).toBe(month - 1);
    expect(date!.getDate()).toBe(day);
  }

  describe('toIsoDay', () => {
    it('lit les composantes locales, jamais UTC', () => {
      expect(toIsoDay(new Date(2010, 4, 15))).toBe('2010-05-15');
      // 23 h 30 locales : toISOString() donnerait le lendemain à l'est… ou la veille à l'ouest.
      expect(toIsoDay(new Date(2010, 4, 15, 23, 30))).toBe('2010-05-15');
      expect(toIsoDay(new Date(2010, 0, 5, 0, 15))).toBe('2010-01-05');
    });

    it('absent ou invalide : null', () => {
      expect(toIsoDay(null)).toBeNull();
      expect(toIsoDay(undefined)).toBeNull();
      expect(toIsoDay(new Date(NaN))).toBeNull();
    });
  });

  describe('parseIsoDay', () => {
    it('yyyy-MM-dd : minuit local du même jour', () => {
      const date = parseIsoDay('2010-05-15');
      expectLocalDay(date, 2010, 5, 15);
      expect(date!.getHours()).toBe(0);
    });

    it('jour inexistant ou format libre : null', () => {
      expect(parseIsoDay('2010-02-30')).toBeNull();
      expect(parseIsoDay('2010-13-01')).toBeNull();
      expect(parseIsoDay('15/05/2010')).toBeNull();
      expect(parseIsoDay('2010-5-15')).toBeNull();
      expect(parseIsoDay('')).toBeNull();
      expect(parseIsoDay(null)).toBeNull();
      expect(parseIsoDay(undefined)).toBeNull();
      expect(parseIsoDay({})).toBeNull();
    });

    it('Date valide : rendue telle quelle ; invalide : null', () => {
      const date = new Date(2010, 4, 15);
      expect(parseIsoDay(date)).toBe(date);
      expect(parseIsoDay(new Date(NaN))).toBeNull();
    });

    it('horodatage hérité de l\'ancien format : jour local', () => {
      expectLocalDay(parseIsoDay(new Date(1985, 1, 20).getTime()), 1985, 2, 20);
      expect(parseIsoDay(NaN)).toBeNull();
    });
  });

  describe('formatDayFirst', () => {
    it('yyyy-MM-dd découpé sans conversion', () => {
      expect(formatDayFirst('2010-05-15')).toBe('15/05/2010');
    });

    it('Date lue dans ses composantes locales', () => {
      expect(formatDayFirst(new Date(2010, 4, 15))).toBe('15/05/2010');
    });

    it('autre valeur : chaîne vide', () => {
      expect(formatDayFirst(null)).toBe('');
      expect(formatDayFirst(undefined)).toBe('');
      expect(formatDayFirst('n\'importe quoi')).toBe('');
      expect(formatDayFirst(new Date(NaN))).toBe('');
      expect(formatDayFirst(1273881600000)).toBe('');
    });
  });

  it('localDay refuse les débordements', () => {
    expect(localDay(2010, 2, 29)).toBeNull();
    expect(localDay(2012, 2, 29)).not.toBeNull();
    expect(localDay(2010, 0, 1)).toBeNull();
    expect(localDay(2010, 1, 0)).toBeNull();
  });
});
