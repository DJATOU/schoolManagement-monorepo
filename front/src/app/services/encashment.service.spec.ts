import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { EncashmentService } from './encashment.service';
import { setupServiceTestBed } from '../../testing/setup';
import { API_BASE_URL } from '../api-base-url';

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

  it('signale qu\'un non-administrateur ne lit pas les versements', () => {
    let message = '';
    service.getStudentEncashments(42).subscribe({ error: (err: Error) => message = err.message });

    http.expectOne(`${API_BASE_URL}/api/students/42/encashments`)
      .flush(null, { status: 403, statusText: 'Forbidden' });

    expect(message).toBe('Action réservée aux administrateurs');
  });
});
