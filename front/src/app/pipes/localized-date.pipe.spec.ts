import { DatePipe, registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import localeEn from '@angular/common/locales/en';
import { TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { LocalizedDatePipe } from './localized-date.pipe';

/** Espaces insécables (U+202F, U+00A0) ramenées à une espace ordinaire. */
const plain = (text: string | null): string | null => text === null ? null : text.replace(/\s/g, ' ');

describe('LocalizedDatePipe', () => {
  let translate: TranslateService;
  let pipe: LocalizedDatePipe;

  // Données de locale enregistrées au démarrage par src/main.ts.
  beforeAll(() => {
    registerLocaleData(localeFr);
    registerLocaleData(localeEn);
  });

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [TranslateModule.forRoot()] });
    translate = TestBed.inject(TranslateService);
    pipe = new LocalizedDatePipe(translate);
  });

  it('écrit le mois en français sous fr, en anglais sous en, et suit le changement de langue', () => {
    const date = new Date(2026, 4, 15, 14, 30);

    translate.use('fr');
    expect(pipe.transform(date, 'mediumDate')).toBe('15 mai 2026');
    expect(plain(pipe.transform(date, 'EEE d MMM y'))).toBe('ven. 15 mai 2026');

    translate.use('en');
    expect(pipe.transform(date, 'mediumDate')).toBe('May 15, 2026');
    expect(pipe.transform(date, 'EEE d MMM y')).toBe('Fri 15 May 2026');
  });

  it('formate par défaut en mediumDate, comme le pipe date', () => {
    translate.use('fr');
    expect(pipe.transform(new Date(2026, 0, 3))).toBe('3 janv. 2026');
  });

  it('lit une chaîne « yyyy-MM-dd » comme le pipe date : un jour local, sans décalage', () => {
    translate.use('en');
    expect(pipe.transform('2026-05-15', 'mediumDate'))
      .toBe(new DatePipe('en').transform('2026-05-15', 'mediumDate'));
    expect(pipe.transform('2026-05-15', 'mediumDate')).toBe('May 15, 2026');
  });

  it('accepte le tableau Jackson d\'un LocalDateTime', () => {
    translate.use('fr');
    expect(pipe.transform([2026, 5, 15, 9, 5], 'dd MMMM y HH:mm')).toBe('15 mai 2026 09:05');
  });

  it('n\'affiche rien pour une valeur absente', () => {
    translate.use('fr');
    expect(pipe.transform(null)).toBeNull();
    expect(pipe.transform(undefined)).toBeNull();
    expect(pipe.transform('')).toBeNull();
    expect(pipe.transform([])).toBeNull();
  });

  it('ramène une langue sans données de locale au français', () => {
    translate.use('de');
    expect(pipe.transform(new Date(2026, 4, 15), 'mediumDate')).toBe('15 mai 2026');
  });

  it('retombe sur la langue par défaut tant qu\'aucune langue n\'est active', () => {
    translate.setDefaultLang('en');
    expect(pipe.transform(new Date(2026, 4, 15), 'mediumDate')).toBe('May 15, 2026');
  });
});
