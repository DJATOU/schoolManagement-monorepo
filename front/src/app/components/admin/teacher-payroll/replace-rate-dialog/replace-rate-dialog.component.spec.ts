import { TestBed } from '@angular/core/testing';
import { payout, rate } from '../../../../../testing/payroll-fixtures';
import { createDialogRefSpy, DialogRefSpy, matDialogProviders, setupComponentTestBed } from '../../../../../testing/setup';
import { ReplaceRateDialogComponent, ReplaceRateDialogData } from './replace-rate-dialog.component';

describe('ReplaceRateDialogComponent', () => {
  let dialogRef: DialogRefSpy;

  async function create(data: ReplaceRateDialogData): Promise<ReplaceRateDialogComponent> {
    dialogRef = createDialogRefSpy();
    await setupComponentTestBed(ReplaceRateDialogComponent, { providers: matDialogProviders(data, dialogRef) });
    const fixture = TestBed.createComponent(ReplaceRateDialogComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  const rates = [
    rate(),
    rate({ id: 8, label: 'Confirmé', teacherPercent: 65 }),
    rate({ id: 9, label: 'Ancien', teacherPercent: 50, active: false })
  ];

  it('ne propose ni le taux de la paie, ni un taux désactivé ; aucun choix par défaut', async () => {
    const component = await create({ payout: payout(), rates });

    expect(component.rates.map(r => r.label)).toEqual(['Confirmé']);
    expect(component.form.get('rateId')!.value).toBeNull();
    component.next();
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('rend le taux choisi et la note nettoyée ; note vide : aucune', async () => {
    const component = await create({ payout: payout(), rates });

    component.form.setValue({ rateId: 8, note: '  Virement  ' });
    component.next();
    expect(dialogRef.close).toHaveBeenCalledWith({ rateId: 8, note: 'Virement' });

    component.form.setValue({ rateId: 8, note: '   ' });
    component.next();
    expect(dialogRef.close).toHaveBeenCalledWith({ rateId: 8, note: null });
  });

  it('retour depuis l\'Aperçu : le choix précédent est repris', async () => {
    const component = await create({ payout: payout(), rates, previous: { rateId: 8, note: 'Virement' } });

    expect(component.form.value).toEqual({ rateId: 8, note: 'Virement' });
  });
});
