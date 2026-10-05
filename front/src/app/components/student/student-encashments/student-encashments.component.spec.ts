import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarRef, TextOnlySnackBar } from '@angular/material/snack-bar';
import { of, throwError } from 'rxjs';

import { StudentEncashmentsComponent } from './student-encashments.component';
import { EncashmentService } from '../../../services/encashment.service';
import { PaymentReceiptPdfService } from '../../../services/payment-receipt-pdf.service';
import { setupComponentTestBed } from '../../../../testing/setup';
import { anEncashment } from '../../../../testing/fixtures';
import { CorrectionDialogComponent, CorrectionDialogData } from '../../shared/correction-dialog/correction-dialog.component';
import { EncashmentEditDialogComponent } from './encashment-edit-dialog/encashment-edit-dialog.component';
import { EncashmentChanges } from '../../../models/correction/correction';

/**
 * Historique des versements d'un élève (A.9, B.7) : un reçu par ligne, annulés compris ;
 * réimpression à partir de l'Encaissement relu au serveur, tampon « ANNULÉ » compris ; annulation
 * et correction par le dialogue commun d'Aperçu.
 */
describe('StudentEncashmentsComponent', () => {
  let fixture: ComponentFixture<StudentEncashmentsComponent>;
  let component: StudentEncashmentsComponent;
  let encashments: jasmine.SpyObj<EncashmentService>;
  let receiptPdf: jasmine.SpyObj<PaymentReceiptPdfService>;

  beforeEach(async () => {
    encashments = jasmine.createSpyObj<EncashmentService>('EncashmentService',
      ['getStudentEncashments', 'getEncashment', 'getCorrectionReasons', 'cancel', 'correct']);
    encashments.getCorrectionReasons.and.returnValue(of(['DATA_ENTRY_ERROR', 'WRONG_STUDENT', 'WRONG_AMOUNT', 'OTHER']));
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

  /** Les dialogues ouverts tour à tour se ferment sur ces issues. */
  function dialogsClosingWith(...outcomes: unknown[]): jasmine.Spy {
    return spyOn(TestBed.inject(MatDialog), 'open').and.returnValues(
      ...outcomes.map(outcome => ({ afterClosed: () => of(outcome) } as MatDialogRef<unknown>)));
  }

  const text = (): string => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const items = (): HTMLElement[] =>
    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.enc-item'));
  const buttons = (selector: string): HTMLButtonElement[] =>
    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll(selector));

  // ------------------------------------------------------------------
  // Liste
  // ------------------------------------------------------------------

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

  it('un versement valide se corrige, s\'annule et se réimprime', () => {
    showFor();

    expect(buttons('.enc-correct').length).toBe(1);
    expect(buttons('.enc-cancel').length).toBe(1);
    expect(buttons('.enc-reprint').length).toBe(1);
  });

  it('un versement annulé reste listé, marqué annulé ; il se réimprime, mais ne se corrige plus', () => {
    showFor([
      anEncashment({ id: 4, receiptNumber: 'RECU-2026-0043' }),
      anEncashment({ id: 3, status: 'CANCELLED', cancelledAt: '2026-10-04T10:00:00', cancelledBy: 'admin' })
    ]);

    const cancelled = items()[1];
    expect(cancelled.classList).toContain('enc-item--cancelled');
    expect(cancelled.querySelector('.enc-status')?.textContent).toContain('payment.encashments.status.CANCELLED');
    expect(cancelled.querySelector('.enc-reprint')).not.toBeNull();
    expect(cancelled.querySelector('.enc-correct')).toBeNull();
    expect(cancelled.querySelector('.enc-cancel')).toBeNull();
  });

  it('en lecture seule (année passée), rien ne se corrige ni ne s\'annule ; tout se réimprime', () => {
    fixture.componentRef.setInput('readOnly', true);
    showFor();

    expect(buttons('.enc-correct').length).toBe(0);
    expect(buttons('.enc-cancel').length).toBe(0);
    expect(buttons('.enc-reprint').length).toBe(1);
    component.cancel(component.encashments[0]);
    component.correct(component.encashments[0]);
    expect(encashments.getCorrectionReasons).not.toHaveBeenCalled();
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

  it('recharge la liste à la demande, après un nouvel encaissement', () => {
    showFor();

    component.reload();

    expect(encashments.getStudentEncashments).toHaveBeenCalledTimes(2);
  });

  // ------------------------------------------------------------------
  // Réimpression
  // ------------------------------------------------------------------

  it('réimprime le reçu de l\'Encaissement relu au serveur, avec ses propres mentions', async () => {
    showFor();
    encashments.getEncashment.and.returnValue(of(anEncashment()));

    buttons('.enc-reprint')[0].click();
    await fixture.whenStable();

    expect(encashments.getEncashment).toHaveBeenCalledWith(3);
    const printed = receiptPdf.generateAndPrint.calls.mostRecent().args[0];
    expect(printed.reference).toBe('RECU-2026-0042');
    expect(printed.adminUsername).toBe('caissier1');
    expect(printed.issuedAt.getTime()).toBe(new Date('2026-10-03T09:15:00').getTime());
    expect(printed.amountPaid).toBe(5000);
    expect(printed.paymentMethodLabel).toBe('payment.dialog.methods.cash');
    expect(printed.cancellation).toBeUndefined();
    expect(component.reprintingId).toBeNull();
    expect(encashments.getStudentEncashments).toHaveBeenCalledTimes(1);
  });

  it('un versement annulé depuis le chargement ressort avec le tampon « ANNULÉ », et la liste se recharge', () => {
    showFor();
    encashments.getEncashment.and.returnValue(of(anEncashment({
      status: 'CANCELLED', cancelledAt: '2026-10-05T11:00:00', replacedByReceiptNumber: 'RECU-2026-0050'
    })));

    buttons('.enc-reprint')[0].click();

    const printed = receiptPdf.generateAndPrint.calls.mostRecent().args[0];
    expect(printed.cancellation).toEqual({ cancelledAt: new Date('2026-10-05T11:00:00'), replacedBy: 'RECU-2026-0050' });
    expect(encashments.getStudentEncashments).toHaveBeenCalledTimes(2);
  });

  it('un échec de relecture est signalé, et la réimpression redevient possible', () => {
    showFor();
    encashments.getEncashment.and.returnValue(throwError(() => new Error('Serveur injoignable')));
    const snackBar = spyOn(TestBed.inject(MatSnackBar), 'open');

    buttons('.enc-reprint')[0].click();

    expect(receiptPdf.generateAndPrint).not.toHaveBeenCalled();
    expect(snackBar.calls.mostRecent().args[0]).toBe('Serveur injoignable');
    expect(component.reprintingId).toBeNull();
  });

  // ------------------------------------------------------------------
  // Annulation
  // ------------------------------------------------------------------

  it('annuler ouvre l\'Aperçu commun, branché sur l\'annulation de ce versement', () => {
    showFor();
    const open = dialogsClosingWith(undefined);
    encashments.cancel.and.returnValue(of({ preview: { series: [], effects: [], amountsUnchanged: true },
      previewToken: 't', result: null }));

    buttons('.enc-cancel')[0].click();

    expect(open.calls.mostRecent().args[0]).toBe(CorrectionDialogComponent);
    const data = open.calls.mostRecent().args[1].data as CorrectionDialogData<unknown>;
    expect(data.titleKey).toBe('correction.cancel.title');
    expect(data.reasons).toEqual(['DATA_ENTRY_ERROR', 'WRONG_STUDENT', 'WRONG_AMOUNT', 'OTHER']);
    expect(data.allowBack).toBeFalsy();
    data.run('confirm', { type: 'WRONG_AMOUNT' }, 'jeton').subscribe();
    expect(encashments.cancel).toHaveBeenCalledWith(3, 'confirm', { type: 'WRONG_AMOUNT' }, 'jeton');
    expect(encashments.getStudentEncashments).toHaveBeenCalledTimes(1);
  });

  it('une annulation confirmée est annoncée, et la liste se recharge', () => {
    showFor();
    dialogsClosingWith({ kind: 'confirmed', result: anEncashment({ status: 'CANCELLED' }) });
    const snackBar = spyOn(TestBed.inject(MatSnackBar), 'open');

    buttons('.enc-cancel')[0].click();

    expect(snackBar.calls.mostRecent().args[0]).toBe('correction.cancel.done');
    expect(encashments.getStudentEncashments).toHaveBeenCalledTimes(2);
  });

  it('les Motifs ne sont demandés au serveur qu\'une fois', () => {
    showFor();
    dialogsClosingWith(undefined, undefined);

    buttons('.enc-cancel')[0].click();
    buttons('.enc-cancel')[0].click();

    expect(encashments.getCorrectionReasons).toHaveBeenCalledTimes(1);
  });

  it('des Motifs indisponibles sont signalés, sans ouvrir l\'Aperçu', () => {
    showFor();
    encashments.getCorrectionReasons.and.returnValue(throwError(() => new Error('Session expirée')));
    const open = spyOn(TestBed.inject(MatDialog), 'open');
    const snackBar = spyOn(TestBed.inject(MatSnackBar), 'open');

    buttons('.enc-cancel')[0].click();

    expect(open).not.toHaveBeenCalled();
    expect(snackBar.calls.mostRecent().args[0]).toBe('Session expirée');
  });

  // ------------------------------------------------------------------
  // Correction
  // ------------------------------------------------------------------

  const changes: EncashmentChanges = { amount: 2000, studentId: 42, groupId: 5, targetSeriesId: 7, paymentMethod: 'cash', notes: null };

  it('corriger : saisie de l\'état voulu, puis Aperçu branché sur ces changements', () => {
    showFor();
    const open = dialogsClosingWith(changes, undefined);
    encashments.correct.and.returnValue(of({ preview: { series: [], effects: [], amountsUnchanged: true },
      previewToken: 't', result: null }));

    buttons('.enc-correct')[0].click();

    expect(open.calls.argsFor(0)[0]).toBe(EncashmentEditDialogComponent);
    expect(open.calls.argsFor(0)[1].data.encashment.id).toBe(3);
    expect(open.calls.argsFor(1)[0]).toBe(CorrectionDialogComponent);
    const data = open.calls.argsFor(1)[1].data as CorrectionDialogData<unknown>;
    expect(data.titleKey).toBe('correction.correct.title');
    expect(data.allowBack).toBeTrue();
    data.run('preview', { type: 'WRONG_AMOUNT' }).subscribe();
    expect(encashments.correct).toHaveBeenCalledWith(3, 'preview', changes, { type: 'WRONG_AMOUNT' }, undefined);
  });

  it('une saisie abandonnée n\'ouvre aucun Aperçu', () => {
    showFor();
    const open = dialogsClosingWith(undefined);

    buttons('.enc-correct')[0].click();

    expect(open).toHaveBeenCalledTimes(1);
  });

  it('« Modifier » ramène à la saisie, avec ce qui avait été tapé', () => {
    showFor();
    const open = dialogsClosingWith(changes, { kind: 'back' }, undefined);

    buttons('.enc-correct')[0].click();

    expect(open).toHaveBeenCalledTimes(3);
    expect(open.calls.argsFor(2)[0]).toBe(EncashmentEditDialogComponent);
    expect(open.calls.argsFor(2)[1].data.changes).toEqual(changes);
  });

  it('après un Remplacement, l\'impression du nouveau reçu est proposée (3.7)', () => {
    showFor();
    const replacement = anEncashment({ id: 9, receiptNumber: 'RECU-2026-0050', amountReceived: 2000,
      allocations: [{ seriesId: 7, seriesName: 'Octobre', amount: 2000, carriedOver: false, active: true }] });
    dialogsClosingWith(changes, { kind: 'confirmed', result: { original: anEncashment({ status: 'CANCELLED' }), replacement } });
    const snackBar = spyOn(TestBed.inject(MatSnackBar), 'open').and.returnValue(
      { onAction: () => of(undefined) } as MatSnackBarRef<TextOnlySnackBar>);

    buttons('.enc-correct')[0].click();

    expect(snackBar.calls.mostRecent().args[0]).toBe('correction.correct.replaced');
    expect(snackBar.calls.mostRecent().args[1]).toBe('correction.correct.print');
    expect(receiptPdf.generateAndPrint.calls.mostRecent().args[0].reference).toBe('RECU-2026-0050');
    expect(encashments.getStudentEncashments).toHaveBeenCalledTimes(2);
  });

  it('mode et note corrigés en place : annoncés, sans reçu à imprimer', () => {
    showFor();
    dialogsClosingWith(changes, { kind: 'confirmed', result: { original: anEncashment(), replacement: null } });
    const snackBar = spyOn(TestBed.inject(MatSnackBar), 'open');

    buttons('.enc-correct')[0].click();

    expect(snackBar.calls.mostRecent().args[0]).toBe('correction.correct.detailsDone');
    expect(receiptPdf.generateAndPrint).not.toHaveBeenCalled();
    expect(encashments.getStudentEncashments).toHaveBeenCalledTimes(2);
  });

  it('un échec d\'impression est signalé', async () => {
    showFor();
    receiptPdf.generateAndPrint.and.rejectWith(new Error('pdf'));
    encashments.getEncashment.and.returnValue(of(anEncashment()));
    const snackBar = spyOn(TestBed.inject(MatSnackBar), 'open');
    spyOn(console, 'error');

    buttons('.enc-reprint')[0].click();
    await fixture.whenStable();

    expect(snackBar.calls.mostRecent().args[0]).toBe('payment.encashments.reprintError');
  });
});
