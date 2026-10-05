import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import frTranslations from '../../../../assets/i18n/fr.json';

import { RefundCreateDialogComponent } from './refund-create-dialog.component';
import { RefundService } from '../../../services/refund.service';
import { RefundReceiptPdfService } from '../../../services/refund-receipt-pdf.service';
import { Refund, RefundReceipt } from '../../../models/refund/refund';

/**
 * Remboursement enregistré, reçu manqué : l'argent est sorti de la caisse, l'administrateur doit le
 * savoir et savoir où réimprimer le reçu. L'échec n'était que journalisé dans la console.
 */
describe('RefundCreateDialogComponent', () => {
  let fixture: ComponentFixture<RefundCreateDialogComponent>;
  let component: RefundCreateDialogComponent;
  let refundService: jasmine.SpyObj<RefundService>;
  let receiptPdf: jasmine.SpyObj<RefundReceiptPdfService>;
  let snackBar: jasmine.SpyObj<MatSnackBar>;
  let dialogRef: jasmine.SpyObj<MatDialogRef<RefundCreateDialogComponent>>;

  const refund: Refund = {
    id: 7, paymentId: 9, studentId: 4, amount: 400, refundDate: new Date(), refundNumber: 'REMB-2026-0007'
  };
  const receipt = { refundId: 7, refundNumber: 'REMB-2026-0007', issuanceRank: 1 } as RefundReceipt;
  const RECEIPT_FAILED =
    'Le remboursement est enregistré, mais son reçu n\'a pas pu être produit. '
    + 'Il reste imprimable depuis l\'historique complet de l\'étudiant.';

  beforeEach(async () => {
    refundService = jasmine.createSpyObj('RefundService', ['getRefundableCap', 'createRefund', 'issueReceipt']);
    receiptPdf = jasmine.createSpyObj('RefundReceiptPdfService', ['generateAndPrint']);
    snackBar = jasmine.createSpyObj('MatSnackBar', ['open']);
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    refundService.getRefundableCap.and.returnValue(of({
      paymentId: 9, amountPaid: 2400, alreadyRefunded: 0, refundableCap: 2400
    }));
    refundService.createRefund.and.returnValue(of(refund));

    await TestBed.configureTestingModule({
      imports: [RefundCreateDialogComponent, NoopAnimationsModule, TranslateModule.forRoot()],
      providers: [
        { provide: RefundService, useValue: refundService },
        { provide: RefundReceiptPdfService, useValue: receiptPdf },
        { provide: MatSnackBar, useValue: snackBar },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: { paymentId: 9, studentId: 4 } }
      ]
    }).compileComponents();
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', frTranslations);
    translate.use('fr');

    fixture = TestBed.createComponent(RefundCreateDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    component.form.setValue({ amount: 400, reason: 'Trop-perçu' });
  });

  it('imprime le reçu après l\'enregistrement, sans avertissement', async () => {
    refundService.issueReceipt.and.returnValue(of(receipt));
    receiptPdf.generateAndPrint.and.returnValue(Promise.resolve());

    component.confirmAndSubmit();
    await fixture.whenStable();

    expect(refundService.issueReceipt).toHaveBeenCalledWith(7);
    expect(receiptPdf.generateAndPrint).toHaveBeenCalledWith(receipt);
    expect(dialogRef.close).toHaveBeenCalledWith(refund);
    expect(snackBar.open).not.toHaveBeenCalled();
  });

  it('avertit quand le serveur ne produit pas le reçu, et dit où le réimprimer', () => {
    refundService.issueReceipt.and.returnValue(throwError(() => new Error('Erreur serveur')));
    spyOn(console, 'error');

    component.confirmAndSubmit();

    expect(snackBar.open).toHaveBeenCalledWith(RECEIPT_FAILED, 'Fermer', jasmine.any(Object));
    // Le remboursement, lui, est bien enregistré : le dialogue se ferme sur lui.
    expect(dialogRef.close).toHaveBeenCalledWith(refund);
  });

  it('avertit aussi quand l\'impression échoue dans le navigateur', async () => {
    refundService.issueReceipt.and.returnValue(of(receipt));
    receiptPdf.generateAndPrint.and.returnValue(Promise.reject(new Error('iframe')));
    spyOn(console, 'error');

    component.confirmAndSubmit();
    await fixture.whenStable();

    expect(snackBar.open).toHaveBeenCalledWith(RECEIPT_FAILED, 'Fermer', jasmine.any(Object));
  });
});
