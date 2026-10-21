import { TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { AppLanguage, toSupportedLanguage } from './app-languages';
import { resolveLocale } from './locale';

/**
 * Langue des documents imprimés.
 *
 * <p>pdfmake n'embarque que Roboto, sans glyphes arabes ni algorithme bidirectionnel : un PDF
 * pdfmake ne reçoit donc jamais de texte arabe. Un document en arabe est produit par le navigateur
 * (page HTML de droite à gauche, voir `pdfDefinitionToHtml`), à partir de la même définition.</p>
 *
 * <p>La langue d'un document est choisie au moment de l'imprimer (voir {@link withPrintLanguage}).
 * Hors de ce choix, la règle historique s'applique : les documents suivent l'interface en
 * français et en anglais, et toute autre langue produit un document en français.</p>
 */
export type PrintLanguage = AppLanguage;

/** Alias historique de {@link PrintLanguage}. */
export type PdfLanguage = PrintLanguage;

/**
 * Langue choisie pour le document en cours de composition, ou `null` hors de tout choix.
 *
 * <p>Portée synchrone : un service lit la langue dans la première instruction de sa méthode
 * publique, avant toute attente, puis y rentre de nouveau après (voir {@link withPrintLanguage}).
 * Ainsi aucune signature publique ne change, et un document composé en dehors d'un choix garde
 * exactement son comportement d'avant.</p>
 */
let scopedLanguage: PrintLanguage | null = null;

/** Langue de l'écran, ramenée à une langue proposée (inconnue → français). */
export function printLanguageOf(translate: TranslateService): PrintLanguage {
  return toSupportedLanguage(translate.currentLang);
}

/**
 * Exécute `render` avec `lang` pour langue des documents, puis rétablit la langue précédente,
 * même si `render` échoue. Les appels s'imbriquent.
 */
export function withPrintLanguage<T>(lang: PrintLanguage, render: () => T): T {
  const previous = scopedLanguage;
  scopedLanguage = lang;
  try {
    return render();
  } finally {
    scopedLanguage = previous;
  }
}

/** Règle historique : la langue de l'interface en français et en anglais, le français sinon. */
function legacyPdfLanguage(translate: TranslateService): 'fr' | 'en' {
  const base = (translate.currentLang ?? '').toLowerCase().split('-')[0];
  return base === 'en' ? 'en' : 'fr';
}

/** Langue dans laquelle un document est rédigé : celle choisie, sinon la règle historique. */
export function pdfLanguage(translate: TranslateService): PrintLanguage {
  return scopedLanguage ?? legacyPdfLanguage(translate);
}

/**
 * Traduit une clé pour un document.
 *
 * <p>Langue du document égale à celle de l'interface : traduction courante. Sinon la clé est lue
 * dans les traductions déjà chargées de la langue du document, puis dans les traductions
 * françaises ; la clé elle-même est renvoyée si aucune ne la contient.</p>
 */
export function pdfInstant(translate: TranslateService, key: string, params?: object): string {
  const lang = pdfLanguage(translate);
  if (lang === translate.currentLang) {
    return translate.instant(key, params);
  }
  if (lang !== 'fr' && translate.translations[lang]) {
    const value = translate.getParsedResult(translate.translations[lang], key, params);
    if (value !== key && value !== undefined && value !== null) {
      return value;
    }
  }
  return translate.getParsedResult(translate.translations['fr'] ?? {}, key, params) ?? key;
}

/** Locale de formatage (dates, nombres) d'un document, cohérente avec {@link pdfLanguage}. */
export function pdfLocale(translate: TranslateService): string {
  return resolveLocale(pdfLanguage(translate));
}

/**
 * Exécute `run` dans la langue de document choisie, une fois ses traductions disponibles.
 *
 * <ul>
 *   <li>aucune langue choisie : `run` s'exécute tel quel (règle historique) ;</li>
 *   <li>langue de la règle historique, ou traductions déjà chargées : exécution synchrone ;</li>
 *   <li>sinon les traductions sont d'abord chargées (sans changer la langue de l'écran).</li>
 * </ul>
 */
export function runInPrintLanguage<T>(translate: TranslateService, lang: PrintLanguage | undefined,
                                      run: () => T | Promise<T>): Promise<T> {
  if (!lang) {
    return Promise.resolve(run());
  }
  if (lang === legacyPdfLanguage(translate) || translate.translations[lang]) {
    return Promise.resolve(withPrintLanguage(lang, run));
  }
  return firstValueFrom(translate.getTranslation(lang)).then(() => withPrintLanguage(lang, run));
}

/**
 * Exécute `render` dans la langue de document `lang`, l'interface momentanément dans cette langue,
 * puis rétablit la langue.
 *
 * <p>Réservé aux documents qui photographient l'écran (html2canvas) ou dont les cellules sont
 * calculées par l'écran appelant : leur texte vient de l'écran, qui doit être rendu dans la langue
 * du document. Le basculement n'a lieu que si les traductions de cette langue sont déjà chargées :
 * il est alors synchrone, aucun rendu n'est peint entre l'aller et le retour, et l'utilisateur ne
 * voit pas l'écran changer de langue. Sinon `render` s'exécute dans la langue courante.</p>
 */
export function inPrintLanguage<T>(translate: TranslateService, lang: PrintLanguage, render: () => T): T {
  return withPrintLanguage(lang, () => {
    const current = translate.currentLang;
    if (!current || current === lang || !translate.translations[lang]) {
      return render();
    }
    translate.use(lang);
    try {
      return render();
    } finally {
      translate.use(current);
    }
  });
}

/**
 * {@link inPrintLanguage} dans la langue de la règle historique : sous une interface en arabe, la
 * capture sort en français comme les autres documents composés hors d'un choix de langue.
 */
export function inPdfLanguage<T>(translate: TranslateService, render: () => T): T {
  return inPrintLanguage(translate, legacyPdfLanguage(translate), render);
}

/**
 * Le document lui-même si `lang` est la langue de la règle historique ; sinon une copie dont les
 * fonctions d'en-tête et de pied de page, appelées plus tard par pdfmake, s'exécutent dans `lang`.
 *
 * <p>Renvoyer le document inchangé dans le cas historique garantit que pdfmake reçoit exactement
 * la même définition qu'avant.</p>
 */
export function bindPrintLanguage<D extends { header?: unknown; footer?: unknown }>(
  translate: TranslateService, doc: D, lang: PrintLanguage): D {
  if (lang === legacyPdfLanguage(translate)) {
    return doc;
  }
  const bind = (part: unknown): unknown => typeof part === 'function'
    ? (...args: unknown[]) => withPrintLanguage(lang, () => (part as (...a: unknown[]) => unknown)(...args))
    : part;
  const bound: { header?: unknown; footer?: unknown } = { ...doc };
  if (doc.header !== undefined) {
    bound.header = bind(doc.header);
  }
  if (doc.footer !== undefined) {
    bound.footer = bind(doc.footer);
  }
  return bound as D;
}
