import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { StudentSearchComponent } from './student-search.component';
import { Student } from '../domain/student';
import { API_BASE_URL } from '../../../api-base-url';
import { setupComponentTestBed } from '../../../../testing/setup';

describe('StudentSearchComponent', () => {
  let component: StudentSearchComponent;
  let fixture: ComponentFixture<StudentSearchComponent>;
  let httpMock: HttpTestingController;

  function student(id: number, lastName: string, status: 'ACTIVE' | 'INACTIVE' = 'ACTIVE'): Student {
    return {
      id, firstName: 'Amina', lastName, status,
      gender: 'F', email: `${id}@example.com`, phoneNumber: '',
      dateOfBirth: new Date('2010-01-01'), placeOfBirth: '', photo: '',
      level: 1, levelId: 1, establishment: ''
    };
  }

  /**
   * Sert la liste des étudiants puis les statuts de paiement demandés pour chacun.
   * Sans statut, `forkJoin` ne complète pas et le spinner resterait affiché.
   */
  function flushStudents(students: Student[]): void {
    httpMock.match(req => req.url === `${API_BASE_URL}/api/students`)
      .forEach(req => req.flush(students));
    fixture.detectChanges();

    students.forEach(s => {
      httpMock.match(`${API_BASE_URL}/api/payments/students/${s.id}/payment-status`)
        .forEach(req => req.flush([]));
    });
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await setupComponentTestBed(StudentSearchComponent);

    fixture = TestBed.createComponent(StudentSearchComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('sort du chargement même sans aucun étudiant', () => {
    // forkJoin([]) ne complète jamais : la liste vide doit être court-circuitée,
    // sinon le spinner reste bloqué sur un écran sans données.
    flushStudents([]);

    expect(component.isLoading).toBeFalse();
    expect(component.filteredStudents).toEqual([]);
  });

  it('trie les étudiants par nom, insensiblement à la casse et aux accents', () => {
    flushStudents([student(1, 'Zerrouki'), student(2, 'élias'), student(3, 'Belkacem')]);

    expect(component.isLoading).toBeFalse();
    expect(component.filteredStudents.map(s => s.lastName))
      .toEqual(['Belkacem', 'élias', 'Zerrouki']);
  });

  it('isInactive() ne se fonde que sur le statut d\'inscription', () => {
    // `active` est un ancien indicateur de suppression logique : le confondre avec le
    // statut badgerait à tort des étudiants pourtant inscrits.
    expect(component.isInactive(student(1, 'Belkacem', 'INACTIVE'))).toBeTrue();
    expect(component.isInactive({ ...student(2, 'Zerrouki'), active: false })).toBeFalse();
  });

  it('le filtre « en retard » ne retient rien quand aucun statut n\'est en retard', () => {
    flushStudents([student(1, 'Belkacem'), student(2, 'Zerrouki')]);

    component.onLateFilterChange(true);

    expect(component.latePaymentCount).toBe(0);
    expect(component.filteredStudents).toEqual([]);
  });
});
