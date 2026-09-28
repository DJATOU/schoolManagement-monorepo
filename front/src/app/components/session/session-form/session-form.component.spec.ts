import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SessionFormComponent } from './session-form.component';
import { setupComponentTestBed } from '../../../../testing/setup';

describe('SessionFormComponent', () => {
  let component: SessionFormComponent;
  let fixture: ComponentFixture<SessionFormComponent>;

  beforeEach(async () => {
    await setupComponentTestBed(SessionFormComponent);

    fixture = TestBed.createComponent(SessionFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('refuse un formulaire vide : titre, type, horaires et rattachements sont requis', () => {
    expect(component.sessionForm.valid).toBeFalse();
    expect(component.sessionForm.get('sessionDetails.title')?.hasError('required')).toBeTrue();
    expect(component.sessionForm.get('sessionDetails.sessionType')?.hasError('required')).toBeTrue();
    expect(component.sessionForm.get('sessionTiming.sessionDateStart')?.hasError('required')).toBeTrue();
    expect(component.sessionForm.get('identifiers.groupId')?.hasError('required')).toBeTrue();
    expect(component.sessionForm.get('identifiers.roomId')?.hasError('required')).toBeTrue();
    expect(component.sessionForm.get('identifiers.teacherId')?.hasError('required')).toBeTrue();
  });

  it('propose les sept jours de la semaine et les cinq types de séance', () => {
    expect(component.weekDays.map(d => d.value))
      .toEqual(['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']);
    expect(component.sessionTypes.map(t => t.value))
      .toEqual(['COURS', 'EXERCICES', 'EXAMEN', 'REVISION', 'AUTRE']);
  });

  it('la répétition est désactivée par défaut, conflits ignorés', () => {
    expect(component.sessionForm.get('recurrence.enabled')?.value).toBeFalse();
    expect(component.sessionForm.get('recurrence.skipConflicts')?.value).toBeTrue();
    expect(component.selectedDays).toEqual([]);
    // Français par défaut : pas de notion AM/PM.
    expect(component.timeFormat).toBe(24);
  });
});
