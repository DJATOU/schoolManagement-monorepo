import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { API_BASE_URL } from '../api-base-url';
import { CorrectionError } from '../models/correction/correction-error';
import { PayoutError } from '../models/payroll/payroll';
import { payoutPreview } from '../../testing/payroll-fixtures';
import { setupServiceTestBed } from '../../testing/setup';
import { TeacherPayoutService } from './teacher-payout.service';

describe('TeacherPayoutService', () => {
  let service: TeacherPayoutService;
  let http: HttpTestingController;
  const api = `${API_BASE_URL}/api`;

  beforeEach(() => {
    setupServiceTestBed();
    service = TestBed.inject(TeacherPayoutService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('séries à payer : filtres transmis seulement s\'ils sont choisis', () => {
    service.getPayable(5, null).subscribe();
    const filtered = http.expectOne(req => req.url === `${api}/teacher-payouts/payable`);
    expect(filtered.request.params.get('teacherId')).toBe('5');
    expect(filtered.request.params.has('groupId')).toBeFalse();
    filtered.flush([]);

    service.getPayable().subscribe();
    const all = http.expectOne(req => req.url === `${api}/teacher-payouts/payable`);
    expect(all.request.params.keys()).toEqual([]);
    all.flush([]);
  });

  it('séries à payer : « Afficher les séries payées » transmis seulement s\'il est coché', () => {
    service.getPayable(null, 3, true).subscribe();
    const withPaid = http.expectOne(req => req.url === `${api}/teacher-payouts/payable`);
    expect(withPaid.request.params.get('groupId')).toBe('3');
    expect(withPaid.request.params.get('includePaid')).toBe('true');
    withPaid.flush([]);

    service.getPayable(null, 3, false).subscribe();
    const withoutPaid = http.expectOne(req => req.url === `${api}/teacher-payouts/payable`);
    expect(withoutPaid.request.params.has('includePaid')).toBeFalse();
    withoutPaid.flush([]);
  });

  it('paies versées : filtres vides écartés, dates au format du serveur', () => {
    service.searchPayouts({ teacherId: 5, groupId: null, status: 'ACTIVE', from: '2030-02-01', to: '' }).subscribe();
    const req = http.expectOne(r => r.url === `${api}/teacher-payouts`);
    expect(req.request.params.get('teacherId')).toBe('5');
    expect(req.request.params.get('status')).toBe('ACTIVE');
    expect(req.request.params.get('from')).toBe('2030-02-01');
    expect(req.request.params.has('groupId')).toBeFalse();
    expect(req.request.params.has('to')).toBeFalse();
    req.flush({ payouts: [], teacherTotal: 0, schoolTotal: 0 });
  });

  it('payer : Aperçu sans jeton, puis confirmation avec le jeton lu', () => {
    service.previewPay(12, 7, 'Remis en main propre').subscribe();
    const preview = http.expectOne(`${api}/teacher-payouts/series/12/pay/preview`);
    expect(preview.request.body).toEqual({ rateId: 7, note: 'Remis en main propre', previewToken: null });
    preview.flush(payoutPreview());

    service.confirmPay(12, 7, null, 'jeton-1').subscribe();
    const confirm = http.expectOne(`${api}/teacher-payouts/series/12/pay/confirm`);
    expect(confirm.request.body).toEqual({ rateId: 7, note: null, previewToken: 'jeton-1' });
    confirm.flush({});
  });

  it('régulariser : Aperçu, puis confirmation sans taux', () => {
    service.previewRegularize(12).subscribe();
    http.expectOne(`${api}/teacher-payouts/series/12/regularize/preview`).flush(payoutPreview({ kind: 'REGULARIZATION' }));

    service.confirmRegularize(12, 'Retard d\'Ali', 'jeton-2').subscribe();
    const confirm = http.expectOne(`${api}/teacher-payouts/series/12/regularize/confirm`);
    expect(confirm.request.body).toEqual({ rateId: null, note: 'Retard d\'Ali', previewToken: 'jeton-2' });
    confirm.flush({});
  });

  it('confirmation périmée : PayoutError portant le nouvel Aperçu', () => {
    let error: unknown;
    service.confirmPay(12, 7, null, 'jeton-1').subscribe({ error: err => error = err });
    const fresh = payoutPreview({ collectedNet: 73000, teacherAmount: 43800, previewToken: 'jeton-2' });
    http.expectOne(`${api}/teacher-payouts/series/12/pay/confirm`).flush({
      status: 'CONFLICT', message: 'Les montants ont changé depuis l\'aperçu', errorCode: 'STALE_PREVIEW',
      preview: fresh, previewToken: 'jeton-2'
    }, { status: 409, statusText: 'Conflict' });

    expect(error instanceof PayoutError).toBeTrue();
    const payoutError = error as PayoutError;
    expect(payoutError.stale).toBeTrue();
    expect(payoutError.preview?.teacherAmount).toBe(43800);
    expect(payoutError.message).toContain('ont changé');
  });

  it('refus nommé (série non terminée) : le motif du serveur, sans Aperçu', () => {
    let error: PayoutError | undefined;
    service.previewPay(12, 7).subscribe({ error: err => error = err });
    http.expectOne(`${api}/teacher-payouts/series/12/pay/preview`).flush(
      { message: 'La série « Octobre » n\'est pas terminée : 2 séance(s) sur 8 restent à valider.' },
      { status: 409, statusText: 'Conflict' });
    expect(error?.stale).toBeFalse();
    expect(error?.message).toContain('2 séance(s) sur 8');
  });

  it('serveur injoignable : le résultat de l\'opération est dit inconnu', () => {
    let message = '';
    service.confirmPay(12, 7, null, 'jeton-1').subscribe({ error: (err: Error) => message = err.message });
    http.expectOne(`${api}/teacher-payouts/series/12/pay/confirm`).error(new ProgressEvent('error'), { status: 0 });
    expect(message).toContain('inconnu');
  });

  it('bordereau : chaque impression est enregistrée par le serveur', () => {
    service.issueSlip(40).subscribe();
    const req = http.expectOne(`${api}/teacher-payouts/40/slips`);
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('annuler et remplacer : corps des corrections, refus en CorrectionError', () => {
    service.cancel(40, 'preview', { type: 'DATA_ENTRY_ERROR' }).subscribe();
    const cancel = http.expectOne(`${api}/teacher-payouts/40/cancel/preview`);
    expect(cancel.request.body).toEqual({ reasonType: 'DATA_ENTRY_ERROR', reasonText: null, previewToken: null });
    cancel.flush({ preview: { series: [], effects: [], amountsUnchanged: true }, previewToken: 'c1', result: null });

    service.replace(40, 'confirm', 8, 'Virement', { type: 'OTHER', text: 'Mauvais taux' }, 'r1').subscribe();
    const replace = http.expectOne(`${api}/teacher-payouts/40/replace/confirm`);
    expect(replace.request.body).toEqual({
      rateId: 8, note: 'Virement', reasonType: 'OTHER', reasonText: 'Mauvais taux', previewToken: 'r1'
    });
    replace.flush({});

    let error: unknown;
    service.cancel(40, 'preview', { type: 'DATA_ENTRY_ERROR' }).subscribe({ error: err => error = err });
    http.expectOne(`${api}/teacher-payouts/40/cancel/preview`).flush(
      { message: 'La paie PAIE-2030-0001 n\'est pas la plus récente de la série « Octobre » : corrigez d\'abord PAIE-2030-0003.' },
      { status: 409, statusText: 'Conflict' });
    expect(error instanceof CorrectionError).toBeTrue();
    expect((error as CorrectionError).message).toContain('corrigez d\'abord PAIE-2030-0003');
  });

  it('motifs de correction d\'une paie', () => {
    let reasons: string[] = [];
    service.getCorrectionReasons().subscribe(list => reasons = list);
    http.expectOne(`${api}/teacher-payouts/correction-reasons`).flush(['DATA_ENTRY_ERROR', 'WRONG_AMOUNT', 'OTHER']);
    expect(reasons).toEqual(['DATA_ENTRY_ERROR', 'WRONG_AMOUNT', 'OTHER']);
  });

  it('paies d\'un enseignant, et une paie', () => {
    let total = -1;
    service.getTeacherPayouts(5).subscribe(list => total = list.teacherTotal);
    http.expectOne(`${api}/teachers/5/payouts`).flush({ payouts: [], teacherTotal: 43200, schoolTotal: 28800 });
    expect(total).toBe(43200);

    let number = '';
    service.getPayout(40).subscribe(payout => number = payout.payoutNumber);
    http.expectOne(`${api}/teacher-payouts/40`).flush({ payoutNumber: 'PAIE-2030-0001' });
    expect(number).toBe('PAIE-2030-0001');
  });
});
