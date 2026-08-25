import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PaymentDialogComponent } from './payment-dialog.component';
import { createDialogRefSpy, DialogRefSpy, matDialogProviders, setupComponentTestBed } from '../../../../testing/setup';
import { aGroup } from '../../../../testing/fixtures';

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
});
