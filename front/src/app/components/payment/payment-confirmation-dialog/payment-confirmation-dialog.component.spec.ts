import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';

import { PaymentConfirmationDialogComponent } from './payment-confirmation-dialog.component';
import { createDialogRefSpy, DialogRefSpy, matDialogProviders, setupComponentTestBed } from '../../../../testing/setup';

/**
 * Dernier écran avant l'encaissement d'un versement.
 *
 * <p>C'est ici que l'administrateur peut constater qu'une partie du montant ne créditera
 * <strong>pas</strong> la série qu'il a choisie : le report est automatique et n'a pas
 * d'étape de confirmation propre (exigence 9.3). Un récapitulatif qui tairait le report
 * laisserait découvrir après coup une série créditée d'un montant inférieur à celui encaissé.</p>
 */
describe('PaymentConfirmationDialogComponent', () => {
  let component: PaymentConfirmationDialogComponent;
  let fixture: ComponentFixture<PaymentConfirmationDialogComponent>;
  let dialogRef: DialogRefSpy;

  const baseData = {
    seriesName: 'Septembre 2025',
    numberOfSessions: 4,
    excludedSessions: 0,
    pricePerSession: 2000,
    totalCost: 8000,
    paymentDetails: [],
    paymentHistory: [],
    totalPaid: 8000,
    remainingAmount: 0,
    isCatchUp: false,
    calculationNote: ''
  };

  async function build(overrides: Record<string, unknown> = {}): Promise<void> {
    TestBed.resetTestingModule();
    dialogRef = createDialogRefSpy();
    await setupComponentTestBed(PaymentConfirmationDialogComponent, {
      providers: matDialogProviders({ ...baseData, ...overrides }, dialogRef)
    });

    // Libellés réels du bloc de répartition : sans eux, le template rendrait les clés brutes
    // et l'assertion sur le nom de la série destinataire ne vérifierait plus rien.
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', {
      payment: {
        dialog: {
          allocation: {
            title: 'Répartition du versement',
            received: 'Montant reçu : {{amount}} DA',
            allocated: 'Imputé sur « {{series}} » : {{amount}} DA',
            carriedOver: 'Reporté sur « {{series}} » : {{amount}} DA',
            carriedOverTotal: 'Total reporté : {{amount}} DA'
          }
        }
      }
    });
    translate.use('fr');

    fixture = TestBed.createComponent(PaymentConfirmationDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await build();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('nomme la série concernée', () => {
    expect(fixture.nativeElement.textContent).toContain('Septembre 2025');
  });

  it('n\'annonce aucun report quand le versement tient sur la série choisie', () => {
    expect(component.hasCarryOver).toBeFalse();
    expect(component.carriedOverTotal).toBe(0);
  });

  it('annonce le report et en totalise les parts', async () => {
    await build({
      amountReceived: 12000,
      amountAllocated: 8000,
      carryOvers: [
        { seriesId: 11, seriesName: 'Octobre 2025', amount: 3000 },
        { seriesId: 12, seriesName: 'Novembre 2025', amount: 1000 }
      ]
    });

    expect(component.hasCarryOver).toBeTrue();
    expect(component.carriedOverTotal).toBe(4000);
  });

  it('nomme les séries destinataires du report', async () => {
    await build({
      amountReceived: 12000,
      amountAllocated: 8000,
      carryOvers: [{ seriesId: 11, seriesName: 'Octobre 2025', amount: 4000 }]
    });

    // Exigence 9.3 : sans le nom de la série créditée, le report est inexplicable à la famille.
    expect(fixture.nativeElement.textContent).toContain('Octobre 2025');
  });

  it('la part imputée plus les parts reportées égalent le montant reçu', async () => {
    await build({
      amountReceived: 12000,
      amountAllocated: 8000,
      carryOvers: [{ seriesId: 11, seriesName: 'Octobre 2025', amount: 4000 }]
    });

    expect(component.data.amountAllocated! + component.carriedOverTotal)
      .toBe(component.data.amountReceived!);
  });

  it('ferme sur « vrai » à la confirmation', () => {
    component.onConfirm();

    expect(dialogRef.close).toHaveBeenCalledWith(true);
  });
});
