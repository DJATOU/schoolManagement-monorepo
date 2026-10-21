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
import { ProfilePdfService } from '../../../services/profile-pdf.service';
import { TranslateService } from '@ngx-translate/core';
import frTranslations from '../../../../assets/i18n/fr.json';
import enTranslations from '../../../../assets/i18n/en.json';
import arTranslations from '../../../../assets/i18n/ar.json';
import { pdfLanguage } from '../../../shared/pdf-i18n';
import { choosePrintLanguage } from '../../../../testing/print-language';

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
  let translate: TranslateService;

  beforeEach(async () => {
    await setupComponentTestBed(StudentProfileComponent, {
      providers: activatedRouteProviders({ id: '42' })
    });
    translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', frTranslations);
    translate.setTranslation('en', enTranslations);
    translate.use('fr');
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

      expect(snackBar).toHaveBeenCalledWith(message, 'Fermer', jasmine.any(Object));
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

  /** Date de naissance : 2010-05-15 s'affiche 15/05/2010, à l'écran comme dans le PDF, quel que soit le fuseau. */
  describe('date de naissance', () => {
    beforeEach(() => {
      component.student = aStudent({ id: 42, dateOfBirth: '2010-05-15' });
      component.loading = false;
      fixture.detectChanges();
    });

    it('fiche : 15/05/2010', () => {
      expect(fixture.nativeElement.textContent).toContain('15/05/2010');
    });

    it('PDF : 15/05/2010', () => {
      const pdf = spyOn(TestBed.inject(ProfilePdfService), 'generateProfilePdf');
      component.printProfilePdf();
      const rows = pdf.calls.mostRecent().args[0].sections.flatMap(section => section.rows);
      expect(rows.find(row => row.label === 'Date de naissance')?.value).toBe('15/05/2010');
    });
  });

  /**
   * Langue de la fiche : le français reste celui d'avant l'externalisation des libellés, l'anglais
   * suit la langue choisie. Sexe et nationalité sont affichés par leur libellé, la valeur
   * enregistrée ne change pas. Le PDF est en français pour toute langue autre que l'anglais.
   */
  describe('langue de la fiche', () => {
    beforeEach(() => {
      component.student = aStudent({ id: 42, gender: 'M', nationality: 'Algérienne' });
      component.loading = false;
      fixture.detectChanges();
    });

    const text = (): string => (fixture.nativeElement.textContent as string).replace(/\s+/g, ' ');

    it('français : libellés identiques à ceux d\'avant', () => {
      expect(text()).toContain('Informations personnelles');
      expect(text()).toContain('Informations académiques');
      expect(text()).toContain('Établissement');
      expect(text()).toContain('Ajouter un paiement');
      expect(text()).toContain('Gérer les groupes');
      expect(text()).toContain('Groupes (0)');
      expect(text()).toContain('Aucun groupe pour le moment');
      expect(text()).toContain('Algérienne');
    });

    it('anglais : libellés anglais, sexe et nationalité traduits', () => {
      translate.use('en');
      fixture.detectChanges();
      expect(text()).toContain('Personal information');
      expect(text()).toContain('Add a payment');
      expect(text()).toContain('Groups (0)');
      expect(text()).toContain('No groups yet');
      expect(text()).toContain('Male');
      expect(text()).toContain('Algerian');
      expect(text()).not.toContain('Aucun groupe pour le moment');
      // La donnée enregistrée reste la chaîne française.
      expect(component.student?.nationality).toBe('Algérienne');
    });

    it('sexe : « M » et « male » donnent le même libellé, une valeur inconnue reste telle quelle', () => {
      expect(component.genderLabel('M')).toBe('Masculin');
      expect(component.genderLabel('female')).toBe('Féminin');
      expect(component.genderLabel('X')).toBe('X');
      expect(component.nationalityLabel('Ancienne saisie')).toBe('Ancienne saisie');
    });

    function pdfLabels(): string[] {
      const pdf = spyOn(TestBed.inject(ProfilePdfService), 'generateProfilePdf');
      component.printProfilePdf();
      const doc = pdf.calls.mostRecent().args[0];
      return [doc.subtitle ?? '', doc.tableTitle ?? '', ...doc.sections.map(section => section.heading),
        ...doc.sections.flatMap(section => section.rows.map(row => row.label)), ...(doc.tableColumns ?? [])];
    }

    it('PDF en français sous le français', () => {
      const labels = pdfLabels();
      expect(labels).toContain('Étudiant · 2AS');
      expect(labels).toContain('Informations personnelles');
      expect(labels).toContain('Sexe');
      expect(labels).toContain('Moyenne');
      expect(labels).toContain('Groupes (0)');
      expect(labels).toContain('Groupe');
    });

    it('PDF en français sous une langue autre que fr / en', () => {
      translate.setTranslation('ar', {});
      translate.use('ar');
      const labels = pdfLabels();
      expect(labels).toContain('Informations personnelles');
      expect(labels).toContain('Téléphone');
      expect(labels).toContain('Groupes (0)');
    });

    it('PDF en anglais sous l\'anglais', () => {
      translate.use('en');
      const labels = pdfLabels();
      expect(labels).toContain('Student · 2AS');
      expect(labels).toContain('Personal information');
      expect(labels).toContain('Gender');
      expect(labels).toContain('Groups (0)');
      expect(labels).not.toContain('Informations personnelles');
    });

    it('impression : le bouton suit l\'écran (arabe), le menu imprime dans la langue choisie', () => {
      translate.setTranslation('ar', arTranslations);
      translate.use('ar');
      fixture.detectChanges();
      const seen: string[] = [];
      const pdf = spyOn(TestBed.inject(ProfilePdfService), 'generateProfilePdf')
        .and.callFake(() => { seen.push(pdfLanguage(translate)); });

      const buttons = (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('button.pf-hb:not(.plm-trigger)');
      Array.from(buttons).find(button => button.textContent?.trim() === 'badge')!.click();
      expect(seen).toEqual(['ar']);
      expect(pdf.calls.mostRecent().args[0].sections[0].heading).toBe(arTranslations.STUDENT_PROFILE.PERSONAL_INFO);

      choosePrintLanguage(fixture, '.pf-header-buttons', 'fr');
      expect(seen).toEqual(['ar', 'fr']);
      expect(pdf.calls.mostRecent().args[0].sections[0].heading).toBe(frTranslations.STUDENT_PROFILE.PERSONAL_INFO);
      expect(translate.currentLang).toBe('ar');
    });
  });
});
