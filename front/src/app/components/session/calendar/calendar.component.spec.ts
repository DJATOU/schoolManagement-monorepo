import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CalendarComponent } from './calendar.component';
import { setupComponentTestBed } from '../../../../testing/setup';

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

  it('démarre avec les filtres repliés et sur « tous les groupes »', () => {
    expect(component.filtersOpen).toBeFalse();
    expect(component.selectedGroup.value).toBe(0);
    expect(component.selectedLevel.value).toBe(0);
    expect(component.selectedSubject.value).toBe(0);
  });
});
