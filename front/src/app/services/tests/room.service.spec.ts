import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { RoomService } from '../room.service';
import { API_BASE_URL } from '../../api-base-url';
import { setupServiceTestBed } from '../../../testing/setup';

describe('RoomService', () => {
  let service: RoomService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    setupServiceTestBed();
    service = TestBed.inject(RoomService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('getRooms() interroge GET /api/rooms', () => {
    service.getRooms().subscribe();

    const req = httpMock.expectOne(`${API_BASE_URL}/api/rooms`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });
});
