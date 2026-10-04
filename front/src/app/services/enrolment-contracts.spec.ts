import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { GroupService } from './group.service';
import { PaymentService } from './payment.service';
import { StudentService } from '../components/student/services/student.service';
import { setupServiceTestBed } from '../../testing/setup';
import { API_BASE_URL } from '../api-base-url';
import { Payment } from '../models/payment/payment';

/**
 * Passe transverse T.2 : les contrats HTTP changés par la spec admin-corrections dans des services
 * qui n'avaient pas de tests. Une faute de corps ou d'en-tête ne se verrait qu'à l'écran.
 */
describe('Contrats HTTP modifiés par admin-corrections', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    setupServiceTestBed();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('inscrire des élèves à un groupe : la date d\'arrivée n\'est jointe que si elle est donnée (C.8)', () => {
    const groups = TestBed.inject(GroupService);
    groups.addStudentsToGroup(5, [1, 2], '2030-01-14').subscribe();
    groups.addStudentsToGroup(5, [3]).subscribe();
    groups.addStudentsToGroup(5, [4], null).subscribe();

    const requests = http.match(`${API_BASE_URL}/api/student-groups/5/addStudents`);
    expect(requests.map(req => req.request.method)).toEqual(['POST', 'POST', 'POST']);
    expect(requests.map(req => req.request.body)).toEqual([
      { studentIds: [1, 2], dateAssigned: '2030-01-14' },
      { studentIds: [3] },
      { studentIds: [4] }
    ]);
    requests.forEach(req => req.flush({}));
  });

  it('inscrire un élève à des groupes : même règle pour la date (C.8)', () => {
    const students = TestBed.inject(StudentService);
    students.addGroupsToStudent(42, [5, 6], '2030-01-14').subscribe();
    students.addGroupsToStudent(42, [7]).subscribe();

    const requests = http.match(`${API_BASE_URL}/api/student-groups/42/addGroups`);
    expect(requests.map(req => req.request.body)).toEqual([
      { groupIds: [5, 6], dateAssigned: '2030-01-14' },
      { groupIds: [7] }
    ]);
    requests.forEach(req => req.flush({}));
  });

  it('versement ordinaire ou de rattrapage : la clé de la soumission part en en-tête, seulement si elle existe', () => {
    const payments = TestBed.inject(PaymentService);
    const payment = { studentId: 42, groupId: 5, sessionSeriesId: 10, amountPaid: 2000 } as unknown as Payment;
    payments.processPayment(payment, 'cle-1').subscribe();
    payments.processPayment(payment).subscribe();
    payments.processCatchUpPayment(payment, 'cle-2').subscribe();
    payments.processCatchUpPayment(payment).subscribe();

    const regular = http.match(`${API_BASE_URL}/api/payments/process`);
    expect(regular.map(req => req.request.headers.get('Idempotency-Key'))).toEqual(['cle-1', null]);
    expect(regular[0].request.body).toEqual(payment);
    const catchUp = http.match(`${API_BASE_URL}/api/payments/process/catch-up`);
    expect(catchUp.map(req => req.request.headers.get('Idempotency-Key'))).toEqual(['cle-2', null]);
    [...regular, ...catchUp].forEach(req => req.flush({}));
  });
});
