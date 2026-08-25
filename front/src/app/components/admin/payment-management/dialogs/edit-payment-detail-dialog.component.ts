import { CommonModule } from '@angular/common';
import { Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { HttpClient } from '@angular/common/http';
import { API_BASE_URL } from '../../../../api-base-url';

/**
 * Dialogue de modification d'un paiement (montant + statut), avec motif obligatoire pour
 * la traçabilité de l'audit.
 *
 * <p>Design : en-tête coloré, rappel du contexte (étudiant / groupe / série / séance) en
 * lecture seule, puis les deux champs modifiables et le motif. Le montant d'origine est
 * rappelé pour visualiser l'écart avant d'enregistrer.</p>
 */
@Component({
  selector: 'app-edit-payment-detail-dialog',
  standalone: true,
  template: `
    <!-- En-tête -->
    <div class="dlg-hero">
      <div class="dlg-hero-icon">
        <mat-icon>edit_note</mat-icon>
      </div>
      <div class="dlg-hero-text">
        <h2 class="dlg-title">{{ 'payment.admin.edit.title' | translate }}</h2>
        <span class="dlg-subtitle">
          {{ 'payment.admin.edit.subtitle' | translate: { id: data?.id } }}
        </span>
      </div>
    </div>

    <mat-dialog-content class="dlg-content">
      <!-- Contexte (lecture seule) -->
      <div class="ctx-card">
        <div class="ctx-row">
          <mat-icon class="ctx-icon">person</mat-icon>
          <span class="ctx-label">{{ 'payment.admin.edit.context.student' | translate }}</span>
          <span class="ctx-value">{{ studentName || '—' }}</span>
        </div>
        <div class="ctx-row">
          <mat-icon class="ctx-icon">groups</mat-icon>
          <span class="ctx-label">{{ 'payment.admin.edit.context.group' | translate }}</span>
          <span class="ctx-value">{{ data?.groupName || '—' }}</span>
        </div>
        <div class="ctx-row">
          <mat-icon class="ctx-icon">calendar_month</mat-icon>
          <span class="ctx-label">{{ 'payment.admin.edit.context.series' | translate }}</span>
          <span class="ctx-value">{{ data?.seriesName || '—' }}</span>
        </div>
        <div class="ctx-row">
          <mat-icon class="ctx-icon">event_note</mat-icon>
          <span class="ctx-label">{{ 'payment.admin.edit.context.session' | translate }}</span>
          <span class="ctx-value">{{ data?.sessionName || '—' }}</span>
        </div>
      </div>

      <form [formGroup]="form" (ngSubmit)="onSave()" class="dlg-form" id="editPaymentForm">
        <!-- Montant -->
        <div class="field-block">
          <label class="field-label" for="amountInput">
            {{ 'payment.admin.edit.amount' | translate }}
          </label>
          <mat-form-field appearance="outline" class="full">
            <input matInput id="amountInput" formControlName="amount" type="number" min="0" required
                   [placeholder]="'payment.admin.edit.amountPlaceholder' | translate">
            <span matTextSuffix class="suffix-currency">DZD</span>
            <mat-error *ngIf="form.get('amount')?.hasError('required')">
              {{ 'payment.admin.edit.amountRequired' | translate }}
            </mat-error>
            <mat-error *ngIf="form.get('amount')?.hasError('min')">
              {{ 'payment.admin.edit.amountMin' | translate }}
            </mat-error>
          </mat-form-field>
          <div class="field-hint" *ngIf="amountChanged">
            <mat-icon class="hint-icon">swap_horiz</mat-icon>
            <span>{{ 'payment.admin.edit.amountChanged' | translate: {
              original: (data?.amountPaid | currency:'DZD':'symbol':'1.0-0'),
              updated: (form.get('amount')?.value | currency:'DZD':'symbol':'1.0-0')
            } }}</span>
          </div>
        </div>

        <!-- Statut -->
        <div class="field-block">
          <label class="field-label">{{ 'payment.admin.edit.status' | translate }}</label>
          <mat-button-toggle-group formControlName="active" class="status-toggle"
                                   [attr.aria-label]="'payment.admin.edit.status' | translate">
            <mat-button-toggle [value]="true" class="toggle-active">
              <mat-icon>check_circle</mat-icon> {{ 'payment.admin.edit.statusActive' | translate }}
            </mat-button-toggle>
            <mat-button-toggle [value]="false" class="toggle-inactive">
              <mat-icon>cancel</mat-icon> {{ 'payment.admin.edit.statusInactive' | translate }}
            </mat-button-toggle>
          </mat-button-toggle-group>
        </div>

        <!-- Motif -->
        <div class="field-block">
          <label class="field-label" for="reasonInput">
            {{ 'payment.admin.edit.reason' | translate }}
            <mat-icon class="label-info"
                      [matTooltip]="'payment.admin.edit.reasonTooltip' | translate">
              info
            </mat-icon>
          </label>
          <mat-form-field appearance="outline" class="full">
            <textarea matInput id="reasonInput" formControlName="reason" rows="3" required
                      maxlength="255"
                      [placeholder]="'payment.admin.edit.reasonPlaceholder' | translate"></textarea>
            <mat-hint align="end">{{ form.get('reason')?.value?.length || 0 }} / 255</mat-hint>
            <mat-error *ngIf="form.get('reason')?.hasError('required')">
              {{ 'payment.admin.edit.reasonRequired' | translate }}
            </mat-error>
          </mat-form-field>
        </div>

        <!-- Erreur serveur -->
        <div class="server-error" *ngIf="errorMessage">
          <mat-icon>error_outline</mat-icon>
          <span>{{ errorMessage }}</span>
        </div>
      </form>
    </mat-dialog-content>

    <mat-dialog-actions class="dlg-actions">
      <span class="required-note" *ngIf="form.invalid && !saving">
        <mat-icon>info</mat-icon> {{ 'payment.admin.edit.requiredNote' | translate }}
      </span>
      <span class="spacer"></span>
      <button mat-stroked-button type="button" [disabled]="saving"
              (click)="dialogRef.close(false)">
        {{ 'common.cancel' | translate }}
      </button>
      <button mat-flat-button color="primary" type="submit" form="editPaymentForm"
              [disabled]="form.invalid || saving" class="save-btn">
        <mat-spinner *ngIf="saving" diameter="18"></mat-spinner>
        <mat-icon *ngIf="!saving">save</mat-icon>
        {{ (saving ? 'payment.admin.edit.saving' : 'payment.admin.edit.save') | translate }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    :host {
      --dlg-primary: #4f46e5;
      --dlg-ink: #111827;
      --dlg-muted: #6b7280;
      --dlg-line: #e9ecf3;
      display: block;
    }

    /* ===== En-tête ===== */
    .dlg-hero {
      display: flex;
      align-items: center;
      gap: 14px;
      padding: 18px 24px;
      background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
      color: #fff;
    }
    .dlg-hero-icon {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 42px;
      height: 42px;
      flex: 0 0 42px;
      border-radius: 12px;
      background: rgba(255, 255, 255, 0.2);
    }
    .dlg-hero-icon mat-icon {
      font-size: 24px;
      width: 24px;
      height: 24px;
    }
    .dlg-hero-text { min-width: 0; }
    .dlg-title {
      margin: 0;
      font-size: 18px;
      font-weight: 700;
      line-height: 1.25;
      color: #fff;
    }
    .dlg-subtitle {
      display: block;
      margin-top: 2px;
      font-size: 12px;
      opacity: 0.85;
    }

    /* ===== Contenu ===== */
    .dlg-content {
      padding: 18px 24px 4px !important;
      max-height: 68vh;
    }

    /* Contexte en lecture seule */
    .ctx-card {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 2px 14px;
      padding: 12px 14px;
      margin-bottom: 20px;
      background: #f8fafc;
      border: 1px solid var(--dlg-line);
      border-radius: 12px;
    }
    .ctx-row {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 5px 0;
      min-width: 0;
      font-size: 13px;
    }
    .ctx-icon {
      font-size: 17px;
      width: 17px;
      height: 17px;
      color: var(--dlg-primary);
      flex: 0 0 17px;
    }
    .ctx-label {
      color: var(--dlg-muted);
      flex: 0 0 auto;
    }
    .ctx-value {
      font-weight: 600;
      color: var(--dlg-ink);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      min-width: 0;
    }

    /* Champs */
    .dlg-form {
      display: flex;
      flex-direction: column;
      gap: 6px;
    }
    .field-block { margin-bottom: 4px; }
    .field-label {
      display: flex;
      align-items: center;
      gap: 5px;
      margin-bottom: 6px;
      font-size: 12px;
      font-weight: 600;
      letter-spacing: 0.3px;
      text-transform: uppercase;
      color: var(--dlg-muted);
    }
    .label-info {
      font-size: 15px;
      width: 15px;
      height: 15px;
      color: #9ca3af;
      cursor: help;
    }
    .full { width: 100%; }
    .suffix-currency {
      font-size: 12px;
      font-weight: 600;
      color: var(--dlg-muted);
      padding-left: 6px;
    }
    .field-hint {
      display: flex;
      align-items: center;
      gap: 6px;
      margin: -12px 0 4px;
      padding: 7px 10px;
      font-size: 12px;
      color: #92400e;
      background: #fffbeb;
      border: 1px solid #fde68a;
      border-radius: 8px;
    }
    .hint-icon {
      font-size: 16px;
      width: 16px;
      height: 16px;
    }

    /* Bascule de statut */
    .status-toggle {
      width: 100%;
      border-radius: 10px;
      overflow: hidden;
    }
    .status-toggle ::ng-deep .mat-button-toggle {
      flex: 1 1 50%;
      text-align: center;
    }
    .status-toggle ::ng-deep .mat-button-toggle-label-content {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
      line-height: 40px;
      font-weight: 600;
      font-size: 13px;
    }
    .status-toggle ::ng-deep mat-icon {
      font-size: 18px;
      width: 18px;
      height: 18px;
    }
    .status-toggle ::ng-deep .toggle-active.mat-button-toggle-checked {
      background: #ecfdf5;
      color: #047857;
    }
    .status-toggle ::ng-deep .toggle-inactive.mat-button-toggle-checked {
      background: #fef2f2;
      color: #b91c1c;
    }

    /* Erreur serveur */
    .server-error {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 10px 12px;
      font-size: 13px;
      color: #b91c1c;
      background: #fef2f2;
      border: 1px solid #fecaca;
      border-radius: 8px;
    }
    .server-error mat-icon {
      font-size: 18px;
      width: 18px;
      height: 18px;
    }

    /* ===== Actions ===== */
    .dlg-actions {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 12px 24px 18px !important;
      border-top: 1px solid var(--dlg-line);
    }
    .spacer { flex: 1 1 auto; }
    .required-note {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      font-size: 11.5px;
      color: var(--dlg-muted);
    }
    .required-note mat-icon {
      font-size: 15px;
      width: 15px;
      height: 15px;
    }
    .save-btn {
      display: inline-flex;
      align-items: center;
      gap: 7px;
    }
    .save-btn mat-icon {
      font-size: 18px;
      width: 18px;
      height: 18px;
    }
    .save-btn ::ng-deep .mat-mdc-progress-spinner circle {
      stroke: currentColor;
    }

    @media (max-width: 520px) {
      .ctx-card { grid-template-columns: 1fr; }
      .required-note { display: none; }
    }
  `],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    TranslateModule
  ]
})
export class EditPaymentDetailDialogComponent {
  form: FormGroup;
  saving = false;
  errorMessage = '';

  constructor(
    private fb: FormBuilder,
    private http: HttpClient,
    private translate: TranslateService,
    public dialogRef: MatDialogRef<EditPaymentDetailDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: any
  ) {
    this.form = this.fb.group({
      amount: [data?.amountPaid, [Validators.required, Validators.min(0)]],
      active: [data?.active, Validators.required],
      reason: ['', Validators.required]
    });
  }

  /** Nom complet de l'étudiant, à partir des champs du tableau. */
  get studentName(): string {
    return `${this.data?.studentFirstName ?? ''} ${this.data?.studentLastName ?? ''}`.trim();
  }

  /** Vrai si le montant saisi diffère du montant d'origine (rappel visuel de l'écart). */
  get amountChanged(): boolean {
    const current = this.form?.get('amount')?.value;
    return current !== null && current !== '' && Number(current) !== Number(this.data?.amountPaid);
  }

  onSave(): void {
    if (this.form.invalid || this.saving) {
      return;
    }
    this.saving = true;
    this.errorMessage = '';

    // L'auteur de l'action est déduit du jeton côté serveur : plus d'en-tête déclaratif.
    this.http.patch(`${API_BASE_URL}/api/payment-details/${this.data.id}`, this.form.value)
      .subscribe({
        next: () => this.dialogRef.close(true),
        error: (error) => {
          this.saving = false;
          this.errorMessage = error?.error?.message
            || this.translate.instant('payment.admin.edit.error');
        }
      });
  }
}
