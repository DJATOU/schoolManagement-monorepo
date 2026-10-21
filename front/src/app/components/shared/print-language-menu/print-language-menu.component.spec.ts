import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';

import { PrintLanguageMenuComponent } from './print-language-menu.component';
import { setupComponentTestBed } from '../../../../testing/setup';
import frTranslations from '../../../../assets/i18n/fr.json';
import enTranslations from '../../../../assets/i18n/en.json';
import arTranslations from '../../../../assets/i18n/ar.json';
import { PrintLanguage } from '../../../shared/pdf-i18n';

/**
 * Choix de la langue d'un document à l'impression : Français, English, العربية, la langue de l'écran
 * en premier ; le choix ne change jamais la langue de l'interface.
 */
describe('PrintLanguageMenuComponent', () => {
  let fixture: ComponentFixture<PrintLanguageMenuComponent>;
  let component: PrintLanguageMenuComponent;
  let translate: TranslateService;

  beforeEach(async () => {
    await setupComponentTestBed(PrintLanguageMenuComponent);
    translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', frTranslations);
    translate.setTranslation('en', enTranslations);
    translate.setTranslation('ar', arTranslations);
    fixture = TestBed.createComponent(PrintLanguageMenuComponent);
    component = fixture.componentInstance;
  });

  function open(): HTMLButtonElement[] {
    fixture.detectChanges();
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.plm-trigger')!.click();
    fixture.detectChanges();
    return Array.from(document.querySelectorAll<HTMLButtonElement>('.plm-menu .plm-option'));
  }

  const codes = (options: HTMLButtonElement[]): (string | null)[] => options.map(o => o.getAttribute('data-lang'));

  it('la langue de l\'écran en premier, signalée, puis les autres dans l\'ordre du sélecteur', () => {
    const expected: Record<string, PrintLanguage[]> = { fr: ['fr', 'en', 'ar'], en: ['en', 'fr', 'ar'], ar: ['ar', 'fr', 'en'] };
    for (const lang of ['fr', 'en', 'ar']) {
      translate.use(lang);
      expect(component.screenLanguage).toBe(lang as PrintLanguage);
      expect(component.options.map(o => o.code)).withContext(lang).toEqual(expected[lang]);
      expect(component.options.map(o => o.screen)).toEqual([true, false, false]);
    }
  });

  it('les trois langues, nommées dans leur propre langue, l\'écran marqué', () => {
    translate.use('fr');
    const options = open();
    expect(codes(options)).toEqual(['fr', 'en', 'ar']);
    expect(options.map(o => o.querySelector('.plm-name')?.textContent?.trim())).toEqual(['Français', 'English', 'العربية']);
    expect(options[0].querySelector('.plm-marker')?.textContent).toContain(frTranslations.PRINT_LANGUAGE.SCREEN);
    expect(options[1].querySelector('.plm-marker')).toBeNull();
    expect(options[2].querySelector('.plm-name')?.getAttribute('dir')).toBe('rtl');
    expect(options[2].querySelector('.plm-name')?.getAttribute('lang')).toBe('ar');
    expect(options[1].getAttribute('aria-label')).toBe('Imprimer en English');
  });

  it('sous « العربية », la boîte d\'impression est annoncée, dans la langue de l\'écran', () => {
    translate.use('en');
    const arabic = open().find(o => o.getAttribute('data-lang') === 'ar')!;
    const hint = arabic.querySelector('.plm-hint');
    expect(hint?.textContent).toBe(enTranslations.PRINT_LANGUAGE.ARABIC_HINT);
    expect(arabic.getAttribute('aria-describedby')).toBe(hint?.id ?? 'absent');
  });

  it('mention absente pour un document qui reste un téléchargement', () => {
    component.arabicOpensPrintDialog = false;
    translate.use('fr');
    expect(document.querySelector('.plm-hint')).toBeNull();
    open();
    expect(document.querySelector('.plm-menu .plm-hint')).toBeNull();
  });

  it('émet la langue choisie, sans changer la langue de l\'écran', () => {
    translate.use('ar');
    const chosen: PrintLanguage[] = [];
    component.selected.subscribe(lang => chosen.push(lang));
    open().find(o => o.getAttribute('data-lang') === 'en')!.click();
    expect(chosen).toEqual(['en']);
    expect(translate.currentLang).toBe('ar');
  });

  it('déclencheur nommé et désactivable', () => {
    translate.use('fr');
    component.disabled = true;
    component.triggerClass = 'pf-hb pf-hb-print';
    fixture.detectChanges();
    const trigger = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.plm-trigger')!;
    expect(trigger.disabled).toBeTrue();
    expect(trigger.getAttribute('aria-label')).toBe(frTranslations.PRINT_LANGUAGE.CHOOSE);
    expect(trigger.classList).toContain('pf-hb-print');
  });
});
