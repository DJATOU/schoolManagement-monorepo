import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import frTranslations from '../../../../../assets/i18n/fr.json';
import { CorrectionDialogComponent, CorrectionDialogData } from '../../../shared/correction-dialog/correction-dialog.component';
import { GroupService } from '../../../../services/group.service';
import { PayoutSlipPdfService } from '../../../../services/payout-slip-pdf.service';
import { TeacherPayRateService } from '../../../../services/teacher-pay-rate.service';
import { TeacherPayoutService } from '../../../../services/teacher-payout.service';
import { TeacherService } from '../../../../services/teacher.service';
import { payout, rate, slip } from '../../../../../testing/payroll-fixtures';
import { setupComponentTestBed } from '../../../../../testing/setup';
import { ReplaceRateDialogComponent } from '../replace-rate-dialog/replace-rate-dialog.component';
import { isoDay, PaidTabComponent } from './paid-tab.component';

describe('PaidTabComponent', () => {
  let fixture: ComponentFixture<PaidTabComponent>;
  let component: PaidTabComponent;
  let payouts: jasmine.SpyObj<TeacherPayoutService>;
  let slipPdf: jasmine.SpyObj<PayoutSlipPdfService>;

  const cancelled = payout({ id: 41, payoutNumber: 'PAIE-2030-0002', status: 'CANCELLED', cancelledAt: '2030-02-03T09:00:00',
    cancelledBy: 'directrice', replacedByNumber: 'PAIE-2030-0003' });
  const deduction = payout({ id: 42, payoutNumber: 'PAIE-2030-0004', kind: 'REGULARIZATION', teacherAmount: -600,
    schoolAmount: -400, baseDelta: -1000 });

  beforeEach(async () => {
    payouts = jasmine.createSpyObj<TeacherPayoutService>('TeacherPayoutService',
      ['searchPayouts', 'issueSlip', 'getCorrectionReasons', 'cancel', 'replace']);
    payouts.searchPayouts.and.returnValue(of({ payouts: [payout(), cancelled, deduction], teacherTotal: 42600, schoolTotal: 28400 }));
    payouts.getCorrectionReasons.and.returnValue(of(['DATA_ENTRY_ERROR', 'WRONG_AMOUNT', 'OTHER']));
    slipPdf = jasmine.createSpyObj<PayoutSlipPdfService>('PayoutSlipPdfService', ['print']);
    slipPdf.print.and.resolveTo();
    await setupComponentTestBed(PaidTabComponent, {
      providers: [
        { provide: TeacherPayoutService, useValue: payouts },
        { provide: TeacherPayRateService, useValue: { getRates: () => of([rate(), rate({ id: 8, label: 'Confirmé' })]) } },
        { provide: PayoutSlipPdfService, useValue: slipPdf },
        { provide: TeacherService, useValue: { getTeachers: () => of([]) } },
        { provide: GroupService, useValue: { getGroups: () => of([]) } }
      ]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', frTranslations);
    translate.use('fr');
    fixture = TestBed.createComponent(PaidTabComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  function row(index: number): HTMLElement {
    return (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>('tr.mat-mdc-row')[index];
  }

  it('active : réimprimer, refaire, annuler ; annulée : réimprimer seulement ; régularisation : pas de remplacement', () => {
    expect(row(0).querySelector('.tp-reprint')).not.toBeNull();
    expect(row(0).querySelector('.tp-replace')).not.toBeNull();
    expect(row(0).querySelector('.tp-cancel')).not.toBeNull();
    expect(row(1).querySelector('.tp-reprint')).not.toBeNull();
    expect(row(1).querySelector('.tp-cancel')).toBeNull();
    expect(row(2).querySelector('.tp-replace')).toBeNull();
    expect(row(2).querySelector('.tp-cancel')).not.toBeNull();
    expect(component.nature(deduction)).toBe('DEDUCTION');
  });

  it('les filtres de date partent au format du serveur', () => {
    component.filterForm.patchValue({ from: new Date(2030, 1, 1), status: 'ACTIVE' });
    expect(payouts.searchPayouts).toHaveBeenCalledWith(jasmine.objectContaining({ from: '2030-02-01', status: 'ACTIVE' }));
    expect(isoDay(null)).toBeNull();
    expect(isoDay(new Date(2030, 11, 31))).toBe('2030-12-31');
  });

  it('réimprimer : le serveur enregistre l\'impression, puis le bordereau s\'imprime', () => {
    payouts.issueSlip.and.returnValue(of(slip({ issuanceRank: 2 })));
    component.reprint(payout());
    expect(payouts.issueSlip).toHaveBeenCalledWith(40);
    expect(slipPdf.print).toHaveBeenCalledWith(slip({ issuanceRank: 2 }));
  });

  it('annuler : dialogue commun des corrections, branché sur l\'annulation de cette paie', () => {
    const openSpy = spyOn(fixture.debugElement.injector.get(MatDialog), 'open')
      .and.returnValue({ afterClosed: () => of({ kind: 'confirmed', result: cancelled }) } as MatDialogRef<unknown>);
    payouts.cancel.and.returnValue(of({ preview: { series: [], effects: [], amountsUnchanged: true }, previewToken: 't', result: null }));

    component.cancel(payout());

    const [componentType, config] = openSpy.calls.mostRecent().args;
    expect(componentType).toBe(CorrectionDialogComponent);
    const data = config?.data as CorrectionDialogData<unknown>;
    expect(data.reasons).toEqual(['DATA_ENTRY_ERROR', 'WRONG_AMOUNT', 'OTHER']);
    expect(data.subject.replace(/\s+/g, ' '))
      .toBe('Paie PAIE-2030-0001 de 43 200,00 DA à Nadia Aït Ahmed (Octobre, Maths 4 AM A)');
    data.run('preview', { type: 'DATA_ENTRY_ERROR' }).subscribe();
    expect(payouts.cancel).toHaveBeenCalledWith(40, 'preview', { type: 'DATA_ENTRY_ERROR' }, undefined);
    expect(payouts.searchPayouts).toHaveBeenCalledTimes(2);
  });

  it('remplacer : choix du taux, puis dialogue des corrections avec ce taux ; « Modifier » ramène au choix', () => {
    const choice = { rateId: 8, note: null };
    const openSpy = spyOn(fixture.debugElement.injector.get(MatDialog), 'open').and.returnValues(
      { afterClosed: () => of(choice) } as MatDialogRef<unknown>,
      { afterClosed: () => of({ kind: 'back' }) } as MatDialogRef<unknown>,
      { afterClosed: () => of(undefined) } as MatDialogRef<unknown>);
    payouts.replace.and.returnValue(of({ preview: { series: [], effects: [], amountsUnchanged: true }, previewToken: 't', result: null }));

    component.replace(payout());

    const calls = openSpy.calls.all();
    expect(calls[0].args[0]).toBe(ReplaceRateDialogComponent);
    expect(calls[1].args[0]).toBe(CorrectionDialogComponent);
    const data = calls[1].args[1]?.data as CorrectionDialogData<unknown>;
    expect(data.allowBack).toBeTrue();
    data.run('confirm', { type: 'WRONG_AMOUNT' }, 'tok').subscribe();
    expect(payouts.replace).toHaveBeenCalledWith(40, 'confirm', 8, null, { type: 'WRONG_AMOUNT' }, 'tok');
    // Retour : le choix du taux est rouvert avec le choix précédent.
    expect(calls[2].args[0]).toBe(ReplaceRateDialogComponent);
    expect((calls[2].args[1]?.data as { previous: unknown }).previous).toEqual(choice);
  });
});
