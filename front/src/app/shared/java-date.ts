/**
 * Conversion des dates renvoyées par le backend Java.
 *
 * <p>Un champ {@code LocalDateTime} est sérialisé par Jackson sous forme de tableau
 * {@code [année, mois, jour, heure, minute, seconde, nano]} (mois 1-based), alors qu'un
 * champ {@code Date} arrive en chaîne ISO. Les deux formats coexistent dans l'API, et le
 * champ peut être nul.</p>
 *
 * <p>Cette conversion était auparavant dupliquée dans les composants sous la forme
 * {@code date.toString().split(',')}, sans aucune protection : sur une date nulle,
 * l'appel levait une {@code TypeError} <strong>pendant le rendu du gabarit</strong>, ce qui
 * interrompait la passe de détection de changement et laissait le reste du tableau vide.</p>
 */

/**
 * Convertit une valeur de date issue de l'API en {@link Date} exploitable.
 *
 * @param value tableau Jackson, chaîne ISO, horodatage, {@link Date}, ou nul
 * @returns la date correspondante, ou {@code null} si la valeur est absente ou invalide
 */
export function parseJavaDate(value: unknown): Date | null {
  if (value === null || value === undefined || value === '') {
    return null;
  }

  if (value instanceof Date) {
    return isNaN(value.getTime()) ? null : value;
  }

  // Format Jackson d'un LocalDateTime : [yyyy, MM, dd, HH, mm, ss, nano] (mois 1-based).
  if (Array.isArray(value) && value.length >= 3) {
    const [year, month, day, hour = 0, minute = 0, second = 0] = value as number[];
    const parsed = new Date(year, month - 1, day, hour, minute, second);
    return isNaN(parsed.getTime()) ? null : parsed;
  }

  if (typeof value === 'string' || typeof value === 'number') {
    const parsed = new Date(value);
    return isNaN(parsed.getTime()) ? null : parsed;
  }

  return null;
}
