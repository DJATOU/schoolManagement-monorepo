import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import localeEn from '@angular/common/locales/en';
import localeArDz from '@angular/common/locales/ar-DZ';

/**
 * Locales dont les données sont enregistrées au démarrage (voir `registerAppLocales`).
 *
 * <p>Angular n'embarque que « en-US » : instancier un `DatePipe` avec une locale non
 * enregistrée lève « NG0701: Missing locale data ». Toute langue inconnue est donc ramenée
 * ici à une locale sûre, afin qu'un ajout de langue dans ngx-translate ne casse pas le
 * formatage des dates avant que sa locale ne soit enregistrée.</p>
 *
 * <p>L'arabe utilise « ar-DZ » : noms de mois algériens (جانفي، فيفري…) et chiffres latins,
 * l'usage des écoles algériennes.</p>
 */
const LOCALE_BY_LANGUAGE: Record<string, string> = { fr: 'fr', en: 'en', ar: 'ar-DZ' };
/** Locale par défaut de l'application. */
const DEFAULT_LOCALE = 'fr';
/**
 * Ramène un code de langue à une locale dont les données sont enregistrées.
 *
 * @param lang code de langue courant (ex. « fr », « en », « en-GB », « ar »)
 * @returns une locale utilisable par les pipes de formatage
 */
export function resolveLocale(lang?: string | null): string {
  if (!lang) {
    return DEFAULT_LOCALE;
  }
  const base = lang.toLowerCase().split('-')[0];
  return LOCALE_BY_LANGUAGE[base] ?? DEFAULT_LOCALE;
}

/**
 * Enregistre les données de toutes les locales que `resolveLocale` peut renvoyer.
 * Appelé une fois avant le démarrage (`src/main.ts`) ; sans cela, un `DatePipe` ou un
 * `formatDate` dans la langue courante lève NG0701 et interrompt le traitement en cours.
 */
export function registerAppLocales(): void {
  registerLocaleData(localeFr);
  registerLocaleData(localeEn);
  registerLocaleData(localeArDz);
}
