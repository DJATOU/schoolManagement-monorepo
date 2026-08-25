import { CommonModule } from '@angular/common';
import { Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatButtonModule } from '@angular/material/button';
import { HttpClient } from '@angular/common/http';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { finalize } from 'rxjs';
import { API_BASE_URL } from '../../../../api-base-url';

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

/**
 * Journal d'audit d'un versement : qui a modifié, supprimé ou réactivé la ligne, quand,
 * et pour quel motif.
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
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>{{ 'common.close' | translate }}</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .history-title { display: flex; align-items: center; gap: 8px; }
    .history-title mat-icon { color: #4f46e5; }
    .audit-row { margin-bottom: 6px; }
    .small { font-size: 12px; color: #555; }
    .actor { color: #6b7280; font-weight: 400; }
    .loading, .empty { display: flex; flex-direction: column; align-items: center; padding: 24px 0; color: #6b7280; }
    .error { color: #b91c1c; }
    .action-modified { color: #d97706; }
    .action-deleted { color: #dc2626; }
    .action-reactivated { color: #059669; }
  `],
  imports: [CommonModule, MatDialogModule, MatListModule, MatIconModule,
    MatProgressSpinnerModule, MatButtonModule, TranslateModule]
})
export class PaymentDetailHistoryDialogComponent implements OnInit {
  history: PaymentDetailAudit[] = [];
  loading = true;
  errorMessage = '';

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: any,
    private http: HttpClient,
    private translate: TranslateService
  ) {}

  ngOnInit(): void {
    this.http.get<PaymentDetailAudit[]>(`${API_BASE_URL}/api/payment-details/${this.data.id}/history`)
      .pipe(finalize(() => this.loading = false))
      .subscribe({
        next: history => {
          this.history = (history || []).map(item => ({
            ...item,
            timestamp: this.convertToDate(item.timestamp) as any
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

  private convertToDate(dateArray: any): Date | string {
    if (!dateArray) {
      return '';
    }

    // If it's already a Date or string, return it
    if (dateArray instanceof Date || typeof dateArray === 'string') {
      return new Date(dateArray);
    }

    // If it's an array [year, month, day, hour, minute, second, nano]
    if (Array.isArray(dateArray) && dateArray.length >= 3) {
      const [year, month, day, hour = 0, minute = 0, second = 0, nano = 0] = dateArray;
      // Month in JavaScript Date is 0-indexed, but Java LocalDateTime is 1-indexed
      return new Date(year, month - 1, day, hour, minute, second, Math.floor(nano / 1000000));
    }

    return dateArray;
  }
}
