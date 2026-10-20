import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { TranslateModule } from '@ngx-translate/core';
import { ImportResult, ImportService } from '../import.service';
import { API_BASE_URL } from '../../api-base-url';

/**
 * Import CSV des inscriptions : envoi multipart sur /api/import/enrolments, et refus du serveur
 * remonté avec son message (gestion d'erreur centralisée du service).
 */
describe('ImportService - inscriptions', () => {
  const enrolmentsUrl = `${API_BASE_URL}/api/import/enrolments`;
  let service: ImportService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, TranslateModule.forRoot()],
      providers: [ImportService],
    });
    service = TestBed.inject(ImportService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('importEnrolments envoie le fichier en multipart (champ « file ») sur /api/import/enrolments', () => {
    const file = new File(['firstName,lastName,group,arrivalDate\n'], '9-inscriptions.csv', { type: 'text/csv' });
    const response: ImportResult = { imported: 1, errors: [{ line: 3, message: 'Déjà inscrit' }], warnings: [] };
    let received: ImportResult | undefined;

    service.importEnrolments(file).subscribe((result) => (received = result));

    const req = httpMock.expectOne(enrolmentsUrl);
    expect(req.request.method).toBe('POST');
    expect(req.request.body instanceof FormData).toBeTrue();
    expect((req.request.body as FormData).get('file')).toBe(file);
    req.flush(response);

    expect(received).toEqual(response);
  });

  it('une réponse 400 devient une erreur portant le message du serveur', () => {
    const file = new File([''], 'vide.csv', { type: 'text/csv' });
    let error: Error | undefined;
    spyOn(console, 'error');

    service.importEnrolments(file).subscribe({ error: (e: Error) => (error = e) });

    httpMock.expectOne(enrolmentsUrl)
      .flush({ message: 'Fichier vide ou absent.' }, { status: 400, statusText: 'Bad Request' });

    expect(error?.message).toBe('Fichier vide ou absent.');
  });
});
