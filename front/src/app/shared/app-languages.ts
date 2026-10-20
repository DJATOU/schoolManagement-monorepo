import { Direction } from '@angular/cdk/bidi';

/** Langues proposées par l'interface. */
export type AppLanguage = 'fr' | 'en' | 'ar';

/** Langue par défaut, et de repli pour toute valeur inconnue. */
export const DEFAULT_LANGUAGE: AppLanguage = 'fr';

/** Clé du stockage local où la langue choisie est conservée. */
export const LANGUAGE_STORAGE_KEY = 'app-language';

/**
 * Langues du sélecteur, chacune nommée dans sa propre langue (autonyme) : un utilisateur qui
 * ne lit pas la langue courante retrouve la sienne. Même principe que `GUIDE_LANG_NAMES`.
 */
export const APP_LANGUAGES: ReadonlyArray<{ code: AppLanguage; name: string }> = [
  { code: 'fr', name: 'Français' },
  { code: 'en', name: 'English' },
  { code: 'ar', name: 'العربية' }
];

/** Codes des langues proposées. */
export const SUPPORTED_LANGS: ReadonlyArray<AppLanguage> = APP_LANGUAGES.map(l => l.code);

/** Ramène une valeur quelconque à une langue proposée (inconnue → français). */
export function toSupportedLanguage(lang: string | null | undefined): AppLanguage {
  return SUPPORTED_LANGS.includes(lang as AppLanguage) ? (lang as AppLanguage) : DEFAULT_LANGUAGE;
}

/** Sens d'écriture d'une langue : seul l'arabe s'écrit de droite à gauche. */
export function directionOf(lang: string | null | undefined): Direction {
  return lang === 'ar' ? 'rtl' : 'ltr';
}

/** Langue enregistrée par l'utilisateur, ou le français (stockage indisponible ou valeur inconnue). */
export function readSavedLanguage(): AppLanguage {
  try {
    return toSupportedLanguage(localStorage.getItem(LANGUAGE_STORAGE_KEY));
  } catch {
    return DEFAULT_LANGUAGE;
  }
}

/** Pose `lang` et `dir` sur l'élément `<html>` (lecteurs d'écran, police arabe, sens d'écriture). */
export function applyDocumentLanguage(lang: AppLanguage, doc: Document = document): void {
  doc.documentElement.lang = lang;
  doc.documentElement.dir = directionOf(lang);
}
