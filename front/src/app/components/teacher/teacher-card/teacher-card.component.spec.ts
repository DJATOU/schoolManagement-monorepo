import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TeacherCardComponent } from './teacher-card.component';
import { setupComponentTestBed } from '../../../../testing/setup';
import { aTeacher } from '../../../../testing/fixtures';

/**
 * `teacher` est une entrée obligatoire : `ngOnInit` construit à partir d'elle le profil
 * passé à `ProfileCardComponent`, sans aucun appel réseau.
 */
describe('TeacherCardComponent', () => {
  let component: TeacherCardComponent;
  let fixture: ComponentFixture<TeacherCardComponent>;

  beforeEach(async () => {
    await setupComponentTestBed(TeacherCardComponent);
    fixture = TestBed.createComponent(TeacherCardComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('teacher', aTeacher());
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('projette l\'enseignant sur le profil de la carte partagée', () => {
    expect(component.profile).toEqual(jasmine.objectContaining({
      id: 9,
      firstName: 'Karim',
      lastName: 'Saïdi',
      email: 'karim.saidi@example.test',
      phoneNumber: '0555987654'
    }));
  });

  it('porte la spécialisation en sous-titre', () => {
    expect(component.profile.subtitle).toContain('Mathématiques');
  });

  it('affiche le nom de l\'enseignant', () => {
    expect(fixture.nativeElement.textContent).toContain('Karim');
    expect(fixture.nativeElement.textContent).toContain('Saïdi');
  });
});
