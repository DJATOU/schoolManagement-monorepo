/**
 * Jours du calendrier (date de naissance) échangés avec le backend sous la forme `yyyy-MM-dd`.
 *
 * <p>Un jour du calendrier n'est pas un instant : il n'a ni heure ni fuseau. Le convertir en
 * instant (`toISOString()`, `new Date('2010-05-15')`, millisecondes) le fait glisser au jour
 * voisin dès que le fuseau du navigateur diffère de celui qui l'a produit. Ces fonctions ne
 * passent donc jamais par UTC : elles lisent et écrivent les composantes <strong>locales</strong>
 * d'un `Date` (minuit local), ou découpent la chaîne directement.</p>
 */

const ISO_DAY = /^(\d{4})-(\d{2})-(\d{2})$/;

function pad(n: number, width = 2): string {
  return String(n).padStart(width, '0');
}

/** Minuit local du jour donné, ou `null` si ce jour n'existe pas (31/02, mois 13…). */
export function localDay(year: number, month: number, day: number): Date | null {
  if (month < 1 || month > 12 || day < 1 || day > 31) {
    return null;
  }
  const date = new Date(year, month - 1, day);
  // Le constructeur reporte les débordements (31 février → 3 mars) : on les refuse.
  return date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day
    ? date
    : null;
}

/**
 * `yyyy-MM-dd` construit depuis les composantes locales du `Date` (jamais `toISOString()`).
 *
 * @returns la chaîne du jour, ou `null` si la date est absente ou invalide
 */
export function toIsoDay(date: Date | null | undefined): string | null {
  if (!(date instanceof Date) || isNaN(date.getTime())) {
    return null;
  }
  return `${pad(date.getFullYear(), 4)}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/**
 * Valeur de l'API vers un `Date` à minuit local du même jour.
 *
 * @param value `yyyy-MM-dd` (strict), `Date` valide, ou horodatage numérique hérité de
 *        l'ancien format de l'API (on en garde le jour local)
 * @returns le jour, ou `null` pour toute autre valeur
 */
export function parseIsoDay(value: unknown): Date | null {
  if (value instanceof Date) {
    return isNaN(value.getTime()) ? null : value;
  }
  if (typeof value === 'number') {
    const instant = new Date(value);
    return isNaN(instant.getTime())
      ? null
      : localDay(instant.getFullYear(), instant.getMonth() + 1, instant.getDate());
  }
  if (typeof value === 'string') {
    const match = ISO_DAY.exec(value.trim());
    return match ? localDay(+match[1], +match[2], +match[3]) : null;
  }
  return null;
}

/**
 * Affichage `dd/MM/yyyy`, identique à la saisie : `yyyy-MM-dd` est découpé tel quel (aucune
 * conversion de fuseau), un `Date` est lu dans ses composantes locales.
 *
 * @returns le jour formaté, ou une chaîne vide si la valeur n'est pas un jour reconnu
 */
export function formatDayFirst(value: unknown): string {
  if (typeof value === 'string') {
    const match = ISO_DAY.exec(value.trim());
    return match ? `${match[3]}/${match[2]}/${match[1]}` : '';
  }
  if (value instanceof Date && !isNaN(value.getTime())) {
    return `${pad(value.getDate())}/${pad(value.getMonth() + 1)}/${pad(value.getFullYear(), 4)}`;
  }
  return '';
}
