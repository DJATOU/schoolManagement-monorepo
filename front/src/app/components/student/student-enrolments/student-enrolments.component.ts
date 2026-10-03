import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { Enrolment, EnrolmentCorrection, EnrolmentCorrectionKind } from '../../../models/enrolment/enrolment';
import { EnrolmentService } from '../../../services/enrolment.service';
import { formatCalendarDay } from '../../../utils/calendar-day';
import { EnrolmentCorrectionFlow } from '../../shared/enrolment-correction/enrolment-correction-flow';

/**
 * Inscriptions d'un élève et leur période : arrivée, départ, et ce qu'on peut en corriger (spec
 * admin-corrections, C.8 ; exigences 5 et 6).
 *
 * <p>Chaque inscription de l'année affichée est listée, close comprise : un élève parti reste dû
 * pour les séances de sa période, et son départ se corrige ou s'annule. Un élève revenu dans un
 * groupe y a deux inscriptions, chacune avec sa période.</p>
 *
 * <p>Actions, réservées à l'ADMIN hors année close : « corriger l'arrivée » ; sur une inscription
 * ouverte, « enregistrer le départ » ; sur une close, « corriger le départ » et « rouvrir ». Chacune
 * passe par un Motif et un Aperçu ({@link EnrolmentCorrectionFlow}).</p>
 */
@Component({
  selector: 'app-student-enrolments',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatExpansionModule, MatIconModule, MatProgressSpinnerModule, TranslateModule],
  templateUrl: './student-enrolments.component.html',
  styleUrls: ['./student-enrolments.component.scss']
})
export class StudentEnrolmentsComponent implements OnChanges {
  @Input() studentId: number | null | undefined;
  /** Année affichée ; toutes les années si nulle. */
  @Input() schoolYearId: number | null | undefined;
  /** Nom de l'élève, rappelé dans les dialogues. */
  @Input() studentName = '';
  /** Corrections permises : ADMIN, année ouverte. */
  @Input() canCorrect = false;

  /** Une correction confirmée : les groupes, versements et statuts de la fiche ont pu changer. */
  @Output() corrected = new EventEmitter<EnrolmentCorrection>();

  readonly formatDay = formatCalendarDay;

  enrolments: Enrolment[] = [];
  loading = false;
  errorMessage = '';
  /** Inscription dont une correction est en cours : ses boutons sont désactivés. */
  correctingId: number | null = null;

  constructor(
    private enrolmentService: EnrolmentService,
    private flow: EnrolmentCorrectionFlow,
    private translate: TranslateService,
    private snackBar: MatSnackBar
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['studentId'] || changes['schoolYearId']) {
      this.reload();
    }
  }

  reload(): void {
    this.errorMessage = '';
    if (this.studentId === null || this.studentId === undefined) {
      this.enrolments = [];
      return;
    }
    this.loading = true;
    this.enrolmentService.getStudentEnrolments(this.studentId, this.schoolYearId).subscribe({
      next: enrolments => {
        this.enrolments = enrolments ?? [];
        this.loading = false;
      },
      error: (err: Error) => {
        this.enrolments = [];
        this.errorMessage = err.message || this.translate.instant('enrolment.list.loadError');
        this.loading = false;
      }
    });
  }

  correct(kind: EnrolmentCorrectionKind, enrolment: Enrolment): void {
    if (!this.canCorrect || this.correctingId !== null) {
      return;
    }
    this.correctingId = enrolment.id;
    this.flow.correct(kind, enrolment, this.studentName).subscribe({
      next: result => {
        this.correctingId = null;
        if (result) {
          this.notify(this.translate.instant(`enrolment.correction.${kind}.done`, { group: enrolment.groupName }));
          this.reload();
          this.corrected.emit(result);
        }
      },
      error: (err: Error) => {
        this.correctingId = null;
        this.notify(err.message || this.translate.instant('enrolment.list.loadError'));
      }
    });
  }

  trackById(_index: number, enrolment: Enrolment): number {
    return enrolment.id;
  }

  private notify(message: string): void {
    this.snackBar.open(message, this.translate.instant('common.close'), { duration: 6000 });
  }
}
