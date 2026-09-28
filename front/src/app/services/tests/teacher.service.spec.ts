import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { TeacherService } from '../teacher.service';
import { API_BASE_URL } from '../../api-base-url';
import { setupServiceTestBed } from '../../../testing/setup';

describe('TeacherService', () => {
  let service: TeacherService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    setupServiceTestBed();
    service = TestBed.inject(TeacherService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('getTeachers() interroge GET /api/teachers', () => {
    service.getTeachers().subscribe();

    const req = httpMock.expectOne(`${API_BASE_URL}/api/teachers`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('searchTeachersByNameStartingWith() passe le terme dans le paramètre search', () => {
    service.searchTeachersByNameStartingWith('dup').subscribe();

    const req = httpMock.expectOne(
      (request) => request.url === `${API_BASE_URL}/api/teachers/searchByNames`
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('search')).toBe('dup');
    req.flush([]);
  });
});
