import { TestBed } from '@angular/core/testing';
import { Direction, Directionality } from '@angular/cdk/bidi';
import { TranslateService } from '@ngx-translate/core';
import { setupServiceTestBed } from '../../testing/setup';
import { AppDirectionality } from '../shared/app-directionality';
import { LANGUAGE_STORAGE_KEY } from '../shared/app-languages';
import { TranslationService } from './translation.service';

/**
 * Choix de la langue : textes, `lang` / `dir` du document et sens d'écriture des composants
 * Material, à chaud et sans rechargement.
 */
describe('TranslationService', () => {
  const html = document.documentElement;
  let savedLang: string;
  let savedDir: string;
  let savedChoice: string | null;

  beforeEach(() => {
    savedLang = html.lang;
    savedDir = html.dir;
    savedChoice = localStorage.getItem(LANGUAGE_STORAGE_KEY);
    localStorage.removeItem(LANGUAGE_STORAGE_KEY);
    html.lang = 'fr';
    html.dir = 'ltr';
    setupServiceTestBed({ providers: [{ provide: Directionality, useExisting: AppDirectionality }] });
  });

  afterEach(() => {
    html.lang = savedLang;
    html.dir = savedDir;
    if (savedChoice === null) {
      localStorage.removeItem(LANGUAGE_STORAGE_KEY);
    } else {
      localStorage.setItem(LANGUAGE_STORAGE_KEY, savedChoice);
    }
  });

  it('passe en arabe : html lang="ar" dir="rtl", Directionality "rtl" émis une seule fois', () => {
    const service = TestBed.inject(TranslationService);
    const directionality = TestBed.inject(AppDirectionality);
    const emitted: Direction[] = [];
    directionality.change.subscribe(dir => emitted.push(dir));

    service.useLanguage('ar');
    service.useLanguage('ar');

    expect(html.lang).toBe('ar');
    expect(html.dir).toBe('rtl');
    expect(directionality.value).toBe('rtl');
    // Le Directionality racine des composants Material est le même objet.
    expect(TestBed.inject(Directionality).value).toBe('rtl');
    expect(emitted).toEqual(['rtl']);
    expect(TestBed.inject(TranslateService).currentLang).toBe('ar');
    expect(localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe('ar');
  });

  it('revient en français : html lang="fr" dir="ltr"', () => {
    const service = TestBed.inject(TranslationService);
    const directionality = TestBed.inject(AppDirectionality);
    service.useLanguage('ar');
    const emitted: Direction[] = [];
    directionality.change.subscribe(dir => emitted.push(dir));

    service.useLanguage('fr');

    expect(html.lang).toBe('fr');
    expect(html.dir).toBe('ltr');
    expect(directionality.value).toBe('ltr');
    expect(emitted).toEqual(['ltr']);
  });

  it('l\'anglais reste de gauche à droite', () => {
    const service = TestBed.inject(TranslationService);
    service.useLanguage('en');
    expect(html.lang).toBe('en');
    expect(html.dir).toBe('ltr');
  });

  it('applique au démarrage la langue enregistrée', () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'ar');
    const service = TestBed.inject(TranslationService);
    expect(service.currentLanguage).toBe('ar');
    expect(html.dir).toBe('rtl');
    expect(TestBed.inject(AppDirectionality).value).toBe('rtl');
  });

  it('ramène une langue enregistrée inconnue au français', () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'de');
    const service = TestBed.inject(TranslationService);
    expect(service.currentLanguage).toBe('fr');
    expect(html.lang).toBe('fr');
    expect(html.dir).toBe('ltr');
    expect(localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe('fr');
  });

  it('ramène une langue demandée inconnue au français', () => {
    const service = TestBed.inject(TranslationService);
    service.useLanguage('xx');
    expect(service.currentLanguage).toBe('fr');
    expect(html.dir).toBe('ltr');
  });
});
