import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarRef, TextOnlySnackBar } from '@angular/material/snack-bar';
import { of, Subject } from 'rxjs';
import { GroupService } from '../../../../services/group.service';
import { PayoutSlipPdfService } from '../../../../services/payout-slip-pdf.service';
import { TeacherPayRateService } from '../../../../services/teacher-pay-rate.service';
import { TeacherPayoutService } from '../../../../services/teacher-payout.service';
import { TeacherService } from '../../../../services/teacher.service';
import { payableSeries, payout, rate, slip } from '../../../../../testing/payroll-fixtures';
import { setupComponentTestBed } from '../../../../../testing/setup';
import { PayoutDialogComponent } from '../payout-dialog/payout-dialog.component';
import { PayableTabComponent } from './payable-tab.component';

describe('PayableTabComponent', () => {
  let fixture: ComponentFixture<PayableTabComponent>;
  let component: PayableTabComponent;
  let payouts: jasmine.SpyObj<TeacherPayoutService>;
  let rates: jasmine.SpyObj<TeacherPayRateService>;
  let slipPdf: jasmine.SpyObj<PayoutSlipPdfService>;

  const rows = [
    payableSeries(),
    payableSeries({ seriesId: 13, seriesName: 'Novembre', state: 'NOT_FINISHED', validatedSessions: 5 }),
    payableSeries({ seriesId: 14, seriesName: 'Septembre', state: 'TO_REGULARIZE', initialPayoutNumber: 'PAIE-2030-0001',
      gap: -600 }),
    payableSeries({ seriesId: 15, seriesName: 'Mai', state: 'NO_TEACHER', teacherId: null, teacherName: null })
  ];

  beforeEach(async () => {
    payouts = jasmine.createSpyObj<TeacherPayoutService>('TeacherPayoutService', ['getPayable', 'issueSlip']);
    payouts.getPayable.and.returnValue(of(rows));
    rates = jasmine.createSpyObj<TeacherPayRateService>('TeacherPayRateService', ['getRates']);
    rates.getRates.and.returnValue(of([rate(), rate({ id: 9, label: 'Ancien', active: false })]));
    slipPdf = jasmine.createSpyObj<PayoutSlipPdfService>('PayoutSlipPdfService', ['print']);
    slipPdf.print.and.resolveTo();
    await setupComponentTestBed(PayableTabComponent, {
      providers: [
        { provide: TeacherPayoutService, useValue: payouts },
        { provide: TeacherPayRateService, useValue: rates },
        { provide: PayoutSlipPdfService, useValue: slipPdf },
        { provide: TeacherService, useValue: { getTeachers: () => of([{ id: 5, firstName: 'Nadia', lastName: 'Aït Ahmed' }]) } },
        { provide: GroupService, useValue: { getGroups: () => of([{ id: 3, name: 'Maths 4 AM A' }]) } }
      ]
    });
    fixture = TestBed.createComponent(PayableTabComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  function row(index: number): HTMLElement {
    return (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>('tr.mat-mdc-row')[index];
  }

  it('une action par ligne qui en appelle une : payer, régulariser ; rien sinon', () => {
    expect(row(0).querySelector('.tp-pay')).not.toBeNull();
    expect(row(1).querySelector('.tp-pay')).toBeNull();
    expect(row(2).querySelector('.tp-regularize')).not.toBeNull();
    expect(row(3).querySelector('.tp-pay, .tp-regularize')).toBeNull();
  });

  it('dit pourquoi une série ne se paie pas encore', () => {
    expect(component.remaining(rows[1])).toBe(3);
    expect(component.gapKey(rows[2])).toBe('teacherPayroll.payable.deductionHint');
    expect(component.gapAmount(rows[2])).toBe(600);
  });

  it('les filtres relisent la liste', () => {
    component.filterForm.patchValue({ teacherId: 5 });
    expect(payouts.getPayable).toHaveBeenCalledWith(5, null);
  });

  it('payer : le dialogue reçoit les seuls taux actifs ; paie enregistrée, bordereau proposé puis imprimé', () => {
    const dialog = fixture.debugElement.injector.get(MatDialog);
    const openSpy = spyOn(dialog, 'open').and.returnValue({ afterClosed: () => of(payout()) } as MatDialogRef<unknown>);
    const action = new Subject<void>();
    const snackBar = fixture.debugElement.injector.get(MatSnackBar);
    spyOn(snackBar, 'open').and.returnValue({ onAction: () => action.asObservable() } as MatSnackBarRef<TextOnlySnackBar>);
    payouts.issueSlip.and.returnValue(of(slip()));

    component.open(rows[0]);

    expect(openSpy).toHaveBeenCalledTimes(1);
    const [componentType, config] = openSpy.calls.mostRecent().args;
    expect(componentType).toBe(PayoutDialogComponent);
    expect((config?.data as { mode: string }).mode).toBe('pay');
    expect((config?.data as { rates: unknown[] }).rates.length).toBe(1);
    expect(payouts.getPayable).toHaveBeenCalledTimes(2);

    action.next();
    expect(payouts.issueSlip).toHaveBeenCalledWith(40);
    expect(slipPdf.print).toHaveBeenCalledWith(slip());
  });

  it('régulariser : le dialogue s\'ouvre en mode régularisation ; fermé sans paie, rien n\'est relu', () => {
    const dialog = fixture.debugElement.injector.get(MatDialog);
    const openSpy = spyOn(dialog, 'open').and.returnValue({ afterClosed: () => of(undefined) } as MatDialogRef<unknown>);

    component.open(rows[2]);

    expect((openSpy.calls.mostRecent().args[1]?.data as { mode: string }).mode).toBe('regularize');
    expect(payouts.getPayable).toHaveBeenCalledTimes(1);
  });

  it('ligne sans action : aucun dialogue', () => {
    const openSpy = spyOn(fixture.debugElement.injector.get(MatDialog), 'open');
    component.open(rows[1]);
    expect(openSpy).not.toHaveBeenCalled();
  });
});
