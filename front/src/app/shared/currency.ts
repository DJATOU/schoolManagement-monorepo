import { TranslateService } from '@ngx-translate/core';
import { pdfInstant } from './pdf-i18n';

/**
 * Clé du suffixe monétaire : « DA » en français et en anglais, « دج » en arabe.
 *
 * <p>Le suffixe était écrit « DA » en dur dans les écrans : une interface en arabe affichait donc
 * « 1200 DA » au lieu de « 1200 دج ». Il est désormais une traduction, lue par les fonctions
 * ci-dessous et par le pipe `appCurrency`.</p>
 */
export const CURRENCY_SUFFIX_KEY = 'COMMON.CURRENCY_SUFFIX';

/** Suffixe de repli, tant que les traductions ne sont pas chargées (ou dans un test sans traductions). */
const DEFAULT_CURRENCY_SUFFIX = 'DA';

/** Montant déjà formaté, ou nombre brut : le suffixe ne touche jamais au formatage du nombre. */
export type CurrencyAmount = string | number | null | undefined;

/** Suffixe monétaire dans la langue de l'interface, pour l'écran. */
export function currencySuffix(translate: TranslateService): string {
  return orDefault(translate.instant(CURRENCY_SUFFIX_KEY));
}

/**
 * Suffixe monétaire dans la langue du PDF : « DA » sous une interface en arabe, comme le reste du
 * document (voir `pdfLanguage`).
 */
export function pdfCurrencySuffix(translate: TranslateService): string {
  return orDefault(pdfInstant(translate, CURRENCY_SUFFIX_KEY));
}

/**
 * « 1 200 DA » / « 1 200 دج » : le montant tel qu'il est reçu, suivi du suffixe de l'interface.
 * Sans montant, le suffixe seul est renvoyé (libellé d'un champ de saisie, par exemple).
 */
export function withCurrency(amount: CurrencyAmount, translate: TranslateService): string {
  return append(amount, currencySuffix(translate));
}

/** {@link withCurrency} dans la langue du PDF : jamais « دج », que la police des PDF ne dessine pas. */
export function pdfWithCurrency(amount: CurrencyAmount, translate: TranslateService): string {
  return append(amount, pdfCurrencySuffix(translate));
}

function append(amount: CurrencyAmount, suffix: string): string {
  return amount === null || amount === undefined || amount === '' ? suffix : `${amount} ${suffix}`;
}

/** Une clé non traduite revient telle quelle de ngx-translate : elle ne doit pas s'afficher. */
function orDefault(value: unknown): string {
  return typeof value === 'string' && value !== '' && value !== CURRENCY_SUFFIX_KEY
    ? value
    : DEFAULT_CURRENCY_SUFFIX;
}
