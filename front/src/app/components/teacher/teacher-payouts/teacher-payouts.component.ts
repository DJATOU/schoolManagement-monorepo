import { CommonModule } from '@angular/common';
import { Component, Input, OnChanges } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { finalize } from 'rxjs/operators';
import { Payout, PayoutList, payoutNature } from '../../../models/payroll/payroll';
import { AmountPipe } from '../../../pipes/amount.pipe';
import { PayoutSlipPdfService } from '../../../services/payout-slip-pdf.service';
import { TeacherPayoutService } from '../../../services/teacher-payout.service';

/**
 * Panneau « Paies » de la fiche d'un enseignant : ce qui lui a été versé, et le total (spec
 * teacher-payroll, exigence 9.3). Pour lui répondre s'il conteste : chaque bordereau se réimprime.
 *
 * <p>Monté pour le rôle ADMIN seulement : les paies sont des pièces de caisse, l'API les refuse à
 * tout autre rôle. Les corrections se font depuis la page « Paie des enseignants ».</p>
 */
@Component({
  selector: 'app-teacher-payouts',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatExpansionModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    TranslateModule,
    AmountPipe
  ],
  templateUrl: './teacher-payouts.component.html',
  styleUrls: ['./teacher-payouts.component.scss']
})
export class TeacherPayoutsComponent implements OnChanges {
  @Input() teacherId: number | null | undefined;

  readonly currencySuffix = 'DA';

  list: PayoutList | null = null;
  loading = false;
  errorMessage = '';
  printingId: number | null = null;

  constructor(
    private payoutService: TeacherPayoutService,
    private slipPdf: PayoutSlipPdfService,
    private snackBar: MatSnackBar,
    private translate: TranslateService
  ) {}

  ngOnChanges(): void {
    this.reload();
  }

  get payouts(): Payout[] {
    return this.list?.payouts ?? [];
  }

  reload(): void {
    if (!this.teacherId) {
      return;
    }
    this.loading = true;
    this.errorMessage = '';
    this.payoutService.getTeacherPayouts(this.teacherId).pipe(finalize(() => this.loading = false)).subscribe({
      next: list => this.list = list,
      error: (err: Error) => {
        this.list = null;
        this.errorMessage = err.message;
      }
    });
  }

  nature(payout: Payout): string {
    return payoutNature(payout);
  }

  reprint(payout: Payout): void {
    if (this.printingId !== null) {
      return;
    }
    this.printingId = payout.id;
    this.payoutService.issueSlip(payout.id).pipe(finalize(() => this.printingId = null)).subscribe({
      next: slip => this.slipPdf.print(slip).catch(() => this.printFailed()),
      error: () => this.printFailed()
    });
  }

  trackById(_: number, payout: Payout): number {
    return payout.id;
  }

  private printFailed(): void {
    this.snackBar.open(this.translate.instant('teacherPayroll.slip.printError'), this.translate.instant('common.close'),
      { duration: 6000, panelClass: ['error-snackbar'] });
  }
}
