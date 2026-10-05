import { CommonModule } from '@angular/common';
import { Component, Inject, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Observable, Subscription } from 'rxjs';
import { PayableSeries, Payout, PayoutError, PayoutPreview, TeacherPayRate } from '../../../../models/payroll/payroll';
import { AmountPipe } from '../../../../pipes/amount.pipe';
import { TeacherPayoutService } from '../../../../services/teacher-payout.service';

/** Longueur maximale d'une note de paie, alignée sur celle d'un motif de correction. */
export const MAX_PAYOUT_NOTE_LENGTH = 500;

/**
 * Ce que le dialogue paie ou régularise.
 *
 * @param series la ligne de « À payer »
 * @param mode   `pay` pour une série terminée, `regularize` pour une série payée dont l'encaissé a changé
 * @param rates  taux actifs, proposés pour une paie ; inutiles pour une régularisation, qui garde
 *               le pourcentage de la paie initiale
 */
export interface PayoutDialogData {
  series: PayableSeries;
  mode: 'pay' | 'regularize';
  rates: TeacherPayRate[];
}

/**
 * Payer un enseignant, ou régulariser sa paie : lire le calcul, puis confirmer (spec teacher-payroll,
 * exigences 3, 4 et 6).
 *
 * <ul>
 *   <li>Aucun taux par défaut : il est choisi. Changer de taux efface l'Aperçu, qu'il faut relire.</li>
 *   <li>Le calcul est dit en clair, tel qu'on l'expliquera à l'enseignant : encaissé, pourcentage,
 *       les deux parts.</li>
 *   <li>Si l'encaissé a changé depuis l'Aperçu (409), le nouveau calcul remplace l'ancien, avec un
 *       avertissement : on confirme de nouveau, en connaissance de cause.</li>
 *   <li>Une régularisation négative est une retenue : elle se lit comme une somme que l'enseignant
 *       doit rendre.</li>
 * </ul>
 *
 * <p>Le dialogue se ferme sur la paie enregistrée ; l'appelant propose alors le bordereau.</p>
 */
@Component({
  selector: 'app-payout-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    TranslateModule,
    AmountPipe
  ],
  templateUrl: './payout-dialog.component.html',
  styleUrls: ['./payout-dialog.component.scss']
})
export class PayoutDialogComponent implements OnInit, OnDestroy {
  readonly currencySuffix = 'DA';
  readonly maxNoteLength = MAX_PAYOUT_NOTE_LENGTH;
  readonly form: FormGroup;

  preview: PayoutPreview | null = null;
  /** Vrai quand le calcul affiché remplace un calcul périmé. */
  staleNotice = false;
  errorMessage = '';
  busy = false;

  private readonly rateChanges: Subscription;

  constructor(
    @Inject(MAT_DIALOG_DATA) readonly data: PayoutDialogData,
    private dialogRef: MatDialogRef<PayoutDialogComponent, Payout | undefined>,
    private payoutService: TeacherPayoutService,
    private translate: TranslateService,
    fb: FormBuilder
  ) {
    this.form = fb.group({
      rateId: [null, this.paying ? Validators.required : []],
      note: ['', Validators.maxLength(MAX_PAYOUT_NOTE_LENGTH)]
    });
    // Le jeton couvre le taux : en changer rend l'Aperçu caduc. La note, elle, n'y entre pas.
    this.rateChanges = this.form.get('rateId')!.valueChanges.subscribe(() => this.resetPreview());
  }

  ngOnInit(): void {
    if (!this.paying) {
      this.requestPreview();
    }
  }

  ngOnDestroy(): void {
    this.rateChanges.unsubscribe();
  }

  get paying(): boolean {
    return this.data.mode === 'pay';
  }

  /** Retenue : la régularisation reprend de l'argent à l'enseignant. */
  get deduction(): boolean {
    return !!this.preview && this.preview.teacherAmount < 0;
  }

  /** Somme que l'enseignant doit rendre, positive. */
  get deductionAmount(): number {
    return this.preview ? -this.preview.teacherAmount : 0;
  }

  /** Ce que l'enseignant doit avoir reçu en tout sur l'encaissé actuel : déjà versé + écart. */
  get teacherDue(): number {
    return this.preview ? this.preview.teacherPaid + this.preview.teacherAmount : 0;
  }

  get canPreview(): boolean {
    return !this.busy && (!this.paying || this.form.get('rateId')!.valid);
  }

  requestPreview(): void {
    if (!this.canPreview) {
      this.form.markAllAsTouched();
      return;
    }
    this.begin();
    const request: Observable<PayoutPreview> = this.paying
      ? this.payoutService.previewPay(this.data.series.seriesId, this.form.get('rateId')!.value, this.note())
      : this.payoutService.previewRegularize(this.data.series.seriesId);
    request.subscribe({
      next: preview => {
        this.busy = false;
        this.preview = preview;
      },
      error: (err: unknown) => this.fail(err)
    });
  }

  confirm(): void {
    if (!this.preview || this.busy || this.form.get('note')!.invalid) {
      return;
    }
    const token = this.preview.previewToken;
    this.begin();
    const request: Observable<Payout> = this.paying
      ? this.payoutService.confirmPay(this.data.series.seriesId, this.form.get('rateId')!.value, this.note(), token)
      : this.payoutService.confirmRegularize(this.data.series.seriesId, this.note(), token);
    request.subscribe({
      next: payout => {
        this.busy = false;
        this.dialogRef.close(payout);
      },
      error: (err: unknown) => {
        if (err instanceof PayoutError && err.stale) {
          // Le nouveau calcul remplace l'ancien ; la confirmation reste à faire, sur lui.
          this.busy = false;
          this.preview = err.preview;
          this.staleNotice = true;
          return;
        }
        this.fail(err);
      }
    });
  }

  cancel(): void {
    this.dialogRef.close();
  }

  private note(): string | null {
    const note = (this.form.get('note')!.value ?? '').trim();
    return note.length > 0 ? note : null;
  }

  private begin(): void {
    this.busy = true;
    this.errorMessage = '';
  }

  private fail(err: unknown): void {
    this.busy = false;
    this.preview = null;
    this.staleNotice = false;
    this.errorMessage = err instanceof Error ? err.message : this.translate.instant('teacherPayroll.dialog.error');
  }

  private resetPreview(): void {
    this.preview = null;
    this.staleNotice = false;
    this.errorMessage = '';
  }
}
