import { CommonModule } from '@angular/common';
import { Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSliderModule } from '@angular/material/slider';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { DiscountService } from '../../../services/discount.service';
import { Discount } from '../../../models/discount/student-discount';

/**
 * Dialogue de modification du taux d'une réduction.
 *
 * <p>Seul le taux est modifiable : changer la portée ou la cible reviendrait à une autre
 * réduction (il faut alors la supprimer et en créer une nouvelle). Le taux est saisi en
 * pourcentage (0 à 100) puis converti en décimal {@code [0, 1]} attendu par le backend.</p>
 */
@Component({
  selector: 'app-discount-edit-dialog',
  standalone: true,
  template: `
    <div class="dlg-hero">
      <div class="dlg-hero-icon">
        <mat-icon>percent</mat-icon>
      </div>
      <div class="dlg-hero-text">
        <h2 class="dlg-title">{{ 'discount.edit.title' | translate }}</h2>
        <span class="dlg-subtitle">{{ subtitle }}</span>
      </div>
    </div>

    <mat-dialog-content class="dlg-content">
      <form [formGroup]="form" (ngSubmit)="onSave()" id="discountEditForm">
        <label class="field-label" for="rateInput">{{ 'discount.edit.rate' | translate }}</label>

        <div class="rate-row">
          <mat-slider min="0" max="100" step="5" discrete class="rate-slider">
            <input matSliderThumb formControlName="rate" [attr.aria-label]="'discount.edit.rate' | translate">
          </mat-slider>
          <mat-form-field appearance="outline" class="rate-field">
            <input matInput id="rateInput" formControlName="rate" type="number" min="0" max="100" required>
            <span matTextSuffix class="suffix">%</span>
          </mat-form-field>
        </div>

        <div class="rate-preview" [class.exemption]="form.get('rate')?.value >= 100">
          <mat-icon>{{ form.get('rate')?.value >= 100 ? 'workspace_premium' : 'sell' }}</mat-icon>
          <span *ngIf="form.get('rate')?.value >= 100">{{ 'discount.exemption' | translate }}</span>
          <span *ngIf="form.get('rate')?.value < 100">
            {{ 'discount.edit.currentRate' | translate: { rate: (data.rate * 100) + '%' } }}
          </span>
        </div>

        <p class="field-hint">{{ 'discount.edit.rateHint' | translate }}</p>

        <mat-error class="inline-error" *ngIf="form.get('rate')?.hasError('required') && form.get('rate')?.touched">
          {{ 'discount.edit.rateRequired' | translate }}
        </mat-error>
        <mat-error class="inline-error"
                   *ngIf="(form.get('rate')?.hasError('min') || form.get('rate')?.hasError('max'))">
          {{ 'discount.edit.rateRange' | translate }}
        </mat-error>

        <div class="server-error" *ngIf="errorMessage">
          <mat-icon>error_outline</mat-icon>
          <span>{{ errorMessage }}</span>
        </div>
      </form>
    </mat-dialog-content>

    <mat-dialog-actions class="dlg-actions">
      <span class="spacer"></span>
      <button mat-stroked-button type="button" [disabled]="saving" (click)="dialogRef.close(false)">
        {{ 'common.cancel' | translate }}
      </button>
      <button mat-flat-button color="primary" type="submit" form="discountEditForm"
              [disabled]="form.invalid || saving" class="save-btn">
        <mat-spinner *ngIf="saving" diameter="18"></mat-spinner>
        <mat-icon *ngIf="!saving">save</mat-icon>
        {{ (saving ? 'discount.edit.saving' : 'discount.edit.save') | translate }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    :host {
      --dlg-primary: #4f46e5;
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
      padding: 20px 24px 0 !important;
      min-width: 380px;
    }
    .field-label {
      display: block;
      margin-bottom: 8px;
      font-size: 12px;
      font-weight: 600;
      letter-spacing: 0.3px;
      text-transform: uppercase;
      color: var(--dlg-muted);
    }
    .rate-row {
      display: flex;
      align-items: center;
      gap: 14px;
    }
    .rate-slider { flex: 1 1 auto; }
    .rate-field {
      flex: 0 0 106px;
      width: 106px;
    }
    .suffix {
      font-size: 13px;
      font-weight: 600;
      color: var(--dlg-muted);
      padding-left: 4px;
    }

    .rate-preview {
      display: flex;
      align-items: center;
      gap: 8px;
      margin: -8px 0 10px;
      padding: 9px 12px;
      font-size: 13px;
      font-weight: 600;
      color: #3730a3;
      background: #eef2ff;
      border: 1px solid #c7d2fe;
      border-radius: 10px;

      &.exemption {
        color: #92400e;
        background: #fffbeb;
        border-color: #fde68a;
      }
    }
    .rate-preview mat-icon {
      font-size: 18px;
      width: 18px;
      height: 18px;
    }

    .field-hint {
      margin: 0 0 6px;
      font-size: 12px;
      color: var(--dlg-muted);
    }
    .inline-error {
      display: block;
      font-size: 12px;
      margin-bottom: 6px;
    }

    .server-error {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-top: 10px;
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

    .dlg-actions {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 10px 24px 18px !important;
      border-top: 1px solid var(--dlg-line);
    }
    .spacer { flex: 1 1 auto; }
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
    .save-btn ::ng-deep .mat-mdc-progress-spinner circle { stroke: currentColor; }

    @media (max-width: 480px) {
      .dlg-content { min-width: 0; }
      .rate-row { flex-direction: column; align-items: stretch; }
      .rate-field { width: 100%; flex: 1 1 auto; }
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
    MatProgressSpinnerModule,
    MatSliderModule,
    TranslateModule
  ]
})
export class DiscountEditDialogComponent {
  form: FormGroup;
  saving = false;
  errorMessage = '';

  constructor(
    private fb: FormBuilder,
    private discountService: DiscountService,
    private translate: TranslateService,
    public dialogRef: MatDialogRef<DiscountEditDialogComponent, boolean>,
    @Inject(MAT_DIALOG_DATA) public data: Discount
  ) {
    // Le backend stocke un décimal [0, 1] ; l'interface manipule des pourcentages.
    this.form = this.fb.group({
      rate: [Math.round((data.rate ?? 0) * 100),
        [Validators.required, Validators.min(0), Validators.max(100)]]
    });
  }

  /** Rappel de l'étudiant et de la cible concernés, affiché sous le titre. */
  get subtitle(): string {
    const student = this.data.studentName
      || this.translate.instant('discount.unknownStudent', { id: this.data.studentId });
    const scope = this.translate.instant(`discount.scopes.${this.data.scope}`);
    const target = this.data.targetName ? ` · ${this.data.targetName}` : '';
    return `${student} · ${scope}${target}`;
  }

  onSave(): void {
    if (this.form.invalid || this.saving || this.data.id === undefined) {
      return;
    }
    this.saving = true;
    this.errorMessage = '';

    const rate = Number(this.form.value.rate) / 100;
    this.discountService.updateRate(this.data.id, rate).subscribe({
      next: () => this.dialogRef.close(true),
      error: (error: Error) => {
        this.saving = false;
        this.errorMessage = error?.message || this.translate.instant('discount.edit.error');
      }
    });
  }
}
