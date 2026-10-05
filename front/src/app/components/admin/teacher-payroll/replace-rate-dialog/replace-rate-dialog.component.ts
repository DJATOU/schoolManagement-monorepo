import { CommonModule } from '@angular/common';
import { Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';
import { Payout, TeacherPayRate } from '../../../../models/payroll/payroll';
import { AmountPipe } from '../../../../pipes/amount.pipe';
import { MAX_PAYOUT_NOTE_LENGTH } from '../payout-dialog/payout-dialog.component';

/** Taux et note de la paie de remplacement, éventuellement déjà choisis (retour depuis l'Aperçu). */
export interface ReplacementChoice {
  rateId: number;
  note: string | null;
}

export interface ReplaceRateDialogData {
  payout: Payout;
  /** Taux actifs ; celui de la paie d'origine n'est pas proposé. */
  rates: TeacherPayRate[];
  previous?: ReplacementChoice;
}

/**
 * Premier temps d'un remplacement de paie : choisir l'autre taux (spec teacher-payroll, exigence
 * 7.2). Le Motif, l'Aperçu et la confirmation suivent dans le dialogue commun des corrections.
 */
@Component({
  selector: 'app-replace-rate-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    TranslateModule,
    AmountPipe
  ],
  template: `
    <h2 mat-dialog-title>{{ 'teacherPayroll.correction.replaceTitle' | translate }}</h2>
    <mat-dialog-content class="rr-content">
      <p class="rr-subject">
        {{ 'teacherPayroll.correction.replaceSubject' | translate: {
             number: data.payout.payoutNumber, label: data.payout.rateLabel, percent: data.payout.teacherPercent,
             amount: (data.payout.teacherAmount | amount), teacher: data.payout.teacherName
           } }}
      </p>
      <p class="rr-hint">{{ 'teacherPayroll.correction.replaceHint' | translate }}</p>
      <form [formGroup]="form" class="rr-form" (ngSubmit)="next()">
        <mat-form-field appearance="outline">
          <mat-label>{{ 'teacherPayroll.correction.newRate' | translate }}</mat-label>
          <mat-select formControlName="rateId" required>
            <mat-option *ngFor="let rate of rates" [value]="rate.id">
              {{ 'teacherPayroll.dialog.rateOption' | translate: { label: rate.label, percent: rate.teacherPercent } }}
            </mat-option>
          </mat-select>
          <mat-hint *ngIf="rates.length === 0">{{ 'teacherPayroll.correction.noOtherRate' | translate }}</mat-hint>
          <mat-error>{{ 'teacherPayroll.dialog.rateRequired' | translate }}</mat-error>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>{{ 'teacherPayroll.dialog.note' | translate }}</mat-label>
          <textarea matInput formControlName="note" rows="2" [attr.maxlength]="maxNoteLength"
            [placeholder]="data.payout.note || ''"></textarea>
          <mat-hint>{{ 'teacherPayroll.correction.noteKept' | translate }}</mat-hint>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" (click)="close()">{{ 'common.cancel' | translate }}</button>
      <button mat-flat-button color="primary" type="button" class="rr-next" (click)="next()" [disabled]="form.invalid">
        {{ 'teacherPayroll.correction.next' | translate }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .rr-content { min-width: min(460px, 90vw); }
    .rr-subject { margin: 0 0 6px; font-weight: 600; color: #1e293b; }
    .rr-hint { margin: 0 0 12px; font-size: 0.85rem; color: #64748b; }
    .rr-form { display: flex; flex-direction: column; }
  `]
})
export class ReplaceRateDialogComponent {
  readonly maxNoteLength = MAX_PAYOUT_NOTE_LENGTH;
  readonly form: FormGroup;
  readonly rates: TeacherPayRate[];

  constructor(
    @Inject(MAT_DIALOG_DATA) readonly data: ReplaceRateDialogData,
    private dialogRef: MatDialogRef<ReplaceRateDialogComponent, ReplacementChoice | undefined>,
    fb: FormBuilder
  ) {
    // Le même taux serait refusé : il n'est pas proposé. Le serveur compare l'identifiant ; ici le
    // libellé figé de la paie suffit à l'écarter de la liste.
    this.rates = data.rates.filter(rate => rate.active && rate.label !== data.payout.rateLabel);
    this.form = fb.group({
      rateId: [data.previous?.rateId ?? null, Validators.required],
      note: [data.previous?.note ?? '', Validators.maxLength(MAX_PAYOUT_NOTE_LENGTH)]
    });
  }

  next(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const note = (this.form.get('note')!.value ?? '').trim();
    this.dialogRef.close({ rateId: this.form.get('rateId')!.value, note: note.length > 0 ? note : null });
  }

  close(): void {
    this.dialogRef.close();
  }
}
