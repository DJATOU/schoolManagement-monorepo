import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { API_BASE_URL } from '../api-base-url';
import { setupServiceTestBed } from '../../testing/setup';
import { TeacherPayRateService } from './teacher-pay-rate.service';

describe('TeacherPayRateService', () => {
  let service: TeacherPayRateService;
  let http: HttpTestingController;
  const url = `${API_BASE_URL}/api/teacher-pay-rates`;

  beforeEach(() => {
    setupServiceTestBed();
    service = TestBed.inject(TeacherPayRateService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lit le catalogue', () => {
    let labels: string[] = [];
    service.getRates().subscribe(rates => labels = rates.map(rate => rate.label));
    http.expectOne(url).flush([{ id: 1, label: 'Standard', teacherPercent: 60, schoolPercent: 40, active: true }]);
    expect(labels).toEqual(['Standard']);
  });

  it('crée, modifie et désactive un taux sur les routes du serveur', () => {
    service.createRate({ label: 'Confirmé', teacherPercent: 62.5 }).subscribe();
    const create = http.expectOne(url);
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual({ label: 'Confirmé', teacherPercent: 62.5 });
    create.flush({});

    service.updateRate(3, { label: 'Confirmé', teacherPercent: 65 }).subscribe();
    const update = http.expectOne(`${url}/3`);
    expect(update.request.method).toBe('PUT');
    update.flush({});

    service.disableRate(3).subscribe();
    const disable = http.expectOne(`${url}/3/disable`);
    expect(disable.request.method).toBe('PATCH');
    disable.flush({});
  });

  it('garde le motif du serveur sur un refus (libellé déjà pris)', () => {
    let message = '';
    service.createRate({ label: 'Standard', teacherPercent: 60 }).subscribe({ error: (err: Error) => message = err.message });
    http.expectOne(url).flush({ message: 'Un taux actif porte déjà le libellé « Standard ».' },
      { status: 409, statusText: 'Conflict' });
    expect(message).toBe('Un taux actif porte déjà le libellé « Standard ».');
  });

  it('dit qu\'une action est réservée aux administrateurs (403)', () => {
    let message = '';
    service.getRates().subscribe({ error: (err: Error) => message = err.message });
    http.expectOne(url).flush({}, { status: 403, statusText: 'Forbidden' });
    expect(message).toBe('Action réservée aux administrateurs');
  });
});
