import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { finalize } from 'rxjs/operators';
import { Group } from '../../../../models/group/group';
import { PayableSeries, Payout } from '../../../../models/payroll/payroll';
import { Teacher } from '../../../../models/teacher/teacher';
import { AmountPipe, formatAmount } from '../../../../pipes/amount.pipe';
import { GroupService } from '../../../../services/group.service';
import { PayoutSlipPdfService } from '../../../../services/payout-slip-pdf.service';
import { TeacherPayRateService } from '../../../../services/teacher-pay-rate.service';
import { TeacherPayoutService } from '../../../../services/teacher-payout.service';
import { TeacherService } from '../../../../services/teacher.service';
import { PayoutDialogComponent, PayoutDialogData } from '../payout-dialog/payout-dialog.component';

/**
 * Onglet « À payer » : les séries terminées à payer, celles en cours, et les séries payées dont
 * l'encaissé a changé depuis (spec teacher-payroll, exigence 2).
 *
 * <p>Une série en cours dit combien de séances restent à valider ; une série sans enseignant ou sans
 * encaissé dit pourquoi elle ne se paie pas. Payer, ou régulariser, ouvre le dialogue du calcul ;
 * une fois la paie enregistrée, le bordereau est proposé à l'impression (exigence 5.1).</p>
 */
@Component({
  selector: 'app-payable-tab',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
    MatTooltipModule,
    TranslateModule,
    AmountPipe
  ],
  templateUrl: './payable-tab.component.html',
  styleUrls: ['../teacher-payroll.shared.scss']
})
export class PayableTabComponent implements OnInit {
  readonly currencySuffix = 'DA';
  readonly displayedColumns = ['group', 'series', 'teacher', 'sessions', 'collected', 'state', 'actions'];

  readonly filterForm: FormGroup;
  rows: PayableSeries[] = [];
  teachers: Teacher[] = [];
  groups: Group[] = [];
  isLoading = false;
  errorMessage = '';
  /** Ouverture du dialogue en cours : les taux sont relus à chaque paie. */
  opening = false;

  constructor(
    fb: FormBuilder,
    private payoutService: TeacherPayoutService,
    private rateService: TeacherPayRateService,
    private teacherService: TeacherService,
    private groupService: GroupService,
    private slipPdf: PayoutSlipPdfService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
    private translate: TranslateService
  ) {
    // includePaid : une série payée et à jour quittait l'onglet ; sur demande, elle y reste visible.
    this.filterForm = fb.group({ teacherId: [null], groupId: [null], includePaid: [false] });
  }

  ngOnInit(): void {
    this.teacherService.getTeachers().subscribe({ next: teachers => this.teachers = teachers || [], error: () => undefined });
    this.groupService.getGroups().subscribe({ next: groups => this.groups = groups || [], error: () => undefined });
    this.filterForm.valueChanges.subscribe(() => this.load());
    this.load();
  }

  load(): void {
    const { teacherId, groupId, includePaid } = this.filterForm.value;
    this.isLoading = true;
    this.errorMessage = '';
    this.payoutService.getPayable(teacherId, groupId, !!includePaid)
      .pipe(finalize(() => this.isLoading = false))
      .subscribe({
        next: rows => this.rows = rows,
        error: (err: Error) => {
          this.rows = [];
          this.errorMessage = err.message;
        }
      });
  }

  /** Ligne qui appelle une action : à payer, ou à régulariser. */
  actionable(row: PayableSeries): boolean {
    return row.state === 'PAYABLE' || row.state === 'TO_REGULARIZE';
  }

  remaining(row: PayableSeries): number {
    return row.activeSessions - row.validatedSessions;
  }

  /**
   * Le nombre prévu ne se montre que s'il dit autre chose que le nombre de séances actives : « 0 / 2
   * (3 prévues) ». Inconnu (0), ou égal, il n'apporterait rien.
   */
  showPlanned(row: PayableSeries): boolean {
    return row.plannedSessions > 0 && row.plannedSessions !== row.activeSessions;
  }

  /** Un écart négatif est une retenue : de l'argent a été rendu depuis la paie. */
  gapKey(row: PayableSeries): string {
    return (row.gap ?? 0) < 0 ? 'teacherPayroll.payable.deductionHint' : 'teacherPayroll.payable.complementHint';
  }

  gapAmount(row: PayableSeries): number {
    return Math.abs(row.gap ?? 0);
  }

  /** Ouvre le calcul : paie d'une série terminée, ou régularisation d'une série payée. */
  open(row: PayableSeries): void {
    if (this.opening || !this.actionable(row)) {
      return;
    }
    const mode = row.state === 'PAYABLE' ? 'pay' : 'regularize';
    this.opening = true;
    this.rateService.getRates().pipe(finalize(() => this.opening = false)).subscribe({
      next: rates => {
        const data: PayoutDialogData = { series: row, mode, rates: rates.filter(rate => rate.active) };
        this.dialog.open<PayoutDialogComponent, PayoutDialogData, Payout | undefined>(PayoutDialogComponent,
          { data, width: '620px', maxWidth: '95vw' })
          .afterClosed().subscribe(payout => {
            if (payout) {
              this.recorded(payout);
              this.load();
            }
          });
      },
      error: (err: Error) => this.notify(err.message)
    });
  }

  /** Paie enregistrée : on le dit, et on propose son bordereau. */
  private recorded(payout: Payout): void {
    const message = this.translate.instant('teacherPayroll.payable.recorded', {
      number: payout.payoutNumber, amount: formatAmount(payout.teacherAmount, this.translate.currentLang)
    });
    this.snackBar.open(message, this.translate.instant('teacherPayroll.slip.print'), { duration: 12000 })
      .onAction().subscribe(() => this.printSlip(payout));
  }

  private printSlip(payout: Payout): void {
    this.payoutService.issueSlip(payout.id).subscribe({
      next: slip => this.slipPdf.print(slip).catch(() => this.notify(this.translate.instant('teacherPayroll.slip.printError'))),
      error: () => this.notify(this.translate.instant('teacherPayroll.slip.printError'))
    });
  }

  private notify(message: string): void {
    this.snackBar.open(message, this.translate.instant('common.close'), { duration: 6000, panelClass: ['error-snackbar'] });
  }

  trackBySeries(_: number, row: PayableSeries): number {
    return row.seriesId;
  }
}
