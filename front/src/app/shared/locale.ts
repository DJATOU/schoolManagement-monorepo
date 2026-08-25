/**
 * Locales dont les données sont enregistrées au démarrage (voir `src/main.ts`).
 *
 * <p>Angular n'embarque que « en-US » : instancier un `DatePipe` avec une locale non
 * enregistrée lève « NG0701: Missing locale data ». Toute langue inconnue est donc ramenée
 * ici à une locale sûre, afin qu'un ajout de langue dans ngx-translate ne casse pas le
 * formatage des dates avant que sa locale ne soit enregistrée.</p>
 */
const REGISTERED_LOCALES = ['fr', 'en'];

/** Locale par défaut de l'application. */
const DEFAULT_LOCALE = 'fr';

/**
 * Ramène un code de langue à une locale dont les données sont enregistrées.
 *
 * @param lang code de langue courant (ex. « fr », « en », « en-GB »)
 * @returns une locale utilisable par les pipes de formatage
 */
export function resolveLocale(lang?: string | null): string {
  if (!lang) {
    return DEFAULT_LOCALE;
  }
  const base = lang.toLowerCase().split('-')[0];
  return REGISTERED_LOCALES.includes(base) ? base : DEFAULT_LOCALE;
}
