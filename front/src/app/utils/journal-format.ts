import { formatCalendarDay } from './calendar-day';

/**
 * Horodatage `yyyy-MM-ddTHH:mm:ss` du serveur, affiché `dd/MM/yyyy HH:mm`.
 *
 * <p>Le texte est découpé, jamais converti en `Date` : c'est déjà l'heure de l'école, et une
 * conversion la décalerait sur un poste réglé sur un autre fuseau. Une autre forme est rendue telle
 * quelle.</p>
 */
export function formatJournalInstant(value: string | null | undefined): string {
  const match = /^(\d{4}-\d{2}-\d{2})T(\d{2}):(\d{2})/.exec(value ?? '');
  return match ? `${formatCalendarDay(match[1])} ${match[2]}:${match[3]}` : (value ?? '');
}
