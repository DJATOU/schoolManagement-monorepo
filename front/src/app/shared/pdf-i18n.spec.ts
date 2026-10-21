import { TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import {
  bindPrintLanguage, inPdfLanguage, inPrintLanguage, pdfInstant, pdfLanguage, pdfLocale, printLanguageOf,
  runInPrintLanguage, withPrintLanguage
} from './pdf-i18n';
import { pdfWithCurrency } from './currency';

/**
 * Langue des PDF : le français et l'anglais suivent l'interface, toute autre langue (l'arabe,
 * que Roboto ne sait pas dessiner) retombe sur les traductions françaises.
 */
describe('pdf-i18n', () => {
  let translate: TranslateService;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [TranslateModule.forRoot()] });
    translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', { PDF: { TITLE: 'Fiche', PAGE: 'Page {{current}} / {{total}}' } });
    translate.setTranslation('en', { PDF: { TITLE: 'Profile', PAGE: 'Page {{current}} of {{total}}' } });
    translate.setTranslation('ar', { PDF: { TITLE: 'بطاقة', PAGE: 'صفحة {{current}} / {{total}}' } });
  });

  it('français : traductions françaises, locale fr', () => {
    translate.use('fr');
    expect(pdfLanguage(translate)).toBe('fr');
    expect(pdfLocale(translate)).toBe('fr');
    expect(pdfInstant(translate, 'PDF.TITLE')).toBe('Fiche');
    expect(pdfInstant(translate, 'PDF.PAGE', { current: 1, total: 2 })).toBe('Page 1 / 2');
  });

  it('anglais : traductions anglaises, locale en', () => {
    translate.use('en');
    expect(pdfLanguage(translate)).toBe('en');
    expect(pdfLocale(translate)).toBe('en');
    expect(pdfInstant(translate, 'PDF.TITLE')).toBe('Profile');
    expect(pdfInstant(translate, 'PDF.PAGE', { current: 1, total: 2 })).toBe('Page 1 of 2');
  });

  it('autre langue (arabe) : jamais de texte arabe, les traductions françaises sont lues', () => {
    translate.use('ar');
    expect(pdfLanguage(translate)).toBe('fr');
    expect(pdfLocale(translate)).toBe('fr');
    expect(pdfInstant(translate, 'PDF.TITLE')).toBe('Fiche');
    expect(pdfInstant(translate, 'PDF.PAGE', { current: 3, total: 4 })).toBe('Page 3 / 4');
  });

  it('clé absente des traductions françaises : la clé est renvoyée', () => {
    translate.use('ar');
    expect(pdfInstant(translate, 'PDF.MISSING')).toBe('PDF.MISSING');
  });

  describe('inPdfLanguage (captures d\'écran)', () => {
    it('arabe : rendu en français, langue rétablie, deux changements synchrones', () => {
      translate.use('ar');
      const changes: string[] = [];
      translate.onLangChange.subscribe(event => changes.push(event.lang));

      const seen = inPdfLanguage(translate, () => [translate.currentLang, translate.instant('PDF.TITLE')]);

      expect(seen).toEqual(['fr', 'Fiche']);
      expect(translate.currentLang).toBe('ar');
      expect(changes).toEqual(['fr', 'ar']);
    });

    it('français et anglais : aucun changement de langue', () => {
      for (const lang of ['fr', 'en']) {
        translate.use(lang);
        const changes: string[] = [];
        const subscription = translate.onLangChange.subscribe(event => changes.push(event.lang));
        expect(inPdfLanguage(translate, () => translate.currentLang)).toBe(lang);
        subscription.unsubscribe();
        expect(changes).withContext(lang).toEqual([]);
      }
    });

    it('rétablit la langue même si le rendu échoue', () => {
      translate.use('ar');
      expect(() => inPdfLanguage(translate, () => { throw new Error('capture'); })).toThrowError('capture');
      expect(translate.currentLang).toBe('ar');
    });

    it('traductions françaises absentes : rendu dans la langue courante, sans chargement', () => {
      translate.resetLang('fr');
      translate.use('ar');
      expect(inPdfLanguage(translate, () => translate.currentLang)).toBe('ar');
    });
  });

  /**
   * Langue choisie au moment d'imprimer : elle vaut pour le document en cours seulement, quelle
   * que soit la langue de l'écran, et n'est jamais celle de l'interface.
   */
  describe('langue choisie à l\'impression', () => {
    beforeEach(() => {
      translate.setTranslation('fr', { COMMON: { CURRENCY_SUFFIX: 'DA' } }, true);
      translate.setTranslation('en', { COMMON: { CURRENCY_SUFFIX: 'DA' } }, true);
      translate.setTranslation('ar', { COMMON: { CURRENCY_SUFFIX: 'دج' } }, true);
    });

    it('chaque langue depuis chaque interface : libellés, locale, suffixe', () => {
      const expected: Record<string, { title: string; locale: string; suffix: string }> = {
        fr: { title: 'Fiche', locale: 'fr', suffix: 'DA' },
        en: { title: 'Profile', locale: 'en', suffix: 'DA' },
        ar: { title: 'بطاقة', locale: 'ar-DZ', suffix: 'دج' }
      };
      for (const screen of ['fr', 'en', 'ar']) {
        translate.use(screen);
        for (const chosen of ['fr', 'en', 'ar'] as const) {
          const seen = withPrintLanguage(chosen, () => ({
            lang: pdfLanguage(translate),
            title: pdfInstant(translate, 'PDF.TITLE'),
            locale: pdfLocale(translate),
            amount: pdfWithCurrency('1 200', translate)
          }));
          expect(seen).withContext(`${screen} → ${chosen}`).toEqual({
            lang: chosen,
            title: expected[chosen].title,
            locale: expected[chosen].locale,
            amount: `1 200 ${expected[chosen].suffix}`
          });
          expect(translate.currentLang).toBe(screen);
        }
      }
    });

    it('hors du choix, la règle historique revient (arabe → français)', () => {
      translate.use('ar');
      withPrintLanguage('ar', () => undefined);
      expect(pdfLanguage(translate)).toBe('fr');
      expect(pdfWithCurrency('1 200', translate)).toBe('1 200 DA');
    });

    it('s\'imbrique et se rétablit, même si le rendu échoue', () => {
      translate.use('fr');
      withPrintLanguage('en', () => {
        withPrintLanguage('ar', () => expect(pdfLanguage(translate)).toBe('ar'));
        expect(pdfLanguage(translate)).toBe('en');
        expect(() => withPrintLanguage('ar', () => { throw new Error('rendu'); })).toThrowError('rendu');
        expect(pdfLanguage(translate)).toBe('en');
      });
      expect(pdfLanguage(translate)).toBe('fr');
    });

    it('clé absente de la langue choisie : traduction française, puis la clé', () => {
      translate.use('fr');
      translate.setTranslation('fr', { ONLY_FR: 'Seulement en français' }, true);
      expect(withPrintLanguage('ar', () => pdfInstant(translate, 'ONLY_FR'))).toBe('Seulement en français');
      expect(withPrintLanguage('ar', () => pdfInstant(translate, 'NOWHERE'))).toBe('NOWHERE');
    });

    it('printLanguageOf : la langue de l\'écran, ramenée à une langue proposée', () => {
      translate.use('ar');
      expect(printLanguageOf(translate)).toBe('ar');
      translate.use('de');
      expect(printLanguageOf(translate)).toBe('fr');
    });

    it('runInPrintLanguage : sans choix, exécution telle quelle et synchrone', () => {
      translate.use('ar');
      let seen = '';
      void runInPrintLanguage(translate, undefined, () => { seen = pdfLanguage(translate); });
      expect(seen).toBe('fr');
    });

    it('runInPrintLanguage : traductions chargées, exécution synchrone dans la langue choisie', () => {
      translate.use('fr');
      let seen = '';
      void runInPrintLanguage(translate, 'ar', () => { seen = pdfInstant(translate, 'PDF.TITLE'); });
      expect(seen).toBe('بطاقة');
      expect(pdfLanguage(translate)).toBe('fr');
    });

    it('runInPrintLanguage : traductions absentes, chargées d\'abord sans changer l\'écran', async () => {
      translate.use('fr');
      translate.resetLang('en');
      const getTranslation = spyOn(translate, 'getTranslation').and.callFake((lang: string) => {
        translate.translations[lang] = { PDF: { TITLE: 'Profile' } };
        return of(translate.translations[lang]);
      });
      const seen = await runInPrintLanguage(translate, 'en', () => pdfInstant(translate, 'PDF.TITLE'));
      expect(getTranslation).toHaveBeenCalledWith('en');
      expect(seen).toBe('Profile');
      expect(translate.currentLang).toBe('fr');
    });

    it('inPrintLanguage : l\'écran passe dans la langue choisie le temps du rendu', () => {
      translate.use('fr');
      const seen = inPrintLanguage(translate, 'ar', () => [translate.currentLang, pdfLanguage(translate)]);
      expect(seen).toEqual(['ar', 'ar']);
      expect(translate.currentLang).toBe('fr');
    });

    it('bindPrintLanguage : document inchangé dans la langue historique, pied de page lié sinon', () => {
      translate.use('fr');
      const doc = { content: [], footer: () => pdfInstant(translate, 'PDF.TITLE') };
      expect(bindPrintLanguage(translate, doc, 'fr')).toBe(doc);
      const bound = bindPrintLanguage(translate, doc, 'en');
      expect(bound).not.toBe(doc);
      expect(bound.content).toBe(doc.content);
      expect((bound.footer as () => string)()).toBe('Profile');
      expect(doc.footer()).toBe('Fiche');
    });
  });
});
