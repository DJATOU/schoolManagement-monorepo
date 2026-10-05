import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Observable } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { TeacherPayRate } from '../../../../models/payroll/payroll';
import { TeacherPayRateService } from '../../../../services/teacher-pay-rate.service';

/** Longueur maximale d'un libellé de taux, alignée sur la colonne. */
export const MAX_RATE_LABEL_LENGTH = 100;

/** Pourcentage strictement entre 0 et 100, au plus deux décimales : la règle du serveur, dite plus tôt. */
const PERCENT_PATTERN = /^\d{1,2}([.,]\d{1,2})?$/;

/**
 * Onglet « Taux » : le catalogue des taux de rémunération, entretenu comme les tarifs (spec
 * teacher-payroll, exigence 1). Créer, modifier, désactiver ; rien n'est supprimé, et rien de ce
 * qui se fait ici ne touche une paie déjà versée.
 */
@Component({
  selector: 'app-rates-tab',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTableModule,
    MatTooltipModule,
    TranslateModule
  ],
  templateUrl: './rates-tab.component.html',
  styleUrls: ['../teacher-payroll.shared.scss', './rates-tab.component.scss']
})
export class RatesTabComponent implements OnInit {
  readonly displayedColumns = ['label', 'teacherPercent', 'schoolPercent', 'status', 'actions'];
  readonly maxLabelLength = MAX_RATE_LABEL_LENGTH;
  readonly form: FormGroup;

  rates: TeacherPayRate[] = [];
  /** Taux en cours de modification ; aucun pour une création. */
  editing: TeacherPayRate | null = null;
  isLoading = false;
  saving = false;
  errorMessage = '';

  constructor(
    fb: FormBuilder,
    private rateService: TeacherPayRateService,
    private snackBar: MatSnackBar,
    private translate: TranslateService
  ) {
    this.form = fb.group({
      label: ['', [Validators.required, Validators.maxLength(MAX_RATE_LABEL_LENGTH)]],
      teacherPercent: ['', [Validators.required, Validators.pattern(PERCENT_PATTERN), percentInRange]]
    });
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.isLoading = true;
    this.rateService.getRates().pipe(finalize(() => this.isLoading = false)).subscribe({
      next: rates => this.rates = rates,
      error: (err: Error) => {
        this.rates = [];
        this.errorMessage = err.message;
      }
    });
  }

  /** Part de l'école annoncée pendant la saisie : 100 − part de l'enseignant. */
  get schoolPercentPreview(): number | null {
    const percent = parsePercent(this.form.get('teacherPercent')!.value);
    return percent !== null && this.form.get('teacherPercent')!.valid ? Math.round((100 - percent) * 100) / 100 : null;
  }

  edit(rate: TeacherPayRate): void {
    this.editing = rate;
    this.errorMessage = '';
    this.form.setValue({ label: rate.label, teacherPercent: String(rate.teacherPercent) });
  }

  cancelEdit(): void {
    this.editing = null;
    this.errorMessage = '';
    this.form.reset({ label: '', teacherPercent: '' });
  }

  save(): void {
    if (this.form.invalid || this.saving) {
      this.form.markAllAsTouched();
      return;
    }
    const request = {
      label: (this.form.get('label')!.value ?? '').trim(),
      teacherPercent: parsePercent(this.form.get('teacherPercent')!.value)!
    };
    const call: Observable<TeacherPayRate> = this.editing
      ? this.rateService.updateRate(this.editing.id, request)
      : this.rateService.createRate(request);
    const key = this.editing ? 'teacherPayroll.rates.updated' : 'teacherPayroll.rates.created';
    this.saving = true;
    this.errorMessage = '';
    call.pipe(finalize(() => this.saving = false)).subscribe({
      next: rate => {
        this.snackBar.open(this.translate.instant(key, { label: rate.label }), this.translate.instant('common.close'),
          { duration: 4000 });
        this.cancelEdit();
        this.load();
      },
      // Le refus du serveur est dit tel quel : libellé déjà pris, borne du pourcentage.
      error: (err: Error) => this.errorMessage = err.message
    });
  }

  disable(rate: TeacherPayRate): void {
    if (this.saving) {
      return;
    }
    this.saving = true;
    this.rateService.disableRate(rate.id).pipe(finalize(() => this.saving = false)).subscribe({
      next: () => {
        this.snackBar.open(this.translate.instant('teacherPayroll.rates.disabled', { label: rate.label }),
          this.translate.instant('common.close'), { duration: 4000 });
        if (this.editing?.id === rate.id) {
          this.cancelEdit();
        }
        this.load();
      },
      error: (err: Error) => this.errorMessage = err.message
    });
  }

  trackById(_: number, rate: TeacherPayRate): number {
    return rate.id;
  }
}

/** « 62,5 » ou « 62.5 » → 62.5 ; rien si la saisie n'est pas un nombre. */
export function parsePercent(value: unknown): number | null {
  if (value === null || value === undefined || String(value).trim() === '') {
    return null;
  }
  const parsed = Number(String(value).trim().replace(',', '.'));
  return Number.isFinite(parsed) ? parsed : null;
}

/** Strictement entre 0 et 100 : 0 % ne paie rien, 100 % ne laisse rien à l'école. */
function percentInRange(control: { value: unknown }): Record<string, boolean> | null {
  const percent = parsePercent(control.value);
  if (percent === null) {
    return null;
  }
  return percent > 0 && percent < 100 ? null : { range: true };
}
