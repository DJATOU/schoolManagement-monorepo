import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { AttendanceService } from './attendance.service';
import { setupServiceTestBed } from '../../testing/setup';
import { API_BASE_URL } from '../api-base-url';
import { AttendanceSubmissionError } from '../models/Attendance/rejected-absence';
import { CorrectionError } from '../models/correction/correction-error';

/**
 * Validation d'une feuille de présence (C.8 ; exigence 7.5) : un refus garde les lignes refusées,
 * que l'écran retire avant de revalider. Avant, tout 409 devenait « présence déjà saisie ».
 */
describe('AttendanceService — feuille de présence', () => {
  let service: AttendanceService;
  let http: HttpTestingController;
  const url = `${API_BASE_URL}/api/attendances/bulk`;

  beforeEach(() => {
    setupServiceTestBed();
    service = TestBed.inject(AttendanceService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function refuse(status: number, body: object | null): AttendanceSubmissionError {
    let error: AttendanceSubmissionError | null = null;
    service.submitAttendance([]).subscribe({ error: (err: AttendanceSubmissionError) => error = err });
    http.expectOne(url).flush(body, { status, statusText: 'x' });
    return error!;
  }

  it('envoie la feuille entière en une requête', () => {
    service.submitAttendance([]).subscribe();

    const req = http.expectOne(url);
    expect(req.request.method).toBe('POST');
    req.flush([]);
  });

  it('absences hors période : les lignes refusées sont gardées, une à une', () => {
    const rejected = [{ studentId: 2, message: 'Absence de Lina Haddad le 14/01/2030 : hors de son inscription' }];

    const error = refuse(409, { message: 'Validation refusée', errorCode: 'ABSENCE_OUTSIDE_WINDOW', rejected });

    expect(error).toEqual(jasmine.any(AttendanceSubmissionError));
    expect(error.outsideWindow).toBeTrue();
    expect(error.rejected).toEqual(rejected as never);
    expect(error.message).toBe('Validation refusée');
    expect(error.status).toBe(409);
  });

  it('un 409 sans ligne refusée n\'est pas pris pour des absences hors période', () => {
    expect(refuse(409, { errorCode: 'ABSENCE_OUTSIDE_WINDOW', rejected: [] }).outsideWindow).toBeFalse();
    expect(refuse(409, { errorCode: 'CONFLICT', rejected: 'x' }).rejected).toEqual([]);
  });

  it('chaque refus dit sa cause : motif du serveur, sinon message par statut', () => {
    expect(refuse(409, {}).message).toBe('Une présence existe déjà pour un ou plusieurs étudiants de cette séance.');
    expect(refuse(409, { message: 'Doublon : Amine Belkacem' }).message).toBe('Doublon : Amine Belkacem');
    expect(refuse(400, { message: 'Séance inconnue' }).message).toBe('Séance inconnue');
    expect(refuse(404, { message: '  ' }).message).toBe('Feuille de présence refusée.');
    expect(refuse(403, {}).message).toBe('Action réservée aux administrateurs');
    expect(refuse(0, null).message).toBe('Serveur injoignable');
    expect(refuse(500, { message: 'NullPointerException' }).message)
      .toBe('La feuille de présence n\'a pas pu être enregistrée.');
    expect(refuse(500, {}).errorCode).toBeNull();
  });
});

/**
 * Corriger une séance validée, ligne par ligne (D.7 ; exigences 8 et 9). Les adresses et les corps sont
 * le contrat avec `AttendanceCorrectionController` : une faute ne se verrait qu'à l'écran.
 */
describe('AttendanceService — corrections d\'une séance validée', () => {
  let service: AttendanceService;
  let http: HttpTestingController;
  const base = `${API_BASE_URL}/api/attendances`;

  beforeEach(() => {
    setupServiceTestBed();
    service = TestBed.inject(AttendanceService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lit les Motifs d\'une correction de présence', () => {
    let reasons: unknown;
    service.getCorrectionReasons().subscribe(value => reasons = value);

    const req = http.expectOne(`${base}/correction-reasons`);
    expect(req.request.method).toBe('GET');
    req.flush(['DATA_ENTRY_ERROR', 'DOCUMENT_RECEIVED', 'OTHER']);
    expect(reasons).toEqual(['DATA_ENTRY_ERROR', 'DOCUMENT_RECEIVED', 'OTHER']);
  });

  it('change une ligne : état voulu, Motif et jeton dans le corps', () => {
    service.correctAttendance(31, 'preview', { present: false, justified: true }, { type: 'DOCUMENT_RECEIVED' })
      .subscribe();
    service.correctAttendance(31, 'confirm', { present: true, justified: false },
      { type: 'OTHER', text: 'Feuille d\'un autre groupe' }, 'jeton').subscribe();

    const preview = http.expectOne(`${base}/31/correct/preview`);
    expect(preview.request.method).toBe('POST');
    expect(preview.request.body).toEqual({ present: false, justified: true, reasonType: 'DOCUMENT_RECEIVED',
      reasonText: null, previewToken: null });
    preview.flush({});
    expect(http.expectOne(`${base}/31/correct/confirm`).request.body).toEqual({ present: true, justified: false,
      reasonType: 'OTHER', reasonText: 'Feuille d\'un autre groupe', previewToken: 'jeton' });
  });

  it('retire une ligne : Motif et jeton seulement', () => {
    service.removeAttendance(31, 'confirm', { type: 'DATA_ENTRY_ERROR' }, 'jeton').subscribe();

    const req = http.expectOne(`${base}/31/remove/confirm`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reasonType: 'DATA_ENTRY_ERROR', reasonText: null, previewToken: 'jeton' });
    req.flush({});
  });

  it('ajoute une ligne à la séance : l\'élève, l\'état voulu, Motif et jeton', () => {
    service.addAttendance(100, 42, 'preview', { present: true, justified: false }, { type: 'DATA_ENTRY_ERROR' })
      .subscribe();

    const req = http.expectOne(`${API_BASE_URL}/api/sessions/100/attendances/add/preview`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ studentId: 42, present: true, justified: false,
      reasonType: 'DATA_ENTRY_ERROR', reasonText: null, previewToken: null });
    req.flush({});
  });

  it('un refus garde le motif du serveur ; un Aperçu périmé, le nouvel Aperçu', () => {
    const errors: CorrectionError[] = [];
    const keep = { error: (err: CorrectionError) => errors.push(err) };
    service.addAttendance(100, 7, 'preview', { present: true, justified: false }, { type: 'DATA_ENTRY_ERROR' })
      .subscribe(keep);
    http.expectOne(`${API_BASE_URL}/api/sessions/100/attendances/add/preview`).flush(
      { message: 'Sami Kaci n\'est pas inscrit au groupe « Math 1ère A » : un rattrapage s\'enregistre par une demande '
          + 'de rattrapage.' }, { status: 409, statusText: 'Conflict' });
    service.removeAttendance(31, 'confirm', { type: 'DATA_ENTRY_ERROR' }, 'ancien').subscribe(keep);
    const preview = { series: [], effects: [], amountsUnchanged: true };
    http.expectOne(`${base}/31/remove/confirm`).flush(
      { message: 'Les données ont changé', errorCode: 'STALE_PREVIEW', preview, previewToken: 'nouveau' },
      { status: 409, statusText: 'Conflict' });
    service.correctAttendance(999, 'preview', { present: true, justified: false }, { type: 'DATA_ENTRY_ERROR' })
      .subscribe(keep);
    http.expectOne(`${base}/999/correct/preview`).flush(null, { status: 404, statusText: 'Not Found' });
    service.getCorrectionReasons().subscribe(keep);
    http.expectOne(`${base}/correction-reasons`).flush(null, { status: 503, statusText: 'Unavailable' });

    expect(errors.map(error => error.message)).toEqual([
      'Sami Kaci n\'est pas inscrit au groupe « Math 1ère A » : un rattrapage s\'enregistre par une demande de rattrapage.',
      'Les données ont changé',
      'Présence introuvable',
      'La correction de la présence n\'a pas pu aboutir'
    ]);
    expect(errors[1].stale).toBeTrue();
    expect(errors[1].previewToken).toBe('nouveau');
  });
});
