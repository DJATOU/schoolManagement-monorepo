import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatOptionModule } from '@angular/material/core';
import { HttpClient } from '@angular/common/http';
import { API_BASE_URL } from '../../../../app.config';

@Component({
  selector: 'app-edit-payment-detail-dialog',
  standalone: true,
  template: `
    <h2 mat-dialog-title>Modifier le paiement</h2>
    <form [formGroup]="form" (ngSubmit)="onSave()" class="dialog-form">
      <mat-form-field appearance="outline">
        <mat-label>Montant</mat-label>
        <input matInput type="number" formControlName="amount" required />
      </mat-form-field>

      <mat-form-field appearance="outline">
        <mat-label>Statut</mat-label>
        <mat-select formControlName="active">
          <mat-option [value]="true">Actif</mat-option>
          <mat-option [value]="false">Inactif</mat-option>
        </mat-select>
      </mat-form-field>

      <mat-form-field appearance="outline">
        <mat-label>Raison</mat-label>
        <textarea matInput rows="3" formControlName="reason" required></textarea>
      </mat-form-field>

      <div class="actions">
        <button mat-button type="button" (click)="dialogRef.close(false)">Annuler</button>
        <button mat-raised-button color="primary" type="submit" [disabled]="form.invalid">Enregistrer</button>
      </div>
    </form>
  `,
  styles: [`
    .dialog-form {
      display: flex;
      flex-direction: column;
      gap: 12px;
      width: 100%;
      min-width: 320px;
    }

    .actions {
      display: flex;
      justify-content: flex-end;
      gap: 8px;
    }
  `],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatSelectModule,
    MatOptionModule
  ]
})
export class EditPaymentDetailDialogComponent {
  form: FormGroup;

  constructor(
    private fb: FormBuilder,
    private http: HttpClient,
    public dialogRef: MatDialogRef<EditPaymentDetailDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { id: number; amount: number; active: boolean }
  ) {
    this.form = this.fb.group({
      amount: [data.amount, [Validators.required, Validators.min(0)]],
      active: [data.active, Validators.required],
      reason: ['', Validators.required]
    });
  }

  onSave(): void {
    if (this.form.invalid) {
      return;
    }

    this.http.patch(`${API_BASE_URL}/api/payment-details/${this.data.id}`, this.form.value, {
      headers: { 'X-Admin-Name': 'Admin' }
    }).subscribe({
      next: () => this.dialogRef.close(true),
      error: () => this.dialogRef.close(false)
    });
  }
}
