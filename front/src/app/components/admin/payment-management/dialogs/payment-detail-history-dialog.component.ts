import { CommonModule } from '@angular/common';
import { Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar } from '@angular/material/snack-bar';
import { HttpClient } from '@angular/common/http';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { finalize } from 'rxjs';
import { API_BASE_URL } from '../../../../api-base-url';
import { StudentRefund } from '../../../../models/refund/refund';
import { AmountPipe } from '../../../../pipes/amount.pipe';
import { RefundService } from '../../../../services/refund.service';
import { RefundReceiptPdfService } from '../../../../services/refund-receipt-pdf.service';

interface PaymentDetailAudit {
  id: number;
  paymentDetailId: number;
  action: string;
  performedBy: string;
  timestamp: string;
  oldValue?: string;
  newValue?: string;
  reason?: string;
}

/** Ligne de l'écran « Gestion des paiements » ouverte dans le dialogue. */
export interface PaymentDetailHistoryData {
  /** Identifiant de la ligne de ventilation. */
  id: number;
  /** Versement de la ligne : ses remboursements sont listés. */
  paymentId?: number | null;
}

/**
 * Historique d'une ligne de versement : les remboursements de son versement, puis le journal
 * d'audit de la ligne elle-même.
 *
 * <p>Les remboursements viennent en premier : ce sont eux qui expliquent un montant affiché net,
 * barré dans le tableau. Chacun porte sa pièce, sa date, son montant, son motif, son auteur, et se
 * réimprime (le serveur compte l'émission, à partir de la deuxième le reçu porte « DUPLICATA »).
 * Le journal d'audit de la ligne ne reçoit plus d'écritures depuis que les lignes se corrigent par
 * leur Encaissement ; il reste affiché pour l'historique antérieur.</p>
 */
@Component({
  selector: 'app-payment-detail-history-dialog',
  standalone: true,
  template: `
    <h2 mat-dialog-title class="history-title">
      <mat-icon>history</mat-icon>
      <span>{{ 'payment.admin.audit.title' | translate }}</span>
    </h2>
    <mat-dialog-content>
      <!-- Remboursements du versement -->
      <section class="refunds" *ngIf="data.paymentId">
        <h3 class="section-title">{{ 'payment.admin.audit.refunds.title' | translate }}</h3>
        <div class="loading" *ngIf="loadingRefunds">
          <mat-progress-spinner mode="indeterminate" diameter="24"></mat-progress-spinner>
        </div>
        <p class="error" *ngIf="refundsError">{{ refundsError }}</p>
        <p class="small muted" *ngIf="!loadingRefunds && !refundsError && !refunds.length">
          {{ 'payment.admin.audit.refunds.empty' | translate }}
        </p>
        <ul class="refund-list" *ngIf="!loadingRefunds && refunds.length">
          <li class="refund-item" *ngFor="let refund of refunds">
            <div class="refund-head">
              <mat-icon class="refund-icon" aria-hidden="true">undo</mat-icon>
              <span class="refund-number">{{ refund.refundNumber }}</span>
              <span class="refund-amount">{{ refund.amount | amount }} DA</span>
            </div>
            <div class="small">
              {{ refund.refundDate | date: 'dd/MM/yyyy' }}
              <span class="actor" *ngIf="refund.recordedBy">
                {{ 'payment.admin.audit.by' | translate: { actor: refund.recordedBy } }}
              </span>
            </div>
            <div class="small refund-reason">
              {{ 'payment.admin.audit.reason' | translate }} :
              {{ refund.reason?.trim() || ('payment.admin.audit.refunds.noReason' | translate) }}
            </div>
            <button mat-stroked-button type="button" class="refund-reprint"
                    (click)="reprint(refund)" [disabled]="reprintingId !== null"
                    [attr.aria-label]="'payment.admin.audit.refunds.reprintAria' | translate:
                      { number: refund.refundNumber }">
              <mat-icon aria-hidden="true">print</mat-icon>
              {{ 'payment.admin.audit.refunds.reprint' | translate }}
            </button>
          </li>
        </ul>
      </section>

      <!-- Journal d'audit de la ligne -->
      <section class="changes">
        <h3 class="section-title">{{ 'payment.admin.audit.changesTitle' | translate }}</h3>
        <div class="loading" *ngIf="loading">
          <mat-progress-spinner mode="indeterminate" diameter="32"></mat-progress-spinner>
        </div>

        <p class="error" *ngIf="errorMessage">{{ errorMessage }}</p>

        <mat-list *ngIf="!loading && history.length">
          <mat-list-item *ngFor="let item of history" class="audit-row">
            <mat-icon matListItemIcon [class]="'action-' + item.action.toLowerCase()">
              {{ actionIcon(item.action) }}
            </mat-icon>
            <div matListItemTitle>
              {{ actionLabel(item.action) }}
              <span class="actor">{{ 'payment.admin.audit.by' | translate: { actor: item.performedBy } }}</span>
            </div>
            <div matListItemLine>{{ item.timestamp | date:'medium' }}</div>
            <div matListItemLine class="small">
              {{ 'payment.admin.audit.reason' | translate }} : {{ item.reason || '—' }}
            </div>
            <div matListItemLine class="small">
              {{ 'payment.admin.audit.change' | translate: {
                   from: item.oldValue || '—', to: item.newValue || '—' } }}
            </div>
          </mat-list-item>
        </mat-list>

        <div class="empty" *ngIf="!loading && !errorMessage && !history.length">
          <mat-icon>history_toggle_off</mat-icon>
          <p>{{ 'payment.admin.audit.empty' | translate }}</p>
        </div>
      </section>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>{{ 'common.close' | translate }}</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .history-title { display: flex; align-items: center; gap: 8px; }
    .history-title mat-icon { color: #4f46e5; }
    .section-title { font-size: 14px; font-weight: 600; margin: 8px 0 4px; color: #374151; }
    .audit-row { margin-bottom: 6px; }
    .small { font-size: 12px; color: #555; }
    .muted { color: #6b7280; font-style: italic; }
    .actor { color: #6b7280; font-weight: 400; }
    .loading, .empty { display: flex; flex-direction: column; align-items: center; padding: 24px 0; color: #6b7280; }
    .error { color: #b91c1c; }
    .action-modified { color: #d97706; }
    .action-deleted { color: #dc2626; }
    .action-reactivated { color: #059669; }
    .refund-list { list-style: none; margin: 0; padding: 0; }
    .refund-item { padding: 8px 0; border-bottom: 1px solid rgba(0, 0, 0, 0.08); }
    .refund-head { display: flex; align-items: center; gap: 8px; }
    .refund-icon { color: #b71c1c; font-size: 18px; width: 18px; height: 18px; }
    .refund-number { font-weight: 600; }
    .refund-amount { margin-left: auto; color: #b71c1c; font-weight: 600; white-space: nowrap; }
    .refund-reprint { margin-top: 6px; }
  `],
  imports: [CommonModule, MatDialogModule, MatListModule, MatIconModule,
    MatProgressSpinnerModule, MatButtonModule, TranslateModule, AmountPipe]
})
export class PaymentDetailHistoryDialogComponent implements OnInit {
  history: PaymentDetailAudit[] = [];
  loading = true;
  errorMessage = '';

  /** Remboursements du versement de la ligne, du plus ancien au plus récent. */
  refunds: StudentRefund[] = [];
  loadingRefunds = false;
  refundsError = '';
  /** Remboursement dont le reçu est en cours de réimpression : un second clic ne relance rien. */
  reprintingId: number | null = null;

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: PaymentDetailHistoryData,
    private http: HttpClient,
    private translate: TranslateService,
    private refundService: RefundService,
    private refundReceiptPdf: RefundReceiptPdfService,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.loadRefunds();
    this.http.get<PaymentDetailAudit[]>(`${API_BASE_URL}/api/payment-details/${this.data.id}/history`)
      .pipe(finalize(() => this.loading = false))
      .subscribe({
        next: history => {
          this.history = (history || []).map(item => ({
            ...item,
            timestamp: this.convertToDate(item.timestamp) as unknown as string
          }));
        },
        // Sans branche d'erreur, un échec affichait « aucun historique », ce qui est
        // trompeur sur un journal d'audit.
        error: error => {
          this.errorMessage = error?.error?.message
            || this.translate.instant('payment.admin.audit.error');
        }
      });
  }

  private loadRefunds(): void {
    if (!this.data.paymentId) {
      return;
    }
    this.loadingRefunds = true;
    this.refundService.getPaymentRefunds(this.data.paymentId)
      .pipe(finalize(() => this.loadingRefunds = false))
      .subscribe({
        next: refunds => this.refunds = refunds || [],
        // Un échec n'est pas « aucun remboursement » : le dire, sinon le montant net du tableau
        // paraîtrait inexpliqué.
        error: (err: Error) => this.refundsError =
          err.message || this.translate.instant('payment.admin.audit.refunds.error')
      });
  }

  /** Réimprime le reçu d'un remboursement ; le serveur compte l'émission (mention « DUPLICATA »). */
  reprint(refund: StudentRefund): void {
    if (this.reprintingId !== null) {
      return;
    }
    this.reprintingId = refund.refundId;
    this.refundService.issueReceipt(refund.refundId)
      .pipe(finalize(() => this.reprintingId = null))
      .subscribe({
        next: receipt => this.refundReceiptPdf.generateAndPrint(receipt).catch((err: unknown) => {
          console.error('Erreur lors de l\'impression du reçu de remboursement :', err);
          this.notify(this.translate.instant('payment.admin.audit.refunds.reprintError'));
        }),
        error: (err: Error) =>
          this.notify(err.message || this.translate.instant('payment.admin.audit.refunds.reprintError'))
      });
  }

  /** Libellé traduit de l'action ; l'ancien affichage montrait MODIFIED / DELETED brut. */
  actionLabel(action: string): string {
    const key = `payment.admin.audit.actions.${action}`;
    const label = this.translate.instant(key);
    return label === key ? action : label;
  }

  actionIcon(action: string): string {
    switch (action) {
      case 'MODIFIED':
        return 'edit';
      case 'DELETED':
        return 'delete_forever';
      case 'REACTIVATED':
        return 'check_circle';
      default:
        return 'timeline';
    }
  }

  private notify(message: string): void {
    this.snackBar.open(message, this.translate.instant('common.close'), { duration: 6000 });
  }

  private convertToDate(dateArray: unknown): Date | string {
    if (!dateArray) {
      return '';
    }

    // If it's already a Date or string, return it
    if (dateArray instanceof Date || typeof dateArray === 'string') {
      return new Date(dateArray);
    }

    // If it's an array [year, month, day, hour, minute, second, nano]
    if (Array.isArray(dateArray) && dateArray.length >= 3) {
      const [year, month, day, hour = 0, minute = 0, second = 0, nano = 0] = dateArray as number[];
      // Month in JavaScript Date is 0-indexed, but Java LocalDateTime is 1-indexed
      return new Date(year, month - 1, day, hour, minute, second, Math.floor(nano / 1000000));
    }

    return String(dateArray);
  }
}
