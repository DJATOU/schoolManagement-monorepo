import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PaymentHistoryDialogComponent } from './payment-history-dialog.component';
import { createDialogRefSpy, DialogRefSpy, matDialogProviders, setupComponentTestBed } from '../../../../../testing/setup';

/**
 * Historique de paiement d'un étudiant, série par série.
 *
 * <p>`data.studentId` est déréférencé au chargement. Les requêtes sont laissées en attente :
 * ce qui est vérifié est que le dialogue s'ouvre sur le bon étudiant et n'affiche aucune
 * série tant qu'aucun groupe n'est choisi — l'écran est un sélecteur avant d'être un tableau.</p>
 */
describe('PaymentHistoryDialogComponent', () => {
  let component: PaymentHistoryDialogComponent;
  let fixture: ComponentFixture<PaymentHistoryDialogComponent>;
  let dialogRef: DialogRefSpy;

  beforeEach(async () => {
    dialogRef = createDialogRefSpy();
    await setupComponentTestBed(PaymentHistoryDialogComponent, {
      providers: matDialogProviders({ studentId: 42 }, dialogRef)
    });
    fixture = TestBed.createComponent(PaymentHistoryDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('porte l\'étudiant sur lequel il a été ouvert', () => {
    expect(component.data.studentId).toBe(42);
  });

  it('n\'affiche aucune série tant qu\'aucun groupe n\'est sélectionné', () => {
    expect(component.selectedSeries).toBeFalsy();
    expect(component.sessionSeries).toEqual([]);
  });
});
