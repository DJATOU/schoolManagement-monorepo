import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ATTENDANCE_STATES, AttendanceStateDialogComponent } from './attendance-state-dialog.component';
import { createDialogRefSpy, DialogRefSpy, matDialogProviders, setupComponentTestBed } from '../../../../testing/setup';

/** État de la ligne à ajouter sur une séance validée (D.7). */
describe('AttendanceStateDialogComponent', () => {
  let fixture: ComponentFixture<AttendanceStateDialogComponent>;
  let dialogRef: DialogRefSpy;

  beforeEach(async () => {
    dialogRef = createDialogRefSpy();
    await setupComponentTestBed(AttendanceStateDialogComponent, {
      providers: matDialogProviders({ studentName: 'Amine Belkacem', session: 'Séance du 07/01/2030 (Math 1ère A)' },
        dialogRef)
    });
    fixture = TestBed.createComponent(AttendanceStateDialogComponent);
    fixture.detectChanges();
  });

  const element = (): HTMLElement => fixture.nativeElement as HTMLElement;

  it('propose présent, absent, absent justifié ; présent d\'abord, choisi par défaut', () => {
    expect(ATTENDANCE_STATES.map(option => option.state)).toEqual([
      { present: true, justified: false },
      { present: false, justified: false },
      { present: false, justified: true }
    ]);
    expect(element().querySelectorAll('mat-radio-button').length).toBe(3);
    expect(fixture.componentInstance.choice).toBe(ATTENDANCE_STATES[0]);
    expect(element().querySelector('.as-session')?.textContent).toContain('Séance du 07/01/2030 (Math 1ère A)');
  });

  it('« Continuer » rend l\'état choisi ; « Annuler » ne rend rien', () => {
    fixture.componentInstance.choice = ATTENDANCE_STATES[2];
    (element().querySelector('.as-confirm') as HTMLButtonElement).click();
    expect(dialogRef.close).toHaveBeenCalledWith({ present: false, justified: true });

    (element().querySelector('.as-cancel') as HTMLButtonElement).click();
    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
