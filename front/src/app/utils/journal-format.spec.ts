import { formatJournalInstant } from './journal-format';

describe('formatJournalInstant', () => {
  it('rend l\'heure de l\'école telle quelle, en jour et heure français', () => {
    expect(formatJournalInstant('2030-01-07T09:05:33')).toBe('07/01/2030 09:05');
    expect(formatJournalInstant('2030-01-07T00:30:00')).toBe('07/01/2030 00:30');
  });

  it('une autre forme est rendue telle quelle, une valeur absente en vide', () => {
    expect(formatJournalInstant('hier')).toBe('hier');
    expect(formatJournalInstant(null)).toBe('');
    expect(formatJournalInstant(undefined)).toBe('');
  });
});
