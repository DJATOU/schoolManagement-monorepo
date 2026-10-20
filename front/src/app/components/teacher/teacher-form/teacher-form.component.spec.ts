import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { By } from '@angular/platform-browser';
import { MatDialogRef } from '@angular/material/dialog';
import { MatTabGroup } from '@angular/material/tabs';
import { of } from 'rxjs';

import { TeacherFormComponent } from './teacher-form.component';
import { API_BASE_URL } from '../../../api-base-url';
import { DEFAULT_NATIONALITY } from '../../../utils/form-options';
import { activatedRouteProviders, setupComponentTestBed } from '../../../../testing/setup';
import { aTeacher } from '../../../../testing/fixtures';

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

  /** Création : la date de naissance part en yyyy-MM-dd, et aucun champ vidé en « null ». */
  describe('création, date de naissance', () => {
    function fillRequired(): void {
      component.teacherForm.patchValue({
        basicInformation: { firstName: 'Karim', lastName: 'Saïdi', gender: 'M' },
        contactInformation: {
          email: 'karim@example.test', phoneNumber: '0555987654', nationality: DEFAULT_NATIONALITY,
          dateOfBirth: new Date(1985, 1, 20)
        },
        professionalDetails: { yearsOfExperience: '12' }
      });
    }

    function submittedFormData(): FormData {
      spyOn(component.dialog, 'open').and.returnValue({ afterClosed: () => of(true) } as MatDialogRef<unknown>);
      component.onSubmit();
      const req = httpMock.expectOne(`${API_BASE_URL}/api/teachers/createTeacher`);
      return req.request.body as FormData;
    }

    it('envoyée au serveur sous la forme 1985-02-20', () => {
      fillRequired();
      expect(submittedFormData().get('dateOfBirth')).toBe('1985-02-20');
    });

    it('après « Effacer », aucune valeur vidée n\'est envoyée comme la chaîne « null »', () => {
      component.onClearForm();
      fillRequired();
      const entries: [string, FormDataEntryValue][] = [];
      submittedFormData().forEach((value, key) => entries.push([key, value]));
      expect(entries.filter(([, value]) => value === 'null' || value === 'undefined')).toEqual([]);
      expect(entries).toContain(['dateOfBirth', '1985-02-20']);
    });
  });
});

/**
 * Modification (/teacher/edit/:id). L'API renvoyait la date de naissance en millisecondes :
 * le datepicker la jugeait invalide et le formulaire ne pouvait plus être enregistré.
 */
describe('TeacherFormComponent — modification, date de naissance', () => {
  let component: TeacherFormComponent;
  let fixture: ComponentFixture<TeacherFormComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await setupComponentTestBed(TeacherFormComponent, { providers: activatedRouteProviders({ id: '9' }) });
    fixture = TestBed.createComponent(TeacherFormComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    // Onglet « Contact » affiché : le datepicker y valide la valeur reçue.
    fixture.debugElement.query(By.directive(MatTabGroup)).componentInstance.selectedIndex = 1;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  async function load(dateOfBirth: unknown): Promise<void> {
    httpMock.expectOne(`${API_BASE_URL}/api/teachers/id/9`)
      .flush({ ...aTeacher(), yearsOfExperience: 12, dateOfBirth });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function savedDateOfBirth(): unknown {
    spyOn(component.dialog, 'open').and.returnValue({ afterClosed: () => of(true) } as MatDialogRef<unknown>);
    component.onSubmit();
    const req = httpMock.expectOne(`${API_BASE_URL}/api/teachers/9`);
    expect(req.request.method).toBe('PUT');
    return (req.request.body as { dateOfBirth?: unknown }).dateOfBirth;
  }

  it('reçue en millisecondes (ancien format) : formulaire valide, renvoyée 1985-02-20', async () => {
    await load(new Date(1985, 1, 20).getTime());
    expect(component.teacherForm.valid).withContext('formulaire valide').toBeTrue();
    expect(savedDateOfBirth()).toBe('1985-02-20');
  });

  it('reçue en 1985-02-20 : formulaire valide, champ 20/02/1985, renvoyée 1985-02-20', async () => {
    await load('1985-02-20');
    expect(component.teacherForm.valid).withContext('formulaire valide').toBeTrue();
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input[formcontrolname="dateOfBirth"]');
    expect(input.value).toBe('20/02/1985');
    expect(savedDateOfBirth()).toBe('1985-02-20');
  });
});
