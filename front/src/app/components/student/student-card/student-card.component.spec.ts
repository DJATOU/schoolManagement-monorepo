import { ComponentFixture, TestBed } from '@angular/core/testing';

import { StudentCardComponent } from './student-card.component';
import { setupComponentTestBed } from '../../../../testing/setup';
import { aStudent } from '../../../../testing/fixtures';

/**
 * `student` est une entrée obligatoire, déréférencée par `ngOnInit`.
 *
 * <p>La fixture laisse `levelId` et `tutorId` indéfinis : renseignés, ils déclenchent des
 * appels HTTP dont la réponse conditionne la construction du profil. Le chemin nominal se
 * teste donc sans réseau, et les tests qui ont besoin du chargement les fournissent.</p>
 */
describe('StudentCardComponent', () => {
  let component: StudentCardComponent;
  let fixture: ComponentFixture<StudentCardComponent>;

  beforeEach(async () => {
    await setupComponentTestBed(StudentCardComponent);
    fixture = TestBed.createComponent(StudentCardComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('student', aStudent());
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('projette l\'étudiant sur le profil de la carte partagée', () => {
    expect(component.profile).toEqual(jasmine.objectContaining({
      id: 1,
      firstName: 'Amina',
      lastName: 'Belkacem',
      email: 'amina.belkacem@example.test'
    }));
  });

  it('porte le niveau en sous-titre', () => {
    expect(component.profile.subtitle).toContain('2AS');
  });

  it('n\'expose aucun tuteur quand l\'étudiant n\'en a pas', () => {
    // `undefined` signifierait « pas encore chargé » ; `null` dit « aucun tuteur », ce que
    // le dos de la carte doit pouvoir distinguer.
    expect(component.tutor).toBeNull();
  });

  it('affiche le nom de l\'étudiant', () => {
    expect(fixture.nativeElement.textContent).toContain('Amina');
    expect(fixture.nativeElement.textContent).toContain('Belkacem');
  });
});
