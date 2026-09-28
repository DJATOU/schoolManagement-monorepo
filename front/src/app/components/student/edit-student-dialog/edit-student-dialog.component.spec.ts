import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EditStudentDialogComponent } from './edit-student-dialog.component';
import { createDialogRefSpy, DialogRefSpy, matDialogProviders, setupComponentTestBed } from '../../../../testing/setup';
import { aStudent } from '../../../../testing/fixtures';

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
