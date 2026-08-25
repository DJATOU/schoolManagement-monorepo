import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TeacherListComponent } from './teacher-list.component';
import { Teacher } from '../../../models/teacher/teacher';
import { setupComponentTestBed } from '../../../../testing/setup';

/**
 * Ligne de liste d'un enseignant : l'enseignant est une entrée obligatoire, le composant
 * en dérive le profil consommé par `app-profile-list-item`.
 */
describe('TeacherListComponent', () => {
  let component: TeacherListComponent;
  let fixture: ComponentFixture<TeacherListComponent>;

  const teacher: Teacher = {
    id: 7,
    firstName: 'Karim',
    lastName: 'Belhadj',
    gender: 'M',
    email: 'karim@example.com',
    phoneNumber: '0555000000',
    dateOfBirth: '1980-05-02',
    placeOfBirth: 'Alger',
    groups: []
  };

  beforeEach(async () => {
    await setupComponentTestBed(TeacherListComponent);

    fixture = TestBed.createComponent(TeacherListComponent);
    component = fixture.componentInstance;
    component.teacher = teacher;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('projette l\'enseignant en profil affichable', () => {
    expect(component.profile).toEqual({
      id: 7,
      firstName: 'Karim',
      lastName: 'Belhadj',
      photo: undefined,
      email: 'karim@example.com',
      phoneNumber: '0555000000'
    });
  });

  it('affiche le nom et le contact de l\'enseignant', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Karim');
    expect(text).toContain('Belhadj');
  });
});
