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

  it('addGroupsToStudent transmet la date d\'arrivée, et l\'omet sans date (le jour même côté serveur)', () => {
    service.addGroupsToStudent(42, [5, 6], '2029-10-15').subscribe();
    service.addGroupsToStudent(42, [7]).subscribe();

    const [dated, undated] = httpMock.match(`${API_BASE_URL}/api/student-groups/42/addGroups`);
    expect(dated.request.method).toBe('POST');
    expect(dated.request.body).toEqual({ groupIds: [5, 6], dateAssigned: '2029-10-15' });
    expect(undated.request.body).toEqual({ groupIds: [7] });
    dated.flush({ message: 'ok' });
    undated.flush({ message: 'ok' });
  });
});
