import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, throwError } from 'rxjs';

import { GroupProfileComponent } from './group-profile.component';
import { activatedRouteProviders, setupComponentTestBed } from '../../../../testing/setup';
import { aGroup, aStudent } from '../../../../testing/fixtures';
import { EnrolmentService } from '../../../services/enrolment.service';
import { GroupService } from '../../../services/group.service';
import { EnrolmentCorrectionFlow } from '../../shared/enrolment-correction/enrolment-correction-flow';
import { Enrolment } from '../../../models/enrolment/enrolment';

/**
 * Fiche d'un groupe, ouverte sur l'identifiant porté par l'URL.
 *
 * <p>Les chargements déclenchés par `ngOnInit` sont laissés en attente : ce qui est vérifié
 * est que la fiche se construit sur l'identifiant de la route et n'affiche, avant réponse,
 * ni photo cassée ni signalement d'année passée — deux états qui induiraient en erreur.</p>
 */
describe('GroupProfileComponent', () => {
  let component: GroupProfileComponent;
  let fixture: ComponentFixture<GroupProfileComponent>;

  beforeEach(async () => {
    await setupComponentTestBed(GroupProfileComponent, {
      providers: activatedRouteProviders({ id: '5' })
    });
    fixture = TestBed.createComponent(GroupProfileComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('n\'expose aucune URL de photo avant le chargement du groupe', () => {
    // Une URL construite sur un groupe encore inconnu produirait une image cassée.
    expect(component.groupPhotoUrl).toBe('');
  });

  it('ne signale pas d\'année passée par défaut', () => {
    // Le gel des modifications ne doit pas s'appliquer par défaut, sans quoi la fiche
    // s'ouvrirait en lecture seule sur un groupe de l'année courante.
    component.groupIsPastYear$.subscribe(isPastYear => expect(isPastYear).toBeFalse());
  });

  /**
   * « Enregistrer le départ » remplace le retrait sec (C.8 ; exigence 6.1) : l'inscription ouverte
   * de l'élève dans ce groupe est relue, puis la correction passe par une date, un Motif et un
   * Aperçu.
   */
  describe('départ d\'un élève', () => {
    let enrolments: EnrolmentService;
    let flow: EnrolmentCorrectionFlow;
    let snackBar: jasmine.Spy;
    const open: Enrolment = {
      id: 8, studentId: 1, groupId: 5, groupName: 'Maths 1B', schoolYearId: 3,
      arrival: '2029-09-01', departure: null, active: true
    };

    beforeEach(() => {
      component.group = aGroup({ id: 5, schoolYearId: 3 });
      enrolments = TestBed.inject(EnrolmentService);
      flow = TestBed.inject(EnrolmentCorrectionFlow);
      snackBar = spyOn(TestBed.inject(MatSnackBar), 'open');
      spyOn(fixture.debugElement.injector.get(GroupService), 'getStudentsByGroupId').and.returnValue(of([]));
    });

    it('corrige l\'inscription ouverte de ce groupe, et non une close ni celle d\'un autre groupe', () => {
      spyOn(enrolments, 'getStudentEnrolments').and.returnValue(of([
        { ...open, id: 6, departure: '2029-11-30', active: false },
        { ...open, id: 7, groupId: 9, groupName: 'Physique 1B' },
        open
      ]));
      const correct = spyOn(flow, 'correct').and.returnValue(of({
        enrolmentId: 8, studentId: 1, groupId: 5, arrival: '2029-09-01', departure: '2030-01-14', active: false }));

      component.recordDeparture(aStudent({ id: 1, firstName: 'Amine', lastName: 'Belkacem' }));

      expect(enrolments.getStudentEnrolments).toHaveBeenCalledWith(1, 3);
      expect(correct).toHaveBeenCalledWith('DEPARTURE', open, 'Amine Belkacem');
      expect(snackBar).toHaveBeenCalledWith('enrolment.correction.DEPARTURE.done', 'Fermer', jasmine.any(Object));
    });

    it('départ abandonné : rien n\'est annoncé', () => {
      spyOn(enrolments, 'getStudentEnrolments').and.returnValue(of([open]));
      spyOn(flow, 'correct').and.returnValue(of(null));

      component.recordDeparture(aStudent({ id: 1 }));

      expect(snackBar).not.toHaveBeenCalled();
    });

    it('plus d\'inscription ouverte : le dit, et relit la liste', () => {
      spyOn(enrolments, 'getStudentEnrolments').and.returnValue(of([{ ...open, departure: '2029-11-30', active: false }]));
      const correct = spyOn(flow, 'correct');

      component.recordDeparture(aStudent({ id: 1 }));

      expect(correct).not.toHaveBeenCalled();
      expect(snackBar).toHaveBeenCalledWith('enrolment.departure.notEnrolled', 'Fermer', jasmine.any(Object));
    });

    it('un échec de lecture est dit tel quel ; sans groupe ni élève, rien n\'est lu', () => {
      const read = spyOn(enrolments, 'getStudentEnrolments')
        .and.returnValue(throwError(() => new Error('Serveur injoignable')));

      component.recordDeparture(aStudent({ id: 1 }));
      expect(snackBar).toHaveBeenCalledWith('Serveur injoignable', 'Fermer', jasmine.any(Object));

      component.recordDeparture(aStudent({ id: undefined }));
      component.group = undefined as never;
      component.recordDeparture(aStudent({ id: 1 }));
      expect(read).toHaveBeenCalledTimes(1);
    });
  });
});
