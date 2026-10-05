import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { SessionService } from './SessionService';
import { setupServiceTestBed } from '../../testing/setup';
import { API_BASE_URL } from '../api-base-url';
import { CorrectionError } from '../models/correction/correction-error';

/**
 * Dévalidation d'une séance (D.3 ; exigence 10.1). Les adresses et les corps sont le contrat avec
 * `AttendanceCorrectionController` : une faute ne se verrait qu'à l'écran.
 */
describe('SessionService — dévalidation', () => {
  let service: SessionService;
  let http: HttpTestingController;

  beforeEach(() => {
    setupServiceTestBed();
    service = TestBed.inject(SessionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lit les Motifs d\'une dévalidation', () => {
    let reasons: unknown;
    service.getUnvalidationReasons().subscribe(value => reasons = value);

    const req = http.expectOne(`${API_BASE_URL}/api/sessions/unvalidation-reasons`);
    expect(req.request.method).toBe('GET');
    req.flush(['DATA_ENTRY_ERROR', 'OTHER']);

    expect(reasons).toEqual(['DATA_ENTRY_ERROR', 'OTHER']);
  });

  it('Aperçu puis confirmation : Motif et jeton dans le corps, rien d\'autre', () => {
    let confirmed: unknown;
    service.unvalidate(100, 'preview', { type: 'DATA_ENTRY_ERROR' }).subscribe();
    service.unvalidate(100, 'confirm', { type: 'OTHER', text: 'Feuille d\'un autre groupe' }, 'jeton')
      .subscribe(response => confirmed = response.result);

    const preview = http.expectOne(`${API_BASE_URL}/api/sessions/100/unvalidate/preview`);
    expect(preview.request.method).toBe('POST');
    expect(preview.request.body).toEqual({ reasonType: 'DATA_ENTRY_ERROR', reasonText: null, previewToken: null });
    preview.flush({});
    const confirm = http.expectOne(`${API_BASE_URL}/api/sessions/100/unvalidate/confirm`);
    expect(confirm.request.body).toEqual({ reasonType: 'OTHER', reasonText: 'Feuille d\'un autre groupe',
      previewToken: 'jeton' });
    confirm.flush({ preview: { series: [], effects: [], amountsUnchanged: true }, previewToken: 'jeton',
      result: { sessionId: 100, removedLines: 2 } });

    expect(confirmed).toEqual({ sessionId: 100, removedLines: 2 });
  });

  it('un Aperçu périmé garde le nouvel Aperçu et son jeton', () => {
    let error: CorrectionError | null = null;
    service.unvalidate(100, 'confirm', { type: 'DATA_ENTRY_ERROR' }, 'ancien')
      .subscribe({ error: (err: CorrectionError) => error = err });

    const preview = { series: [], effects: [], amountsUnchanged: false };
    http.expectOne(`${API_BASE_URL}/api/sessions/100/unvalidate/confirm`).flush(
      { message: 'Les données ont changé', errorCode: 'STALE_PREVIEW', preview, previewToken: 'nouveau' },
      { status: 409, statusText: 'Conflict' });

    expect(error).toEqual(jasmine.any(CorrectionError));
    expect(error!.stale).toBeTrue();
    expect(error!.preview).toEqual(preview);
    expect(error!.previewToken).toBe('nouveau');
    expect(error!.message).toBe('Les données ont changé');
  });

  it('chaque refus dit sa cause : motif du serveur, sinon message par statut', () => {
    const messages: string[] = [];
    const fail = (status: number, body: object | null): void => {
      service.unvalidate(100, 'preview', { type: 'DATA_ENTRY_ERROR' })
        .subscribe({ error: (err: Error) => messages.push(err.message) });
      http.expectOne(`${API_BASE_URL}/api/sessions/100/unvalidate/preview`).flush(body, { status, statusText: 'x' });
    };

    fail(409, { message: 'La séance du 14/01/2030 n\'est pas validée : rien à dévalider.' });
    fail(400, { message: '   ' });
    fail(401, {});
    fail(403, {});
    fail(404, { message: 'Séance introuvable : 100' });
    fail(404, null);
    fail(500, {});
    fail(0, null);
    fail(418, {});

    expect(messages).toEqual([
      'La séance du 14/01/2030 n\'est pas validée : rien à dévalider.',
      'Correction refusée',
      'Session expirée : reconnectez-vous',
      'Action réservée aux administrateurs',
      'Séance introuvable : 100',
      'Séance introuvable',
      'Erreur serveur. Veuillez réessayer plus tard.',
      'Serveur injoignable',
      'La dévalidation de la séance n\'a pas pu aboutir'
    ]);
  });

  it('erreur réseau : dite comme telle', () => {
    let message = '';
    service.getUnvalidationReasons().subscribe({ error: (err: Error) => message = err.message });
    http.expectOne(`${API_BASE_URL}/api/sessions/unvalidation-reasons`).error(new ErrorEvent('network', { message: 'coupure' }));

    expect(message).toBe('Erreur : coupure');
  });

  it('les remboursements en cause et le code du refus sont conservés', () => {
    let error: CorrectionError | null = null;
    service.unvalidate(100, 'preview', { type: 'DATA_ENTRY_ERROR' }).subscribe({ error: (err: CorrectionError) => error = err });

    const refund = { refundNumber: 'RMB-2030-0001', refundDate: '2030-01-20', amount: 2000, seriesName: 'Janvier' };
    http.expectOne(`${API_BASE_URL}/api/sessions/100/unvalidate/preview`).flush(
      { message: 'Plancher', errorCode: 'REFUND_FLOOR', blockingRefunds: [refund] }, { status: 409, statusText: 'Conflict' });

    expect(error!.status).toBe(409);
    expect(error!.errorCode).toBe('REFUND_FLOOR');
    expect(error!.blockingRefunds).toEqual([refund]);
    expect(error!.stale).toBeFalse();
  });
});
