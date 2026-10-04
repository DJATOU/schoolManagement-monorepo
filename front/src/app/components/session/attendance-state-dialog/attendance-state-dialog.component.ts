import { CommonModule } from '@angular/common';
import { Component, Inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatRadioModule } from '@angular/material/radio';
import { TranslateModule } from '@ngx-translate/core';

import { AttendanceState } from '../../../models/Attendance/attendance-correction';

/** Un état proposé, et la clé de son libellé. */
export interface AttendanceStateOption {
  key: 'PRESENT' | 'ABSENT' | 'JUSTIFIED';
  state: AttendanceState;
}

/** Les trois états d'une ligne, dans l'ordre d'affichage. */
export const ATTENDANCE_STATES: AttendanceStateOption[] = [
  { key: 'PRESENT', state: { present: true, justified: false } },
  { key: 'ABSENT', state: { present: false, justified: false } },
  { key: 'JUSTIFIED', state: { present: false, justified: true } }
];

/** Élève à ajouter, et la séance, déjà rédigée (« Séance du 07/01/2030 (Math 1ère A) »). */
export interface AttendanceStateDialogData {
  studentName: string;
  session: string;
}

/**
 * Choisir l'état de la ligne à ajouter sur une séance validée : présent, absent, absent justifié
 * (spec admin-corrections, D.7 ; exigence 8.1).
 *
 * <p>Le choix est dit avant l'Aperçu, qui en montre l'effet : une présence ajoutée compte dans le dû,
 * une absence non. Présent est proposé d'abord : on ajoute le plus souvent un élève venu qu'on a
 * oublié de noter.</p>
 */
@Component({
  selector: 'app-attendance-state-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, MatButtonModule, MatDialogModule, MatRadioModule, TranslateModule],
  templateUrl: './attendance-state-dialog.component.html',
  styles: [`
    .as-session { margin: 0 0 8px; font-weight: 500; color: #1e293b; }
    .as-options { display: flex; flex-direction: column; gap: 6px; margin: 4px 0 8px; }
    .as-hint { margin: 4px 0 0; font-size: 0.85rem; color: #64748b; }
  `]
})
export class AttendanceStateDialogComponent {
  readonly options = ATTENDANCE_STATES;
  choice: AttendanceStateOption = ATTENDANCE_STATES[0];

  constructor(
    private dialogRef: MatDialogRef<AttendanceStateDialogComponent, AttendanceState>,
    @Inject(MAT_DIALOG_DATA) public data: AttendanceStateDialogData
  ) {}

  confirm(): void {
    this.dialogRef.close(this.choice.state);
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
