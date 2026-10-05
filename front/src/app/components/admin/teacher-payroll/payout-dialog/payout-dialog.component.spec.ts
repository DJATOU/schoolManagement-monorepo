import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import frTranslations from '../../../../../assets/i18n/fr.json';
import { PayoutError } from '../../../../models/payroll/payroll';
import { TeacherPayoutService } from '../../../../services/teacher-payout.service';
import { payableSeries, payout, payoutPreview, rate } from '../../../../../testing/payroll-fixtures';
import {
  createDialogRefSpy,
  DialogRefSpy,
  matDialogProviders,
  setupComponentTestBed
} from '../../../../../testing/setup';
import { PayoutDialogComponent, PayoutDialogData } from './payout-dialog.component';

describe('PayoutDialogComponent', () => {
  let fixture: ComponentFixture<PayoutDialogComponent>;
  let component: PayoutDialogComponent;
  let dialogRef: DialogRefSpy;
  let payouts: jasmine.SpyObj<TeacherPayoutService>;

  async function open(data: PayoutDialogData): Promise<void> {
    dialogRef = createDialogRefSpy();
    payouts = jasmine.createSpyObj<TeacherPayoutService>('TeacherPayoutService',
      ['previewPay', 'confirmPay', 'previewRegularize', 'confirmRegularize']);
    await setupComponentTestBed(PayoutDialogComponent, {
      providers: [...matDialogProviders(data, dialogRef), { provide: TeacherPayoutService, useValue: payouts }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', frTranslations);
    translate.use('fr');
    fixture = TestBed.createComponent(PayoutDialogComponent);
    component = fixture.componentInstance;
  }

  /** Texte affiché, espaces de groupement des montants (U+202F en français) ramenées à une espace. */
  function text(): string {
    return ((fixture.nativeElement as HTMLElement).textContent ?? '').replace(/\s+/g, ' ');
  }

  function button(selector: string): HTMLButtonElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector(selector);
  }

  describe('payer', () => {
    beforeEach(async () => {
      await open({ series: payableSeries(), mode: 'pay', rates: [rate(), rate({ id: 8, label: 'Confirmé', teacherPercent: 65 })] });
      fixture.detectChanges();
    });

    it('aucun taux par défaut : le calcul attend le choix', () => {
      expect(component.form.get('rateId')!.value).toBeNull();
      expect(button('.pd-preview-button')!.disabled).toBeTrue();
      component.requestPreview();
      expect(payouts.previewPay).not.toHaveBeenCalled();
    });

    it('calcul en clair, puis confirmation avec le jeton lu ; ferme sur la paie enregistrée', () => {
      payouts.previewPay.and.returnValue(of(payoutPreview()));
      payouts.confirmPay.and.returnValue(of(payout()));
      component.form.patchValue({ rateId: 7, note: '  Remis en main propre ' });

      component.requestPreview();
      fixture.detectChanges();

      expect(payouts.previewPay).toHaveBeenCalledWith(12, 7, 'Remis en main propre');
      expect(text()).toContain('72 000,00 DA × 60 % = 43 200,00 DA pour Nadia Aït Ahmed');
      expect(text()).toContain('Part de l\'école : 28 800,00 DA');
      expect(text()).toContain('74 000,00 DA versés − 2 000,00 DA remboursés = 72 000,00 DA nets');

      component.confirm();
      expect(payouts.confirmPay).toHaveBeenCalledWith(12, 7, 'Remis en main propre', 'jeton-1');
      expect(dialogRef.close).toHaveBeenCalledWith(payout());
    });

    it('changer de taux efface le calcul : il faut le relire', () => {
      payouts.previewPay.and.returnValue(of(payoutPreview()));
      component.form.patchValue({ rateId: 7 });
      component.requestPreview();
      expect(component.preview).not.toBeNull();

      component.form.patchValue({ rateId: 8 });
      expect(component.preview).toBeNull();
      component.confirm();
      expect(payouts.confirmPay).not.toHaveBeenCalled();
    });

    it('encaissé changé depuis le calcul : le nouveau remplace l\'ancien, avec un avertissement', () => {
      payouts.previewPay.and.returnValue(of(payoutPreview()));
      const fresh = payoutPreview({ collectedNet: 73000, baseDelta: 73000, teacherAmount: 43800, previewToken: 'jeton-2' });
      payouts.confirmPay.and.returnValues(
        throwError(() => new PayoutError('Les montants ont changé', 409, 'STALE_PREVIEW', fresh, 'jeton-2')),
        of(payout()));
      component.form.patchValue({ rateId: 7 });
      component.requestPreview();

      component.confirm();
      fixture.detectChanges();

      expect(dialogRef.close).not.toHaveBeenCalled();
      expect(component.staleNotice).toBeTrue();
      expect(text()).toContain('43 800,00 DA');
      expect(text()).toContain('voici le nouveau');

      component.confirm();
      expect(payouts.confirmPay.calls.mostRecent().args[3]).toBe('jeton-2');
      expect(dialogRef.close).toHaveBeenCalledWith(payout());
    });

    it('refus du serveur affiché tel quel, sans calcul', () => {
      payouts.previewPay.and.returnValue(throwError(() =>
        new PayoutError('La série « Octobre » est déjà payée (PAIE-2030-0001).', 409)));
      component.form.patchValue({ rateId: 7 });

      component.requestPreview();
      fixture.detectChanges();

      expect(component.preview).toBeNull();
      expect(text()).toContain('déjà payée (PAIE-2030-0001)');
    });
  });

  describe('régulariser', () => {
    it('calcul demandé à l\'ouverture ; complément dit en clair, puis confirmé', async () => {
      await open({ series: payableSeries({ state: 'TO_REGULARIZE', initialPayoutNumber: 'PAIE-2030-0001' }), mode: 'regularize', rates: [] });
      payouts.previewRegularize.and.returnValue(of(payoutPreview({
        kind: 'REGULARIZATION', collectedNet: 75000, netCovered: 72000, baseDelta: 3000, teacherAmount: 1800,
        schoolAmount: 1200, teacherPaid: 43200, previewToken: 'reg-1'
      })));
      payouts.confirmRegularize.and.returnValue(of(payout({ kind: 'REGULARIZATION' })));
      fixture.detectChanges();

      expect(payouts.previewRegularize).toHaveBeenCalledWith(12);
      expect(text()).toContain('75 000,00 DA − déjà couvert 72 000,00 DA = 3 000,00 DA');
      expect(text()).toContain('45 000,00 DA ; déjà versé : 43 200,00 DA');
      expect(text()).toContain('Complément à verser à Nadia Aït Ahmed : 1 800,00 DA');
      expect(text()).toContain('paie initiale PAIE-2030-0001');

      component.confirm();
      expect(payouts.confirmRegularize).toHaveBeenCalledWith(12, null, 'reg-1');
    });

    it('retenue : somme à rendre, bouton « Enregistrer la retenue »', async () => {
      await open({ series: payableSeries({ state: 'TO_REGULARIZE' }), mode: 'regularize', rates: [] });
      payouts.previewRegularize.and.returnValue(of(payoutPreview({
        kind: 'REGULARIZATION', collectedNet: 71000, netCovered: 72000, baseDelta: -1000, teacherAmount: -600,
        schoolAmount: -400, teacherPaid: 43200
      })));
      fixture.detectChanges();

      expect(component.deduction).toBeTrue();
      expect(text()).toContain('Retenue : Nadia Aït Ahmed doit rendre 600,00 DA');
      expect(button('.pd-confirm-button')!.textContent).toContain('Enregistrer la retenue');
    });
  });
});
