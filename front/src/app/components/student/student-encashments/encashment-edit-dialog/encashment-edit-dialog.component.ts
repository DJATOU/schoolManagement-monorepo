import { CommonModule } from '@angular/common';
import { Component, Inject, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';
import { Subscription } from 'rxjs';

import { Encashment } from '../../../../models/payment/encashment';
import { EncashmentChanges } from '../../../../models/correction/correction';
import { Group } from '../../../../models/group/group';
import { SessionSeries } from '../../../../models/sessionSerie/sessionSerie';
import { Student } from '../../domain/student';
import { GroupService } from '../../../../services/group.service';
import { SeriesService } from '../../../../services/series.service';
import { PAYMENT_METHOD_OPTIONS } from '../../../../utils/form-options';

/** Un choix de liste : identifiant et libellé. */
interface Choice {
  id: number;
  label: string;
}

/**
 * @param encashment le versement à corriger
 * @param changes    une saisie précédente, quand l'administratrice revient de l'Aperçu pour la
 *                   modifier
 */
export interface EncashmentEditDialogData {
  encashment: Encashment;
  changes?: EncashmentChanges;
}

/**
 * Saisir le versement tel qu'il aurait dû être : montant, groupe, élève, Série, mode, note (spec
 * admin-corrections, exigence 3.1). Rend l'état voulu complet ; l'Aperçu vient ensuite.
 *
 * <p>Le groupe se choisit parmi ceux où l'élève du versement peut payer, l'élève parmi les inscrits
 * du groupe choisi : le cas courant d'un versement saisi sur un camarade du même groupe. Un versement
 * à reporter sur un élève sans groupe commun s'annule, puis s'encaisse depuis sa fiche.</p>
 *
 * <p>Ce composant ne décide rien : le serveur juge si la correction est un Remplacement ou une
 * correction du mode et de la note, et la refuse si rien ne change.</p>
 */
@Component({
  selector: 'app-encashment-edit-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    TranslateModule
  ],
  templateUrl: './encashment-edit-dialog.component.html',
  styleUrls: ['./encashment-edit-dialog.component.scss']
})
export class EncashmentEditDialogComponent implements OnInit, OnDestroy {
  readonly paymentMethods = PAYMENT_METHOD_OPTIONS;
  readonly form: FormGroup;

  groups: Choice[] = [];
  series: Choice[] = [];
  students: Choice[] = [];
  loadError = false;

  private readonly subscriptions = new Subscription();

  constructor(
    private fb: FormBuilder,
    private dialogRef: MatDialogRef<EncashmentEditDialogComponent, EncashmentChanges>,
    private groupService: GroupService,
    private seriesService: SeriesService,
    @Inject(MAT_DIALOG_DATA) public data: EncashmentEditDialogData
  ) {
    const encashment = data.encashment;
    const start = data.changes ?? {
      amount: encashment.amountReceived,
      studentId: encashment.studentId,
      groupId: encashment.groupId,
      targetSeriesId: encashment.targetSeriesId,
      paymentMethod: encashment.paymentMethod ?? null,
      notes: encashment.notes ?? null
    };
    this.form = this.fb.group({
      amount: [start.amount, [Validators.required, Validators.min(0.01)]],
      groupId: [start.groupId, Validators.required],
      studentId: [start.studentId, Validators.required],
      targetSeriesId: [start.targetSeriesId, Validators.required],
      paymentMethod: [start.paymentMethod ?? null],
      notes: [start.notes ?? '']
    });
  }

  ngOnInit(): void {
    const encashment = this.data.encashment;
    this.groupService.getGroupsForPayment(encashment.studentId).subscribe({
      next: groups => {
        this.groups = this.withCurrent(groups.filter(g => g.id != null).map(g => this.groupChoice(g)),
          encashment.groupId, encashment.groupName);
      },
      error: () => {
        this.loadError = true;
        this.groups = [{ id: encashment.groupId, label: encashment.groupName }];
      }
    });
    this.loadGroupMembers(this.form.get('groupId')!.value, false);
    this.subscriptions.add(this.form.get('groupId')!.valueChanges.subscribe(groupId => {
      if (groupId != null) {
        this.loadGroupMembers(groupId, true);
      }
    }));
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  /** Rien de changé : la correction serait refusée, inutile d'aller jusqu'à l'Aperçu. */
  get unchanged(): boolean {
    const e = this.data.encashment;
    const v = this.form.value;
    return Number(v.amount) === e.amountReceived
      && v.studentId === e.studentId
      && v.groupId === e.groupId
      && v.targetSeriesId === e.targetSeriesId
      && this.blankToNull(v.paymentMethod) === this.blankToNull(e.paymentMethod)
      && this.blankToNull(v.notes) === this.blankToNull(e.notes);
  }

  submit(): void {
    if (this.form.invalid || this.unchanged) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.value;
    this.dialogRef.close({
      amount: Number(v.amount),
      studentId: v.studentId,
      groupId: v.groupId,
      targetSeriesId: v.targetSeriesId,
      paymentMethod: this.blankToNull(v.paymentMethod),
      notes: this.blankToNull(v.notes)
    });
  }

  cancel(): void {
    this.dialogRef.close();
  }

  /**
   * Séries et inscrits du groupe choisi. Sur un changement de groupe, une Série ou un élève qui n'en
   * font pas partie sont désélectionnés : on ne garde pas un choix devenu impossible.
   */
  private loadGroupMembers(groupId: number, groupChanged: boolean): void {
    const encashment = this.data.encashment;
    this.seriesService.getSeriesByGroupId(groupId).subscribe({
      next: series => {
        this.series = series.filter(s => s.id != null).map(s => this.seriesChoice(s));
        if (!groupChanged && groupId === encashment.groupId) {
          this.series = this.withCurrent(this.series, encashment.targetSeriesId, encashment.targetSeriesName);
        }
        this.keepIfAvailable('targetSeriesId', this.series);
      },
      error: () => this.loadError = true
    });
    this.groupService.getStudentsByGroupId(groupId).subscribe({
      next: students => {
        this.students = students.filter(s => s.id != null).map(s => this.studentChoice(s));
        if (groupId === encashment.groupId) {
          this.students = this.withCurrent(this.students, encashment.studentId, encashment.studentName);
        }
        this.keepIfAvailable('studentId', this.students);
      },
      error: () => this.loadError = true
    });
  }

  private keepIfAvailable(control: 'targetSeriesId' | 'studentId', choices: Choice[]): void {
    const value = this.form.get(control)!.value;
    if (value != null && !choices.some(choice => choice.id === value)) {
      this.form.get(control)!.setValue(null);
    }
  }

  /** La valeur actuelle du versement reste toujours choisissable, même absente de la liste servie. */
  private withCurrent(choices: Choice[], id: number, label: string): Choice[] {
    return choices.some(choice => choice.id === id) ? choices : [{ id, label }, ...choices];
  }

  private groupChoice(group: Group): Choice {
    return { id: group.id!, label: group.name };
  }

  private seriesChoice(series: SessionSeries): Choice {
    return { id: series.id!, label: series.name };
  }

  private studentChoice(student: Student): Choice {
    return { id: student.id!, label: `${student.firstName ?? ''} ${student.lastName ?? ''}`.trim() };
  }

  private blankToNull(value: string | null | undefined): string | null {
    const text = (value ?? '').trim();
    return text.length > 0 ? text : null;
  }
}
