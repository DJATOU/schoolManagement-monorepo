import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import frTranslations from '../../assets/i18n/fr.json';
import enTranslations from '../../assets/i18n/en.json';

import { CorrectionJournalService } from './correction-journal.service';
import { setupServiceTestBed } from '../../testing/setup';
import { API_BASE_URL } from '../api-base-url';

/**
 * Journal d'un élève (D.5). L'adresse et les paramètres sont le contrat avec
 * `CorrectionJournalController` : une faute ne se verrait qu'à l'écran.
 */
describe('CorrectionJournalService', () => {
  let service: CorrectionJournalService;
  let http: HttpTestingController;

  beforeEach(() => {
    setupServiceTestBed();
    // Messages d'erreur traduits : les assertions portent sur le texte français affiché.
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', frTranslations);
    translate.use('fr');
    service = TestBed.inject(CorrectionJournalService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lit le Journal, bornes jointes seulement si elles sont données', () => {
    service.getJournal(42, '2030-01-01', '2030-01-31').subscribe();
    service.getJournal(42, '2030-01-01').subscribe();
    service.getJournal(42, null, '2030-01-31').subscribe();
    service.getJournal(42).subscribe();

    const both = http.expectOne(`${API_BASE_URL}/api/students/42/journal?from=2030-01-01&to=2030-01-31`);
    expect(both.request.method).toBe('GET');
    both.flush({});
    http.expectOne(`${API_BASE_URL}/api/students/42/journal?from=2030-01-01`).flush({});
    http.expectOne(`${API_BASE_URL}/api/students/42/journal?to=2030-01-31`).flush({});
    http.expectOne(`${API_BASE_URL}/api/students/42/journal`).flush({});
  });

  it('chaque refus dit sa cause : motif du serveur, sinon message par statut', () => {
    const messages: string[] = [];
    const fail = (status: number, body: object | null): void => {
      service.getJournal(42).subscribe({ error: (err: Error) => messages.push(err.message) });
      http.expectOne(`${API_BASE_URL}/api/students/42/journal`).flush(body, { status, statusText: 'x' });
    };

    fail(400, { message: 'Période invalide : du 31/01/2030 au 10/01/2030, la fin précède le début.' });
    fail(400, { message: '  ' });
    fail(404, { message: 'Étudiant introuvable : 42' });
    fail(404, null);
    fail(401, {});
    fail(403, {});
    fail(0, null);
    fail(500, {});

    expect(messages).toEqual([
      'Période invalide : du 31/01/2030 au 10/01/2030, la fin précède le début.',
      'Le journal n\'a pas pu être chargé.',
      'Étudiant introuvable : 42',
      'Le journal n\'a pas pu être chargé.',
      'Session expirée : reconnectez-vous',
      'Action réservée aux administrateurs',
      'Serveur injoignable',
      'Le journal n\'a pas pu être chargé.'
    ]);
  });

  it('en anglais, les messages du navigateur sont en anglais ; le motif du serveur reste tel quel', () => {
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', enTranslations);
    translate.use('en');
    const messages: string[] = [];
    const fail = (status: number, body: object | null): void => {
      service.getJournal(42).subscribe({ error: (err: Error) => messages.push(err.message) });
      http.expectOne(`${API_BASE_URL}/api/students/42/journal`).flush(body, { status, statusText: 'x' });
    };
    fail(400, { message: 'Période invalide : du 31/01/2030 au 10/01/2030, la fin précède le début.' });
    fail(403, {});
    fail(0, null);
    fail(500, {});
    expect(messages).toEqual([
      'Période invalide : du 31/01/2030 au 10/01/2030, la fin précède le début.',
      'Action restricted to administrators',
      'Server unreachable',
      'The correction log could not be loaded.'
    ]);
  });
});
