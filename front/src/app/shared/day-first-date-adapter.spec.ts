import { TestBed } from '@angular/core/testing';
import { DateAdapter, MAT_DATE_FORMATS, MatDateFormats } from '@angular/material/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { DAY_FIRST_DATE_FORMATS, DayFirstDateAdapter, provideDayFirstDateAdapter } from './day-first-date-adapter';

describe('DayFirstDateAdapter', () => {
  let adapter: DateAdapter<Date>;
  let translate: TranslateService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: provideDayFirstDateAdapter()
    });
    translate = TestBed.inject(TranslateService);
    translate.use('fr');
    adapter = TestBed.inject(DateAdapter);
  });

  function expectLocalDay(date: Date | null, year: number, month: number, day: number): void {
    expect(date).not.toBeNull();
    expect(adapter.isValid(date!)).toBeTrue();
    expect(date!.getFullYear()).toBe(year);
    expect(date!.getMonth()).toBe(month - 1);
    expect(date!.getDate()).toBe(day);
  }

  it('est fourni avec ses formats', () => {
    expect(adapter).toEqual(jasmine.any(DayFirstDateAdapter));
    expect(TestBed.inject<MatDateFormats>(MAT_DATE_FORMATS)).toBe(DAY_FIRST_DATE_FORMATS);
  });

  describe('parse', () => {
    it('15/05/2010 : le 15 mai 2010 (jour en tête)', () => {
      expectLocalDay(adapter.parse('15/05/2010', 'input'), 2010, 5, 15);
    });

    it('05/06/2010 : le 5 juin, pas le 6 mai', () => {
      expectLocalDay(adapter.parse('05/06/2010', 'input'), 2010, 6, 5);
    });

    it('séparateurs et zéros facultatifs : 5/6/2010, 15-05-2010, 15.05.2010', () => {
      expectLocalDay(adapter.parse('5/6/2010', 'input'), 2010, 6, 5);
      expectLocalDay(adapter.parse('15-05-2010', 'input'), 2010, 5, 15);
      expectLocalDay(adapter.parse(' 15.05.2010 ', 'input'), 2010, 5, 15);
    });

    it('2010-05-15 (format de l\'API) accepté', () => {
      expectLocalDay(adapter.parse('2010-05-15', 'input'), 2010, 5, 15);
    });

    it('jour inexistant ou texte libre : date invalide, pas reportée', () => {
      const feb31 = adapter.parse('31/02/2010', 'input');
      expect(feb31).not.toBeNull();
      expect(adapter.isValid(feb31!)).toBeFalse();
      expect(adapter.isValid(adapter.parse('15/13/2010', 'input')!)).toBeFalse();
      expect(adapter.isValid(adapter.parse('mai 2010', 'input')!)).toBeFalse();
    });

    it('champ vide : null (pas d\'erreur de saisie)', () => {
      expect(adapter.parse('', 'input')).toBeNull();
      expect(adapter.parse('   ', 'input')).toBeNull();
      expect(adapter.parse(null, 'input')).toBeNull();
    });
  });

  it('format(…, input) : JJ/MM/AAAA', () => {
    expect(adapter.format(new Date(2010, 4, 15), 'input')).toBe('15/05/2010');
    expect(adapter.format(new Date(2010, 5, 5), DAY_FIRST_DATE_FORMATS.display.dateInput)).toBe('05/06/2010');
  });

  it('deserialize(2010-05-15) : jour local (NativeDateAdapter lirait minuit UTC)', () => {
    expectLocalDay(adapter.deserialize('2010-05-15'), 2010, 5, 15);
    expect(adapter.deserialize('2010-05-15')!.getDate()).toBe(15);
    expect(adapter.deserialize('')).toBeNull();
    expect(adapter.isValid(adapter.deserialize('2010-02-30')!)).toBeFalse();
  });

  it('les noms de mois suivent la langue de l\'interface', () => {
    expect(adapter.getMonthNames('long')[4].toLowerCase()).toBe('mai');
    translate.use('en');
    expect(adapter.getMonthNames('long')[4]).toBe('May');
  });
});
