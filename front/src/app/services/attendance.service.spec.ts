import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { AttendanceService } from './attendance.service';
import { setupServiceTestBed } from '../../testing/setup';
import { API_BASE_URL } from '../api-base-url';
import { AttendanceSubmissionError } from '../models/Attendance/rejected-absence';

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
