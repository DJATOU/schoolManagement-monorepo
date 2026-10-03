/**
 * Jour `yyyy-MM-dd` renvoyé par le serveur, affiché `dd/MM/yyyy`.
 *
 * Le texte est découpé, jamais converti en `Date` : `new Date('2030-01-14')` est minuit UTC, donc
 * la veille dans un fuseau à l'ouest de Greenwich. Un jour d'inscription doit rester ce jour-là.
 * Une valeur absente ou d'une autre forme est rendue telle quelle.
 */
export function formatCalendarDay(day: string | null | undefined): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(day ?? '');
  return match ? `${match[3]}/${match[2]}/${match[1]}` : (day ?? '');
}

/**
 * Le jour calendaire d'un instant, `yyyy-MM-dd`, lu dans le fuseau du poste — et non en UTC, comme
 * le ferait `toISOString()`, qui donne la veille avant 1 h du matin à Alger.
 */
export function calendarDayOf(instant: Date = new Date()): string {
  const pad = (value: number): string => String(value).padStart(2, '0');
  return `${instant.getFullYear()}-${pad(instant.getMonth() + 1)}-${pad(instant.getDate())}`;
}

/** Vrai pour un jour `yyyy-MM-dd` qui existe au calendrier (le 31/02 n'existe pas). */
export function isCalendarDay(day: string | null | undefined): boolean {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(day ?? '');
  if (!match) {
    return false;
  }
  const [year, month, date] = [Number(match[1]), Number(match[2]), Number(match[3])];
  const probe = new Date(year, month - 1, date);
  return probe.getFullYear() === year && probe.getMonth() === month - 1 && probe.getDate() === date;
}
