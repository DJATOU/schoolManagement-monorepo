import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';

import { TeacherProfileComponent } from './teacher-profile.component';
import { Teacher } from '../../../models/teacher/teacher';
import { API_BASE_URL } from '../../../api-base-url';
import { setupComponentTestBed } from '../../../../testing/setup';

describe('TeacherProfileComponent', () => {
  let component: TeacherProfileComponent;
  let fixture: ComponentFixture<TeacherProfileComponent>;
  let httpMock: HttpTestingController;

  const teacher: Teacher = {
    id: 7,
    firstName: 'Karim',
    lastName: 'Belhadj',
    gender: 'M',
    email: 'karim@example.com',
    phoneNumber: '0555000000',
    dateOfBirth: '1980-05-02',
    placeOfBirth: 'Alger',
    specialization: 'Mathématiques',
    yearsOfExperience: 12,
    groups: []
  };

  beforeEach(async () => {
    // La fiche se lit à partir de l'identifiant présent dans l'URL.
    await setupComponentTestBed(TeacherProfileComponent, {
      providers: [{
        provide: ActivatedRoute,
        useValue: { snapshot: { paramMap: convertToParamMap({ id: '7' }) }, params: { subscribe: () => undefined } }
      }]
    });

    fixture = TestBed.createComponent(TeacherProfileComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  /** Sert la fiche enseignant et ses groupes. */
  function flushTeacher(body: Teacher = teacher): void {
    httpMock.expectOne(`${API_BASE_URL}/api/teachers/id/7`).flush(body);
    httpMock.expectOne(`${API_BASE_URL}/api/groups/teacher/7`).flush([]);
    fixture.detectChanges();
  }

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('démarre en chargement puis affiche l\'enseignant de l\'URL', () => {
    expect(component.loading).toBeTrue();

    flushTeacher();

    expect(component.loading).toBeFalse();
    expect(component.teacher).toEqual(teacher);
    expect(fixture.nativeElement.textContent).toContain('Belhadj');
  });

  it('getInitials() combine les deux initiales', () => {
    flushTeacher();

    expect(component.getInitials()).toBe('KB');
  });

  it('sans photo, aucune URL d\'image n\'est construite', () => {
    // Le repli sur les initiales dépend d'une URL vide, pas d'une image cassée.
    flushTeacher();

    expect(component.teacherPhotoUrl).toBe('');
    expect(component.hasImageError).toBeFalse();
  });
});
