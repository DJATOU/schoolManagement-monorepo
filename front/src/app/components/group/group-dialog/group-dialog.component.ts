import { Component, Inject, AfterViewInit, } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, Validators, ReactiveFormsModule, ValidationErrors } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { Group } from '../../../models/group/group';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { gsap } from 'gsap';
import { calendarDayOf, isCalendarDay } from '../../../utils/calendar-day';

/**
 * @param allGroups groupes proposés
 * @param yearStart premier jour de l'année scolaire affichée (`yyyy-MM-dd`), borne du calendrier
 * @param yearEnd   dernier jour de l'année scolaire affichée
 */
export interface GroupDialogData {
  allGroups: Group[];
  selectedGroups?: number[];
  yearStart?: string | null;
  yearEnd?: string | null;
}

/** Groupes choisis et date d'arrivée commune (`yyyy-MM-dd`). */
export interface GroupSelection {
  groupIds: number[];
  arrival: string;
}

function calendarDay(control: AbstractControl): ValidationErrors | null {
  return isCalendarDay(control.value) ? null : { calendarDay: true };
}

/**
 * Inscrire un étudiant à des groupes, à une date d'arrivée réelle (spec admin-corrections, C.8 ;
 * exigences 5.1, 5.2).
 *
 * <p>La date est proposée au jour même et reste un jour `yyyy-MM-dd`, sans conversion en `Date`.
 * Le serveur refuse une date hors de l'année scolaire du groupe, en nommant ses bornes.</p>
 */
@Component({
  selector: 'app-group-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatDialogModule,
    TranslateModule
  ],
  templateUrl: './group-dialog.component.html',
  styleUrls: ['./group-dialog.component.scss']
})
export class GroupDialogComponent implements AfterViewInit {
  groupForm: FormGroup;
  allGroups: Group[] = [];

  constructor(
    private fb: FormBuilder,
    private dialogRef: MatDialogRef<GroupDialogComponent, GroupSelection>,
    @Inject(MAT_DIALOG_DATA) public data: GroupDialogData
  ) {
    this.groupForm = this.fb.group({
      groupIds: [[], Validators.required],
      arrival: [calendarDayOf(), calendarDay]
    });
    this.allGroups = data.allGroups;
  }

  ngAfterViewInit(): void {
    gsap.from('.group-dialog-content', { duration: 0.8, y: -100, opacity: 0, ease: 'bounce' });
    gsap.from('.group-dialog-actions', { duration: 0.8, y: 100, opacity: 0, ease: 'bounce' });
  }

  onSubmit(): void {
    if (this.groupForm.valid && this.groupForm.value.groupIds.length > 0) {
      this.dialogRef.close({ groupIds: this.groupForm.value.groupIds, arrival: this.groupForm.value.arrival });
    }
  }

  onCancel(): void {
    this.dialogRef.close();
  }
}
