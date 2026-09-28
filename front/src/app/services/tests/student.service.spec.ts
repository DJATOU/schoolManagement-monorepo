import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { StudentService } from '../../components/student/services/student.service';
import { API_BASE_URL } from '../../api-base-url';
import { setupServiceTestBed } from '../../../testing/setup';

describe('StudentService', () => {
  let service: StudentService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    setupServiceTestBed();
    service = TestBed.inject(StudentService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('getStudents() sans argument n\'envoie aucun paramètre de filtrage', () => {
    service.getStudents().subscribe();

    const req = httpMock.expectOne(`${API_BASE_URL}/api/students`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.keys().length).toBe(0);
    req.flush([]);
  });

  it('getStudents(7, true) transmet schoolYearId et includeInactive', () => {
    service.getStudents(7, true).subscribe();

    const req = httpMock.expectOne(
      (request) => request.url === `${API_BASE_URL}/api/students`
    );
    expect(req.request.params.get('schoolYearId')).toBe('7');
    expect(req.request.params.get('includeInactive')).toBe('true');
    req.flush([]);
  });
});
