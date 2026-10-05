import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { finalize } from 'rxjs/operators';
import { CorrectionReason, CorrectionReasonType } from '../../../../models/correction/correction';
import { Group } from '../../../../models/group/group';
import { Payout, PayoutCorrection, PayoutList, PayoutStatus, payoutNature } from '../../../../models/payroll/payroll';
import { Teacher } from '../../../../models/teacher/teacher';
import { AmountPipe, formatAmount } from '../../../../pipes/amount.pipe';
import { CorrectionStep } from '../../../../services/encashment.service';
import { GroupService } from '../../../../services/group.service';
import { PayoutSlipPdfService } from '../../../../services/payout-slip-pdf.service';
import { TeacherPayRateService } from '../../../../services/teacher-pay-rate.service';
import { TeacherPayoutService } from '../../../../services/teacher-payout.service';
import { TeacherService } from '../../../../services/teacher.service';
import {
  CorrectionDialogComponent,
  CorrectionDialogData,
  CorrectionDialogResult
} from '../../../shared/correction-dialog/correction-dialog.component';
import {
  ReplaceRateDialogComponent,
  ReplaceRateDialogData,
  ReplacementChoice
} from '../replace-rate-dialog/replace-rate-dialog.component';

/**
 * Onglet « Paies versées » : filtrer, totaliser, réimprimer, corriger (spec teacher-payroll,
 * exigences 5.3, 7 et 9.2).
 *
 * <p>Les totaux sont ceux des seules paies actives du filtre, calculés par le serveur. Annuler et
 * remplacer passent par le dialogue commun des corrections : Motif, Aperçu, confirmation. Seule la
 * paie active la plus récente d'une série se corrige ; le serveur nomme celle à corriger d'abord.</p>
 */
@Component({
  selector: 'app-paid-tab',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatDatepickerModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatNativeDateModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    MatTableModule,
    MatTooltipModule,
    TranslateModule,
    AmountPipe
  ],
  templateUrl: './paid-tab.component.html',
  styleUrls: ['../teacher-payroll.shared.scss']
})
export class PaidTabComponent implements OnInit {
  readonly currencySuffix = 'DA';
  readonly statuses: PayoutStatus[] = ['ACTIVE', 'CANCELLED'];
  readonly displayedColumns = ['number', 'paidAt', 'teacher', 'series', 'nature', 'rate', 'base', 'teacherAmount',
    'schoolAmount', 'status', 'actions'];

  readonly filterForm: FormGroup;
  list: PayoutList | null = null;
  teachers: Teacher[] = [];
  groups: Group[] = [];
  isLoading = false;
  errorMessage = '';
  /** Paie dont le bordereau est en cours d'émission : une impression à la fois. */
  printingId: number | null = null;

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
    this.filterForm = fb.group({ teacherId: [null], groupId: [null], status: [null], from: [null], to: [null] });
  }

  ngOnInit(): void {
    this.teacherService.getTeachers().subscribe({ next: teachers => this.teachers = teachers || [], error: () => undefined });
    this.groupService.getGroups().subscribe({ next: groups => this.groups = groups || [], error: () => undefined });
    this.filterForm.valueChanges.subscribe(() => this.load());
    this.load();
  }

  get payouts(): Payout[] {
    return this.list?.payouts ?? [];
  }

  load(): void {
    const { teacherId, groupId, status, from, to } = this.filterForm.value;
    this.isLoading = true;
    this.errorMessage = '';
    this.payoutService.searchPayouts({ teacherId, groupId, status, from: isoDay(from), to: isoDay(to) })
      .pipe(finalize(() => this.isLoading = false))
      .subscribe({
        next: list => this.list = list,
        error: (err: Error) => {
          this.list = null;
          this.errorMessage = err.message;
        }
      });
  }

  resetFilters(): void {
    this.filterForm.reset({ teacherId: null, groupId: null, status: null, from: null, to: null });
  }

  nature(payout: Payout): string {
    return payoutNature(payout);
  }

  // ------------------------------------------------------------------
  // Bordereau
  // ------------------------------------------------------------------

  reprint(payout: Payout): void {
    if (this.printingId !== null) {
      return;
    }
    this.printingId = payout.id;
    this.payoutService.issueSlip(payout.id).pipe(finalize(() => this.printingId = null)).subscribe({
      next: slip => this.slipPdf.print(slip).catch(() => this.notify(this.translate.instant('teacherPayroll.slip.printError'))),
      error: () => this.notify(this.translate.instant('teacherPayroll.slip.printError'))
    });
  }

  // ------------------------------------------------------------------
  // Corrections
  // ------------------------------------------------------------------

  cancel(payout: Payout): void {
    this.payoutService.getCorrectionReasons().subscribe({
      next: reasons => {
        const data: CorrectionDialogData<Payout> = {
          titleKey: 'teacherPayroll.correction.cancelTitle',
          subject: this.subject(payout),
          reasons,
          run: (step: CorrectionStep, reason: CorrectionReason, token?: string) =>
            this.payoutService.cancel(payout.id, step, reason, token)
        };
        this.dialog.open<CorrectionDialogComponent<Payout>, CorrectionDialogData<Payout>, CorrectionDialogResult<Payout>>(
          CorrectionDialogComponent, { data, width: '620px', maxWidth: '95vw' })
          .afterClosed().subscribe(outcome => {
            if (outcome?.kind === 'confirmed') {
              this.snackBar.open(this.translate.instant('teacherPayroll.correction.cancelled', { number: payout.payoutNumber }),
                this.translate.instant('common.close'), { duration: 6000 });
              this.load();
            }
          });
      },
      error: (err: Error) => this.notify(err.message)
    });
  }

  /** Remplacer : choisir l'autre taux, puis Motif, Aperçu et confirmation. « Modifier » ramène au choix. */
  replace(payout: Payout, previous?: ReplacementChoice): void {
    this.rateService.getRates().subscribe({
      next: rates => {
        this.dialog.open<ReplaceRateDialogComponent, ReplaceRateDialogData, ReplacementChoice | undefined>(
          ReplaceRateDialogComponent, { data: { payout, rates, previous }, width: '560px', maxWidth: '95vw' })
          .afterClosed().subscribe(choice => {
            if (choice) {
              this.previewReplacement(payout, choice);
            }
          });
      },
      error: (err: Error) => this.notify(err.message)
    });
  }

  private previewReplacement(payout: Payout, choice: ReplacementChoice): void {
    this.payoutService.getCorrectionReasons().subscribe({
      next: (reasons: CorrectionReasonType[]) => {
        const data: CorrectionDialogData<PayoutCorrection> = {
          titleKey: 'teacherPayroll.correction.replaceTitle',
          subject: this.subject(payout),
          reasons,
          allowBack: true,
          run: (step: CorrectionStep, reason: CorrectionReason, token?: string) =>
            this.payoutService.replace(payout.id, step, choice.rateId, choice.note, reason, token)
        };
        this.dialog.open<CorrectionDialogComponent<PayoutCorrection>, CorrectionDialogData<PayoutCorrection>,
          CorrectionDialogResult<PayoutCorrection>>(CorrectionDialogComponent, { data, width: '620px', maxWidth: '95vw' })
          .afterClosed().subscribe(outcome => {
            if (outcome?.kind === 'back') {
              this.replace(payout, choice);
            } else if (outcome?.kind === 'confirmed') {
              const replacement = outcome.result.replacement;
              this.snackBar.open(this.translate.instant('teacherPayroll.correction.replaced', {
                number: payout.payoutNumber, replacement: replacement.payoutNumber
              }), this.translate.instant('teacherPayroll.slip.print'), { duration: 12000 })
                .onAction().subscribe(() => this.reprint(replacement));
              this.load();
            }
          });
      },
      error: (err: Error) => this.notify(err.message)
    });
  }

  /** « Paie PAIE-2030-0001 de 43 200,00 DA à Nadia Aït Ahmed (Octobre, Maths 4 AM A) ». */
  private subject(payout: Payout): string {
    return this.translate.instant('teacherPayroll.correction.subject.' + payoutNature(payout), {
      number: payout.payoutNumber,
      amount: formatAmount(Math.abs(payout.teacherAmount), this.translate.currentLang),
      teacher: payout.teacherName,
      series: payout.seriesName,
      group: payout.groupName
    });
  }

  private notify(message: string): void {
    this.snackBar.open(message, this.translate.instant('common.close'), { duration: 6000, panelClass: ['error-snackbar'] });
  }

  trackById(_: number, payout: Payout): number {
    return payout.id;
  }
}

/** Jour local au format `yyyy-MM-dd`, ce qu'attend le serveur ; rien sans date. */
export function isoDay(value: Date | string | null | undefined): string | null {
  if (!value) {
    return null;
  }
  const date = value instanceof Date ? value : new Date(value);
  if (isNaN(date.getTime())) {
    return null;
  }
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}
