import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { PayoutSlipPdfService } from '../../../services/payout-slip-pdf.service';
import { TeacherPayoutService } from '../../../services/teacher-payout.service';
import { payout, slip } from '../../../../testing/payroll-fixtures';
import { setupComponentTestBed } from '../../../../testing/setup';
import { TeacherPayoutsComponent } from './teacher-payouts.component';

describe('TeacherPayoutsComponent', () => {
  let fixture: ComponentFixture<TeacherPayoutsComponent>;
  let component: TeacherPayoutsComponent;
  let payouts: jasmine.SpyObj<TeacherPayoutService>;
  let slipPdf: jasmine.SpyObj<PayoutSlipPdfService>;

  beforeEach(async () => {
    payouts = jasmine.createSpyObj<TeacherPayoutService>('TeacherPayoutService', ['getTeacherPayouts', 'issueSlip']);
    slipPdf = jasmine.createSpyObj<PayoutSlipPdfService>('PayoutSlipPdfService', ['print']);
    slipPdf.print.and.resolveTo();
    await setupComponentTestBed(TeacherPayoutsComponent, {
      providers: [{ provide: TeacherPayoutService, useValue: payouts }, { provide: PayoutSlipPdfService, useValue: slipPdf }]
    });
    fixture = TestBed.createComponent(TeacherPayoutsComponent);
    component = fixture.componentInstance;
  });

  function show(teacherId: number): void {
    component.teacherId = teacherId;
    component.ngOnChanges();
    fixture.detectChanges();
  }

  it('liste les paies de l\'enseignant, annulées marquées, avec le total versé', () => {
    payouts.getTeacherPayouts.and.returnValue(of({
      payouts: [payout(), payout({ id: 41, payoutNumber: 'PAIE-2030-0002', status: 'CANCELLED' })],
      teacherTotal: 43200, schoolTotal: 28800
    }));

    show(5);

    const element = fixture.nativeElement as HTMLElement;
    expect(payouts.getTeacherPayouts).toHaveBeenCalledWith(5);
    expect(element.querySelectorAll('.tpo-item').length).toBe(2);
    expect(element.querySelectorAll('.tpo-item--cancelled').length).toBe(1);
    expect(element.querySelector('.tpo-total')).not.toBeNull();
  });

  it('sans enseignant : rien n\'est demandé', () => {
    component.teacherId = null;
    component.ngOnChanges();
    expect(payouts.getTeacherPayouts).not.toHaveBeenCalled();
  });

  it('refus du serveur affiché', () => {
    payouts.getTeacherPayouts.and.returnValue(throwError(() => new Error('Enseignant introuvable : 5')));
    show(5);
    expect((fixture.nativeElement as HTMLElement).querySelector('.tpo-error')?.textContent).toContain('introuvable');
  });

  it('réimprimer : impression enregistrée par le serveur, puis bordereau', () => {
    payouts.getTeacherPayouts.and.returnValue(of({ payouts: [payout()], teacherTotal: 43200, schoolTotal: 28800 }));
    payouts.issueSlip.and.returnValue(of(slip({ issuanceRank: 2 })));
    show(5);

    component.reprint(payout());

    expect(payouts.issueSlip).toHaveBeenCalledWith(40);
    expect(slipPdf.print).toHaveBeenCalledWith(slip({ issuanceRank: 2 }));
    expect(component.printingId).toBeNull();
  });
});
