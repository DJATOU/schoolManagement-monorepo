import { CommonModule } from '@angular/common';
import { Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

/**
 * Données d'affichage du dialogue de saisie de motif.
 *
 * <p>Les textes (titre, message, libellé du bouton, exemple) sont fournis <strong>déjà
 * traduits</strong> par l'appelant, qui connaît le contexte de l'action.</p>
 */
export interface ReasonDialogData {
  /** Titre de l'action (ex. « Désactiver le paiement »). */
  title: string;
  /** Phrase explicative sous le titre. */
  message: string;
  /** Libellé du bouton de confirmation (ex. « Désactiver »). */
  confirmLabel: string;
  /** Exemple de motif affiché en placeholder. */
  placeholder?: string;
  /** Ton du dialogue : {@code danger} (rouge) pour une action destructrice. */
  tone?: 'danger' | 'primary';
  /** Récapitulatif optionnel de la ligne concernée. */
  summary?: string;
}

/**
 * Dialogue de saisie d'un motif obligatoire, en remplacement du {@code window.prompt()}
 * natif du navigateur (non stylable et incohérent avec le reste de l'interface).
 *
 * <p>Le motif est requis : il alimente l'historique d'audit du paiement. Ferme en renvoyant
 * la chaîne saisie, ou {@code undefined} si l'utilisateur annule.</p>
 */
@Component({
  selector: 'app-reason-dialog',
  standalone: true,
  template: `
    <div class="dlg-hero" [class.danger]="data.tone === 'danger'">
      <div class="dlg-hero-icon">
        <mat-icon>{{ data.tone === 'danger' ? 'block' : 'restart_alt' }}</mat-icon>
      </div>
      <div class="dlg-hero-text">
        <h2 class="dlg-title">{{ data.title }}</h2>
        <span class="dlg-subtitle" *ngIf="data.summary">{{ data.summary }}</span>
      </div>
    </div>

    <mat-dialog-content class="dlg-content">
      <p class="dlg-message">{{ data.message }}</p>

      <form [formGroup]="form" (ngSubmit)="onConfirm()" id="reasonForm">
        <label class="field-label" for="reasonField">
          {{ 'payment.admin.reasonDialog.label' | translate }}
        </label>
        <mat-form-field appearance="outline" class="full">
          <textarea matInput id="reasonField" formControlName="reason" rows="3" required
                    maxlength="255" cdkFocusInitial
                    [placeholder]="data.placeholder
                      || ('payment.admin.reasonDialog.defaultPlaceholder' | translate)"></textarea>
          <mat-hint align="end">{{ form.get('reason')?.value?.length || 0 }} / 255</mat-hint>
          <mat-error *ngIf="form.get('reason')?.hasError('required')">
            {{ 'payment.admin.reasonDialog.required' | translate }}
          </mat-error>
        </mat-form-field>
      </form>
    </mat-dialog-content>

    <mat-dialog-actions class="dlg-actions">
      <span class="spacer"></span>
      <button mat-stroked-button type="button" (click)="dialogRef.close()">
        {{ 'common.cancel' | translate }}
      </button>
      <button mat-flat-button type="submit" form="reasonForm"
              [color]="data.tone === 'danger' ? 'warn' : 'primary'"
              [class.danger-btn]="data.tone === 'danger'"
              [disabled]="form.invalid">
        {{ data.confirmLabel }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    :host {
      --dlg-muted: #6b7280;
      --dlg-line: #e9ecf3;
      display: block;
    }

    .dlg-hero {
      display: flex;
      align-items: center;
      gap: 14px;
      padding: 18px 24px;
      background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
      color: #fff;

      &.danger {
        background: linear-gradient(135deg, #dc2626 0%, #b91c1c 100%);
      }
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

    .dlg-content {
      padding: 18px 24px 0 !important;
      min-width: 360px;
    }
    .dlg-message {
      margin: 0 0 16px;
      font-size: 13.5px;
      line-height: 1.5;
      color: #374151;
    }
    .field-label {
      display: block;
      margin-bottom: 6px;
      font-size: 12px;
      font-weight: 600;
      letter-spacing: 0.3px;
      text-transform: uppercase;
      color: var(--dlg-muted);
    }
    .full { width: 100%; }

    .dlg-actions {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 8px 24px 18px !important;
      border-top: 1px solid var(--dlg-line);
    }
    .spacer { flex: 1 1 auto; }
    .danger-btn:not([disabled]) {
      background: #dc2626;
      color: #fff;
    }

    @media (max-width: 480px) {
      .dlg-content { min-width: 0; }
    }
  `],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    TranslateModule
  ]
})
export class ReasonDialogComponent {
  form: FormGroup;

  constructor(
    private fb: FormBuilder,
    public dialogRef: MatDialogRef<ReasonDialogComponent, string>,
    @Inject(MAT_DIALOG_DATA) public data: ReasonDialogData
  ) {
    this.form = this.fb.group({
      reason: ['', [Validators.required]]
    });
  }

  onConfirm(): void {
    if (this.form.invalid) {
      return;
    }
    this.dialogRef.close(String(this.form.value.reason).trim());
  }
}
