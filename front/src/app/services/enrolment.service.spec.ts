import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { EnrolmentService } from './enrolment.service';
import { setupServiceTestBed } from '../../testing/setup';
import { API_BASE_URL } from '../api-base-url';
import { CorrectionError } from '../models/correction/correction-error';

/**
 * Inscriptions d'un élève et leurs corrections (C.8). Les adresses et les corps sont le contrat
 * avec `StudentGroupController` et `EnrolmentCorrectionController` : une faute ne se verrait qu'à
 * l'écran.
 */
describe('EnrolmentService', () => {
  let service: EnrolmentService;
  let http: HttpTestingController;

  beforeEach(() => {
    setupServiceTestBed();
    service = TestBed.inject(EnrolmentService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lit les inscriptions d\'un élève, filtrées sur l\'année si elle est donnée', () => {
    service.getStudentEnrolments(42, 3).subscribe();
    service.getStudentEnrolments(42).subscribe();

    const filtered = http.expectOne(`${API_BASE_URL}/api/student-groups/42/enrolments?schoolYearId=3`);
    expect(filtered.request.method).toBe('GET');
    filtered.flush([]);
    http.expectOne(`${API_BASE_URL}/api/student-groups/42/enrolments`).flush([]);
  });

  it('lit les Motifs proposés par correction', () => {
    let reasons: unknown;
    service.getCorrectionReasons().subscribe(value => reasons = value);

    http.expectOne(`${API_BASE_URL}/api/enrolments/correction-reasons`)
      .flush({ ARRIVAL: ['ARRIVAL_DATE_CORRECTED'], DEPARTURE: ['STUDENT_LEFT'], REOPEN: ['DATA_ENTRY_ERROR'] });

    expect(reasons).toEqual({ ARRIVAL: ['ARRIVAL_DATE_CORRECTED'], DEPARTURE: ['STUDENT_LEFT'], REOPEN: ['DATA_ENTRY_ERROR'] });
  });

  it('corrige l\'arrivée : date, présences notées, Motif et jeton dans le corps', () => {
    service.correctArrival(7, 'preview', '2030-01-05', { type: 'DATA_ENTRY_ERROR' }).subscribe();
    service.correctArrival(7, 'confirm', '2030-01-05', { type: 'OTHER', text: 'Inscrit en retard' }, 'jeton',
      { 41: false, 42: true }).subscribe();

    const preview = http.expectOne(`${API_BASE_URL}/api/enrolments/7/arrival/preview`);
    expect(preview.request.method).toBe('POST');
    expect(preview.request.body).toEqual({ arrival: '2030-01-05', attendances: [], reasonType: 'DATA_ENTRY_ERROR',
      reasonText: null, previewToken: null });
    preview.flush({});
    const confirm = http.expectOne(`${API_BASE_URL}/api/enrolments/7/arrival/confirm`);
    expect(confirm.request.body).toEqual({
      arrival: '2030-01-05',
      attendances: [{ sessionId: 41, present: false }, { sessionId: 42, present: true }],
      reasonType: 'OTHER', reasonText: 'Inscrit en retard', previewToken: 'jeton'
    });
    confirm.flush({});
  });

  it('enregistre le départ, avec ou sans retrait des présences postérieures', () => {
    service.setDeparture(7, 'preview', '2030-01-14', true, { type: 'STUDENT_LEFT' }).subscribe();

    const req = http.expectOne(`${API_BASE_URL}/api/enrolments/7/departure/preview`);
    expect(req.request.body).toEqual({ departure: '2030-01-14', removePresencesAfter: true, attendances: [],
      reasonType: 'STUDENT_LEFT', reasonText: null, previewToken: null });
    req.flush({});
  });

  it('rouvre une inscription, sans date', () => {
    service.reopen(7, 'confirm', { type: 'DATA_ENTRY_ERROR' }, 'jeton', { 41: true }).subscribe();

    const req = http.expectOne(`${API_BASE_URL}/api/enrolments/7/reopen/confirm`);
    expect(req.request.body).toEqual({ attendances: [{ sessionId: 41, present: true }],
      reasonType: 'DATA_ENTRY_ERROR', reasonText: null, previewToken: 'jeton' });
    req.flush({});
  });

  it('un Aperçu périmé garde le nouvel Aperçu et son jeton', () => {
    let error: CorrectionError | null = null;
    service.correctArrival(7, 'confirm', '2030-01-10', { type: 'DATA_ENTRY_ERROR' }, 'ancien')
      .subscribe({ error: (err: CorrectionError) => error = err });

    const preview = { series: [], effects: [], amountsUnchanged: true };
    http.expectOne(`${API_BASE_URL}/api/enrolments/7/arrival/confirm`).flush(
      { message: 'Les données ont changé', errorCode: 'STALE_PREVIEW', preview, previewToken: 'nouveau' },
      { status: 409, statusText: 'Conflict' });

    expect(error).toEqual(jasmine.any(CorrectionError));
    expect(error!.stale).toBeTrue();
    expect(error!.previewToken).toBe('nouveau');
    expect(error!.message).toBe('Les données ont changé');
  });

  it('chaque refus dit sa cause : motif du serveur, sinon message par statut', () => {
    const messages: string[] = [];
    const fail = (status: number, body: object): void => {
      service.reopen(7, 'preview', { type: 'DATA_ENTRY_ERROR' }).subscribe({ error: (err: Error) => messages.push(err.message) });
      http.expectOne(`${API_BASE_URL}/api/enrolments/7/reopen/preview`).flush(body, { status, statusText: 'x' });
    };

    fail(400, { message: 'La date de départ du 01/07/2030 est hors de l\'année' });
    fail(409, {});
    fail(401, {});
    fail(403, {});
    fail(404, { message: 'Inscription introuvable : 7' });
    fail(404, {});
    fail(500, {});
    fail(418, {});

    expect(messages).toEqual([
      'La date de départ du 01/07/2030 est hors de l\'année',
      'Correction refusée',
      'Session expirée : reconnectez-vous',
      'Action réservée aux administrateurs',
      'Inscription introuvable : 7',
      'Inscription introuvable',
      'Erreur serveur. Veuillez réessayer plus tard.',
      'Une erreur est survenue lors de la lecture des inscriptions'
    ]);
  });

  it('serveur injoignable, erreur réseau : dits comme tels', () => {
    const messages: string[] = [];
    service.getStudentEnrolments(42).subscribe({ error: (err: Error) => messages.push(err.message) });
    http.expectOne(`${API_BASE_URL}/api/student-groups/42/enrolments`).flush(null, { status: 0, statusText: 'Unknown' });
    service.getStudentEnrolments(42).subscribe({ error: (err: Error) => messages.push(err.message) });
    http.expectOne(`${API_BASE_URL}/api/student-groups/42/enrolments`)
      .error(new ErrorEvent('network', { message: 'coupure' }));

    expect(messages).toEqual(['Serveur injoignable', 'Erreur : coupure']);
  });
});
