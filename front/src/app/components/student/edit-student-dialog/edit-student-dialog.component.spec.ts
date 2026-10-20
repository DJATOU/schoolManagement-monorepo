import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EditStudentDialogComponent } from './edit-student-dialog.component';
import { createDialogRefSpy, DialogRefSpy, matDialogProviders, setupComponentTestBed } from '../../../../testing/setup';
import { aStudent } from '../../../../testing/fixtures';
import { Student } from '../domain/student';

/**
 * Édition d'un étudiant existant.
 *
 * <p>`data.student` préremplit le formulaire. Le préremplissage est le point sous test : les
 * champs saisis à l'inscription doivent rester modifiables ensuite, sans quoi une erreur de
 * saisie serait définitive.</p>
 */
describe('EditStudentDialogComponent', () => {
  let component: EditStudentDialogComponent;
  let fixture: ComponentFixture<EditStudentDialogComponent>;
  let dialogRef: DialogRefSpy;

  const student = aStudent();

  beforeEach(async () => {
    dialogRef = createDialogRefSpy();
    await setupComponentTestBed(EditStudentDialogComponent, {
      providers: matDialogProviders({ student }, dialogRef)
    });
    fixture = TestBed.createComponent(EditStudentDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('porte l\'étudiant à éditer', () => {
    expect(component.data.student).toEqual(student);
  });

  it('préremplit le formulaire avec les valeurs actuelles', () => {
    const serialized = JSON.stringify(component.editStudentForm.value);

    expect(serialized).toContain('Amina');
    expect(serialized).toContain('Belkacem');
  });
});

/**
 * Date de naissance : un jour du calendrier. L'ancien chemin envoyait `toISOString()` (instant
 * UTC) : enregistré par le serveur dans son propre fuseau, le jour reculait d'un cran.
 */
describe('EditStudentDialogComponent — date de naissance', () => {
  let fixture: ComponentFixture<EditStudentDialogComponent>;
  let dialogRef: DialogRefSpy;

  async function open(student: Student): Promise<EditStudentDialogComponent> {
    dialogRef = createDialogRefSpy();
    await setupComponentTestBed(EditStudentDialogComponent, {
      providers: matDialogProviders({ student }, dialogRef)
    });
    fixture = TestBed.createComponent(EditStudentDialogComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  function savedStudent(): { dateOfBirth?: unknown } {
    const payload = dialogRef.close.calls.mostRecent().args[0] as { student: { dateOfBirth?: unknown } };
    return payload.student;
  }

  it('2010-05-15 reçu : le champ affiche 15/05/2010 (jour local)', async () => {
    const component = await open(aStudent({ dateOfBirth: '2010-05-15' }));
    const value: Date = component.editStudentForm.get('dateOfBirth')?.value;
    expect([value.getFullYear(), value.getMonth(), value.getDate()]).toEqual([2010, 4, 15]);
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input[formcontrolname="dateOfBirth"]');
    expect(input.value).toBe('15/05/2010');
  });

  it('enregistré sans y toucher : renvoyé tel quel, 2010-05-15', async () => {
    const component = await open(aStudent({ dateOfBirth: '2010-05-15' }));
    component.onSave();
    expect(savedStudent().dateOfBirth).toBe('2010-05-15');
  });

  it('élève sans date de naissance (import CSV) : aucune date envoyée', async () => {
    const component = await open(aStudent({ dateOfBirth: null }));
    component.onSave();
    expect(dialogRef.close).toHaveBeenCalled();
    expect(savedStudent().dateOfBirth).toBeUndefined();
  });
});
