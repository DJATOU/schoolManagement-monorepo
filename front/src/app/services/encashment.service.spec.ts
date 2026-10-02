import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { EncashmentService } from './encashment.service';
import { setupServiceTestBed } from '../../testing/setup';
import { API_BASE_URL } from '../api-base-url';
import { CorrectionError } from '../models/correction/correction-error';

/**
 * Lecture des Encaissements : le reçu à réimprimer et l'historique d'un élève (A.8).
 *
 * <p>Les adresses sont le contrat avec `EncashmentController` ; une faute de chemin ne se
 * verrait qu'à l'écran, en 404.</p>
 */
describe('EncashmentService', () => {
  let service: EncashmentService;
  let http: HttpTestingController;

  beforeEach(() => {
    setupServiceTestBed();
    service = TestBed.inject(EncashmentService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lit un Encaissement par son identifiant', () => {
    service.getEncashment(3).subscribe();

    const req = http.expectOne(`${API_BASE_URL}/api/encashments/3`);
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('lit l\'historique des versements d\'un élève', () => {
    service.getStudentEncashments(42).subscribe();

    const req = http.expectOne(`${API_BASE_URL}/api/students/42/encashments`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('conserve le motif du serveur sur un versement introuvable', () => {
    let message = '';
    service.getEncashment(99).subscribe({ error: (err: Error) => message = err.message });

    http.expectOne(`${API_BASE_URL}/api/encashments/99`)
      .flush({ message: 'Encaissement introuvable : 99' }, { status: 404, statusText: 'Not Found' });

    expect(message).toBe('Encaissement introuvable : 99');
  });

  it('lit les Motifs proposés pour corriger un versement', () => {
    service.getCorrectionReasons().subscribe();

    http.expectOne(`${API_BASE_URL}/api/encashments/correction-reasons`).flush(['WRONG_AMOUNT']);
  });

  it('annule en Aperçu puis en confirmation, Motif et jeton dans le corps', () => {
    service.cancel(3, 'preview', { type: 'WRONG_AMOUNT' }).subscribe();
    service.cancel(3, 'confirm', { type: 'OTHER', text: 'Doublon' }, 'jeton').subscribe();

    const preview = http.expectOne(`${API_BASE_URL}/api/encashments/3/cancel/preview`);
    expect(preview.request.method).toBe('POST');
    expect(preview.request.body).toEqual({ reasonType: 'WRONG_AMOUNT', reasonText: null, previewToken: null });
    preview.flush({});
    const confirm = http.expectOne(`${API_BASE_URL}/api/encashments/3/cancel/confirm`);
    expect(confirm.request.body).toEqual({ reasonType: 'OTHER', reasonText: 'Doublon', previewToken: 'jeton' });
    confirm.flush({});
  });

  it('corrige vers l\'état voulu complet', () => {
    const changes = { amount: 2000, studentId: 42, groupId: 5, targetSeriesId: 7, paymentMethod: 'cheque', notes: null };

    service.correct(3, 'confirm', changes, { type: 'WRONG_AMOUNT' }, 'jeton').subscribe();

    const req = http.expectOne(`${API_BASE_URL}/api/encashments/3/correct/confirm`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ ...changes, reasonType: 'WRONG_AMOUNT', reasonText: null, previewToken: 'jeton' });
    req.flush({});
  });

  it('garde le nouvel Aperçu et son jeton joints à un Aperçu périmé', () => {
    let refused: CorrectionError | undefined;
    service.cancel(3, 'confirm', { type: 'WRONG_AMOUNT' }, 'ancien').subscribe({ error: err => refused = err });

    const preview = { series: [], effects: [], amountsUnchanged: true };
    http.expectOne(`${API_BASE_URL}/api/encashments/3/cancel/confirm`).flush(
      { message: 'Les données ont changé', errorCode: 'STALE_PREVIEW', preview, previewToken: 'nouveau' },
      { status: 409, statusText: 'Conflict' });

    expect(refused).toBeInstanceOf(CorrectionError);
    expect(refused!.stale).toBeTrue();
    expect(refused!.message).toBe('Les données ont changé');
    expect(refused!.preview).toEqual(preview);
    expect(refused!.previewToken).toBe('nouveau');
  });

  it('garde les remboursements en cause et le motif d\'un refus métier', () => {
    let refused: CorrectionError | undefined;
    service.cancel(3, 'preview', { type: 'WRONG_AMOUNT' }).subscribe({ error: err => refused = err });

    const blockingRefunds = [{ refundNumber: 'REMB-2030-0001', refundDate: '2030-02-05', amount: 2000, seriesName: 'Janvier' }];
    http.expectOne(`${API_BASE_URL}/api/encashments/3/cancel/preview`).flush(
      { message: 'Correction refusée', errorCode: 'REFUND_FLOOR', blockingRefunds },
      { status: 409, statusText: 'Conflict' });

    expect(refused!.stale).toBeFalse();
    expect(refused!.status).toBe(409);
    expect(refused!.blockingRefunds).toEqual(blockingRefunds);
  });

  it('un refus de saisie garde son motif ; sans motif, un message par défaut', () => {
    const messages: string[] = [];
    service.correct(3, 'preview', { amount: 1, studentId: 1, groupId: 1, targetSeriesId: 1 }, { type: 'WRONG_AMOUNT' })
      .subscribe({ error: (err: Error) => messages.push(err.message) });
    service.cancel(3, 'preview', { type: 'WRONG_AMOUNT' }).subscribe({ error: (err: Error) => messages.push(err.message) });

    http.expectOne(`${API_BASE_URL}/api/encashments/3/correct/preview`)
      .flush({ message: 'Correction sans changement effectif' }, { status: 400, statusText: 'Bad Request' });
    http.expectOne(`${API_BASE_URL}/api/encashments/3/cancel/preview`)
      .flush(null, { status: 400, statusText: 'Bad Request' });

    expect(messages).toEqual(['Correction sans changement effectif', 'Correction refusée']);
  });

  it('signale qu\'un non-administrateur ne lit pas les versements', () => {
    let message = '';
    service.getStudentEncashments(42).subscribe({ error: (err: Error) => message = err.message });

    http.expectOne(`${API_BASE_URL}/api/students/42/encashments`)
      .flush(null, { status: 403, statusText: 'Forbidden' });

    expect(message).toBe('Action réservée aux administrateurs');
  });
});
