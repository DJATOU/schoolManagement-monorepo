import { formatCalendarDay } from './calendar-day';

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
