import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, throwError } from 'rxjs';

import { StudentEncashmentsComponent } from './student-encashments.component';
import { EncashmentService } from '../../../services/encashment.service';
import { PaymentReceiptPdfService } from '../../../services/payment-receipt-pdf.service';
import { setupComponentTestBed } from '../../../../testing/setup';
import { anEncashment } from '../../../../testing/fixtures';

/**
 * Historique des versements d'un élève (A.9) : un reçu par ligne, annulés compris, et la
 * réimpression d'un reçu à partir de l'Encaissement relu au serveur.
 */
describe('StudentEncashmentsComponent', () => {
  let fixture: ComponentFixture<StudentEncashmentsComponent>;
  let component: StudentEncashmentsComponent;
  let encashments: jasmine.SpyObj<EncashmentService>;
  let receiptPdf: jasmine.SpyObj<PaymentReceiptPdfService>;

  beforeEach(async () => {
    encashments = jasmine.createSpyObj<EncashmentService>('EncashmentService',
      ['getStudentEncashments', 'getEncashment']);
    receiptPdf = jasmine.createSpyObj<PaymentReceiptPdfService>('PaymentReceiptPdfService', ['generateAndPrint']);
    receiptPdf.generateAndPrint.and.resolveTo();

    await setupComponentTestBed(StudentEncashmentsComponent, {
      providers: [
        { provide: EncashmentService, useValue: encashments },
        { provide: PaymentReceiptPdfService, useValue: receiptPdf }
      ]
    });
    fixture = TestBed.createComponent(StudentEncashmentsComponent);
    component = fixture.componentInstance;
  });

  /** Affiche la liste de l'élève 42, telle que le serveur la renvoie. */
  function showFor(list = [anEncashment()]): void {
    encashments.getStudentEncashments.and.returnValue(of(list));
    fixture.componentRef.setInput('studentId', 42);
    fixture.detectChanges();
  }

  const text = (): string => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const items = (): HTMLElement[] =>
    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.enc-item'));
  const reprintButtons = (): HTMLButtonElement[] =>
    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.enc-reprint'));

  it('liste les versements de l\'élève dans l\'ordre du serveur, le plus récent d\'abord', () => {
    showFor([
      anEncashment({ id: 4, receiptNumber: 'RECU-2026-0043' }),
      anEncashment({ id: 3, receiptNumber: 'RECU-2026-0042' })
    ]);

    expect(encashments.getStudentEncashments).toHaveBeenCalledWith(42);
    expect(items().map(item => item.querySelector('.enc-number')?.textContent?.trim()))
      .toEqual(['RECU-2026-0043', 'RECU-2026-0042']);
  });

  it('montre pour chaque versement le montant, le groupe, la série visée et la note', () => {
    showFor();

    const item = items()[0];
    expect(item.querySelector('.enc-amount')?.textContent).toContain('5,000.00');
    expect(item.querySelector('.enc-meta')?.textContent).toContain('Maths 1B');
    expect(item.querySelector('.enc-meta')?.textContent).toContain('Octobre');
    expect(item.textContent).toContain('Versement du père');
  });

  it('nomme une ligne de report par série reportée, et aucune pour la série visée', () => {
    // Sans chargeur de traduction, le libellé rendu est la clé : on compte ses occurrences.
    showFor();

    const carryOverLines = Array.from(items()[0].querySelectorAll('.enc-detail'))
      .filter(line => line.textContent?.includes('payment.encashments.carriedOverTo'));
    expect(carryOverLines.length).toBe(2);
    expect(component.carryOvers(component.encashments[0]).map(a => a.seriesName))
      .toEqual(['Novembre', 'Décembre']);
  });

  it('garde un versement annulé dans la liste, marqué annulé, sans réimpression', () => {
    showFor([
      anEncashment({ id: 4, receiptNumber: 'RECU-2026-0043' }),
      anEncashment({
        id: 3, status: 'CANCELLED', cancelledAt: '2026-10-04T10:00:00', cancelledBy: 'admin'
      })
    ]);

    const cancelled = items()[1];
    expect(cancelled.classList).toContain('enc-item--cancelled');
    expect(cancelled.querySelector('.enc-status')?.textContent).toContain('payment.encashments.status.CANCELLED');
    expect(cancelled.querySelector('.enc-reprint')).toBeNull();
    expect(reprintButtons().length).toBe(1);
  });

  it('annonce l\'absence de versement', () => {
    showFor([]);

    expect(text()).toContain('payment.encashments.empty');
    expect(items().length).toBe(0);
  });

  it('affiche le motif d\'un échec de chargement', () => {
    encashments.getStudentEncashments.and.returnValue(throwError(() => new Error('Action réservée aux administrateurs')));
    fixture.componentRef.setInput('studentId', 42);
    fixture.detectChanges();

    expect(text()).toContain('Action réservée aux administrateurs');
    expect(items().length).toBe(0);
  });

  it('ne charge rien sans élève', () => {
    fixture.componentRef.setInput('studentId', null);
    fixture.detectChanges();

    expect(encashments.getStudentEncashments).not.toHaveBeenCalled();
  });

  it('réimprime le reçu de l\'Encaissement relu au serveur, avec ses propres mentions', async () => {
    showFor();
    encashments.getEncashment.and.returnValue(of(anEncashment()));

    reprintButtons()[0].click();
    await fixture.whenStable();

    expect(encashments.getEncashment).toHaveBeenCalledWith(3);
    const printed = receiptPdf.generateAndPrint.calls.mostRecent().args[0];
    expect(printed.reference).toBe('RECU-2026-0042');
    expect(printed.adminUsername).toBe('caissier1');
    expect(printed.issuedAt.getTime()).toBe(new Date('2026-10-03T09:15:00').getTime());
    expect(printed.amountPaid).toBe(5000);
    expect(printed.paymentMethodLabel).toBe('payment.dialog.methods.cash');
    expect(component.reprintingId).toBeNull();
  });

  it('n\'imprime pas un versement annulé depuis le chargement, et recharge la liste', () => {
    showFor();
    encashments.getEncashment.and.returnValue(of(anEncashment({ status: 'CANCELLED' })));
    const snackBar = spyOn(TestBed.inject(MatSnackBar), 'open');

    reprintButtons()[0].click();

    expect(receiptPdf.generateAndPrint).not.toHaveBeenCalled();
    expect(encashments.getStudentEncashments).toHaveBeenCalledTimes(2);
    expect(snackBar.calls.mostRecent().args[0]).toBe('payment.encashments.cancelledMeanwhile');
  });

  it('recharge la liste à la demande, après un nouvel encaissement', () => {
    showFor();

    component.reload();

    expect(encashments.getStudentEncashments).toHaveBeenCalledTimes(2);
  });
});
