import { calendarDayOf, formatCalendarDay, isCalendarDay } from './calendar-day';

describe('formatCalendarDay', () => {
  it('affiche un jour du serveur au format de l\'école', () => {
    expect(formatCalendarDay('2030-01-14')).toBe('14/01/2030');
  });

  it('garde le jour même dans un fuseau à l\'ouest de Greenwich : le texte n\'est jamais converti en Date', () => {
    // new Date('2030-01-01') vaut minuit UTC, donc le 31/12/2029 à Montréal.
    expect(formatCalendarDay('2030-01-01')).toBe('01/01/2030');
  });

  it('valeur absente : chaîne vide ; autre forme : rendue telle quelle', () => {
    expect(formatCalendarDay(null)).toBe('');
    expect(formatCalendarDay(undefined)).toBe('');
    expect(formatCalendarDay('2030-01-14T10:00:00')).toBe('2030-01-14T10:00:00');
  });
});

describe('calendarDayOf', () => {
  it('donne le jour local de l\'instant, mois et jour sur deux chiffres', () => {
    expect(calendarDayOf(new Date(2030, 0, 7, 10, 30))).toBe('2030-01-07');
    expect(calendarDayOf(new Date(2030, 10, 25))).toBe('2030-11-25');
  });

  it('lit le jour dans le fuseau du poste, et non en UTC : 00:30 reste le jour même', () => {
    // toISOString() donnerait la veille à l'est de Greenwich (Alger : UTC+1).
    expect(calendarDayOf(new Date(2030, 0, 14, 0, 30))).toBe('2030-01-14');
    expect(calendarDayOf(new Date(2030, 0, 14, 23, 59))).toBe('2030-01-14');
  });

  it('sans argument : aujourd\'hui', () => {
    const now = new Date();
    expect(calendarDayOf()).toBe(calendarDayOf(now));
  });
});

describe('isCalendarDay', () => {
  it('accepte un jour qui existe, y compris le 29/02 d\'une année bissextile', () => {
    expect(isCalendarDay('2030-01-14')).toBeTrue();
    expect(isCalendarDay('2028-02-29')).toBeTrue();
  });

  it('refuse un jour qui n\'existe pas, une autre forme, une valeur absente', () => {
    expect(isCalendarDay('2030-02-29')).toBeFalse();
    expect(isCalendarDay('2030-13-01')).toBeFalse();
    expect(isCalendarDay('2030-04-31')).toBeFalse();
    expect(isCalendarDay('14/01/2030')).toBeFalse();
    expect(isCalendarDay('')).toBeFalse();
    expect(isCalendarDay(null)).toBeFalse();
    expect(isCalendarDay(undefined)).toBeFalse();
  });
});
