import { TranslateService } from '@ngx-translate/core';
import { resolveLocale } from './locale';

/**
 * Langue des documents PDF.
 *
 * <p>pdfmake n'embarque que Roboto, sans glyphes arabes ni algorithme bidirectionnel : un PDF
 * ne doit donc jamais recevoir de texte arabe. Les PDF suivent l'interface en français et en
 * anglais ; toute autre langue (l'arabe notamment) produit un PDF en français.</p>
 */
export type PdfLanguage = 'fr' | 'en';

/** Langue dans laquelle un PDF est rédigé, déduite de la langue de l'interface. */
export function pdfLanguage(translate: TranslateService): PdfLanguage {
  const base = (translate.currentLang ?? '').toLowerCase().split('-')[0];
  return base === 'en' ? 'en' : 'fr';
}

/**
 * Traduit une clé pour un PDF.
 *
 * <p>Interface en français ou en anglais : traduction courante. Toute autre langue : la clé est
 * lue dans les traductions françaises déjà chargées, et la clé elle-même est renvoyée si elles
 * ne la contiennent pas.</p>
 */
export function pdfInstant(translate: TranslateService, key: string, params?: object): string {
  const current = translate.currentLang;
  if (current === 'fr' || current === 'en') {
    return translate.instant(key, params);
  }
  return translate.getParsedResult(translate.translations['fr'] ?? {}, key, params) ?? key;
}

/** Locale de formatage (dates, nombres) d'un PDF, cohérente avec {@link pdfLanguage}. */
export function pdfLocale(translate: TranslateService): string {
  return resolveLocale(pdfLanguage(translate));
}
/**
 * Exécute `render` avec l'interface momentanément dans la langue du PDF, puis rétablit la langue.
 *
 * <p>Réservé aux PDF qui photographient l'écran (html2canvas) au lieu de composer leur texte : sous
 * une interface en arabe, la capture doit sortir en français comme les autres documents. Le
 * basculement n'a lieu que si les traductions de la langue du PDF sont déjà chargées (le français,
 * langue par défaut, l'est toujours) : il est alors synchrone, aucun rendu n'est peint entre l'aller
 * et le retour, et l'utilisateur ne voit pas l'écran changer de langue. Sinon `render` s'exécute
 * dans la langue courante.</p>
 */
export function inPdfLanguage<T>(translate: TranslateService, render: () => T): T {
  const current = translate.currentLang;
  const target = pdfLanguage(translate);
  if (!current || current === target || !translate.translations[target]) {
    return render();
  }
  translate.use(target);
  try {
    return render();
  } finally {
    translate.use(current);
  }
}
