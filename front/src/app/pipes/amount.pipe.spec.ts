import { TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { AmountPipe, formatAmount } from './amount.pipe';

/** Espaces de groupement (U+202F en français, U+00A0 ailleurs) ramenées à une espace ordinaire. */
const plain = (text: string): string => text.replace(/\s/g, ' ');

describe('formatAmount', () => {
  it('formate en français : espace de milliers, virgule, deux décimales', () => {
    expect(plain(formatAmount(2400, 'fr'))).toBe('2 400,00');
    expect(plain(formatAmount(1234567.5, 'fr'))).toBe('1 234 567,50');
  });

  it('formate en anglais : virgule de milliers, point décimal', () => {
    expect(formatAmount(2400, 'en')).toBe('2,400.00');
  });

  it('arrondit à deux décimales', () => {
    expect(formatAmount(10.006, 'en')).toBe('10.01');
    expect(formatAmount(0.1, 'fr')).toBe('0,10');
  });

  it('traite un montant absent comme zéro, plutôt que d\'afficher « NaN »', () => {
    expect(formatAmount(null, 'fr')).toBe('0,00');
    expect(formatAmount(undefined, 'fr')).toBe('0,00');
  });

  it('ramène une langue inconnue ou absente au français, langue par défaut', () => {
    expect(plain(formatAmount(2400, 'de'))).toBe('2 400,00');
    expect(plain(formatAmount(2400, undefined))).toBe('2 400,00');
    expect(plain(formatAmount(2400, 'fr-FR'))).toBe('2 400,00');
  });
});

describe('AmountPipe', () => {
  let translate: TranslateService;
  let pipe: AmountPipe;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [TranslateModule.forRoot()] });
    translate = TestBed.inject(TranslateService);
    pipe = new AmountPipe(translate);
  });

  it('suit la langue active, et change avec elle', () => {
    translate.use('fr');
    expect(plain(pipe.transform(2400))).toBe('2 400,00');

    translate.use('en');
    expect(pipe.transform(2400)).toBe('2,400.00');
  });

  it('retombe sur la langue par défaut tant qu\'aucune langue n\'est active', () => {
    translate.setDefaultLang('en');
    expect(pipe.transform(2400)).toBe('2,400.00');
  });
});
