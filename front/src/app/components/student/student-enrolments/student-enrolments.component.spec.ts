import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { StudentEnrolmentsComponent } from './student-enrolments.component';
import { setupComponentTestBed } from '../../../../testing/setup';
import { EnrolmentService } from '../../../services/enrolment.service';
import { EnrolmentCorrectionFlow } from '../../shared/enrolment-correction/enrolment-correction-flow';
import { Enrolment, EnrolmentCorrection } from '../../../models/enrolment/enrolment';

/**
 * Inscriptions d'un élève sur sa fiche (C.8 ; exigences 5 et 6) : arrivée et départ de chacune,
 * closes comprises, et les corrections que l'état de chacune permet.
 */
describe('StudentEnrolmentsComponent', () => {
  let fixture: ComponentFixture<StudentEnrolmentsComponent>;
  let component: StudentEnrolmentsComponent;
  let enrolments: jasmine.SpyObj<EnrolmentService>;
  let flow: jasmine.SpyObj<EnrolmentCorrectionFlow>;

  const math: Enrolment = {
    id: 7, studentId: 1, groupId: 5, groupName: 'Math 1ère A', schoolYearId: 3,
    arrival: '2029-09-01', departure: '2029-11-30', active: false
  };
  const mathAgain: Enrolment = { ...math, id: 8, arrival: '2030-01-06', departure: null, active: true };
  const result: EnrolmentCorrection = {
    enrolmentId: 8, studentId: 1, groupId: 5, arrival: '2030-01-06', departure: '2030-02-04', active: false
  };

  beforeEach(async () => {
    enrolments = jasmine.createSpyObj<EnrolmentService>('EnrolmentService', ['getStudentEnrolments']);
    enrolments.getStudentEnrolments.and.returnValue(of([math, mathAgain]));
    flow = jasmine.createSpyObj<EnrolmentCorrectionFlow>('EnrolmentCorrectionFlow', ['correct']);
    await setupComponentTestBed(StudentEnrolmentsComponent, { providers: [
      { provide: EnrolmentService, useValue: enrolments },
      { provide: EnrolmentCorrectionFlow, useValue: flow }
    ] });
    fixture = TestBed.createComponent(StudentEnrolmentsComponent);
    component = fixture.componentInstance;
  });

  function show(canCorrect: boolean): HTMLElement {
    fixture.componentRef.setInput('studentId', 1);
    fixture.componentRef.setInput('schoolYearId', 3);
    fixture.componentRef.setInput('studentName', 'Amine Belkacem');
    fixture.componentRef.setInput('canCorrect', canCorrect);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const items = (element: HTMLElement): HTMLElement[] => Array.from(element.querySelectorAll<HTMLElement>('.en-item'));
  const buttons = (item: HTMLElement): string[] =>
    Array.from(item.querySelectorAll('.en-action')).map(button => button.className.match(/en-(arrival|departure|reopen)/)![1]);

  it('lit les inscriptions de l\'élève sur l\'année affichée', () => {
    show(false);

    expect(enrolments.getStudentEnrolments).toHaveBeenCalledWith(1, 3);
    expect(component.enrolments).toEqual([math, mathAgain]);
  });

  it('affiche arrivée et départ en jours, et le statut en toutes lettres', () => {
    const [closed, open] = items(show(false));

    expect(closed.textContent).toContain('Math 1ère A');
    expect(closed.textContent).toContain('enrolment.list.closed');
    expect(closed.classList).toContain('en-item--closed');
    expect(open.textContent).toContain('enrolment.list.open');
    expect(open.querySelector('.en-period')!.textContent).not.toContain('enrolment.list.departure');
    expect(component.formatDay(math.departure)).toBe('30/11/2029');
  });

  it('actions selon l\'état : ouverte → arrivée, départ ; close → arrivée, départ, rouvrir', () => {
    const [closed, open] = items(show(true));

    expect(buttons(open)).toEqual(['arrival', 'departure']);
    expect(buttons(closed)).toEqual(['arrival', 'departure', 'reopen']);
    expect(open.querySelector('.en-departure')!.textContent).toContain('enrolment.list.recordDeparture');
    expect(closed.querySelector('.en-departure')!.textContent).toContain('enrolment.list.correctDeparture');
  });

  it('sans droit de correction (VIEWER, année close) : aucune action', () => {
    const element = show(false);

    expect(element.querySelectorAll('.en-action').length).toBe(0);
    component.correct('ARRIVAL', mathAgain);
    expect(flow.correct).not.toHaveBeenCalled();
  });

  it('une correction confirmée est annoncée, la liste relue, la fiche prévenue', () => {
    flow.correct.and.returnValue(of(result));
    const corrected = jasmine.createSpy('corrected');
    component.corrected.subscribe(corrected);
    show(true);

    component.correct('DEPARTURE', mathAgain);

    expect(flow.correct).toHaveBeenCalledWith('DEPARTURE', mathAgain, 'Amine Belkacem');
    expect(enrolments.getStudentEnrolments).toHaveBeenCalledTimes(2);
    expect(corrected).toHaveBeenCalledWith(result);
    expect(component.correctingId).toBeNull();
  });

  it('une correction abandonnée ne relit rien et ne prévient personne', () => {
    flow.correct.and.returnValue(of(null));
    const corrected = jasmine.createSpy('corrected');
    component.corrected.subscribe(corrected);
    show(true);

    component.correct('REOPEN', math);

    expect(enrolments.getStudentEnrolments).toHaveBeenCalledTimes(1);
    expect(corrected).not.toHaveBeenCalled();
    expect(component.correctingId).toBeNull();
  });

  it('une seule correction à la fois', () => {
    show(true);
    component.correctingId = 7;

    component.correct('ARRIVAL', mathAgain);

    expect(flow.correct).not.toHaveBeenCalled();
  });

  it('un échec des Motifs libère les boutons', () => {
    flow.correct.and.returnValue(throwError(() => new Error('Serveur injoignable')));
    show(true);

    component.correct('ARRIVAL', mathAgain);

    expect(component.correctingId).toBeNull();
  });

  it('échec de lecture : dit comme tel, et non comme une liste vide', () => {
    enrolments.getStudentEnrolments.and.returnValue(throwError(() => new Error('Serveur injoignable')));
    const element = show(false);

    expect(element.querySelector('.en-error')!.textContent).toContain('Serveur injoignable');
    expect(element.querySelector('.pf-empty')).toBeNull();
  });

  it('aucune inscription : dit en clair ; sans élève, rien n\'est lu', () => {
    enrolments.getStudentEnrolments.and.returnValue(of([]));
    expect(show(false).querySelector('.pf-empty')!.textContent).toContain('enrolment.list.empty');

    fixture.componentRef.setInput('studentId', null);
    fixture.detectChanges();
    expect(component.enrolments).toEqual([]);
    expect(enrolments.getStudentEnrolments).toHaveBeenCalledTimes(1);
  });
});
