import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { SubjectService } from '../subject.service';
import { API_BASE_URL } from '../../api-base-url';
import { setupServiceTestBed } from '../../../testing/setup';

describe('SubjectService', () => {
  let service: SubjectService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    setupServiceTestBed();
    service = TestBed.inject(SubjectService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('getSubjects() interroge GET /api/subjects', () => {
    service.getSubjects().subscribe();

    const req = httpMock.expectOne(`${API_BASE_URL}/api/subjects`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });
});
