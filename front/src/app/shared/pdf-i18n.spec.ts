import { TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { inPdfLanguage, pdfInstant, pdfLanguage, pdfLocale } from './pdf-i18n';

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
});
