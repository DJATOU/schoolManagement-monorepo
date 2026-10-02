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
