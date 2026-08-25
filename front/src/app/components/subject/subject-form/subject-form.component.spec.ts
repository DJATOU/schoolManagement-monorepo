import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SubjectFormComponent } from './subject-form.component';
import { setupComponentTestBed } from '../../../../testing/setup';

describe('SubjectFormComponent', () => {
  let component: SubjectFormComponent;
  let fixture: ComponentFixture<SubjectFormComponent>;

  beforeEach(async () => {
    await setupComponentTestBed(SubjectFormComponent);

    fixture = TestBed.createComponent(SubjectFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('démarre en mode création (aucun identifiant dans l\'URL)', () => {
    expect(component.editingId).toBeNull();
  });

  it('exige le nom de la matière, la description restant optionnelle', () => {
    expect(component.subjectForm.valid).toBeFalse();
    expect(component.subjectForm.get('name')?.hasError('required')).toBeTrue();
    expect(component.subjectForm.get('description')?.valid).toBeTrue();

    component.subjectForm.get('name')?.setValue('Mathématiques');
    expect(component.subjectForm.valid).toBeTrue();
  });
});
