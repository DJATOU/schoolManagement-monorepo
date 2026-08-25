import { ComponentFixture, TestBed } from '@angular/core/testing';

import { StudentProfileComponent } from './student-profile.component';
import { activatedRouteProviders, setupComponentTestBed } from '../../../../testing/setup';

/**
 * Fiche d'un étudiant, ouverte sur l'identifiant porté par l'URL.
 *
 * <p>La fiche intègre `<app-group-change-notice>`, qui fait son propre appel HTTP : le socle
 * de test doit donc fournir `HttpClient`, faute de quoi la fiche entière échoue à cause d'un
 * bandeau purement informatif.</p>
 */
describe('StudentProfileComponent', () => {
  let component: StudentProfileComponent;
  let fixture: ComponentFixture<StudentProfileComponent>;

  beforeEach(async () => {
    await setupComponentTestBed(StudentProfileComponent, {
      providers: activatedRouteProviders({ id: '42' })
    });
    fixture = TestBed.createComponent(StudentProfileComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('reste en chargement jusqu\'à la réponse du serveur', () => {
    // Sans cet état, la fiche afficherait un instant des champs vides que l'administrateur
    // pourrait prendre pour des données manquantes.
    expect(component.loading).toBeTrue();
  });

  it('n\'expose aucune URL de photo avant le chargement de l\'étudiant', () => {
    expect(component.studentPhotoUrl).toBe('');
  });
});
