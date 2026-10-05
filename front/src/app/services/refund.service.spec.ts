import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { RefundService } from './refund.service';
import { setupServiceTestBed } from '../../testing/setup';
import { API_BASE_URL } from '../api-base-url';
import { StudentRefund } from '../models/refund/refund';

/**
 * Contrat HTTP des remboursements lus par l'historique : la liste d'un élève et la réimpression
 * d'un reçu. Une faute de chemin ne se verrait qu'à l'écran, en 404.
 */
describe('RefundService', () => {
  let service: RefundService;
  let http: HttpTestingController;

  beforeEach(() => {
    setupServiceTestBed();
    service = TestBed.inject(RefundService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lit les remboursements d\'un élève sous sa route, à côté de ses Encaissements', () => {
    const refunds: StudentRefund[] = [{
      refundId: 7, refundNumber: 'REMB-2026-0007', refundDate: '2026-10-05', amount: 400, seriesId: 12
    }];
    let received: StudentRefund[] = [];
    service.getStudentRefunds(42).subscribe(value => received = value);

    const req = http.expectOne(`${API_BASE_URL}/api/students/42/refunds`);
    expect(req.request.method).toBe('GET');
    req.flush(refunds);

    expect(received).toEqual(refunds);
  });

  it('lit les remboursements d\'un versement, pour l\'historique d\'une ligne', () => {
    let received: StudentRefund[] = [];
    service.getPaymentRefunds(9).subscribe(value => received = value);

    const req = http.expectOne(`${API_BASE_URL}/api/refunds/payment/9`);
    expect(req.request.method).toBe('GET');
    req.flush([{ refundId: 7, refundNumber: 'REMB-2026-0007', refundDate: '2026-10-05', amount: 400, paymentId: 9 }]);

    expect(received.map(refund => refund.refundId)).toEqual([7]);
  });

  it('réimprime un reçu par un POST, pour que le serveur compte l\'émission', () => {
    service.issueReceipt(7).subscribe();

    const req = http.expectOne(`${API_BASE_URL}/api/refunds/7/receipts`);
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('nomme un refus de lecture réservé aux administrateurs', () => {
    let message = '';
    service.getStudentRefunds(42).subscribe({ error: (err: Error) => message = err.message });

    http.expectOne(`${API_BASE_URL}/api/students/42/refunds`)
      .flush(null, { status: 403, statusText: 'Forbidden' });

    expect(message).toBe('Action réservée aux administrateurs');
  });

  it('garde le message du serveur sur un élève inconnu', () => {
    let message = '';
    service.getStudentRefunds(99).subscribe({ error: (err: Error) => message = err.message });

    http.expectOne(`${API_BASE_URL}/api/students/99/refunds`)
      .flush({ message: 'Étudiant introuvable : 99' }, { status: 404, statusText: 'Not Found' });

    expect(message).toBe('Étudiant introuvable : 99');
  });
});
