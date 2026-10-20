import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EditTeacherDialogComponent } from './edit-teacher-dialog.component';
import { createDialogRefSpy, DialogRefSpy, matDialogProviders, withCommonTesting } from '../../../../testing/setup';
import { aTeacher } from '../../../../testing/fixtures';

/**
 * Modification d'un enseignant : la date de naissance est un jour du calendrier. L'ancien chemin
 * envoyait `toISOString()` (instant UTC), que le serveur enregistrait dans son propre fuseau : le
 * jour reculait d'un cran à chaque enregistrement.
 */
describe('EditTeacherDialogComponent', () => {
  let fixture: ComponentFixture<EditTeacherDialogComponent>;
  let component: EditTeacherDialogComponent;
  let dialogRef: DialogRefSpy;

  beforeEach(async () => {
    dialogRef = createDialogRefSpy();
    await TestBed.configureTestingModule(withCommonTesting({
      imports: [EditTeacherDialogComponent],
      providers: matDialogProviders({ teacher: { ...aTeacher(), dateOfBirth: '1985-02-20' } }, dialogRef)
    })).compileComponents();
    fixture = TestBed.createComponent(EditTeacherDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  it('1985-02-20 reçu : le champ affiche 20/02/1985', () => {
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input[formcontrolname="dateOfBirth"]');
    expect(input.value).toBe('20/02/1985');
  });

  it('enregistré sans y toucher : renvoyé tel quel, 1985-02-20', () => {
    component.onSave();
    const payload = dialogRef.close.calls.mostRecent().args[0] as { teacher: { dateOfBirth?: unknown } };
    expect(payload.teacher.dateOfBirth).toBe('1985-02-20');
  });
});
