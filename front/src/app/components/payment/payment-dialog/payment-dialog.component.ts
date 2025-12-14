import { Component, Inject, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialog, MatDialogModule } from '@angular/material/dialog';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatOptionModule } from '@angular/material/core';
import { Group } from '../../../models/group/group';
import { SessionSeries } from '../../../models/sessionSerie/sessionSerie';
import { SeriesService } from '../../../services/series.service';
import { PaymentService } from '../../../services/payment.service';
import { Payment } from '../../../models/payment/payment';
import { PaymentConfirmationDialogComponent } from '../payment-confirmation-dialog/payment-confirmation-dialog.component';
import { MatSnackBar } from '@angular/material/snack-bar';
import { PaymentDetail } from '../../../models/paymentDetail/paymentDetail';
import { PricingService } from '../../../services/pricing.service';
import { HttpErrorResponse } from '@angular/common/http';
import { AttendanceService } from '../../../services/attendance.service';
import { forkJoin } from 'rxjs';

@Component({
  selector: 'app-payment-dialog',
  standalone: true,
  templateUrl: './payment-dialog.component.html',
  styleUrls: ['./payment-dialog.component.scss'],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatOptionModule
  ]
})
export class PaymentDialogComponent implements OnInit {
  paymentForm: FormGroup;
  groups: Group[];
  sessionSeries: SessionSeries[] = [];
  paymentMethods = [
    { value: 'cash', label: 'Espèces' },
    { value: 'cheque', label: 'Chèque' },
    { value: 'carte_bancaire', label: 'Carte bancaire' },
    { value: 'autre', label: 'Autre' }
  ];
  studentId: number;
  paymentDetails: PaymentDetail[] = [];
  totalAmountPaid = 0;
  totalAmountOwed = 0;
  remainingAmount = 0;
  nextCatchUpSessionId: number | null = null;

  constructor(
    private fb: FormBuilder,
    private dialogRef: MatDialogRef<PaymentDialogComponent>,
    private sessionSeriesService: SeriesService,
    private paymentService: PaymentService,
    private pricingService: PricingService,
    private attendanceService: AttendanceService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
    @Inject(MAT_DIALOG_DATA) public data: { studentId: number, groups: Group[] }
  ) {
    this.groups = data.groups;
    this.studentId = data.studentId;

    this.paymentForm = this.fb.group({
      groupId: [null, Validators.required],
      sessionSeriesId: [null, Validators.required],
      amountPaid: [null, [Validators.required, Validators.min(0)]],
      paymentMethod: ['', Validators.required],
      paymentDescription: ['']
    });
  }

  ngOnInit(): void {
    this.paymentForm.get('groupId')!.valueChanges.subscribe(groupId => {
      this.loadSessionSeries(groupId);
      this.loadSessionPrice(groupId);
    });
  }

  loadSessionSeries(groupId: number | null): void {
    if (groupId) {
      this.sessionSeriesService.getSessionSeriesByGroupId(groupId).subscribe({
        next: (series) => {
          this.sessionSeries = series;
          this.paymentForm.get('sessionSeriesId')!.setValue(null);
        },
        error: (err) => {
          console.error('Erreur lors du chargement des séries de sessions :', err);
        }
      });
    }
  }

  loadSessionPrice(groupId: number | null): void {
    const group = this.groups.find(g => g.id === groupId);

    if (group?.priceId) {
      this.pricingService.getPricingById(group.priceId).subscribe({
        next: pricing => {
          this.sessionPrice = pricing.price;
          const amountControl = this.paymentForm.get('amountPaid');
          if (amountControl && (amountControl.pristine || amountControl.value === null)) {
            amountControl.setValue(pricing.price);
          }
        },
        error: (err) => {
          console.error('Erreur lors du chargement du tarif de la session :', err);
          this.sessionPrice = null;
        }
      });
    } else {
      this.sessionPrice = null;
      this.paymentForm.get('amountPaid')!.reset();
    }
  }

  openConfirmationDialog(paymentData: Payment): void {
    if (!paymentData.sessionSeriesId) {
      this.snackBar.open('Veuillez sélectionner une série avant de poursuivre.', 'Fermer', { duration: 4000 });
      return;
    }

    const sessionSeriesId = paymentData.sessionSeriesId;
    const sessionSeries = this.sessionSeries.find(series => series.id === sessionSeriesId);
    const seriesName = sessionSeries?.name || 'Série inconnue';

    const group = this.groups.find(group => group.id === sessionSeries?.groupId);
    if (group?.priceId) {
      this.nextCatchUpSessionId = null;
      // Charger en parallèle : pricing, attendances, payment history et payment details
      forkJoin({
        pricing: this.pricingService.getPricingById(group.priceId),
        attendances: this.attendanceService.getAttendanceByStudentAndSeries(this.studentId, sessionSeriesId),
        paymentDetails: this.paymentService.getPaymentDetailsForSeries(this.studentId, sessionSeriesId)
        }).subscribe({
          next: ({ pricing, attendances, paymentDetails }) => {
            const pricePerSession = pricing.price;
            const paymentHistory: Payment[] = [];

            // Déterminer si l'étudiant est en rattrapage pour cette série
            // Un étudiant est en rattrapage si TOUTES ses attendances pour cette série ont isCatchUp = true
            const isCatchUpStudent = attendances.length > 0 && attendances.every(a => a.isCatchUp);

          let numberOfSessions: number;
          let totalCost: number;
          let calculationNote: string;

          const totalPaidFromDetails = paymentDetails
            ? paymentDetails.reduce((acc, detail) => acc + (detail.amountPaid || 0), 0)
            : undefined;

          if (isCatchUpStudent) {
            // RATTRAPAGE : Compter uniquement les sessions où l'étudiant est PRÉSENT
            numberOfSessions = attendances.filter(a => a.isCatchUp && a.isPresent).length;
            const totalPaidPreviously = (paymentDetails || [])
              .filter(detail => detail.isCatchUp)
              .reduce((acc, curr) => acc + (curr.amountPaid || 0), 0);

            totalCost = numberOfSessions * pricePerSession;
            calculationNote = 'Rattrapage : paiement par session assistée';

            const newTotalPaid = totalPaidPreviously + paymentData.amountPaid;

            // Vérifier si le nouveau total payé dépasse le coût
            if (newTotalPaid > totalCost) {
              const surplus = newTotalPaid - totalCost;
              this.snackBar.open(
                `Le montant payé dépasse le coût total des sessions de rattrapage (${numberOfSessions}) de ${surplus} DA. Le paiement ne peut pas être effectué.`,
                'Fermer',
                { duration: 5000 }
              );
              return;
            }

            const remainingAmount = totalCost - newTotalPaid;

            // Ouvrir le dialogue de confirmation
            const dialogRef = this.dialog.open(PaymentConfirmationDialogComponent, {
              width: '500px',
              data: {
                seriesName: seriesName,
                numberOfSessions: numberOfSessions,
                pricePerSession: pricePerSession,
                totalCost: totalCost,
                paymentDetails: paymentDetails,
                paymentHistory: paymentHistory,
                totalPaid: newTotalPaid,
                remainingAmount: remainingAmount,
                isCatchUp: isCatchUpStudent,
                calculationNote: calculationNote
              }
            });

            dialogRef.afterClosed().subscribe(result => {
              if (result) {
                this.submitPayment(paymentData);
              }
            });
            return;
          } else {
            // NORMAL : Utiliser le nombre de sessions créées
            numberOfSessions = sessionSeries!.numberOfSessionsCreated;
            totalCost = numberOfSessions * pricePerSession;
            calculationNote = '';
          }

          const totalPaidPreviously = totalPaidFromDetails
            ?? paymentHistory.reduce((acc, curr) => acc + curr.amountPaid, 0);
          const newTotalPaid = totalPaidPreviously + paymentData.amountPaid;

          // Vérifier si le nouveau total payé dépasse le coût
          if (newTotalPaid > totalCost) {
            const surplus = newTotalPaid - totalCost;
            this.snackBar.open(
              `Le montant payé dépasse le coût total (${numberOfSessions} sessions) de ${surplus} DA. Le paiement ne peut pas être effectué.`,
              'Fermer',
              { duration: 5000 }
            );
            return;
          }

          const remainingAmount = totalCost - newTotalPaid;

          // Ouvrir le dialogue de confirmation
          const dialogRef = this.dialog.open(PaymentConfirmationDialogComponent, {
            width: '500px',
            data: {
              seriesName: seriesName,
              numberOfSessions: numberOfSessions,
              pricePerSession: pricePerSession,
              totalCost: totalCost,
              paymentDetails: paymentDetails,
              paymentHistory: paymentHistory,
              totalPaid: newTotalPaid,
              remainingAmount: remainingAmount,
              isCatchUp: isCatchUpStudent,
              calculationNote: calculationNote
            }
          });

          dialogRef.afterClosed().subscribe(result => {
            if (result) {
              this.submitPayment(paymentData);
            }
          });
        },
        error: (err) => {
          console.error('Erreur lors de la récupération des données:', err);
          this.snackBar.open('Erreur lors de la récupération des données.', 'Fermer', { duration: 5000 });
        }
      });
    } else {
      this.snackBar.open('Les informations de tarification du groupe sont introuvables.', 'Fermer', { duration: 5000 });
    }
  }
  
  onSubmit(): void {
    if (this.paymentForm.valid) {
      const paymentData: Payment = {
        ...this.paymentForm.value,
        studentId: this.studentId
      };

      this.openConfirmationDialog(paymentData);
    }
  }

  submitPayment(paymentData: Payment): void {
    if (this.nextCatchUpSessionId) {
      paymentData.sessionId = this.nextCatchUpSessionId;
    }

    const paymentRequest = paymentData.sessionId && this.nextCatchUpSessionId
      ? this.paymentService.processCatchUpPayment(paymentData)
      : this.paymentService.addPayment(paymentData);

    paymentRequest.subscribe({
      next: (response) => {
        this.snackBar.open('Paiement effectué avec succès', 'Fermer', { duration: 3000 });
        this.dialogRef.close(response);
      },
      error: (err: HttpErrorResponse) => {
        console.error('Erreur lors du traitement du paiement:', err);
        let errorMessage = 'Une erreur est survenue lors du traitement du paiement.';
        if (err.error && err.error.message) {
          errorMessage = err.error.message;
        }
        this.snackBar.open(errorMessage, 'Fermer', { duration: 5000 });
      }
    });
  }
  

  onCancel(): void {
    this.dialogRef.close();
  }
}
