import { CommonModule } from '@angular/common';
import { Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { HttpClient } from '@angular/common/http';
import { API_BASE_URL } from '../../../../app.config';

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

@Component({
  selector: 'app-payment-detail-history-dialog',
  standalone: true,
  template: `
    <h2 mat-dialog-title>Historique des actions</h2>
    <mat-dialog-content>
      <mat-nav-list *ngIf="history.length; else empty">
        <mat-list-item *ngFor="let item of history">
          <mat-icon matListItemIcon>timeline</mat-icon>
          <div matListItemTitle>{{item.action}} par {{item.performedBy}}</div>
          <div matListItemLine>{{item.timestamp | date:'short'}}</div>
          <div matListItemLine class="small">Raison: {{item.reason || 'N/A'}}</div>
          <div matListItemLine class="small">Ancien: {{item.oldValue || '-'}} → Nouveau: {{item.newValue || '-'}} </div>
        </mat-list-item>
      </mat-nav-list>
      <ng-template #empty>
        <p>Aucun historique pour ce paiement.</p>
      </ng-template>
    </mat-dialog-content>
  `,
  styles: [`
    mat-list-item { margin-bottom: 6px; }
    .small { font-size: 12px; color: #555; }
  `],
  imports: [CommonModule, MatDialogModule, MatListModule, MatIconModule]
})
export class PaymentDetailHistoryDialogComponent implements OnInit {
  history: PaymentDetailAudit[] = [];

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: any,
    private http: HttpClient
  ) {}

  ngOnInit(): void {
    this.http.get<PaymentDetailAudit[]>(`${API_BASE_URL}/api/payment-details/${this.data.id}/history`)
      .subscribe(history => this.history = history || []);
  }
}
