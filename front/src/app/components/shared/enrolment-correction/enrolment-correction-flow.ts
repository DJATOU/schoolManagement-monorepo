import { Injectable } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { TranslateService } from '@ngx-translate/core';
import { AsyncSubject, Observable, of, tap } from 'rxjs';

import { AttendanceMarks, CorrectionReason, CorrectionResponse } from '../../../models/correction/correction';
import {
  Enrolment,
  EnrolmentCorrection,
  EnrolmentCorrectionKind,
  EnrolmentCorrectionReasons,
  EnrolmentDateChange
} from '../../../models/enrolment/enrolment';
import { CorrectionStep } from '../../../services/encashment.service';
import { EnrolmentService } from '../../../services/enrolment.service';
import { formatCalendarDay } from '../../../utils/calendar-day';
import {
  CorrectionDialogComponent,
  CorrectionDialogData,
  CorrectionDialogResult
} from '../correction-dialog/correction-dialog.component';
import {
  EnrolmentDateDialogComponent,
  EnrolmentDateDialogData
} from './enrolment-date-dialog/enrolment-date-dialog.component';

/**
 * Mener une correction d'inscription de bout en bout, depuis n'importe quel écran : la fiche élève
 * (ses inscriptions) comme la fiche groupe (« enregistrer le départ ») (spec admin-corrections,
 * C.8 ; exigences 5.5 à 5.7, 6.1, 6.3, 6.5).
 *
 * <ul>
 *   <li><b>Arrivée, départ</b> : saisie de la date, puis Aperçu commun à toutes les corrections, d'où
 *       « Modifier » ramène à la saisie sans rien perdre.</li>
 *   <li><b>Réouverture</b> : rien à saisir, l'Aperçu s'ouvre directement.</li>
 * </ul>
 *
 * <p>Ni service HTTP ni règle métier : il enchaîne les dialogues et confie chaque Aperçu et chaque
 * confirmation à {@link EnrolmentService}. Le serveur seul juge.</p>
 */
@Injectable({ providedIn: 'root' })
export class EnrolmentCorrectionFlow {

  /** Motifs proposés par le serveur, chargés au premier besoin. */
  private reasons: EnrolmentCorrectionReasons | null = null;

  constructor(
    private dialog: MatDialog,
    private enrolmentService: EnrolmentService,
    private translate: TranslateService
  ) {}

  /**
   * Corrige une inscription. Émet l'inscription corrigée une fois confirmée, ou `null` si la
   * correction est abandonnée ; une erreur si les Motifs n'ont pu être lus.
   */
  correct(kind: EnrolmentCorrectionKind, enrolment: Enrolment, studentName: string): Observable<EnrolmentCorrection | null> {
    // AsyncSubject : l'issue est rendue même à qui s'abonne après coup (dialogues fermés d'emblée).
    const outcome = new AsyncSubject<EnrolmentCorrection | null>();
    const done = (result: EnrolmentCorrection | null): void => {
      outcome.next(result);
      outcome.complete();
    };
    this.loadReasons().subscribe({
      next: reasons => kind === 'REOPEN'
        ? this.preview(enrolment, studentName, { kind, date: null, removePresencesAfter: false }, reasons, done)
        : this.editDate(kind, enrolment, studentName, reasons, done),
      error: (err: unknown) => outcome.error(err)
    });
    return outcome.asObservable();
  }

  private editDate(kind: 'ARRIVAL' | 'DEPARTURE', enrolment: Enrolment, studentName: string,
                   reasons: EnrolmentCorrectionReasons, done: (result: EnrolmentCorrection | null) => void,
                   previous?: EnrolmentDateChange): void {
    this.dialog.open<EnrolmentDateDialogComponent, EnrolmentDateDialogData, EnrolmentDateChange>(
      EnrolmentDateDialogComponent, { data: { kind, enrolment, studentName, previous }, width: '520px', maxWidth: '95vw' })
      .afterClosed().subscribe(change => change
        ? this.preview(enrolment, studentName, change, reasons, done)
        : done(null));
  }

  private preview(enrolment: Enrolment, studentName: string, change: EnrolmentDateChange,
                  reasons: EnrolmentCorrectionReasons, done: (result: EnrolmentCorrection | null) => void): void {
    const reopen = change.kind === 'REOPEN';
    const data: CorrectionDialogData<EnrolmentCorrection> = {
      titleKey: this.titleKey(change.kind, enrolment),
      subject: this.subject(enrolment, studentName),
      reasons: reasons[change.kind] ?? [],
      allowBack: !reopen,
      run: (step: CorrectionStep, reason: CorrectionReason, token?: string, marks?: AttendanceMarks) =>
        this.run(enrolment, change, step, reason, token, marks)
    };
    this.dialog.open<CorrectionDialogComponent<EnrolmentCorrection>, CorrectionDialogData<EnrolmentCorrection>,
      CorrectionDialogResult<EnrolmentCorrection>>(CorrectionDialogComponent, { data, width: '660px', maxWidth: '95vw' })
      .afterClosed().subscribe(outcome => {
        if (outcome?.kind === 'back' && change.kind !== 'REOPEN') {
          this.editDate(change.kind, enrolment, studentName, reasons, done, change);
        } else {
          done(outcome?.kind === 'confirmed' ? outcome.result : null);
        }
      });
  }

  private run(enrolment: Enrolment, change: EnrolmentDateChange, step: CorrectionStep, reason: CorrectionReason,
              token?: string, marks?: AttendanceMarks): Observable<CorrectionResponse<EnrolmentCorrection>> {
    switch (change.kind) {
      case 'ARRIVAL':
        return this.enrolmentService.correctArrival(enrolment.id, step, change.date!, reason, token, marks);
      case 'DEPARTURE':
        return this.enrolmentService.setDeparture(enrolment.id, step, change.date!, change.removePresencesAfter,
          reason, token, marks);
      default:
        return this.enrolmentService.reopen(enrolment.id, step, reason, token, marks);
    }
  }

  /** Enregistrer un départ n'est pas corriger un départ déjà enregistré. */
  private titleKey(kind: EnrolmentCorrectionKind, enrolment: Enrolment): string {
    if (kind === 'DEPARTURE') {
      return enrolment.departure ? 'enrolment.correction.DEPARTURE.correctTitle' : 'enrolment.correction.DEPARTURE.title';
    }
    return `enrolment.correction.${kind}.title`;
  }

  /** « Amine Belkacem — « Math 1ère A », du 01/09/2029 au 14/01/2030 ». */
  private subject(enrolment: Enrolment, studentName: string): string {
    const period = enrolment.departure
      ? this.translate.instant('enrolment.period.closed', {
        arrival: formatCalendarDay(enrolment.arrival), departure: formatCalendarDay(enrolment.departure)
      })
      : this.translate.instant('enrolment.period.open', { arrival: formatCalendarDay(enrolment.arrival) });
    return this.translate.instant('enrolment.correction.subject', { student: studentName, group: enrolment.groupName, period });
  }

  private loadReasons(): Observable<EnrolmentCorrectionReasons> {
    return this.reasons
      ? of(this.reasons)
      : this.enrolmentService.getCorrectionReasons().pipe(tap(reasons => this.reasons = reasons));
  }
}
