import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PaymentDialogComponent } from './payment-dialog.component';
import { createDialogRefSpy, DialogRefSpy, matDialogProviders, setupComponentTestBed } from '../../../../testing/setup';
import { aGroup } from '../../../../testing/fixtures';
import { of } from 'rxjs';
import { PaymentService } from '../../../services/payment.service';
import { PaymentReceiptPdfService } from '../../../services/payment-receipt-pdf.service';
import { Payment } from '../../../models/payment/payment';
import { PaymentAllocationResult } from '../../../models/payment/payment-allocation';
import { Encashment } from '../../../models/payment/encashment';

/**
 * Formulaire de saisie d'un versement.
 *
 * <p>Le point le plus sensible de cet écran est le refus d'un montant nul ou négatif :
 * `Validators.min(0)` est inclusif et laissait passer un versement à 0, qui créait une ligne
 * de paiement sans rien encaisser et imprimait un reçu n'attestant d'aucune somme. Le
 * validateur dédié est donc sous test explicite.</p>
 *
 * <p>Le plafond haut n'est pas testé ici : il dépend des devis chargés depuis le serveur, ce
 * qui relève d'un test d'intégration et non de la construction du formulaire.</p>
 */
describe('PaymentDialogComponent', () => {
  let component: PaymentDialogComponent;
  let fixture: ComponentFixture<PaymentDialogComponent>;
  let dialogRef: DialogRefSpy;

  beforeEach(async () => {
    dialogRef = createDialogRefSpy();
    await setupComponentTestBed(PaymentDialogComponent, {
      providers: matDialogProviders(
        { studentId: 42, groups: [aGroup()], studentName: 'Amina Belkacem' },
        dialogRef
      )
    });
    fixture = TestBed.createComponent(PaymentDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('reprend l\'étudiant et ses groupes des données du dialogue', () => {
    expect(component.studentId).toBe(42);
    expect(component.studentName).toBe('Amina Belkacem');
    expect(component.groups.map(group => group.name)).toEqual(['Maths 1B']);
  });

  it('refuse un montant nul', () => {
    const amount = component.paymentForm.get('amountPaid')!;

    amount.setValue(0);

    expect(amount.hasError('nonPositiveAmount')).toBeTrue();
  });

  it('refuse un montant négatif', () => {
    const amount = component.paymentForm.get('amountPaid')!;

    amount.setValue(-100);

    expect(amount.hasError('nonPositiveAmount')).toBeTrue();
  });

  it('accepte un montant strictement positif', () => {
    const amount = component.paymentForm.get('amountPaid')!;

    amount.setValue(2000);

    expect(amount.hasError('nonPositiveAmount')).toBeFalse();
  });

  it('laisse le contrôle « requis » signaler un champ vide, sans doubler l\'erreur', () => {
    // Deux messages simultanés pour un champ vide brouilleraient la cause réelle.
    const amount = component.paymentForm.get('amountPaid')!;

    amount.setValue(null);

    expect(amount.hasError('required')).toBeTrue();
    expect(amount.hasError('nonPositiveAmount')).toBeFalse();
  });

  it('propose les espèces par défaut', () => {
    // Mode de règlement de la très grande majorité des encaissements à l'accueil.
    expect(component.paymentForm.get('paymentMethod')!.value).toBe('cash');
  });

  it('désactive le paiement intégral tant qu\'aucune série n\'est choisie', () => {
    expect(component.paymentForm.get('fullSeriesPayment')!.disabled).toBeTrue();
    expect(component.canPayFullSeries).toBeFalse();
  });

  it('exige un groupe et une série : le formulaire est invalide à l\'ouverture', () => {
    expect(component.paymentForm.valid).toBeFalse();
  });

  it('ferme le dialogue à l\'annulation', () => {
    component.onCancel();

    expect(dialogRef.close).toHaveBeenCalled();
  });

  /**
   * Le reçu imprimé à l'encaissement reprend l'Encaissement enregistré par le serveur (A.8).
   *
   * <p>L'écran fabriquait auparavant sa propre référence à partir de l'identifiant de la ligne
   * de paiement et de l'heure du navigateur, et signait du compte connecté : un réimprimé depuis
   * l'historique n'aurait jamais porté le même numéro que l'original.</p>
   */
  describe('reçu imprimé à l\'encaissement', () => {
    let paymentService: PaymentService;
    let generateAndPrint: jasmine.Spy<PaymentReceiptPdfService['generateAndPrint']>;

    const paymentData = (): Payment => ({
      studentId: 42, groupId: 5, sessionSeriesId: 7, amountPaid: 1500, amountDue: 0,
      paymentType: 'SERIES', paymentMethod: 'cash', paymentForMonth: new Date(2026, 9, 1),
      status: 'PENDING'
    });

    beforeEach(() => {
      paymentService = TestBed.inject(PaymentService);
      generateAndPrint = spyOn(TestBed.inject(PaymentReceiptPdfService), 'generateAndPrint')
        .and.resolveTo();
    });

    it('imprime le numéro, la date et l\'auteur fixés par le serveur', () => {
      spyOn(paymentService, 'processPayment').and.returnValue(of(anAllocation()));

      component.submitPayment(paymentData());

      const printed = generateAndPrint.calls.mostRecent().args[0];
      expect(printed.reference).toBe('RECU-2026-0042');
      expect(printed.issuedAt.getTime()).toBe(new Date('2026-10-03T09:15:00').getTime());
      expect(printed.adminUsername).toBe('caissier1');
    });

    it('imprime le montant de CE versement, jamais le cumul de la série', () => {
      // payment.amountPaid porte le cumul : 4 000 dont 2 500 versés auparavant.
      spyOn(paymentService, 'processPayment').and.returnValue(of(anAllocation()));

      component.submitPayment(paymentData());

      expect(generateAndPrint.calls.mostRecent().args[0].amountPaid).toBe(1500);
    });

    it('le rattrapage imprime lui aussi le reçu enregistré', () => {
      const catchUp = anAllocation({ receiptNumber: 'RECU-2026-0043', kind: 'CATCH_UP' });
      spyOn(paymentService, 'processCatchUpPayment').and.returnValue(of(catchUp));
      component.nextCatchUpSessionId = 300;

      component.submitPayment(paymentData());

      expect(generateAndPrint.calls.mostRecent().args[0].reference).toBe('RECU-2026-0043');
      expect(dialogRef.close).toHaveBeenCalledWith(catchUp);
    });
  });
});

/** Réponse d'encaissement : 1 500 DA imputés sur la série 7, cumul de la série 4 000 DA. */
function anAllocation(encashment: Partial<Encashment> = {}): PaymentAllocationResult {
  return {
    studentId: 42, groupId: 5, seriesId: 7,
    amountReceived: 1500, amountAllocated: 1500, amountCarriedOver: 0, carryOvers: [],
    payment: {
      id: 11, studentId: 42, groupId: 5, sessionSeriesId: 7, amountPaid: 4000, amountDue: 4000,
      paymentType: 'SERIES', paymentForMonth: new Date(2026, 9, 1), status: 'PAID'
    },
    encashment: {
      id: 3, receiptNumber: 'RECU-2026-0042', status: 'ACTIVE', kind: 'REGULAR',
      amountReceived: 1500, paymentMethod: 'cash', receivedAt: '2026-10-03T09:15:00',
      receivedBy: 'caissier1', studentId: 42, studentName: 'Amina Belkacem', groupId: 5,
      groupName: 'Maths 1B', targetSeriesId: 7, targetSeriesName: 'Octobre',
      allocations: [{ seriesId: 7, seriesName: 'Octobre', amount: 1500, carriedOver: false, active: true }],
      ...encashment
    }
  };
}
