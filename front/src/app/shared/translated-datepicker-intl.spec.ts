import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerIntl, MatDatepickerModule } from '@angular/material/datepicker';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import frTranslations from '../../assets/i18n/fr.json';
import enTranslations from '../../assets/i18n/en.json';
import { TranslatedDatepickerIntl, provideTranslatedDatepickerIntl } from './translated-datepicker-intl';

/** Composant autonome qui importe MatDatepickerModule, comme les formulaires de l'application. */
@Component({
  standalone: true,
  selector: 'app-datepicker-host',
  imports: [MatDatepickerModule, MatNativeDateModule],
  template: '<mat-calendar></mat-calendar>'
})
class PlainHostComponent {}

@Component({
  standalone: true,
  selector: 'app-translated-datepicker-host',
  imports: [MatDatepickerModule, MatNativeDateModule],
  providers: [provideTranslatedDatepickerIntl()],
  template: '<mat-calendar></mat-calendar>'
})
class TranslatedHostComponent {}

describe('TranslatedDatepickerIntl', () => {
  let translate: TranslateService;
  let intl: TranslatedDatepickerIntl;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [TranslatedDatepickerIntl]
    });
    translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', frTranslations);
    translate.setTranslation('en', enTranslations);
    translate.use('fr');
    intl = TestBed.inject(TranslatedDatepickerIntl);
  });

  it('nomme les commandes du calendrier en français', () => {
    expect(intl.openCalendarLabel).toBe('Ouvrir le calendrier');
    expect(intl.prevMonthLabel).toBe('Mois précédent');
    expect(intl.switchToMultiYearViewLabel).toBe('Choisir le mois et l\'année');
    expect(intl.startDateLabel).toBe('Date de début');
  });

  it('suit le changement de langue et prévient les calendriers affichés', () => {
    const changes = jasmine.createSpy('changes');
    intl.changes.subscribe(changes);

    translate.use('en');

    expect(intl.openCalendarLabel).toBe('Open calendar');
    expect(intl.nextYearLabel).toBe('Next year');
    expect(intl.endDateLabel).toBe('End date');
    expect(changes).toHaveBeenCalled();
  });

  it('prévaut sur la déclaration de MatDatepickerModule dans un composant autonome', () => {
    // MatDatepickerModule déclare son propre MatDatepickerIntl : un composant autonome qui
    // l'importe ne voit pas celui de app.config.ts. Le fournisseur du composant, lui, prévaut.
    const plain = TestBed.createComponent(PlainHostComponent);
    expect(plain.debugElement.injector.get(MatDatepickerIntl) instanceof TranslatedDatepickerIntl).toBeFalse();

    const translated = TestBed.createComponent(TranslatedHostComponent);
    const intl = translated.debugElement.injector.get(MatDatepickerIntl);
    expect(intl instanceof TranslatedDatepickerIntl).toBeTrue();
    expect(intl.openCalendarLabel).toBe('Ouvrir le calendrier');
  });
});
