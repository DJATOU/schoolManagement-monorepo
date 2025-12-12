import { CommonModule } from '@angular/common';
import { Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatSelectModule } from '@angular/material/select';
import { GroupTransferService } from '../../../services/group-transfer.service';
import { GroupTransfer, TransferImpactCalculation } from '../../../models/transfer/group-transfer';

@Component({
  selector: 'app-transfer-dialog',
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
    <h2 mat-dialog-title>Transférer l'étudiant</h2>
    <mat-dialog-content [formGroup]="transferForm" class="transfer-content">
      <mat-form-field appearance="fill">
        <mat-label>Groupe de destination</mat-label>
        <input matInput type="number" formControlName="toGroupId" />
      </mat-form-field>
      <mat-form-field appearance="fill">
        <mat-label>Motif</mat-label>
        <input matInput formControlName="reason" />
      </mat-form-field>
      <button mat-stroked-button color="primary" (click)="calculateImpact()" [disabled]="transferForm.invalid">Calculer l'impact</button>
      <div *ngIf="impact" class="impact">
        <p>Différence de prix: {{ impact.priceDifference }}</p>
        <p>Ajustement total: {{ impact.totalAdjustment }}</p>
        <p>Recommandation: {{ impact.recommendation }}</p>
      </div>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Annuler</button>
      <button mat-raised-button color="primary" (click)="transfer()" [disabled]="transferForm.invalid">Confirmer le transfert</button>
    </mat-dialog-actions>
  `,
  styles: [`.transfer-content { display: grid; gap: 12px; } .impact { background: #f5f5f5; padding: 8px; border-radius: 4px; }`]
})
export class TransferDialogComponent {
  transferForm: FormGroup;
  impact: TransferImpactCalculation | null = null;

  constructor(
    private fb: FormBuilder,
    private transferService: GroupTransferService,
    private snackBar: MatSnackBar,
    private dialogRef: MatDialogRef<TransferDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { studentId: number; fromGroupId: number }
  ) {
    this.transferForm = this.fb.group({
      toGroupId: [null, Validators.required],
      reason: [''],
      transferDate: [new Date(), Validators.required]
    });
  }

  calculateImpact(): void {
    const toGroupId = this.transferForm.value.toGroupId;
    this.transferService
      .calculateTransferImpact(this.data.studentId, this.data.fromGroupId, toGroupId)
      .subscribe({
        next: impact => {
          this.impact = impact;
          this.snackBar.open('Impact calculé', 'Fermer', { duration: 3000 });
        },
        error: () => this.snackBar.open('Erreur lors du calcul de l\'impact', 'Fermer', { duration: 4000 })
      });
  }

  transfer(): void {
    const payload: Partial<GroupTransfer> = {
      ...this.transferForm.value,
      studentId: this.data.studentId,
      fromGroupId: this.data.fromGroupId,
      status: 'PENDING'
    };

    this.transferService.transferStudent(payload).subscribe({
      next: transfer => {
        this.snackBar.open('Transfert créé', 'Fermer', { duration: 3000 });
        this.dialogRef.close(transfer);
      },
      error: () => this.snackBar.open('Erreur lors du transfert', 'Fermer', { duration: 4000 })
    });
  }
}
