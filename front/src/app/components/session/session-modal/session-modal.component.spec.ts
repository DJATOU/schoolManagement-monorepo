import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpRequest } from '@angular/common/http';
import { HttpTestingController } from '@angular/common/http/testing';
import { By } from '@angular/platform-browser';
import { MatDialog, MatDialogConfig, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTabGroup } from '@angular/material/tabs';
import { TranslateService } from '@ngx-translate/core';
import { firstValueFrom, of, throwError } from 'rxjs';

import { SessionModalComponent } from './session-modal.component';
import { createDialogRefSpy, DialogRefSpy, matDialogProviders, setupComponentTestBed } from '../../../../testing/setup';
import { aSession, aStudent } from '../../../../testing/fixtures';
import { API_BASE_URL } from '../../../api-base-url';
import { RejectedAbsence } from '../../../models/Attendance/rejected-absence';
import { RollCall, RollCallStudent } from '../../../models/session/roll-call';
import { SessionUnvalidation } from '../../../models/session/session-unvalidation';
import { SessionService } from '../../../services/SessionService';
import { Student } from '../../student/domain/student';
import { CorrectionDialogComponent, CorrectionDialogData } from '../../shared/correction-dialog/correction-dialog.component';
import { AuthService } from '../../../services/auth.service';
import { AttendanceService } from '../../../services/attendance.service';
import { GroupService } from '../../../services/group.service';
import { JustificationEditDialogComponent } from '../../attendance/justification-edit-dialog/justification-edit-dialog.component';
import { AddStudentDialogComponent } from '../add-student-dialog/add-student-dialog.component';
import { AttendanceStateDialogComponent } from '../attendance-state-dialog/attendance-state-dialog.component';

/**
 * Fiche détaillée d'une séance, ouverte depuis le calendrier.
 *
 * <p>Le point sous test est `writeDisabled$` : les commandes d'écriture — édition, validation,
 * saisie de présence — doivent être désactivées si la vue est en lecture seule (année passée)
 * <strong>ou</strong> si l'utilisateur n'est pas administrateur. Un utilisateur non connecté
 * dans un test ne doit donc surtout pas se retrouver avec les droits d'écriture.</p>
 */
describe('SessionModalComponent', () => {
  let component: SessionModalComponent;
  let fixture: ComponentFixture<SessionModalComponent>;
  let dialogRef: DialogRefSpy;

  beforeEach(async () => {
    dialogRef = createDialogRefSpy();
    // Reconstruit à chaque test : un test modifie la séance pour exercer le cas « sans
    // groupe », et un objet partagé propagerait cette mutation aux tests suivants selon leur
    // ordre d'exécution.
    const sessionData = { ...aSession(), students: [aStudent()] };
    await setupComponentTestBed(SessionModalComponent, {
      providers: matDialogProviders(sessionData, dialogRef)
    });
    fixture = TestBed.createComponent(SessionModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('porte la séance sur laquelle il a été ouvert', () => {
    expect(component.sessionData.id).toBe(100);
    expect(component.sessionData.groupId).toBe(5);
  });

  it('interdit l\'écriture à un utilisateur sans rôle administrateur', async () => {
    // Le défaut à éviter est l'inverse : autoriser l'écriture par défaut ouvrirait la
    // validation des présences à un simple lecteur.
    await expectAsync(firstValueFrom(component.writeDisabled$)).toBeResolvedTo(true);
  });

  it('n\'ouvre pas la fiche du groupe quand la séance n\'y est pas rattachée', () => {
    const open = spyOn(window, 'open');
    component.sessionData.groupId = undefined as unknown as number;

    component.openGroup();

    expect(open).not.toHaveBeenCalled();
  });
});

/**
 * Feuille d'appel servie par le serveur (spec admin-corrections, C.3 ; exigences 6.2, 7.1, 7.2).
 *
 * <p>La séance du 14/01/2030 du groupe « Maths 1B ». Le défaut corrigé : quand la feuille revenait
 * vide, l'écran la complétait par tous les étudiants du groupe, cochés présents par défaut —
 * un étudiant arrivé après la séance pouvait ainsi être noté absent d'une séance qui ne le
 * concernait pas.</p>
 */
describe('SessionModalComponent — feuille d\'appel', () => {
  let http: HttpTestingController;

  const rollCallUrl = (req: HttpRequest<unknown>) => req.url.endsWith('/api/sessions/100/roll-call');

  function aRollCall(overrides: Partial<RollCall> = {}): RollCall {
    return { sessionId: 100, groupId: 5, sessionDay: '2030-01-14', students: [], notConcerned: [], ...overrides };
  }

  function aRollCallStudent(overrides: Partial<RollCallStudent> = {}): RollCallStudent {
    return { id: 1, firstName: 'Amine', lastName: 'Belkacem', gender: 'M', arrival: '2029-09-01', departure: null,
      ...overrides };
  }

  async function open(students: Student[] = []): Promise<ComponentFixture<SessionModalComponent>> {
    await setupComponentTestBed(SessionModalComponent, {
      providers: matDialogProviders({ ...aSession(), students }, createDialogRefSpy())
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', {
      SESSION_MODAL: {
        LEFT_ON: 'Parti le {{day}}',
        ROLL_CALL_ERROR: 'Feuille non chargée',
        ROLL_CALL_EMPTY_NO_ENROLMENT: 'Aucun inscrit',
        ROLL_CALL_EMPTY_NOT_CONCERNED: 'Personne le {{day}} :',
        WINDOW_FROM: 'à partir du {{from}}',
        WINDOW_FROM_TO: 'du {{from}} au {{to}}',
        WINDOW_UNDATED: 'non datée'
      }
    });
    translate.use('fr');
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(SessionModalComponent);
    fixture.detectChanges();
    return fixture;
  }

  /** Répond à la feuille d'appel, puis aux présences déjà saisies (aucune). */
  async function answer(fixture: ComponentFixture<SessionModalComponent>, rollCall: RollCall): Promise<void> {
    http.expectOne(rollCallUrl).flush(rollCall);
    await fixture.whenStable();
    http.match(req => req.url.includes('/api/attendances/session/')).forEach(req => req.flush([]));
    fixture.detectChanges();
  }

  function text(fixture: ComponentFixture<SessionModalComponent>): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  /** Le contenu d'un onglet n'est rendu qu'à son activation : on ouvre « Présences ». */
  async function attendancesTab(fixture: ComponentFixture<SessionModalComponent>): Promise<HTMLElement> {
    fixture.debugElement.query(By.directive(MatTabGroup)).componentInstance.selectedIndex = 1;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  // « Liste du groupe » : toute lecture « …/students », par /api/groups comme par /api/student-groups.
  it('demande la feuille de la séance, et jamais la liste du groupe', async () => {
    const fixture = await open();
    await answer(fixture, aRollCall({ students: [aRollCallStudent()] }));

    expect(http.match(req => /\/students\b/.test(req.url)).length).toBe(0);
    expect((fixture.componentInstance.sessionData.students as Student[]).map(s => s.lastName)).toEqual(['Belkacem']);
  });

  it('coche présents les étudiants attendus', async () => {
    const fixture = await open();
    await answer(fixture, aRollCall({ students: [aRollCallStudent(), aRollCallStudent({ id: 2, lastName: 'Haddad' })] }));

    expect(fixture.componentInstance.sessionData.students.map(s => s.isPresent)).toEqual([true, true]);
    expect((fixture.componentInstance.sessionData.students as Student[]).map(s => s.isCatchUp)).toEqual([false, false]);
  });

  it('signale l\'étudiant parti dont la séance est dans la période', async () => {
    const fixture = await open();
    await answer(fixture, aRollCall({ students: [aRollCallStudent({ departure: '2030-01-14' })] }));

    expect((await attendancesTab(fixture)).querySelector('.departed-label')?.textContent?.trim()).toBe('Parti le 14/01/2030');
  });

  it('feuille vide : ne la complète pas, et nomme les étudiants non concernés avec leurs périodes', async () => {
    const fixture = await open();
    await answer(fixture, aRollCall({
      notConcerned: [
        { id: 2, firstName: 'Lina', lastName: 'Haddad', windows: [{ arrival: '2030-01-21', departure: null }] },
        { id: 3, firstName: 'Nour', lastName: 'Zerrouki', windows: [
          { arrival: '2029-09-01', departure: '2029-12-31' }, { arrival: '2030-02-01', departure: null }] }
      ]
    }));

    expect(fixture.componentInstance.sessionData.students).toEqual([]);
    expect(http.match(req => /\/students\b/.test(req.url)).length)
      .withContext('aucun repli sur la liste du groupe').toBe(0);
    const tab = await attendancesTab(fixture);
    expect(tab.querySelector('.roll-call-empty')?.textContent).toContain('Personne le 14/01/2030 :');
    const lines = Array.from(tab.querySelectorAll('.roll-call-empty li')).map(li => li.textContent?.trim());
    expect(lines).toEqual([
      'Lina Haddad : à partir du 21/01/2030',
      'Nour Zerrouki : du 01/09/2029 au 31/12/2029 ; à partir du 01/02/2030'
    ]);
  });

  it('feuille vide d\'un groupe sans inscrit : le dit', async () => {
    const fixture = await open();
    await answer(fixture, aRollCall());

    expect((await attendancesTab(fixture)).querySelector('.roll-call-empty')?.textContent).toContain('Aucun inscrit');
  });

  it('inscription non datée : expliquée comme telle', async () => {
    const fixture = await open();
    await answer(fixture, aRollCall({
      notConcerned: [{ id: 4, firstName: 'Sami', lastName: 'Kaci', windows: [{ arrival: null, departure: null }] }]
    }));

    expect((await attendancesTab(fixture)).querySelector('.roll-call-empty li')?.textContent?.trim())
      .toBe('Sami Kaci : non datée');
  });

  it('feuille non chargée : le dit, sans la faire passer pour une feuille vide', async () => {
    const fixture = await open();
    http.expectOne(rollCallUrl).flush({ message: 'panne' }, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    http.match(req => req.url.includes('/api/attendances/session/')).forEach(req => req.flush([]));
    fixture.detectChanges();

    const tab = await attendancesTab(fixture);
    expect(fixture.componentInstance.rollCallError).toBeTrue();
    expect(tab.querySelector('.roll-call-error')?.textContent).toContain('Feuille non chargée');
    expect(tab.querySelector('.roll-call-empty')).toBeNull();
    expect(text(fixture)).not.toContain('Aucun inscrit');
  });

  describe('absences refusées à la validation (C.8 ; exigence 7.5)', () => {
    const bulk = (req: HttpRequest<unknown>) => req.url.endsWith('/api/attendances/bulk') && req.method === 'POST';
    const lina = {
      studentId: 2, firstName: 'Lina', lastName: 'Haddad', sessionId: 100, sessionDay: '2030-01-14',
      reason: 'OUTSIDE_WINDOW', windows: [{ arrival: '2030-01-21', departure: null }],
      message: 'Absence de Lina Haddad le 14/01/2030 : hors de son inscription au groupe « Maths 1B » (à partir du 21/01/2030).'
    };

    /** Feuille d'Amine (présent) et de Lina (absente), validée une première fois. */
    async function validated(): Promise<ComponentFixture<SessionModalComponent>> {
      const fixture = await open();
      await answer(fixture, aRollCall({ students: [aRollCallStudent(), aRollCallStudent({ id: 2, firstName: 'Lina',
        lastName: 'Haddad' })] }));
      fixture.componentInstance.sessionData.students[1].isPresent = false;
      fixture.componentInstance.onValidateSession();
      return fixture;
    }

    it('nomme chaque ligne refusée, sans valider la séance', async () => {
      const fixture = await validated();
      http.expectOne(bulk).flush({ message: 'Validation refusée', errorCode: 'ABSENCE_OUTSIDE_WINDOW', rejected: [lina] },
        { status: 409, statusText: 'Conflict' });

      const tab = await attendancesTab(fixture);
      expect(fixture.componentInstance.rejectedAbsences.length).toBe(1);
      expect(fixture.componentInstance.isFinished).toBeFalsy();
      expect(Array.from(tab.querySelectorAll('.rejected-absences li')).map(li => li.textContent?.trim()))
        .toEqual([lina.message]);
      expect(tab.querySelector('.remove-rejected-btn')).not.toBeNull();
    });

    it('« Retirer ces lignes » retire exactement elles ; la revalidation part sans elles', async () => {
      const fixture = await validated();
      http.expectOne(bulk).flush({ message: 'Validation refusée', errorCode: 'ABSENCE_OUTSIDE_WINDOW', rejected: [lina] },
        { status: 409, statusText: 'Conflict' });
      const tab = await attendancesTab(fixture);

      // Sans rôle administrateur (le cas de ce test), le bouton est désactivé comme la validation.
      expect(tab.querySelector<HTMLButtonElement>('.remove-rejected-btn')!.disabled).toBeTrue();
      fixture.componentInstance.removeRejectedLines();
      fixture.detectChanges();

      expect((fixture.componentInstance.sessionData.students as Student[]).map(s => s.lastName)).toEqual(['Belkacem']);
      expect(fixture.componentInstance.rejectedAbsences).toEqual([]);
      expect((fixture.nativeElement as HTMLElement).querySelector('.rejected-absences')).toBeNull();

      fixture.componentInstance.onValidateSession();
      const again = http.expectOne(bulk);
      expect((again.request.body as { studentId: number }[]).map(line => line.studentId)).toEqual([1]);
      again.flush([]);
      expect(fixture.componentInstance.rejectedAbsences).toEqual([]);
    });

    it('un autre refus s\'affiche tel que le serveur le rédige, sans liste de lignes', async () => {
      const fixture = await validated();
      const snackBar = spyOn(TestBed.inject(MatSnackBar), 'open');
      http.expectOne(bulk).flush({ message: 'Séance d\'une année close : lecture seule.', errorCode: 'CONFLICT' },
        { status: 409, statusText: 'Conflict' });

      expect(fixture.componentInstance.rejectedAbsences).toEqual([]);
      expect(snackBar).toHaveBeenCalledWith('Séance d\'une année close : lecture seule.', 'OK', jasmine.any(Object));
    });
  });

  /**
   * Dévalider une séance validée par erreur (D.3 ; exigence 10.1) : Motif, Aperçu et confirmation
   * par le dialogue commun aux corrections, en un appel serveur. Le défaut corrigé : deux appels sans
   * Motif ni Trace (« séance non terminée », puis « présences désactivées »), puis toute la feuille
   * décochée à l'écran, comme si chacun avait été absent.
   */
  describe('dévalidation (D.3)', () => {
    const reasonsUrl = `${API_BASE_URL}/api/sessions/unvalidation-reasons`;
    let captured: CorrectionDialogData<SessionUnvalidation> | null;
    let dialogOpen: jasmine.Spy;
    let snackBar: jasmine.Spy;

    /** Séance du 07/09/2026 (« Maths 1B ») validée, feuille d'Amine fournie par l'appelant. */
    async function validatedSession(outcome: unknown): Promise<ComponentFixture<SessionModalComponent>> {
      const amine = aStudent({ isPresent: true } as Partial<Student>);
      await setupComponentTestBed(SessionModalComponent, {
        providers: matDialogProviders({ ...aSession({ isFinished: true }), students: [amine] }, createDialogRefSpy())
      });
      const translate = TestBed.inject(TranslateService);
      translate.setTranslation('fr', {
        SESSION_MODAL: {
          UNVALIDATE_SUBJECT: 'Séance du {{day}} ({{group}})',
          UNVALIDATE_SUCCESS: 'Dévalidée : {{count}} ligne(s)',
          UNVALIDATE_ERROR: 'Motifs non chargés'
        }
      });
      translate.use('fr');
      http = TestBed.inject(HttpTestingController);
      const fixture = TestBed.createComponent(SessionModalComponent);
      fixture.detectChanges();
      await fixture.whenStable();
      http.match(req => req.url.includes('/api/attendances/session/')).forEach(req => req.flush([]));
      captured = null;
      dialogOpen = spyOn(fixture.debugElement.injector.get(MatDialog), 'open').and.callFake(
        ((_component: unknown, config?: MatDialogConfig<CorrectionDialogData<SessionUnvalidation>>) => {
          captured = config?.data ?? null;
          return { afterClosed: () => of(outcome) } as MatDialogRef<unknown>;
        }) as never);
      snackBar = spyOn(TestBed.inject(MatSnackBar), 'open');
      return fixture;
    }

    it('ouvre le dialogue des corrections avec les Motifs du serveur, la séance nommée', async () => {
      const fixture = await validatedSession(undefined);

      fixture.componentInstance.onUnvalidateSession();
      http.expectOne(reasonsUrl).flush(['DATA_ENTRY_ERROR', 'OTHER']);

      expect(dialogOpen).toHaveBeenCalledOnceWith(CorrectionDialogComponent, jasmine.any(Object));
      expect(captured!.titleKey).toBe('SESSION_MODAL.UNVALIDATE_TITLE');
      expect(captured!.subject).toBe('Séance du 07/09/2026 (Maths 1B)');
      expect(captured!.reasons).toEqual(['DATA_ENTRY_ERROR', 'OTHER']);
    });

    it('l\'Aperçu et la confirmation passent par la seule adresse de dévalidation, Motif et jeton joints', async () => {
      const fixture = await validatedSession(undefined);
      fixture.componentInstance.onUnvalidateSession();
      http.expectOne(reasonsUrl).flush(['DATA_ENTRY_ERROR', 'OTHER']);

      captured!.run('preview', { type: 'DATA_ENTRY_ERROR' }).subscribe();
      const preview = http.expectOne(`${API_BASE_URL}/api/sessions/100/unvalidate/preview`);
      expect(preview.request.method).toBe('POST');
      expect(preview.request.body).toEqual({ reasonType: 'DATA_ENTRY_ERROR', reasonText: null, previewToken: null });
      preview.flush({});
      captured!.run('confirm', { type: 'OTHER', text: 'Mauvaise séance' }, 'jeton').subscribe();
      expect(http.expectOne(`${API_BASE_URL}/api/sessions/100/unvalidate/confirm`).request.body)
        .toEqual({ reasonType: 'OTHER', reasonText: 'Mauvaise séance', previewToken: 'jeton' });
    });

    it('confirmée : la séance est à valider, la feuille rechargée du serveur, cochée par défaut', async () => {
      const fixture = await validatedSession({ kind: 'confirmed', result: { sessionId: 100, removedLines: 2 } });
      const component = fixture.componentInstance;
      component.rejectedAbsences = [{ studentId: 9 } as RejectedAbsence];

      component.onUnvalidateSession();
      http.expectOne(reasonsUrl).flush(['DATA_ENTRY_ERROR', 'OTHER']);

      expect(component.isFinished).toBeFalse();
      expect(component.sessionData.isFinished).withContext('relu par l\'écran appelant à la fermeture').toBeFalse();
      expect(component.rejectedAbsences).toEqual([]);
      expect(snackBar).toHaveBeenCalledWith('Dévalidée : 2 ligne(s)', jasmine.any(String), jasmine.any(Object));
      http.expectOne(rollCallUrl).flush(aRollCall({ students: [aRollCallStudent(), aRollCallStudent({ id: 2,
        firstName: 'Lina', lastName: 'Haddad' })] }));
      // La feuille rechargée, la lecture des présences part après quelques microtâches.
      await new Promise(resolve => setTimeout(resolve));
      http.expectOne(req => req.url.endsWith('/api/attendances/session/100')).flush([]);

      expect((component.sessionData.students as Student[]).map(s => s.lastName)).toEqual(['Belkacem', 'Haddad']);
      expect(component.sessionData.students.map(s => s.isPresent)).toEqual([true, true]);
      expect(http.match(req => /unfinish|deactivate/.test(req.url)).length)
        .withContext('les anciens raccourcis ne sont plus appelés').toBe(0);
    });

    it('abandonnée : rien ne change, rien n\'est rechargé', async () => {
      const fixture = await validatedSession(undefined);
      const component = fixture.componentInstance;

      component.onUnvalidateSession();
      http.expectOne(reasonsUrl).flush(['DATA_ENTRY_ERROR', 'OTHER']);

      expect(component.isFinished).toBeTrue();
      expect(component.sessionData.isFinished).toBeTrue();
      expect((component.sessionData.students as Student[]).map(s => s.lastName)).toEqual(['Belkacem']);
      expect(http.match(rollCallUrl).length).toBe(0);
      expect(snackBar).not.toHaveBeenCalled();
    });

    it('Motifs illisibles : le dit, sans ouvrir le dialogue', async () => {
      const fixture = await validatedSession(undefined);

      fixture.componentInstance.onUnvalidateSession();
      http.expectOne(reasonsUrl).flush(null, { status: 503, statusText: 'Unavailable' });

      expect(dialogOpen).not.toHaveBeenCalled();
      expect(snackBar).toHaveBeenCalledWith('La dévalidation de la séance n\'a pas pu aboutir', 'OK', jasmine.any(Object));
    });

    it('erreur sans message : le texte de l\'écran', async () => {
      const fixture = await validatedSession(undefined);
      spyOn(TestBed.inject(SessionService), 'getUnvalidationReasons').and.returnValue(throwError(() => new Error('')));

      fixture.componentInstance.onUnvalidateSession();

      expect(snackBar).toHaveBeenCalledWith('Motifs non chargés', 'OK', jasmine.any(Object));
    });
  });

  /**
   * Corriger une séance validée, ligne par ligne (D.7 ; exigences 8 et 9). Séance du 07/09/2026
   * (« Maths 1B ») : Amine présent (ligne 31), Lina absente (32), Nour attendue sans ligne, Sami venu en
   * rattrapage (33). La feuille ne se ressoumet plus : chaque ligne se corrige seule, motif et aperçu.
   */
  describe('corrections d\'une séance validée (D.7)', () => {
    const reasonsUrl = `${API_BASE_URL}/api/attendances/correction-reasons`;
    const lines = [
      { id: 31, studentId: 1, isPresent: true, isJustified: false, isCatchUp: false },
      { id: 32, studentId: 2, isPresent: false, isJustified: false, isCatchUp: false },
      { id: 33, studentId: 4, isPresent: true, isJustified: false, isCatchUp: true, catchUpBillingState: 'HOST_BILLED' }
    ];
    let component: SessionModalComponent;
    let dialogOpen: jasmine.Spy;
    let opened: { component: unknown; data: unknown }[];
    let outcomes: unknown[];

    /** La feuille validée chargée ; `admin` faux : un lecteur, sans droit d'écriture. */
    async function validatedSheet(admin = true): Promise<ComponentFixture<SessionModalComponent>> {
      await setupComponentTestBed(SessionModalComponent, {
        providers: matDialogProviders({ ...aSession({ isFinished: true }), students: [] }, createDialogRefSpy())
      });
      spyOn(TestBed.inject(AuthService), 'hasRole').and.returnValue(admin);
      const translate = TestBed.inject(TranslateService);
      translate.setTranslation('fr', {
        SESSION_MODAL: {
          SESSION_LABEL: 'Séance du {{day}} ({{group}})',
          CORRECTION: {
            NO_LINE: 'Aucune ligne',
            SUBJECT: '{{session}} — {{student}} : {{change}}',
            LINE_REMOVED: '{{state}} → ligne retirée',
            CATCH_UP_REMOVED: 'rattrapage retiré',
            LINE_ADDED: 'ajout, {{state}}',
            STATE_INLINE: { PRESENT: 'présent', ABSENT: 'absent', JUSTIFIED: 'absent (justifié)' },
            DONE: 'Correction enregistrée',
            REASONS_ERROR: 'Motifs non chargés'
          }
        },
        common: { close: 'Fermer' }
      });
      translate.use('fr');
      http = TestBed.inject(HttpTestingController);
      const fixture = TestBed.createComponent(SessionModalComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      await loadSheet(fixture);
      opened = [];
      outcomes = [];
      dialogOpen = spyOn(fixture.debugElement.injector.get(MatDialog), 'open').and.callFake(
        ((dialogComponent: unknown, config?: MatDialogConfig) => {
          opened.push({ component: dialogComponent, data: config?.data });
          return { afterClosed: () => of(outcomes.shift()) } as MatDialogRef<unknown>;
        }) as never);
      return fixture;
    }

    /** La feuille d'appel, puis ses lignes, puis l'élève venu d'ailleurs, lu à part. */
    async function loadSheet(fixture: ComponentFixture<SessionModalComponent>): Promise<void> {
      http.expectOne(rollCallUrl).flush(aRollCall({ students: [
        aRollCallStudent(),
        aRollCallStudent({ id: 2, firstName: 'Lina', lastName: 'Haddad' }),
        aRollCallStudent({ id: 3, firstName: 'Nour', lastName: 'Zerrouki' })
      ] }));
      await new Promise(resolve => setTimeout(resolve));
      http.expectOne(req => req.url.endsWith('/api/attendances/session/100')).flush(lines);
      http.expectOne(req => req.url.endsWith('/api/students/id/4')).flush(aStudent({ id: 4, firstName: 'Sami',
        lastName: 'Kaci' }));
      fixture.detectChanges();
    }

    const row = (lastName: string): Student =>
      (component.sessionData.students as Student[]).find(student => student.lastName === lastName)!;

    /** Le dialogue des corrections, ouvert après lecture des Motifs. */
    function correctionDialog(): CorrectionDialogData<unknown> {
      const call = opened.find(entry => entry.component === CorrectionDialogComponent);
      return call!.data as CorrectionDialogData<unknown>;
    }

    it('chaque ligne dit ce qu\'elle permet : présence, absence, rattrapage, ou aucune ligne', async () => {
      const fixture = await validatedSheet();

      expect(component.lineKind(row('Belkacem'))).toBe('present');
      expect(component.lineKind(row('Haddad'))).toBe('absent');
      expect(component.lineKind(row('Zerrouki'))).toBe('missing');
      expect(component.lineKind(row('Kaci'))).toBe('catchUp');
      expect(row('Belkacem').attendanceId).toBe(31);
      expect(row('Kaci').attendanceId).toBe(33);
      const tab = await attendancesTab(fixture);
      const items = Array.from(tab.querySelectorAll('.student-list-item'));
      expect(items.map(item => item.querySelectorAll('.correct-line-btn').length)).toEqual([1, 1, 1, 1]);
      const nour = items.find(item => item.textContent?.includes('Zerrouki'))!;
      expect(nour.querySelector('.no-line-label')?.textContent?.trim()).toBe('Aucune ligne');
      expect(nour.querySelector('.student-checkbox mat-checkbox, mat-checkbox.student-checkbox')).toBeNull();
      expect(tab.querySelector<HTMLButtonElement>('.add-student-btn')!.disabled).toBeFalse();
    });

    it('le menu de chaque ligne propose ce que la ligne permet, et le fait', async () => {
      const fixture = await validatedSheet();
      const change = spyOn(component, 'changeLine');
      const remove = spyOn(component, 'removeLine');
      const add = spyOn(component, 'addLine');
      const justify = spyOn(component, 'editJustification');
      const tab = await attendancesTab(fixture);

      /** Ouvre le menu de la ligne ; rend ses entrées, par classe. */
      async function menu(lastName: string): Promise<HTMLButtonElement[]> {
        const item = Array.from(tab.querySelectorAll('.student-list-item'))
          .find(candidate => candidate.textContent?.includes(lastName))!;
        (item.querySelector('.correct-line-btn') as HTMLButtonElement).click();
        fixture.detectChanges();
        await fixture.whenStable();
        return Array.from(document.querySelectorAll<HTMLButtonElement>('.mat-mdc-menu-panel button[mat-menu-item]'));
      }
      const classes = (buttons: HTMLButtonElement[]): string[] =>
        buttons.map(button => ['note-absent', 'note-justified', 'note-present', 'edit-justification', 'remove-line',
          'remove-catch-up', 'add-present', 'add-absent', 'add-justified'].find(name => button.classList.contains(name))!);

      let items = await menu('Belkacem');
      expect(classes(items)).toEqual(['note-absent', 'note-justified', 'remove-line']);
      items[1].click();
      expect(change).toHaveBeenCalledWith(row('Belkacem'), { present: false, justified: true });

      items = await menu('Haddad');
      expect(classes(items)).toEqual(['note-present', 'edit-justification', 'remove-line']);
      items[0].click();
      expect(change).toHaveBeenCalledWith(row('Haddad'), { present: true, justified: false });
      items = await menu('Haddad');
      items[1].click();
      expect(justify).toHaveBeenCalledWith(row('Haddad'));

      items = await menu('Kaci');
      expect(classes(items)).toEqual(['remove-catch-up']);
      items[0].click();
      expect(remove).toHaveBeenCalledWith(row('Kaci'));

      items = await menu('Zerrouki');
      expect(classes(items)).toEqual(['add-present', 'add-absent', 'add-justified']);
      items[2].click();
      expect(add).toHaveBeenCalledWith(row('Zerrouki'), { present: false, justified: true });
      items = await menu('Belkacem');
      items[0].click();
      expect(change).toHaveBeenCalledWith(row('Belkacem'), { present: false, justified: false });
      items = await menu('Belkacem');
      items[2].click();
      expect(remove).toHaveBeenCalledWith(row('Belkacem'));
    });

    it('un lecteur ne voit aucun bouton de correction', async () => {
      const fixture = await validatedSheet(false);

      expect((await attendancesTab(fixture)).querySelectorAll('.correct-line-btn').length).toBe(0);
    });

    it('séance non validée : seul un rattrapage déjà enregistré se retire', async () => {
      await validatedSheet();
      component.isFinished = false;

      expect(component.lineKind(row('Belkacem'))).toBeNull();
      expect(component.lineKind(row('Zerrouki'))).toBeNull();
      expect(component.lineKind(row('Kaci'))).toBe('catchUp');
    });

    it('noter absent (justifié) : l\'Aperçu commun, la ligne nommée, l\'adresse de la correction', async () => {
      await validatedSheet();
      outcomes.push(undefined);

      component.changeLine(row('Belkacem'), { present: false, justified: true });
      http.expectOne(reasonsUrl).flush(['DATA_ENTRY_ERROR', 'DOCUMENT_RECEIVED', 'OTHER']);

      const data = correctionDialog();
      expect(data.titleKey).toBe('SESSION_MODAL.CORRECTION.TITLE_CHANGE');
      expect(data.subject).toBe('Séance du 07/09/2026 (Maths 1B) — Amine Belkacem : présent → absent (justifié)');
      expect(data.reasons).toEqual(['DATA_ENTRY_ERROR', 'DOCUMENT_RECEIVED', 'OTHER']);
      data.run('preview', { type: 'DOCUMENT_RECEIVED' }).subscribe();
      expect(http.expectOne(`${API_BASE_URL}/api/attendances/31/correct/preview`).request.body).toEqual({
        present: false, justified: true, reasonType: 'DOCUMENT_RECEIVED', reasonText: null, previewToken: null });
      expect(http.match(rollCallUrl).length).withContext('abandonnée : rien n\'est relu').toBe(0);
    });

    it('absence justifiée : dite telle quelle, qu\'on la note présent ou qu\'on la retire', async () => {
      await validatedSheet();
      row('Haddad').isJustified = true;
      outcomes.push(undefined, undefined);

      component.changeLine(row('Haddad'), { present: true, justified: false });
      http.expectOne(reasonsUrl).flush(['DATA_ENTRY_ERROR']);
      expect(correctionDialog().subject)
        .toBe('Séance du 07/09/2026 (Maths 1B) — Lina Haddad : absent (justifié) → présent');
      opened = [];
      component.removeLine(row('Haddad'));
      expect(correctionDialog().subject)
        .toBe('Séance du 07/09/2026 (Maths 1B) — Lina Haddad : absent (justifié) → ligne retirée');
    });

    it('noter présent une absence ; les Motifs ne sont lus qu\'une fois', async () => {
      await validatedSheet();
      outcomes.push(undefined, undefined);

      component.changeLine(row('Haddad'), { present: true, justified: false });
      http.expectOne(reasonsUrl).flush(['DATA_ENTRY_ERROR']);
      expect(correctionDialog().subject).toBe('Séance du 07/09/2026 (Maths 1B) — Lina Haddad : absent → présent');
      component.removeLine(row('Belkacem'));

      expect(http.match(reasonsUrl).length).toBe(0);
      expect(opened.filter(entry => entry.component === CorrectionDialogComponent).length).toBe(2);
    });

    it('retirer une ligne, retirer un rattrapage : chacun son titre, la même adresse', async () => {
      await validatedSheet();
      outcomes.push(undefined, undefined);

      component.removeLine(row('Haddad'));
      http.expectOne(reasonsUrl).flush(['DATA_ENTRY_ERROR']);
      expect(correctionDialog().titleKey).toBe('SESSION_MODAL.CORRECTION.TITLE_REMOVE');
      expect(correctionDialog().subject).toBe('Séance du 07/09/2026 (Maths 1B) — Lina Haddad : absent → ligne retirée');
      correctionDialog().run('confirm', { type: 'DATA_ENTRY_ERROR' }, 'jeton').subscribe();
      expect(http.expectOne(`${API_BASE_URL}/api/attendances/32/remove/confirm`).request.body)
        .toEqual({ reasonType: 'DATA_ENTRY_ERROR', reasonText: null, previewToken: 'jeton' });

      opened = [];
      component.removeLine(row('Kaci'));
      expect(correctionDialog().titleKey).toBe('SESSION_MODAL.CORRECTION.TITLE_CATCH_UP');
      expect(correctionDialog().subject).toBe('Séance du 07/09/2026 (Maths 1B) — Sami Kaci : rattrapage retiré');
      correctionDialog().run('preview', { type: 'DATA_ENTRY_ERROR' }).subscribe();
      http.expectOne(`${API_BASE_URL}/api/attendances/33/remove/preview`);
    });

    it('ajouter la ligne d\'un élève attendu : la séance et l\'élève dans l\'appel', async () => {
      await validatedSheet();
      outcomes.push(undefined);

      component.addLine(row('Zerrouki'), { present: false, justified: false });
      http.expectOne(reasonsUrl).flush(['DATA_ENTRY_ERROR']);

      expect(correctionDialog().titleKey).toBe('SESSION_MODAL.CORRECTION.TITLE_ADD');
      expect(correctionDialog().subject).toBe('Séance du 07/09/2026 (Maths 1B) — Nour Zerrouki : ajout, absent');
      correctionDialog().run('preview', { type: 'DATA_ENTRY_ERROR' }).subscribe();
      expect(http.expectOne(`${API_BASE_URL}/api/sessions/100/attendances/add/preview`).request.body).toEqual({
        studentId: 3, present: false, justified: false, reasonType: 'DATA_ENTRY_ERROR', reasonText: null,
        previewToken: null });
    });

    it('confirmée : le dit, puis relit la feuille du serveur', async () => {
      const fixture = await validatedSheet();
      const snackBar = spyOn(TestBed.inject(MatSnackBar), 'open');
      outcomes.push({ kind: 'confirmed', result: { attendanceId: 31 } });

      component.changeLine(row('Belkacem'), { present: false, justified: false });
      http.expectOne(reasonsUrl).flush(['DATA_ENTRY_ERROR']);

      expect(snackBar).toHaveBeenCalledWith('Correction enregistrée', 'Fermer', jasmine.any(Object));
      expect(component.sessionData.students).withContext('feuille vidée avant relecture').toEqual([]);
      await loadSheet(fixture);
      expect(row('Belkacem').attendanceId).toBe(31);
    });

    it('Motifs illisibles : le dit, sans ouvrir le dialogue', async () => {
      await validatedSheet();
      const snackBar = spyOn(TestBed.inject(MatSnackBar), 'open');

      component.removeLine(row('Belkacem'));
      http.expectOne(reasonsUrl).flush(null, { status: 503, statusText: 'Unavailable' });

      expect(dialogOpen).not.toHaveBeenCalled();
      expect(snackBar).toHaveBeenCalledWith('La correction de la présence n\'a pas pu aboutir', 'OK', jasmine.any(Object));
    });

    it('erreur sans message : le texte de l\'écran', async () => {
      await validatedSheet();
      const snackBar = spyOn(TestBed.inject(MatSnackBar), 'open');
      spyOn(TestBed.inject(AttendanceService), 'getCorrectionReasons').and.returnValue(throwError(() => new Error('')));

      component.removeLine(row('Belkacem'));

      expect(snackBar).toHaveBeenCalledWith('Motifs non chargés', 'OK', jasmine.any(Object));
    });

    it('justification d\'une absence : son dialogue, avec la ligne ; relue seulement si elle a changé', async () => {
      const fixture = await validatedSheet();
      outcomes.push(undefined, { attendanceId: 32, justified: true });

      component.editJustification(row('Haddad'));
      expect(opened[0].component).toBe(JustificationEditDialogComponent);
      expect(opened[0].data).toEqual(jasmine.objectContaining({ attendanceId: 32, justified: false,
        sessionName: 'Maths 1B' }));
      expect(http.match(rollCallUrl).length).toBe(0);

      component.editJustification(row('Haddad'));
      expect(component.sessionData.students).toEqual([]);
      await loadSheet(fixture);
    });

    it('ajouter un élève à une séance validée : l\'élève, l\'état de sa ligne, puis la correction', async () => {
      const fixture = await validatedSheet();
      const groups = fixture.debugElement.injector.get(GroupService);
      spyOn(groups, 'getLevelIdByGroupId').and.returnValue(of(3));
      spyOn(groups, 'getStudentsByGroupId').and.returnValue(of([aStudent({ id: 5 })]));
      const yanis = aStudent({ id: 5, firstName: 'Yanis', lastName: 'Amrani' });
      outcomes.push(yanis, { present: true, justified: false }, undefined);

      component.openAddStudentDialog();
      http.expectOne(reasonsUrl).flush(['DATA_ENTRY_ERROR']);

      expect(opened.map(entry => entry.component)).toEqual([AddStudentDialogComponent, AttendanceStateDialogComponent,
        CorrectionDialogComponent]);
      expect(opened[0].data).toEqual(jasmine.objectContaining({ levelId: 3, groupMemberIds: [5],
        existingStudentIds: [1, 2, 3, 4] }));
      expect(opened[1].data).toEqual({ studentName: 'Yanis Amrani', session: 'Séance du 07/09/2026 (Maths 1B)' });
      expect(correctionDialog().subject).toBe('Séance du 07/09/2026 (Maths 1B) — Yanis Amrani : ajout, présent');
      correctionDialog().run('preview', { type: 'DATA_ENTRY_ERROR' }).subscribe();
      expect((http.expectOne(`${API_BASE_URL}/api/sessions/100/attendances/add/preview`).request.body as
        { studentId: number }).studentId).toBe(5);
    });

    it('ajout abandonné au choix de l\'élève ou de son état : aucune correction', async () => {
      const fixture = await validatedSheet();
      const groups = fixture.debugElement.injector.get(GroupService);
      spyOn(groups, 'getLevelIdByGroupId').and.returnValue(of(3));
      spyOn(groups, 'getStudentsByGroupId').and.returnValue(of([]));
      outcomes.push(null, aStudent({ id: 5 }), undefined);

      component.openAddStudentDialog();
      component.openAddStudentDialog();

      expect(opened.map(entry => entry.component)).toEqual([AddStudentDialogComponent, AddStudentDialogComponent,
        AttendanceStateDialogComponent]);
      expect(http.match(reasonsUrl).length).toBe(0);
    });
  });

  it('étudiants déjà fournis par l\'appelant : la feuille n\'est pas redemandée', async () => {
    const fixture = await open([aStudent()]);
    await fixture.whenStable();
    http.match(req => req.url.includes('/api/attendances/session/')).forEach(req => req.flush([]));

    expect(http.match(rollCallUrl).length).toBe(0);
    expect(fixture.componentInstance.rollCall).toBeNull();
  });
});
