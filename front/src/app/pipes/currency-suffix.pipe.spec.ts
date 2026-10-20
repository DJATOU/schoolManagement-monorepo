import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import frTranslations from '../../assets/i18n/fr.json';
import enTranslations from '../../assets/i18n/en.json';
import arTranslations from '../../assets/i18n/ar.json';
import { AmountPipe, formatAmount } from './amount.pipe';
import { CurrencySuffixPipe } from './currency-suffix.pipe';
import {
  CURRENCY_SUFFIX_KEY,
  currencySuffix,
  pdfCurrencySuffix,
  pdfWithCurrency,
  withCurrency
} from '../shared/currency';
import { inPdfLanguage } from '../shared/pdf-i18n';

/** Espaces de groupement (U+202F en français, U+00A0 ailleurs) ramenées à une espace ordinaire. */
const plain = (text: string | null | undefined): string => (text ?? '').replace(/\s+/g, ' ').trim();

/** Montants affichés par les deux pipes de nombre de l'application, suivis du suffixe. */
@Component({
  standalone: true,
  imports: [CommonModule, AmountPipe, CurrencySuffixPipe],
  template: `
    <span class="amount">{{ value | amount | appCurrency }}</span>
    <span class="number">{{ value | number:'1.0-2' | appCurrency }}</span>`
})
class AmountHostComponent {
  value = 1200;
}

/**
 * Suffixe du dinar : « DA » en français et en anglais, « دج » en arabe. Les PDF, rédigés en
 * français sous une interface en arabe, gardent « DA ». Le formatage du nombre n'est jamais touché.
 */
describe('Suffixe monétaire (DA / دج)', () => {
  let translate: TranslateService;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [TranslateModule.forRoot(), AmountHostComponent] });
    translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', frTranslations);
    translate.setTranslation('en', enTranslations);
    translate.setTranslation('ar', arTranslations);
  });

  describe('withCurrency (écran)', () => {
    it('français : « 1 200 DA », nombre inchangé', () => {
      translate.use('fr');
      expect(currencySuffix(translate)).toBe('DA');
      expect(withCurrency('1 200', translate)).toBe('1 200 DA');
      expect(plain(withCurrency(formatAmount(1200, 'fr'), translate))).toBe('1 200,00 DA');
    });

    it('anglais : « 1,200.00 DA », inchangé', () => {
      translate.use('en');
      expect(currencySuffix(translate)).toBe('DA');
      expect(withCurrency(formatAmount(1200, 'en'), translate)).toBe('1,200.00 DA');
    });

    it('arabe : suffixe « دج », le nombre reste formaté comme avant', () => {
      translate.use('ar');
      expect(currencySuffix(translate)).toBe('دج');
      expect(withCurrency('1 200', translate)).toBe('1 200 دج');
      expect(withCurrency(formatAmount(1200, 'ar'), translate)).toBe(`${formatAmount(1200, 'ar')} دج`);
      expect(withCurrency(1200, translate)).not.toContain('DA');
    });

    it('sans montant : le suffixe seul, pour le libellé d\'un champ de saisie', () => {
      translate.use('ar');
      expect(withCurrency(null, translate)).toBe('دج');
      expect(withCurrency(undefined, translate)).toBe('دج');
      expect(withCurrency('', translate)).toBe('دج');
      expect(withCurrency(0, translate)).toBe('0 دج');
    });

    it('traductions absentes : « DA », jamais la clé brute', () => {
      translate.resetLang('fr');
      translate.resetLang('ar');
      translate.use('ar');
      expect(translate.instant(CURRENCY_SUFFIX_KEY)).toBe(CURRENCY_SUFFIX_KEY);
      expect(currencySuffix(translate)).toBe('DA');
      expect(withCurrency('1 200', translate)).toBe('1 200 DA');
    });
  });

  describe('pdfWithCurrency (documents)', () => {
    it('interface en arabe : le PDF, rédigé en français, garde « DA »', () => {
      translate.use('ar');
      expect(pdfCurrencySuffix(translate)).toBe('DA');
      expect(pdfWithCurrency('1 200,00', translate)).toBe('1 200,00 DA');
      // L'écran, lui, est en « دج » au même instant.
      expect(withCurrency('1 200,00', translate)).toBe('1 200,00 دج');
    });

    it('interface en français ou en anglais : « DA »', () => {
      for (const lang of ['fr', 'en']) {
        translate.use(lang);
        expect(pdfWithCurrency('1 200', translate)).withContext(lang).toBe('1 200 DA');
      }
    });

    it('capture imprimée sous l\'arabe (inPdfLanguage) : « DA », puis « دج » à l\'écran', () => {
      translate.use('ar');
      expect(inPdfLanguage(translate, () => withCurrency('3000', translate))).toBe('3000 DA');
      expect(withCurrency('3000', translate)).toBe('3000 دج');
    });
  });

  describe('pipe appCurrency', () => {
    it('suit la langue de l\'interface sans changer le nombre formaté', () => {
      const fixture = TestBed.createComponent(AmountHostComponent);
      const text = (selector: string) =>
        plain((fixture.nativeElement as HTMLElement).querySelector(selector)?.textContent);

      translate.use('fr');
      fixture.detectChanges();
      expect(text('.amount')).toBe('1 200,00 DA');
      expect(text('.number')).toBe('1,200 DA');

      translate.use('en');
      fixture.detectChanges();
      expect(text('.amount')).toBe('1,200.00 DA');
      expect(text('.number')).toBe('1,200 DA');

      translate.use('ar');
      fixture.detectChanges();
      expect(text('.amount')).toBe(`${plain(formatAmount(1200, 'ar'))} دج`);
      expect(text('.number')).toBe('1,200 دج');
    });
  });

  describe('fichiers de traduction', () => {
    it('définit le suffixe : « DA » en français et en anglais, « دج » en arabe', () => {
      expect(frTranslations.COMMON.CURRENCY_SUFFIX).toBe('DA');
      expect(enTranslations.COMMON.CURRENCY_SUFFIX).toBe('DA');
      expect(arTranslations.COMMON.CURRENCY_SUFFIX).toBe('دج');
    });

    it('n\'écrit jamais « DA » dans une valeur arabe', () => {
      const offenders: string[] = [];
      const walk = (node: unknown, path: string): void => {
        if (typeof node === 'string') {
          if (/\bDA\b/.test(node)) {
            offenders.push(`${path} = ${node}`);
          }
        } else if (node && typeof node === 'object') {
          Object.entries(node).forEach(([key, value]) => walk(value, path ? `${path}.${key}` : key));
        }
      };
      walk(arTranslations, '');
      expect(offenders).withContext(offenders.join(' ; ')).toEqual([]);
    });
  });
});
