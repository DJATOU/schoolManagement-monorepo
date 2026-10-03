import { CommonModule } from '@angular/common';
import { Component, Inject, OnDestroy } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, ReactiveFormsModule, ValidationErrors } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';
import { Subscription } from 'rxjs';

import { Enrolment, EnrolmentDateChange } from '../../../../models/enrolment/enrolment';
import { calendarDayOf, formatCalendarDay, isCalendarDay } from '../../../../utils/calendar-day';

/**
 * @param kind        corriger l'arrivée, ou enregistrer / corriger le départ
 * @param enrolment   l'inscription corrigée
 * @param studentName élève, pour le rappel en tête du dialogue
 * @param previous    saisie précédente, quand l'administratrice revient de l'Aperçu pour la modifier
 */
export interface EnrolmentDateDialogData {
  kind: 'ARRIVAL' | 'DEPARTURE';
  enrolment: Enrolment;
  studentName: string;
  previous?: EnrolmentDateChange;
}

/** Un jour `yyyy-MM-dd` qui existe au calendrier. */
function calendarDay(control: AbstractControl): ValidationErrors | null {
  return isCalendarDay(control.value) ? null : { calendarDay: true };
}

/**
 * Saisir la date d'une correction d'inscription : nouvelle arrivée, ou départ (spec
 * admin-corrections, exigences 5.5, 6.1, 6.3, 6.5). Rend la saisie ; l'Aperçu vient ensuite.
 *
 * <ul>
 *   <li>Un départ est proposé au jour même (6.1) ; une arrivée, à sa date actuelle.</li>
 *   <li>La date reste un jour `yyyy-MM-dd`, saisi et rendu sans passer par `Date` : aucun fuseau ne
 *       peut le décaler.</li>
 *   <li>Pour un départ, l'administratrice peut demander de retirer aussi les présences ordinaires
 *       postérieures (6.3) ; sinon elles restent, facturées comme séances consommées.</li>
 * </ul>
 *
 * <p>Ce composant ne décide rien : le serveur refuse une date hors de l'année, un départ avant
 * l'arrivée, un chevauchement, et l'Aperçu dit ce que la correction change.</p>
 */
@Component({
  selector: 'app-enrolment-date-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    TranslateModule
  ],
  templateUrl: './enrolment-date-dialog.component.html',
  styleUrls: ['./enrolment-date-dialog.component.scss']
})
export class EnrolmentDateDialogComponent implements OnDestroy {
  readonly form: FormGroup;
  /** La date saisie est celle déjà enregistrée : rien à corriger. */
  unchanged = false;

  private readonly subscription: Subscription;

  constructor(
    private fb: FormBuilder,
    private dialogRef: MatDialogRef<EnrolmentDateDialogComponent, EnrolmentDateChange>,
    @Inject(MAT_DIALOG_DATA) public data: EnrolmentDateDialogData
  ) {
    const proposed = data.previous?.date ?? this.recorded ?? calendarDayOf();
    this.form = this.fb.group({
      date: [proposed, calendarDay],
      removePresencesAfter: [data.previous?.removePresencesAfter ?? false]
    });
    this.unchanged = proposed === this.recorded;
    this.subscription = this.form.get('date')!.valueChanges.subscribe(value => this.unchanged = value === this.recorded);
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  get departure(): boolean {
    return this.data.kind === 'DEPARTURE';
  }

  /** Clé du titre : enregistrer un départ n'est pas corriger un départ déjà enregistré. */
  get titleKey(): string {
    if (!this.departure) {
      return 'enrolment.correction.ARRIVAL.title';
    }
    return this.data.enrolment.departure ? 'enrolment.correction.DEPARTURE.correctTitle'
      : 'enrolment.correction.DEPARTURE.title';
  }

  /** Période actuelle, en clair : « du 01/09/2029 au 14/01/2030 », « à partir du 01/09/2029 ». */
  get period(): { key: string; params: Record<string, string> } {
    const { arrival, departure } = this.data.enrolment;
    return departure
      ? { key: 'enrolment.period.closed', params: { arrival: formatCalendarDay(arrival), departure: formatCalendarDay(departure) } }
      : { key: 'enrolment.period.open', params: { arrival: formatCalendarDay(arrival) } };
  }

  /**
   * Bornes proposées au calendrier : une arrivée au plus tard le jour du départ, un départ au plus
   * tôt le jour de l'arrivée. Une indication seulement : le serveur juge.
   */
  get min(): string | null {
    return this.departure ? this.data.enrolment.arrival : null;
  }

  get max(): string | null {
    return this.departure ? null : this.data.enrolment.departure;
  }

  submit(): void {
    if (this.form.invalid || this.unchanged) {
      this.form.markAllAsTouched();
      return;
    }
    this.dialogRef.close({
      kind: this.data.kind,
      date: this.form.get('date')!.value,
      removePresencesAfter: this.departure && this.form.get('removePresencesAfter')!.value === true
    });
  }

  cancel(): void {
    this.dialogRef.close();
  }

  /** La date enregistrée de ce que l'on corrige ; nulle pour un départ pas encore enregistré. */
  private get recorded(): string | null {
    return this.departure ? this.data.enrolment.departure : this.data.enrolment.arrival;
  }
}
