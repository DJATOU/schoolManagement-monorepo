import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { PricingService } from '../pricing.service';
import { API_BASE_URL } from '../../api-base-url';
import { setupServiceTestBed } from '../../../testing/setup';

describe('PricingService', () => {
  let service: PricingService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    setupServiceTestBed();
    service = TestBed.inject(PricingService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('getPricings() interroge GET /api/pricings', () => {
    service.getPricings().subscribe();

    const req = httpMock.expectOne(`${API_BASE_URL}/api/pricings`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });
});
