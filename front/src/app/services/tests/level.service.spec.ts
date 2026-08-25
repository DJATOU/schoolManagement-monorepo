import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { LevelService } from '../level.service';
import { API_BASE_URL } from '../../api-base-url';
import { setupServiceTestBed } from '../../../testing/setup';

describe('LevelService', () => {
  let service: LevelService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    setupServiceTestBed();
    service = TestBed.inject(LevelService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('getLevels() interroge GET /api/levels', () => {
    service.getLevels().subscribe();

    const req = httpMock.expectOne(`${API_BASE_URL}/api/levels`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });
});
