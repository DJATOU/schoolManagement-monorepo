import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { TeacherSearchComponent } from './teacher-search.component';
import { Teacher } from '../../../models/teacher/teacher';
import { API_BASE_URL } from '../../../api-base-url';
import { setupComponentTestBed } from '../../../../testing/setup';

describe('TeacherSearchComponent', () => {
  let component: TeacherSearchComponent;
  let fixture: ComponentFixture<TeacherSearchComponent>;
  let httpMock: HttpTestingController;

  function teacher(id: number, lastName: string, groups: Teacher['groups'] = []): Teacher {
    return {
      id, firstName: 'Karim', lastName, groups,
      gender: 'M', email: `${id}@example.com`, phoneNumber: '',
      dateOfBirth: '1980-01-01', placeOfBirth: ''
    };
  }

  /** Sert la requête de liste ouverte par ngOnInit. */
  function flushTeachers(teachers: Teacher[]): void {
    httpMock.match(`${API_BASE_URL}/api/teachers`).forEach(req => req.flush(teachers));
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await setupComponentTestBed(TeacherSearchComponent);

    fixture = TestBed.createComponent(TeacherSearchComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('affiche les cartes par défaut et démarre en chargement', () => {
    expect(component.viewMode).toBe('card');
    expect(component.isLoading).toBeTrue();
  });

  it('trie les enseignants par nom, insensiblement à la casse et aux accents', () => {
    flushTeachers([teacher(1, 'Zerrouki'), teacher(2, 'élias'), teacher(3, 'Belhadj')]);

    expect(component.isLoading).toBeFalse();
    expect(component.filteredTeachers.map(t => t.lastName))
      .toEqual(['Belhadj', 'élias', 'Zerrouki']);
  });

  it('le filtre « avec groupes » écarte les enseignants sans groupe', () => {
    flushTeachers([
      teacher(1, 'Belhadj', [{
        id: 10, name: 'Maths 1B', groupTypeId: 1, levelId: 1, subjectId: 1,
        sessionNumberPerSerie: 8, priceId: 1, teacherId: 1
      }]),
      teacher(2, 'Zerrouki')
    ]);

    component.onHasGroupsFilterChange(true);

    expect(component.filteredTeachers.map(t => t.lastName)).toEqual(['Belhadj']);
    expect(component.totalTeachers).toBe(1);
  });
});
