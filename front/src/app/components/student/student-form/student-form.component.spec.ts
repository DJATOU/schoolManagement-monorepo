import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { By } from '@angular/platform-browser';
import { MatTabGroup } from '@angular/material/tabs';

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

  /** Date de naissance : saisie JJ/MM/AAAA, envoyée yyyy-MM-dd (jour du calendrier, sans fuseau). */
  describe('date de naissance', () => {
    /** Affiche l'onglet « Contact », où se trouve le champ, et renvoie son input. */
    async function birthDateInput(): Promise<HTMLInputElement> {
      fixture.debugElement.query(By.directive(MatTabGroup)).componentInstance.selectedIndex = 1;
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
      return fixture.nativeElement.querySelector('input[formcontrolname="dateOfBirth"]');
    }

    function type(input: HTMLInputElement, text: string): void {
      input.value = text;
      input.dispatchEvent(new Event('input'));
      input.dispatchEvent(new Event('blur'));
      fixture.detectChanges();
    }

    function fillRequired(): void {
      component.studentForm.patchValue({
        personalInformation: { firstName: 'Léa', lastName: 'Haddad', gender: 'female' },
        contactInformation: { email: 'lea@example.test', dateOfBirth: new Date(2010, 4, 15) },
        academicInformation: { level: 1 }
      });
    }

    it('15/05/2010 tapé au clavier : le 15 mai 2010, sans erreur de saisie', async () => {
      const input = await birthDateInput();
      type(input, '15/05/2010');
      const control = component.studentForm.get('contactInformation.dateOfBirth');
      expect(control?.hasError('matDatepickerParse')).toBeFalse();
      const value: Date = control?.value;
      expect(value instanceof Date).toBeTrue();
      expect([value.getFullYear(), value.getMonth(), value.getDate()]).toEqual([2010, 4, 15]);
    });

    it('05/06/2010 tapé au clavier : le 5 juin, pas le 6 mai', async () => {
      const input = await birthDateInput();
      type(input, '05/06/2010');
      const value: Date = component.studentForm.get('contactInformation.dateOfBirth')?.value;
      expect([value.getFullYear(), value.getMonth(), value.getDate()]).toEqual([2010, 5, 5]);
    });

    it('envoyée au serveur sous la forme 2010-05-15', () => {
      fillRequired();
      expect(component['prepareFormData']().get('dateOfBirth')).toBe('2010-05-15');
    });

    it('après « Effacer », aucune valeur vidée n\'est envoyée comme la chaîne « null »', () => {
      component.onClearForm();
      fillRequired();
      const entries: [string, FormDataEntryValue][] = [];
      component['prepareFormData']().forEach((value, key) => entries.push([key, value]));
      expect(entries.filter(([, value]) => value === 'null' || value === 'undefined')).toEqual([]);
      expect(entries).toContain(['dateOfBirth', '2010-05-15']);
    });
  });
});
