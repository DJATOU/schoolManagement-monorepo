import { Component, Inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { HttpClient } from '@angular/common/http';
import { API_BASE_URL } from '../../../../app.config';

interface AuditItem {
  id: number;
  action: string;
  performedBy: string;
  timestamp: string;
  oldValue?: string;
  newValue?: string;
  reason?: string;
}

@Component({
  selector: 'app-payment-detail-history-dialog',
  standalone: true,
  template: `
    <h2 mat-dialog-title>Historique des modifications</h2>
    <mat-dialog-content class="history-content">
      <div *ngIf="history.length === 0" class="empty">Aucun historique disponible.</div>
      <mat-list *ngIf="history.length > 0">
        <mat-list-item *ngFor="let item of history">
          <mat-icon matListIcon>history</mat-icon>
          <div matLine>{{ item.action }} par {{ item.performedBy }}</div>
          <div matLine class="secondary">{{ item.timestamp | date:'short' }} • Raison : {{ item.reason || 'N/A' }}</div>
          <div matLine class="details">Avant : {{ item.oldValue || 'N/A' }}</div>
          <div matLine class="details">Après : {{ item.newValue || 'N/A' }}</div>
        </mat-list-item>
      </mat-list>
    </mat-dialog-content>
  `,
  styles: [`
    .history-content {
      min-width: 420px;
    }

    .secondary {
      font-size: 12px;
      color: #666;
    }

    .details {
      font-size: 12px;
      color: #444;
    }

    .empty {
      padding: 16px;
      text-align: center;
      color: #777;
    }
  `],
  imports: [CommonModule, MatDialogModule, MatListModule, MatIconModule]
})
export class PaymentDetailHistoryDialogComponent implements OnInit {
  history: AuditItem[] = [];

  constructor(
    private http: HttpClient,
    @Inject(MAT_DIALOG_DATA) public data: { id: number }
  ) {}

  ngOnInit(): void {
    this.http.get<AuditItem[]>(`${API_BASE_URL}/api/payment-details/${this.data.id}/history`).subscribe({
      next: history => this.history = history,
      error: () => this.history = []
    });
  }
}
