import { ComponentFixture, TestBed } from '@angular/core/testing';

import { StudentProfileComponent } from './student-profile.component';
import { activatedRouteProviders, setupComponentTestBed } from '../../../../testing/setup';
import { aGroup, aStudent } from '../../../../testing/fixtures';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';
import { GroupService } from '../../../services/group.service';
import { StudentEncashmentsComponent } from '../student-encashments/student-encashments.component';

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

  /**
   * Un encaissement depuis la fiche doit apparaître dans l'historique des versements sans
   * recharger la page (A.9) ; un dialogue fermé sans encaisser ne déclenche rien.
   */
  describe('après le dialogue de versement', () => {
    let panel: jasmine.SpyObj<StudentEncashmentsComponent>;

    function closeDialogWith(result: unknown): void {
      component.student = aStudent({ id: 42 });
      panel = jasmine.createSpyObj<StudentEncashmentsComponent>('StudentEncashmentsComponent', ['reload']);
      component.encashmentsPanel = panel;
      // Services fournis au niveau du composant : on les prend dans son injecteur.
      spyOn(fixture.debugElement.injector.get(GroupService), 'getGroupsForPayment')
        .and.returnValue(of([aGroup()]));
      spyOn(fixture.debugElement.injector.get(MatDialog), 'open')
        .and.returnValue({ afterClosed: () => of(result) } as MatDialogRef<unknown>);

      component.openPaymentDialog();
    }

    it('recharge l\'historique des versements quand un versement est encaissé', () => {
      closeDialogWith({ amountReceived: 1500 });

      expect(panel.reload).toHaveBeenCalled();
    });

    it('ne recharge rien quand le dialogue est fermé sans encaisser', () => {
      closeDialogWith(undefined);

      expect(panel.reload).not.toHaveBeenCalled();
    });
  });
});
