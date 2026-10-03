import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { EnrolmentCorrectionFlow } from './enrolment-correction-flow';
import { EnrolmentDateDialogComponent, EnrolmentDateDialogData } from './enrolment-date-dialog/enrolment-date-dialog.component';
import { CorrectionDialogComponent, CorrectionDialogData } from '../correction-dialog/correction-dialog.component';
import { setupServiceTestBed } from '../../../../testing/setup';
import { EnrolmentService } from '../../../services/enrolment.service';
import { Enrolment, EnrolmentCorrection, EnrolmentDateChange } from '../../../models/enrolment/enrolment';

/**
 * L'enchaînement des dialogues d'une correction d'inscription (C.8) : saisie de la date, Aperçu,
 * « Modifier » qui ramène à la saisie sans rien perdre, et la bonne correction appelée au serveur.
 */
describe('EnrolmentCorrectionFlow', () => {
  let flow: EnrolmentCorrectionFlow;
  let dialog: jasmine.SpyObj<MatDialog>;
  let enrolments: jasmine.SpyObj<EnrolmentService>;
  /** Issues des dialogues ouverts, dans l'ordre. */
  let closings: unknown[];

  const enrolment: Enrolment = {
    id: 7, studentId: 1, groupId: 5, groupName: 'Math 1ère A', schoolYearId: 3,
    arrival: '2029-09-01', departure: null, active: true
  };
  const result: EnrolmentCorrection = {
    enrolmentId: 7, studentId: 1, groupId: 5, arrival: '2029-09-01', departure: '2030-01-14', active: false
  };
  const departure: EnrolmentDateChange = { kind: 'DEPARTURE', date: '2030-01-14', removePresencesAfter: true };

  beforeEach(() => {
    closings = [];
    dialog = jasmine.createSpyObj<MatDialog>('MatDialog', ['open']);
    dialog.open.and.callFake((() => ({ afterClosed: () => of(closings.shift()) })) as never);
    enrolments = jasmine.createSpyObj<EnrolmentService>('EnrolmentService',
      ['getCorrectionReasons', 'correctArrival', 'setDeparture', 'reopen']);
    enrolments.getCorrectionReasons.and.returnValue(of({
      ARRIVAL: ['ARRIVAL_DATE_CORRECTED', 'DATA_ENTRY_ERROR', 'OTHER'],
      DEPARTURE: ['STUDENT_LEFT', 'DATA_ENTRY_ERROR', 'OTHER'],
      REOPEN: ['DATA_ENTRY_ERROR', 'OTHER']
    }));
    setupServiceTestBed({ providers: [
      { provide: MatDialog, useValue: dialog },
      { provide: EnrolmentService, useValue: enrolments }
    ] });
    flow = TestBed.inject(EnrolmentCorrectionFlow);
  });

  const opened = (index: number): { component: unknown; data: unknown } => {
    const [component, config] = dialog.open.calls.argsFor(index) as [unknown, { data: unknown }];
    return { component, data: config.data };
  };
  const correctionData = (index: number): CorrectionDialogData<EnrolmentCorrection> =>
    opened(index).data as CorrectionDialogData<EnrolmentCorrection>;

  it('départ : saisie, puis Aperçu aux Motifs du départ ; l\'issue confirmée est rendue', () => {
    closings.push(departure, { kind: 'confirmed', result });
    let outcome: EnrolmentCorrection | null | undefined;

    flow.correct('DEPARTURE', enrolment, 'Amine Belkacem').subscribe(value => outcome = value);

    expect(opened(0).component).toBe(EnrolmentDateDialogComponent);
    expect((opened(0).data as EnrolmentDateDialogData).kind).toBe('DEPARTURE');
    expect(opened(1).component).toBe(CorrectionDialogComponent);
    const data = correctionData(1);
    expect(data.titleKey).toBe('enrolment.correction.DEPARTURE.title');
    expect(data.reasons).toEqual(['STUDENT_LEFT', 'DATA_ENTRY_ERROR', 'OTHER']);
    expect(data.allowBack).toBeTrue();
    expect(data.subject).toBe('enrolment.correction.subject');
    expect(outcome).toEqual(result);
  });

  it('l\'Aperçu appelle le départ avec la date, le retrait demandé, le Motif, le jeton et les présences', () => {
    closings.push(departure, undefined);
    enrolments.setDeparture.and.returnValue(of({ preview: { series: [], effects: [], amountsUnchanged: true },
      previewToken: 't', result: null }));
    flow.correct('DEPARTURE', enrolment, 'Amine Belkacem').subscribe();

    correctionData(1).run('confirm', { type: 'STUDENT_LEFT' }, 't', { 41: true }).subscribe();

    expect(enrolments.setDeparture).toHaveBeenCalledWith(7, 'confirm', '2030-01-14', true, { type: 'STUDENT_LEFT' },
      't', { 41: true });
  });

  it('« Modifier » rouvre la saisie avec ce qui avait été tapé', () => {
    closings.push(departure, { kind: 'back' }, undefined);
    let outcome: EnrolmentCorrection | null | undefined;

    flow.correct('DEPARTURE', enrolment, 'Amine Belkacem').subscribe(value => outcome = value);

    expect(dialog.open).toHaveBeenCalledTimes(3);
    expect(opened(2).component).toBe(EnrolmentDateDialogComponent);
    expect((opened(2).data as EnrolmentDateDialogData).previous).toEqual(departure);
    expect(outcome).toBeNull();
  });

  it('arrivée : la correction d\'arrivée est appelée, titre et Motifs de l\'arrivée', () => {
    closings.push({ kind: 'ARRIVAL', date: '2030-01-05', removePresencesAfter: false }, undefined);
    enrolments.correctArrival.and.returnValue(of({ preview: { series: [], effects: [], amountsUnchanged: true },
      previewToken: 't', result: null }));
    flow.correct('ARRIVAL', enrolment, 'Amine Belkacem').subscribe();

    const data = correctionData(1);
    data.run('preview', { type: 'DATA_ENTRY_ERROR' }).subscribe();

    expect(data.titleKey).toBe('enrolment.correction.ARRIVAL.title');
    expect(data.reasons).toEqual(['ARRIVAL_DATE_CORRECTED', 'DATA_ENTRY_ERROR', 'OTHER']);
    expect(enrolments.correctArrival).toHaveBeenCalledWith(7, 'preview', '2030-01-05', { type: 'DATA_ENTRY_ERROR' },
      undefined, undefined);
  });

  it('départ déjà enregistré : titre « corriger le départ »', () => {
    closings.push(departure, undefined);

    flow.correct('DEPARTURE', { ...enrolment, departure: '2030-01-07', active: false }, 'Amine Belkacem').subscribe();

    expect(correctionData(1).titleKey).toBe('enrolment.correction.DEPARTURE.correctTitle');
  });

  it('réouverture : Aperçu direct, sans saisie ni « Modifier »', () => {
    closings.push({ kind: 'confirmed', result: { ...result, departure: null, active: true } });
    enrolments.reopen.and.returnValue(of({ preview: { series: [], effects: [], amountsUnchanged: true },
      previewToken: 't', result: null }));
    let outcome: EnrolmentCorrection | null | undefined;

    flow.correct('REOPEN', { ...enrolment, departure: '2030-01-14', active: false }, 'Amine Belkacem')
      .subscribe(value => outcome = value);
    const data = correctionData(0);
    data.run('preview', { type: 'DATA_ENTRY_ERROR' }).subscribe();

    expect(dialog.open).toHaveBeenCalledTimes(1);
    expect(opened(0).component).toBe(CorrectionDialogComponent);
    expect(data.titleKey).toBe('enrolment.correction.REOPEN.title');
    expect(data.allowBack).toBeFalse();
    expect(data.reasons).toEqual(['DATA_ENTRY_ERROR', 'OTHER']);
    expect(enrolments.reopen).toHaveBeenCalledWith(7, 'preview', { type: 'DATA_ENTRY_ERROR' }, undefined, undefined);
    expect(outcome?.active).toBeTrue();
  });

  it('saisie abandonnée : aucune correction, issue nulle', () => {
    closings.push(undefined);
    let outcome: EnrolmentCorrection | null | undefined;

    flow.correct('ARRIVAL', enrolment, 'Amine Belkacem').subscribe(value => outcome = value);

    expect(dialog.open).toHaveBeenCalledTimes(1);
    expect(outcome).toBeNull();
  });

  it('les Motifs sont lus une fois ; leur échec est rendu à l\'appelant', () => {
    closings.push(undefined, undefined);
    flow.correct('ARRIVAL', enrolment, 'Amine Belkacem').subscribe();
    flow.correct('ARRIVAL', enrolment, 'Amine Belkacem').subscribe();
    expect(enrolments.getCorrectionReasons).toHaveBeenCalledTimes(1);

    const fresh = new EnrolmentCorrectionFlow(dialog, enrolments, TestBed.inject(TranslateService));
    enrolments.getCorrectionReasons.and.returnValue(throwError(() => new Error('Serveur injoignable')));
    let message = '';
    fresh.correct('ARRIVAL', enrolment, 'Amine Belkacem').subscribe({ error: (err: Error) => message = err.message });

    expect(message).toBe('Serveur injoignable');
  });
});
