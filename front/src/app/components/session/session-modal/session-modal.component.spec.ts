import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpRequest } from '@angular/common/http';
import { HttpTestingController } from '@angular/common/http/testing';
import { By } from '@angular/platform-browser';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTabGroup } from '@angular/material/tabs';
import { TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';

import { SessionModalComponent } from './session-modal.component';
import { createDialogRefSpy, DialogRefSpy, matDialogProviders, setupComponentTestBed } from '../../../../testing/setup';
import { aSession, aStudent } from '../../../../testing/fixtures';
import { RollCall, RollCallStudent } from '../../../models/session/roll-call';
import { Student } from '../../student/domain/student';

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

  it('étudiants déjà fournis par l\'appelant : la feuille n\'est pas redemandée', async () => {
    const fixture = await open([aStudent()]);
    await fixture.whenStable();
    http.match(req => req.url.includes('/api/attendances/session/')).forEach(req => req.flush([]));

    expect(http.match(rollCallUrl).length).toBe(0);
    expect(fixture.componentInstance.rollCall).toBeNull();
  });
});
