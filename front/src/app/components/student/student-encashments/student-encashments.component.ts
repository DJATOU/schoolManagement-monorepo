import { CommonModule, DecimalPipe } from '@angular/common';
import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Observable, of, tap } from 'rxjs';

import { Encashment, EncashmentAllocation } from '../../../models/payment/encashment';
import {
  CorrectionReason,
  CorrectionReasonType,
  EncashmentChanges,
  EncashmentCorrection
} from '../../../models/correction/correction';
import { CorrectionStep, EncashmentService } from '../../../services/encashment.service';
import { PaymentReceiptPdfService } from '../../../services/payment-receipt-pdf.service';
import { receiptFromEncashment } from '../../../utils/encashment-receipt';
import { PAYMENT_METHOD_OPTIONS } from '../../../utils/form-options';
import {
  CorrectionDialogComponent,
  CorrectionDialogData,
  CorrectionDialogResult
} from '../../shared/correction-dialog/correction-dialog.component';
import {
  EncashmentEditDialogComponent,
  EncashmentEditDialogData
} from './encashment-edit-dialog/encashment-edit-dialog.component';

/**
 * Historique des versements d'un élève : un Encaissement par ligne, le plus récent d'abord, et ce
 * qu'on peut en faire — réimprimer, annuler, corriger (spec admin-corrections, A.9 et B.7 ;
 * exigences 1.1, 2, 3).
 *
 * <p>Chaque ligne est un versement tel qu'il a eu lieu : numéro de reçu, date et heure, montant
 * reçu, groupe et Série visés, mode, auteur, parts reportées. Un versement annulé reste listé,
 * marqué comme tel, avec son remplacement s'il en a un.</p>
 *
 * <p><b>Corriger</b> passe par deux dialogues successifs : la saisie de l'état voulu, puis
 * l'Aperçu commun à toutes les corrections, d'où l'on peut revenir à la saisie. <b>Annuler</b> ouvre
 * directement l'Aperçu. Après un Remplacement, l'impression du nouveau reçu est proposée (3.7).</p>
 *
 * <p><b>Réimprimer</b> relit l'Encaissement au serveur : un versement annulé depuis le chargement de
 * la liste ressort avec le tampon « ANNULÉ », jamais comme un reçu valide (2.6).</p>
 *
 * <p>Réservé à l'ADMIN, comme toute donnée financière ; en lecture seule (année passée), annuler et
 * corriger sont désactivés, la réimpression reste possible.</p>
 */
@Component({
  selector: 'app-student-encashments',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatExpansionModule,
    MatIconModule,
    MatProgressSpinnerModule,
    TranslateModule
  ],
  providers: [DecimalPipe],
  templateUrl: './student-encashments.component.html',
  styleUrls: ['./student-encashments.component.scss']
})
export class StudentEncashmentsComponent implements OnChanges {
  /** Élève dont les versements sont listés. */
  @Input() studentId: number | null | undefined;

  /** Lecture seule (année passée) : ni annulation ni correction. */
  @Input() readOnly = false;

  /** Suffixe monétaire, aligné sur le reste de l'application (« 8 000 DA »). */
  readonly currencySuffix = 'DA';

  /** Versements de l'élève, dans l'ordre du serveur : le plus récent d'abord. */
  encashments: Encashment[] = [];
  loading = false;
  errorMessage = '';
  /** Versement en cours de réimpression : son bouton est désactivé pour éviter un double clic. */
  reprintingId: number | null = null;

  /** Motifs proposés par le serveur, chargés au premier besoin. */
  private reasons: CorrectionReasonType[] | null = null;

  constructor(
    private encashmentService: EncashmentService,
    private receiptPdfService: PaymentReceiptPdfService,
    private translate: TranslateService,
    private snackBar: MatSnackBar,
    private dialog: MatDialog,
    private decimal: DecimalPipe
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['studentId']) {
      this.reload();
    }
  }

  /** Recharge la liste, après un nouvel encaissement ou une correction. */
  reload(): void {
    this.errorMessage = '';
    if (this.studentId === null || this.studentId === undefined) {
      this.encashments = [];
      return;
    }
    this.loading = true;
    this.encashmentService.getStudentEncashments(this.studentId).subscribe({
      next: encashments => {
        this.encashments = encashments ?? [];
        this.loading = false;
      },
      error: (err: Error) => {
        this.encashments = [];
        this.errorMessage = err.message || this.translate.instant('payment.encashments.loadError');
        this.loading = false;
      }
    });
  }

  /** Parts reportées sur d'autres séries que la série visée. */
  carryOvers(encashment: Encashment): EncashmentAllocation[] {
    return (encashment.allocations ?? []).filter(allocation => allocation.carriedOver);
  }

  /** Seul un versement actif s'annule ou se corrige, et jamais en lecture seule. */
  canCorrect(encashment: Encashment): boolean {
    return encashment.status === 'ACTIVE' && !this.readOnly;
  }

  /** Libellé traduit du mode de règlement ; le code brut à défaut de libellé connu. */
  methodLabel(encashment: Encashment): string {
    const option = PAYMENT_METHOD_OPTIONS.find(candidate => candidate.value === encashment.paymentMethod);
    return option ? this.translate.instant(option.labelKey) : (encashment.paymentMethod ?? '');
  }

  trackById(_index: number, encashment: Encashment): number {
    return encashment.id;
  }

  // ------------------------------------------------------------------
  // Réimpression
  // ------------------------------------------------------------------

  /**
   * Réimprime le reçu d'un versement, relu au serveur ; annulé, il porte le tampon « ANNULÉ ».
   *
   * <p>Si le versement a été annulé depuis le chargement, la liste est rechargée pour afficher son
   * nouvel état.</p>
   */
  reprint(encashment: Encashment): void {
    if (this.reprintingId !== null) {
      return;
    }
    this.reprintingId = encashment.id;
    this.encashmentService.getEncashment(encashment.id).subscribe({
      next: current => {
        if (current.status !== encashment.status) {
          this.reload();
        }
        this.print(current).finally(() => this.reprintingId = null);
      },
      error: (err: Error) => {
        this.reprintingId = null;
        this.notify(err.message || this.translate.instant('payment.encashments.reprintError'));
      }
    });
  }

  // ------------------------------------------------------------------
  // Annulation
  // ------------------------------------------------------------------

  /** Annuler un versement : Motif, Aperçu, confirmation (exigence 2). */
  cancel(encashment: Encashment): void {
    if (!this.canCorrect(encashment)) {
      return;
    }
    this.loadReasons().subscribe({
      next: reasons => {
        const data: CorrectionDialogData<Encashment> = {
          titleKey: 'correction.cancel.title',
          subject: this.subject(encashment),
          reasons,
          run: (step: CorrectionStep, reason: CorrectionReason, token?: string) =>
            this.encashmentService.cancel(encashment.id, step, reason, token)
        };
        this.dialog.open<CorrectionDialogComponent<Encashment>, CorrectionDialogData<Encashment>,
          CorrectionDialogResult<Encashment>>(CorrectionDialogComponent, { data, width: '620px', maxWidth: '95vw' })
          .afterClosed().subscribe(outcome => {
            if (outcome?.kind === 'confirmed') {
              this.notify(this.translate.instant('correction.cancel.done', { number: encashment.receiptNumber }));
              this.reload();
            }
          });
      },
      error: (err: Error) => this.notify(err.message)
    });
  }

  // ------------------------------------------------------------------
  // Correction
  // ------------------------------------------------------------------

  /**
   * Corriger un versement : saisie de l'état voulu, puis Aperçu ; « Modifier » ramène à la saisie,
   * sans rien perdre de ce qui a été tapé (exigence 3).
   */
  correct(encashment: Encashment, previous?: EncashmentChanges): void {
    if (!this.canCorrect(encashment)) {
      return;
    }
    this.dialog.open<EncashmentEditDialogComponent, EncashmentEditDialogData, EncashmentChanges>(
      EncashmentEditDialogComponent, { data: { encashment, changes: previous }, width: '560px', maxWidth: '95vw' })
      .afterClosed().subscribe(changes => {
        if (changes) {
          this.previewCorrection(encashment, changes);
        }
      });
  }

  private previewCorrection(encashment: Encashment, changes: EncashmentChanges): void {
    this.loadReasons().subscribe({
      next: reasons => {
        const data: CorrectionDialogData<EncashmentCorrection> = {
          titleKey: 'correction.correct.title',
          subject: this.subject(encashment),
          reasons,
          allowBack: true,
          run: (step: CorrectionStep, reason: CorrectionReason, token?: string) =>
            this.encashmentService.correct(encashment.id, step, changes, reason, token)
        };
        this.dialog.open<CorrectionDialogComponent<EncashmentCorrection>, CorrectionDialogData<EncashmentCorrection>,
          CorrectionDialogResult<EncashmentCorrection>>(CorrectionDialogComponent,
          { data, width: '620px', maxWidth: '95vw' })
          .afterClosed().subscribe(outcome => {
            if (outcome?.kind === 'back') {
              this.correct(encashment, changes);
            } else if (outcome?.kind === 'confirmed') {
              this.reload();
              this.announce(encashment, outcome.result);
            }
          });
      },
      error: (err: Error) => this.notify(err.message)
    });
  }

  /** Après un Remplacement, l'impression du nouveau reçu est proposée (exigence 3.7). */
  private announce(encashment: Encashment, correction: EncashmentCorrection): void {
    const replacement = correction.replacement;
    if (!replacement) {
      this.notify(this.translate.instant('correction.correct.detailsDone', { number: encashment.receiptNumber }));
      return;
    }
    this.snackBar.open(
      this.translate.instant('correction.correct.replaced', {
        original: encashment.receiptNumber,
        replacement: replacement.receiptNumber
      }),
      this.translate.instant('correction.correct.print'),
      { duration: 15000 })
      .onAction().subscribe(() => this.print(replacement));
  }

  // ------------------------------------------------------------------

  /** « Reçu RECU-2030-0001 de 3 000,00 DA — Janvier (Math 1ère A) ». */
  private subject(encashment: Encashment): string {
    return this.translate.instant('correction.subject', {
      number: encashment.receiptNumber,
      amount: this.decimal.transform(encashment.amountReceived, '1.2-2'),
      series: encashment.targetSeriesName,
      group: encashment.groupName
    });
  }

  private loadReasons(): Observable<CorrectionReasonType[]> {
    return this.reasons
      ? of(this.reasons)
      : this.encashmentService.getCorrectionReasons().pipe(tap(reasons => this.reasons = reasons));
  }

  /** La génération est asynchrone (chargement du logo) : l'échec est capté sur la promesse. */
  private print(encashment: Encashment): Promise<void> {
    return this.receiptPdfService.generateAndPrint(receiptFromEncashment(encashment, this.methodLabel(encashment)))
      .catch((err: unknown) => {
        console.error('Erreur lors de l\'impression du reçu :', err);
        this.notify(this.translate.instant('payment.encashments.reprintError'));
      });
  }

  private notify(message: string): void {
    this.snackBar.open(message, this.translate.instant('common.close'), { duration: 6000 });
  }
}
