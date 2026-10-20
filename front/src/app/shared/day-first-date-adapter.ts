import { Injectable, OnDestroy, Provider, inject } from '@angular/core';
import { DateAdapter, MAT_DATE_FORMATS, MatDateFormats, NativeDateAdapter } from '@angular/material/core';
import { TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs';
import { resolveLocale } from './locale';
import { localDay } from './iso-day';

/** Jour en tête : 15/05/2010, 15-05-2010, 15.05.2010, 5/6/2010. */
const DAY_FIRST = /^(\d{1,2})[/.-](\d{1,2})[/.-](\d{4})$/;
/** Format de l'API, accepté aussi à la saisie (copier-coller). */
const ISO_DAY = /^(\d{4})-(\d{1,2})-(\d{1,2})$/;
/** Format d'affichage du champ de saisie, reconnu par {@link DayFirstDateAdapter.format}. */
const INPUT_FORMAT = 'input';

/**
 * Adaptateur de datepicker pour les dates de naissance : saisie et affichage `JJ/MM/AAAA`.
 *
 * <p>`NativeDateAdapter` analyse la saisie avec `Date.parse`, c'est-à-dire au format américain
 * mois en tête : « 15/05/2010 » y est invalide (le formulaire reste bloqué) et « 05/06/2010 »
 * devient le 6 mai. Il désérialise aussi `yyyy-MM-dd` en minuit UTC, soit la veille dans un
 * fuseau à l'ouest de Greenwich. Celui-ci lit le jour en tête, refuse un jour inexistant au lieu
 * de le reporter, et désérialise au jour local. Les noms de mois du calendrier suivent la langue
 * de l'interface.</p>
 */
@Injectable()
export class DayFirstDateAdapter extends NativeDateAdapter implements OnDestroy {
  private readonly langSub?: Subscription;

  constructor() {
    super();
    const translate = inject(TranslateService, { optional: true });
    if (translate) {
      this.setLocale(resolveLocale(translate.currentLang || translate.defaultLang));
      this.langSub = translate.onLangChange.subscribe(event => this.setLocale(resolveLocale(event.lang)));
    }
  }

  ngOnDestroy(): void {
    this.langSub?.unsubscribe();
  }

  override parse(value: unknown, parseFormat?: unknown): Date | null {
    if (typeof value === 'number') {
      return super.parse(value, parseFormat);
    }
    if (value === null || value === undefined) {
      return null;
    }
    const text = String(value).trim();
    if (!text) {
      return null;
    }
    const dayFirst = DAY_FIRST.exec(text);
    if (dayFirst) {
      return localDay(+dayFirst[3], +dayFirst[2], +dayFirst[1]) ?? this.invalid();
    }
    const iso = ISO_DAY.exec(text);
    if (iso) {
      return localDay(+iso[1], +iso[2], +iso[3]) ?? this.invalid();
    }
    return this.invalid();
  }

  override format(date: Date, displayFormat: object | string): string {
    if (displayFormat === INPUT_FORMAT) {
      if (!this.isValid(date)) {
        throw Error('DayFirstDateAdapter: Cannot format invalid date.');
      }
      const day = String(date.getDate()).padStart(2, '0');
      const month = String(date.getMonth() + 1).padStart(2, '0');
      return `${day}/${month}/${String(date.getFullYear()).padStart(4, '0')}`;
    }
    return super.format(date, displayFormat as object);
  }

  override deserialize(value: unknown): Date | null {
    const iso = typeof value === 'string' ? ISO_DAY.exec(value.trim()) : null;
    if (iso) {
      // Jour local, là où NativeDateAdapter lirait minuit UTC (la veille à l'ouest de Greenwich).
      return localDay(+iso[1], +iso[2], +iso[3]) ?? this.invalid();
    }
    return super.deserialize(value);
  }
}

/** Formats associés à {@link DayFirstDateAdapter}. */
export const DAY_FIRST_DATE_FORMATS: MatDateFormats = {
  parse: {
    dateInput: INPUT_FORMAT
  },
  display: {
    dateInput: INPUT_FORMAT,
    monthYearLabel: { year: 'numeric', month: 'short' },
    dateA11yLabel: { year: 'numeric', month: 'long', day: 'numeric' },
    monthYearA11yLabel: { year: 'numeric', month: 'long' }
  }
};

/** Providers d'un composant dont le datepicker saisit une date de naissance. */
export function provideDayFirstDateAdapter(): Provider[] {
  return [
    { provide: DateAdapter, useClass: DayFirstDateAdapter },
    { provide: MAT_DATE_FORMATS, useValue: DAY_FIRST_DATE_FORMATS }
  ];
}
