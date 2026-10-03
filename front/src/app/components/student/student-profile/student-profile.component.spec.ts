import { ComponentFixture, TestBed } from '@angular/core/testing';

import { StudentProfileComponent } from './student-profile.component';
import { activatedRouteProviders, setupComponentTestBed } from '../../../../testing/setup';
import { aGroup, aStudent } from '../../../../testing/fixtures';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, throwError } from 'rxjs';
import { GroupService } from '../../../services/group.service';
import { StudentEncashmentsComponent } from '../student-encashments/student-encashments.component';
import { StudentEnrolmentsComponent } from '../student-enrolments/student-enrolments.component';
import { StudentJournalComponent } from '../student-journal/student-journal.component';
import { StudentService } from '../services/student.service';
import { AuthService } from '../../../services/auth.service';
import { By } from '@angular/platform-browser';

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

  /** Le Journal nomme les reçus : il n'apparaît que pour l'ADMIN, comme les versements (D.5). */
  it('Journal des corrections : affiché à l\'ADMIN, pour l\'élève de la fiche ; absent sinon', () => {
    const auth = TestBed.inject(AuthService);
    const role = spyOn(auth, 'hasRole').and.returnValue(false);
    component.student = aStudent({ id: 42 });
    component.loading = false;
    fixture.detectChanges();
    expect(fixture.debugElement.query(By.directive(StudentJournalComponent))).toBeNull();

    role.and.returnValue(true);
    fixture.destroy();
    fixture = TestBed.createComponent(StudentProfileComponent);
    component = fixture.componentInstance;
    component.student = aStudent({ id: 42 });
    component.loading = false;
    fixture.detectChanges();
    const journal = fixture.debugElement.query(By.directive(StudentJournalComponent));
    expect(journal).not.toBeNull();
    expect((journal.componentInstance as StudentJournalComponent).studentId).toBe(42);
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

  /** Inscription à des groupes, à une date d'arrivée réelle, et refus dits tels quels (C.8). */
  describe('inscription à des groupes', () => {
    let studentService: StudentService;
    let snackBar: jasmine.Spy;

    beforeEach(() => {
      component.student = aStudent({ id: 42 });
      studentService = fixture.debugElement.injector.get(StudentService);
      // Le composant importe SharedModule, qui fournit sa propre instance : on la prend chez lui.
      snackBar = spyOn(fixture.debugElement.injector.get(MatSnackBar), 'open');
      component.allGroups = [aGroup({ id: 5, name: 'Math 1ère A' })];
    });

    it('transmet les groupes et la date d\'arrivée choisis ; les inscriptions sont relues', () => {
      const add = spyOn(studentService, 'addGroupsToStudent').and.returnValue(of({ message: 'ok' }));
      const enrolments = jasmine.createSpyObj<StudentEnrolmentsComponent>('StudentEnrolmentsComponent', ['reload']);
      component.enrolmentsPanel = enrolments;
      component.groupForm.patchValue({ groupIds: [5], arrival: '2029-10-15' });

      component.onSubmitGroups();

      expect(add).toHaveBeenCalledWith(42, [5], '2029-10-15');
      expect(enrolments.reload).toHaveBeenCalled();
      expect(component.studentGroups.map(group => group.id)).toEqual([5]);
      expect(component.groupForm.value).toEqual({ groupIds: [], arrival: null });
    });

    it('un refus affiche le message du serveur, qui nomme l\'année et ses bornes', () => {
      const message = 'La date d\'arrivée du 01/07/2030 est hors de l\'année scolaire 2029-2030 (du 01/09/2029 au 30/06/2030).';
      spyOn(studentService, 'addGroupsToStudent').and.returnValue(throwError(() => ({ status: 400, error: { message } })));
      component.groupForm.patchValue({ groupIds: [5], arrival: '2030-07-01' });

      component.onSubmitGroups();

      expect(snackBar).toHaveBeenCalledWith(message, 'Close', jasmine.any(Object));
    });

    it('le dialogue rend groupes et date ; l\'inscription part avec eux', () => {
      component.studentLevelId = 3;
      component.allGroups = [aGroup({ id: 5, name: 'Math 1ère A', levelId: 3 })];
      const add = spyOn(studentService, 'addGroupsToStudent').and.returnValue(of({ message: 'ok' }));
      const open = spyOn(fixture.debugElement.injector.get(MatDialog), 'open')
        .and.returnValue({ afterClosed: () => of({ groupIds: [5], arrival: '2029-11-04' }) } as MatDialogRef<unknown>);

      component.openGroupDialog();

      expect(open).toHaveBeenCalled();
      expect(add).toHaveBeenCalledWith(42, [5], '2029-11-04');
    });

    it('une correction d\'inscription relit les groupes suivis et les versements', () => {
      const groups = spyOn(studentService, 'getGroupsForStudent').and.returnValue(of([]));
      const encashments = jasmine.createSpyObj<StudentEncashmentsComponent>('StudentEncashmentsComponent', ['reload']);
      component.encashmentsPanel = encashments;

      component.onEnrolmentCorrected();

      expect(groups).toHaveBeenCalledWith(42, component.selectedSchoolYearId ?? undefined);
      expect(encashments.reload).toHaveBeenCalled();
      expect(component.studentFullName).toBe(`${component.student!.firstName} ${component.student!.lastName}`);
    });
  });
});
