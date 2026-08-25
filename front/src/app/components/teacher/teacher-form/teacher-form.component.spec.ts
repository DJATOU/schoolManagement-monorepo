import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { TeacherFormComponent } from './teacher-form.component';
import { API_BASE_URL } from '../../../api-base-url';
import { DEFAULT_NATIONALITY } from '../../../utils/form-options';
import { setupComponentTestBed } from '../../../../testing/setup';

describe('TeacherFormComponent', () => {
  let component: TeacherFormComponent;
  let fixture: ComponentFixture<TeacherFormComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await setupComponentTestBed(TeacherFormComponent);

    fixture = TestBed.createComponent(TeacherFormComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('démarre en création et charge les matières enseignables', () => {
    expect(component.isEditMode).toBeFalse();
    expect(component.teacherId).toBeNull();

    const req = httpMock.expectOne(`${API_BASE_URL}/api/subjects`);
    expect(req.request.method).toBe('GET');
    req.flush([{ id: 1, name: 'Mathématiques' }]);

    expect(component.subjects.length).toBe(1);
  });

  it('refuse un formulaire vide : identité, contact et expérience sont requis', () => {
    expect(component.teacherForm.valid).toBeFalse();
    expect(component.teacherForm.get('basicInformation.firstName')?.hasError('required')).toBeTrue();
    expect(component.teacherForm.get('basicInformation.lastName')?.hasError('required')).toBeTrue();
    expect(component.teacherForm.get('basicInformation.gender')?.hasError('required')).toBeTrue();
    expect(component.teacherForm.get('contactInformation.email')?.hasError('required')).toBeTrue();
    expect(component.teacherForm.get('contactInformation.phoneNumber')?.hasError('required')).toBeTrue();
    expect(component.teacherForm.get('professionalDetails.yearsOfExperience')?.hasError('required')).toBeTrue();
    // La spécialisation reste optionnelle.
    expect(component.teacherForm.get('professionalDetails.specialization')?.valid).toBeTrue();
  });

  it('n\'accepte qu\'un nombre d\'années d\'expérience numérique', () => {
    const years = component.teacherForm.get('professionalDetails.yearsOfExperience');
    years?.setValue('douze');
    expect(years?.hasError('pattern')).toBeTrue();
    years?.setValue('12');
    expect(years?.valid).toBeTrue();
  });

  it('préremplit la nationalité par défaut', () => {
    expect(component.teacherForm.get('contactInformation.nationality')?.value)
      .toBe(DEFAULT_NATIONALITY);
  });
});
