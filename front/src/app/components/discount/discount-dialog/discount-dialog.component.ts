import { CommonModule } from '@angular/common';
import { Component, Inject, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { DiscountService } from '../../../services/discount.service';
import { StudentDiscount } from '../../../models/discount/student-discount';

@Component({
  selector: 'app-discount-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule
  ],
  template: `
    <h2 mat-dialog-title>Gérer les réductions</h2>
    <mat-dialog-content [formGroup]="discountForm" class="discount-content">
      <mat-form-field appearance="fill">
        <mat-label>Type de réduction</mat-label>
        <mat-select formControlName="discountType">
          <mat-option value="PERCENTAGE">%</mat-option>
          <mat-option value="FIXED">Montant fixe</mat-option>
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="fill">
        <mat-label>Valeur</mat-label>
        <input matInput type="number" formControlName="discountValue" />
      </mat-form-field>
      <mat-form-field appearance="fill">
        <mat-label>Raison</mat-label>
        <input matInput formControlName="reason" />
      </mat-form-field>
      <mat-form-field appearance="fill">
        <mat-label>Groupe (optionnel)</mat-label>
        <input matInput type="number" formControlName="groupId" />
      </mat-form-field>
      <mat-form-field appearance="fill">
        <mat-label>Date de début</mat-label>
        <input matInput type="date" formControlName="startDate" />
      </mat-form-field>
      <mat-form-field appearance="fill">
        <mat-label>Date de fin</mat-label>
        <input matInput type="date" formControlName="endDate" />
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Annuler</button>
      <button mat-raised-button color="primary" (click)="save()" [disabled]="discountForm.invalid">Enregistrer</button>
    </mat-dialog-actions>
  `,
  styles: [`.discount-content { display: grid; gap: 12px; }`]
})
export class DiscountDialogComponent implements OnInit {
  discountForm: FormGroup;

  constructor(
    private fb: FormBuilder,
    private discountService: DiscountService,
    private snackBar: MatSnackBar,
    private dialogRef: MatDialogRef<DiscountDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { studentId: number; discount?: StudentDiscount }
  ) {
    this.discountForm = this.fb.group({
      discountType: [data.discount?.discountType || 'PERCENTAGE', Validators.required],
      discountValue: [data.discount?.discountValue || 0, Validators.required],
      reason: [data.discount?.reason || '', Validators.required],
      groupId: [data.discount?.groupId || null],
      startDate: [data.discount?.startDate ? this.toDateInput(data.discount.startDate) : '', Validators.required],
      endDate: [data.discount?.endDate ? this.toDateInput(data.discount.endDate) : ''],
      isActive: [data.discount?.isActive ?? true]
    });
  }

  ngOnInit(): void {}

  save(): void {
    const payload: Partial<StudentDiscount> = {
      ...this.discountForm.value,
      studentId: this.data.studentId
    };

    const request = this.data.discount?.id
      ? this.discountService.updateDiscount(this.data.discount.id, payload)
      : this.discountService.addDiscount(payload);

    request.subscribe({
      next: discount => {
        this.snackBar.open('Réduction enregistrée', 'Fermer', { duration: 3000 });
        this.dialogRef.close(discount);
      },
      error: () => this.snackBar.open('Erreur lors de la sauvegarde de la réduction', 'Fermer', { duration: 4000 })
    });
  }

  private toDateInput(date: string | Date): string {
    return new Date(date).toISOString().substring(0, 10);
  }
}
