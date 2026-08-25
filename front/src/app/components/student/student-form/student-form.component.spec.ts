import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { StudentFormComponent } from './student-form.component';
import { API_BASE_URL } from '../../../api-base-url';
import { DEFAULT_NATIONALITY } from '../../../utils/form-options';
import { setupComponentTestBed } from '../../../../testing/setup';

describe('StudentComponent', () => {
  let component: StudentFormComponent;
  let fixture: ComponentFixture<StudentFormComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await setupComponentTestBed(StudentFormComponent);

    fixture = TestBed.createComponent(StudentFormComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('charge les niveaux proposés au formulaire', () => {
    const req = httpMock.expectOne(`${API_BASE_URL}/api/levels`);
    expect(req.request.method).toBe('GET');
    req.flush([{ id: 1, name: 'Première année', levelCode: '1AS' }]);

    expect(component.levels.length).toBe(1);
  });

  it('refuse un formulaire vide : identité, contact et niveau sont requis', () => {
    expect(component.studentForm.valid).toBeFalse();
    expect(component.studentForm.get('personalInformation.firstName')?.hasError('required')).toBeTrue();
    expect(component.studentForm.get('personalInformation.lastName')?.hasError('required')).toBeTrue();
    expect(component.studentForm.get('personalInformation.gender')?.hasError('required')).toBeTrue();
    expect(component.studentForm.get('contactInformation.email')?.hasError('required')).toBeTrue();
    expect(component.studentForm.get('contactInformation.dateOfBirth')?.hasError('required')).toBeTrue();
    expect(component.studentForm.get('academicInformation.level')?.hasError('required')).toBeTrue();
  });

  it('valide le format de l\'email et n\'accepte qu\'une moyenne numérique', () => {
    const email = component.studentForm.get('contactInformation.email');
    email?.setValue('pas-un-email');
    expect(email?.hasError('email')).toBeTrue();
    email?.setValue('amina@example.com');
    expect(email?.valid).toBeTrue();

    const score = component.studentForm.get('academicInformation.averageScore');
    score?.setValue('abc');
    expect(score?.hasError('pattern')).toBeTrue();
    score?.setValue('15');
    expect(score?.valid).toBeTrue();
  });

  it('préremplit la nationalité par défaut', () => {
    expect(component.studentForm.get('contactInformation.nationality')?.value)
      .toBe(DEFAULT_NATIONALITY);
    expect(component.selectedTutor).toBeNull();
  });
});
