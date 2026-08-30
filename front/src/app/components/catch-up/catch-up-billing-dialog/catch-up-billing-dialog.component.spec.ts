import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CatchUpBillingDialogComponent } from './catch-up-billing-dialog.component';
import { PendingCatchUp } from '../../../models/catchUp/catch-up-billing';
import { createDialogRefSpy, DialogRefSpy, matDialogProviders, setupComponentTestBed } from '../../../../testing/setup';

/**
 * Résolution de la facturation d'un rattrapage.
 *
 * <p>Ce qui est vérifié ici est l'absence de présélection et le verrou du bouton. Ce sont les
 * deux traductions visibles de la règle « aucune valeur par défaut » : un défaut ne serait jamais
 * relu, et le jour où il serait faux l'erreur passerait inaperçue — c'est-à-dire exactement le
 * scénario de double facturation que cet écran existe pour empêcher.</p>
 */
describe('CatchUpBillingDialogComponent', () => {
  let component: CatchUpBillingDialogComponent;
  let fixture: ComponentFixture<CatchUpBillingDialogComponent>;
  let dialogRef: DialogRefSpy;

  const pending: PendingCatchUp = {
    attendanceId: 126,
    studentId: 135,
    studentName: 'Younes Bennani',
    sessionId: 162,
    sessionTitle: 'ok voice 2',
    sessionDate: '2026-09-14T18:30:00',
    hostGroupId: 9,
    hostGroupName: 'groupe anglais',
    levelName: '3eme année',
    subjectName: 'Anglais',
    seriesId: 53,
    seriesName: 'groupe anglais - 09-2026-001'
  };

  beforeEach(async () => {
    dialogRef = createDialogRefSpy();
    await setupComponentTestBed(CatchUpBillingDialogComponent, {
      providers: matDialogProviders({ pending, mode: 'resolve' }, dialogRef)
    });
    fixture = TestBed.createComponent(CatchUpBillingDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('ne présélectionne aucune décision de facturation', () => {
    // `null` signifie « non tranché », et doit rester distinct de `false` (« à facturer »).
    expect(component.alreadyPaid).toBeNull();
    expect(component.selectedMissedSessionId).toBeNull();
  });

  it('interdit l\'enregistrement tant que les deux décisions manquent', () => {
    expect(component.canSave).toBeFalse();

    component.selectedMissedSessionId = 4;
    expect(component.canSave)
      .withContext('la séance manquée seule ne suffit pas')
      .toBeFalse();

    component.alreadyPaid = false;
    expect(component.canSave)
      .withContext('les deux décisions prises, y compris « à facturer »')
      .toBeTrue();
  });

  it('accepte « déjà payée » comme « à facturer » : les deux sont des décisions', () => {
    component.selectedMissedSessionId = 4;
    component.alreadyPaid = true;
    expect(component.canSave).toBeTrue();
  });

  it('n\'enregistre rien si les décisions ne sont pas prises', () => {
    component.save();
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('porte le rattrapage sur lequel il a été ouvert', () => {
    expect(component.data.pending.attendanceId).toBe(126);
    expect(component.isCorrection).toBeFalse();
  });
});
