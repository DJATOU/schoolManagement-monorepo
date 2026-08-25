import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { Payment } from '../../../models/payment/payment';
import { CarriedOverAmount } from '../../../models/payment/payment-allocation';
import { PaymentDetail } from '../../../models/paymentDetail/paymentDetail';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatOptionModule } from '@angular/material/core';
import { TranslateModule } from '@ngx-translate/core';
import { AdminOnlyDirective } from '../../../shared/admin-only.directive';

@Component({
  selector: 'app-payment-confirmation-dialog',
  standalone: true,
  templateUrl: './payment-confirmation-dialog.component.html',
  styleUrls: ['./payment-confirmation-dialog.component.scss'],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatOptionModule,
    TranslateModule,
    AdminOnlyDirective
  ]
})
export class PaymentConfirmationDialogComponent {

  constructor(
    private dialogRef: MatDialogRef<PaymentConfirmationDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: {
      seriesName: string;
      /** Nombre de séances facturables à l'étudiant, et non nombre de séances de la série. */
      numberOfSessions: number;
      /** Séances non facturées : antérieures à l'inscription et non suivies. */
      excludedSessions?: number;
      pricePerSession: number;
      totalCost: number;
      paymentDetails: PaymentDetail[];
      paymentHistory: Payment[];
      totalPaid: number;
      remainingAmount: number;
      isCatchUp: boolean;
      calculationNote: string;
      /** Montant total reçu : part imputée sur la série visée plus parts reportées. */
      amountReceived?: number;
      /** Part imputée sur la série visée, inférieure au montant reçu en cas de report. */
      amountAllocated?: number;
      /** Parts reportées sur les séries suivantes, avec leur série destinataire. */
      carryOvers?: CarriedOverAmount[];
    }
  ) {}

  /**
   * Vrai lorsqu'une part du versement crédite une autre série que celle choisie.
   *
   * <p>Le report est automatique et sans étape de confirmation distincte : ce récapitulatif est
   * le dernier écran où l'administrateur peut constater qu'une partie du montant ne créditera pas
   * la série qu'il a sélectionnée (exigence 9.3).</p>
   */
  get hasCarryOver(): boolean {
    return !!this.data.carryOvers && this.data.carryOvers.length > 0;
  }

  /** Somme des parts reportées. */
  get carriedOverTotal(): number {
    return (this.data.carryOvers ?? []).reduce((total, carryOver) => total + carryOver.amount, 0);
  }

  onConfirm(): void {
    this.dialogRef.close(true); // Confirm the payment
  }

  onCancel(): void {
    this.dialogRef.close(false); // Cancel the payment
  }
}
