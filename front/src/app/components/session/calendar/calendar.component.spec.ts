import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialogConfig, MatDialogRef } from '@angular/material/dialog';
import { EventApi, EventClickArg } from '@fullcalendar/core';
import { of } from 'rxjs';

import { TranslateService } from '@ngx-translate/core';

import { CalendarComponent, calendarLanguageOptions } from './calendar.component';
import { setupComponentTestBed } from '../../../../testing/setup';
import { aSession } from '../../../../testing/fixtures';
import { SessionService } from '../../../services/SessionService';

describe('CalendarComponent', () => {
  let component: CalendarComponent;
  let fixture: ComponentFixture<CalendarComponent>;

  beforeEach(async () => {
    await setupComponentTestBed(CalendarComponent);

    fixture = TestBed.createComponent(CalendarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('configure FullCalendar sur la vue mois avec les quatre vues disponibles', () => {
    expect(component.calendarOptions).toBeDefined();
    expect(component.calendarOptions!.initialView).toBe('dayGridMonth');
    // Les quatre plugins conditionnent les vues mois / semaine / jour / liste.
    expect(component.calendarOptions!.plugins?.length).toBe(4);
    // Hauteur « auto » : la grille prend sa hauteur naturelle, la carte défile.
    expect(component.calendarOptions!.height).toBe('auto');
  });

  it('fournit les locales fr et ar-dz, et suit la langue de l\'interface (arabe en droite-à-gauche)', () => {
    const codes = (component.calendarOptions!.locales ?? []).map(l => l.code);
    expect(codes).toEqual(['fr', 'ar-dz']);

    const translate = TestBed.inject(TranslateService);
    translate.use('ar');
    expect(component.calendarOptions!.locale).toBe('ar-dz');
    expect(component.calendarOptions!.direction).toBe('rtl');

    translate.use('en');
    expect(component.calendarOptions!.locale).toBe('en');
    expect(component.calendarOptions!.direction).toBe('ltr');

    translate.use('fr');
    expect(component.calendarOptions!.locale).toBe('fr');
    expect(component.calendarOptions!.direction).toBe('ltr');
  });

  it('calendarLanguageOptions() ramène une langue inconnue au français', () => {
    expect(calendarLanguageOptions(undefined)).toEqual({ locale: 'fr', direction: 'ltr' });
    expect(calendarLanguageOptions('de')).toEqual({ locale: 'fr', direction: 'ltr' });
    expect(calendarLanguageOptions('ar')).toEqual({ locale: 'ar-dz', direction: 'rtl' });
  });

  it('démarre avec les filtres repliés et sur « tous les groupes »', () => {
    expect(component.filtersOpen).toBeFalse();
    expect(component.selectedGroup.value).toBe(0);
    expect(component.selectedLevel.value).toBe(0);
    expect(component.selectedSubject.value).toBe(0);
  });

  /**
   * Couleur de la séance à la fermeture de sa fiche (D.3). Une dévalidation laisse la fiche ouverte,
   * la feuille à refaire, et la fiche peut se fermer sans résultat : la séance restait affichée
   * validée jusqu'au rechargement de la page.
   */
  describe('état de la séance à la fermeture de sa fiche', () => {
    /** Ouvre la séance 100 ; la fiche fait `change` sur ses données puis se ferme sur `result`. */
    function clickSession(finished: boolean, change: (data: { isFinished?: boolean }) => void, result: unknown) {
      spyOn(TestBed.inject(SessionService), 'getSessionById').and.returnValue(of(aSession({ isFinished: finished })));
      spyOn(component.dialog, 'open').and.callFake(((_component: unknown, config?: MatDialogConfig) => {
        change(config!.data as { isFinished?: boolean });
        return { afterClosed: () => of(result) } as MatDialogRef<unknown>;
      }) as never);
      const event = jasmine.createSpyObj<EventApi>('EventApi', ['setProp', 'setExtendedProp', 'remove'],
        { extendedProps: { id: '100' } });
      component.handleEventClick({ event } as unknown as EventClickArg);
      return event;
    }

    it('dévalidée puis fermée sans résultat : plus affichée validée', () => {
      const event = clickSession(true, data => data.isFinished = false, undefined);

      expect(event.setProp).toHaveBeenCalledWith('classNames', []);
      expect(event.setExtendedProp).toHaveBeenCalledWith('isFinished', false);
    });

    it('validée : affichée validée', () => {
      const event = clickSession(false, data => data.isFinished = true, { isFinished: true });

      expect(event.setProp).toHaveBeenCalledWith('classNames', ['is-finished']);
      expect(event.setExtendedProp).toHaveBeenCalledWith('isFinished', true);
    });

    it('fermée sans changement : garde son état', () => {
      const event = clickSession(true, () => undefined, undefined);

      expect(event.setProp).toHaveBeenCalledWith('classNames', ['is-finished']);
    });

    it('supprimée : retirée du calendrier', () => {
      const event = clickSession(false, () => undefined, 'deleted');

      expect(event.remove).toHaveBeenCalled();
      expect(event.setProp).not.toHaveBeenCalled();
    });
  });
});
