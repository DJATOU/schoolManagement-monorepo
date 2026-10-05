import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateService } from '@ngx-translate/core';
import { of, Subject, throwError } from 'rxjs';
import frTranslations from '../../../../../assets/i18n/fr.json';

import { PaymentDetailHistoryDialogComponent } from './payment-detail-history-dialog.component';
import { matDialogProviders, setupComponentTestBed } from '../../../../../testing/setup';
import { RefundService } from '../../../../services/refund.service';
import { RefundReceiptPdfService } from '../../../../services/refund-receipt-pdf.service';
import { RefundReceipt, StudentRefund } from '../../../../models/refund/refund';

/** Espaces de groupement (U+202F en français) ramenées à une espace ordinaire. */
const plain = (text: string | null | undefined): string => (text ?? '').replace(/\s+/g, ' ').trim();

/**
 * Historique d'une ligne de versement : les remboursements de son versement expliquent le montant
 * net affiché dans le tableau, et chacun se réimprime.
 */
describe('PaymentDetailHistoryDialogComponent', () => {
  let fixture: ComponentFixture<PaymentDetailHistoryDialogComponent>;
  let component: PaymentDetailHistoryDialogComponent;
  let http: HttpTestingController;
  let refundService: jasmine.SpyObj<RefundService>;
  let receiptPdf: jasmine.SpyObj<RefundReceiptPdfService>;
  let snackBar: jasmine.SpyObj<MatSnackBar>;

  const refund = (overrides: Partial<StudentRefund> = {}): StudentRefund => ({
    refundId: 7, refundNumber: 'REMB-2026-0007', refundDate: '2026-10-05T10:00:00', amount: 400,
    reason: 'Trop-perçu', paymentId: 9, recordedBy: 'directrice', ...overrides
  });
  const receipt = { refundId: 7, refundNumber: 'REMB-2026-0007', issuanceRank: 2 } as RefundReceipt;

  async function open(data: { id: number; paymentId?: number | null },
                      refunds: StudentRefund[] | Error = []): Promise<void> {
    refundService = jasmine.createSpyObj('RefundService', ['getPaymentRefunds', 'issueReceipt']);
    receiptPdf = jasmine.createSpyObj('RefundReceiptPdfService', ['generateAndPrint']);
    snackBar = jasmine.createSpyObj('MatSnackBar', ['open']);
    refundService.getPaymentRefunds.and.returnValue(
      refunds instanceof Error ? throwError(() => refunds) : of(refunds));

    await setupComponentTestBed(PaymentDetailHistoryDialogComponent, {
      providers: [
        ...matDialogProviders(data),
        { provide: RefundService, useValue: refundService },
        { provide: RefundReceiptPdfService, useValue: receiptPdf },
        { provide: MatSnackBar, useValue: snackBar }
      ]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', frTranslations);
    translate.use('fr');
    http = TestBed.inject(HttpTestingController);

    fixture = TestBed.createComponent(PaymentDetailHistoryDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    http.expectOne(request => request.url.endsWith(`/api/payment-details/${data.id}/history`)).flush([]);
    fixture.detectChanges();
  }

  afterEach(() => http.verify());

  const text = (): string => plain(fixture.nativeElement.textContent);

  it('liste les remboursements du versement : pièce, date, montant, motif, auteur', async () => {
    await open({ id: 5, paymentId: 9 }, [refund()]);

    expect(refundService.getPaymentRefunds).toHaveBeenCalledWith(9);
    const item = plain(fixture.nativeElement.querySelector('.refund-item')?.textContent);
    expect(item).toContain('REMB-2026-0007');
    expect(item).toContain('400,00 DA');
    expect(item).toContain('05/10/2026');
    expect(item).toContain('par directrice');
    expect(item).toContain('Trop-perçu');
  });

  it('nomme un motif absent plutôt que de laisser un blanc', async () => {
    await open({ id: 5, paymentId: 9 }, [refund({ reason: '  ' })]);

    expect(plain(fixture.nativeElement.querySelector('.refund-reason')?.textContent))
      .toContain('Motif non renseigné');
  });

  it('dit qu\'aucun remboursement n\'a eu lieu, et garde le journal de la ligne', async () => {
    await open({ id: 5, paymentId: 9 }, []);

    expect(text()).toContain('Aucun remboursement sur ce versement.');
    expect(text()).toContain('Modifications de la ligne');
    expect(text()).toContain('Aucune action enregistrée pour ce versement.');
  });

  it('distingue un échec de chargement d\'une absence de remboursement', async () => {
    await open({ id: 5, paymentId: 9 }, new Error('Action réservée aux administrateurs'));

    expect(text()).toContain('Action réservée aux administrateurs');
    expect(text()).not.toContain('Aucun remboursement sur ce versement.');
  });

  it('sans versement, ne demande aucun remboursement', async () => {
    await open({ id: 5, paymentId: null });

    expect(refundService.getPaymentRefunds).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('.refunds')).toBeNull();
  });

  it('réimprime le reçu : le serveur l\'émet, le service PDF l\'imprime', async () => {
    await open({ id: 5, paymentId: 9 }, [refund()]);
    refundService.issueReceipt.and.returnValue(of(receipt));
    receiptPdf.generateAndPrint.and.returnValue(Promise.resolve());

    (fixture.nativeElement.querySelector('.refund-reprint') as HTMLButtonElement).click();

    expect(refundService.issueReceipt).toHaveBeenCalledWith(7);
    expect(receiptPdf.generateAndPrint).toHaveBeenCalledWith(receipt);
    expect(component.reprintingId).toBeNull();
  });

  it('ignore un second clic pendant une réimpression', async () => {
    await open({ id: 5, paymentId: 9 }, [refund()]);
    refundService.issueReceipt.and.returnValue(new Subject<RefundReceipt>());

    component.reprint(refund());
    component.reprint(refund());

    expect(refundService.issueReceipt).toHaveBeenCalledTimes(1);
    expect(component.reprintingId).toBe(7);
  });

  it('signale un refus du serveur, puis permet de réessayer', async () => {
    await open({ id: 5, paymentId: 9 }, [refund()]);
    refundService.issueReceipt.and.returnValue(throwError(() => new Error('Remboursement introuvable ou inactif')));

    component.reprint(refund());

    expect(snackBar.open).toHaveBeenCalledWith('Remboursement introuvable ou inactif', 'Fermer', jasmine.any(Object));
    expect(component.reprintingId).toBeNull();
  });

  it('signale un échec d\'impression dans le navigateur', async () => {
    await open({ id: 5, paymentId: 9 }, [refund()]);
    refundService.issueReceipt.and.returnValue(of(receipt));
    receiptPdf.generateAndPrint.and.returnValue(Promise.reject(new Error('iframe')));
    spyOn(console, 'error');

    component.reprint(refund());
    await fixture.whenStable();

    expect(snackBar.open).toHaveBeenCalledWith(
      'Le reçu de remboursement n\'a pas pu être imprimé. Réessayez.', 'Fermer', jasmine.any(Object));
  });
});
