import { CommonModule } from '@angular/common';
import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { Encashment, EncashmentAllocation } from '../../../models/payment/encashment';
import { EncashmentService } from '../../../services/encashment.service';
import { PaymentReceiptPdfService } from '../../../services/payment-receipt-pdf.service';
import { receiptFromEncashment } from '../../../utils/encashment-receipt';
import { PAYMENT_METHOD_OPTIONS } from '../../../utils/form-options';

/**
 * Historique des versements d'un élève : un Encaissement par ligne, le plus récent d'abord
 * (spec admin-corrections, A.9, exigence 1.1).
 *
 * <p>Chaque ligne est un versement tel qu'il a eu lieu : numéro de reçu, date et heure, montant
 * reçu, groupe et série visés, mode, auteur, et les parts reportées sur d'autres séries. Un
 * versement annulé reste listé, marqué comme tel : c'est la trace qu'un reçu remis à une famille
 * n'est plus valable.</p>
 *
 * <p>Réservé à l'ADMIN, comme toute donnée financière : le serveur refuse les autres rôles, et
 * l'appelant n'insère le composant que pour l'ADMIN.</p>
 *
 * <p>La réimpression relit l'Encaissement au serveur plutôt que de réutiliser la ligne affichée :
 * un versement annulé depuis le chargement de la liste ne doit pas ressortir sur un reçu
 * valide.</p>
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
  templateUrl: './student-encashments.component.html',
  styleUrls: ['./student-encashments.component.scss']
})
export class StudentEncashmentsComponent implements OnChanges {
  /** Élève dont les versements sont listés. */
  @Input() studentId: number | null | undefined;

  /** Suffixe monétaire, aligné sur le reste de l'application (« 8 000 DA »). */
  readonly currencySuffix = 'DA';

  /** Versements de l'élève, dans l'ordre du serveur : le plus récent d'abord. */
  encashments: Encashment[] = [];
  loading = false;
  errorMessage = '';
  /** Versement en cours de réimpression : son bouton est désactivé pour éviter un double clic. */
  reprintingId: number | null = null;

  constructor(
    private encashmentService: EncashmentService,
    private receiptPdfService: PaymentReceiptPdfService,
    private translate: TranslateService,
    private snackBar: MatSnackBar
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['studentId']) {
      this.reload();
    }
  }

  /** Recharge la liste, après un nouvel encaissement par exemple. */
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

  /** Seul un versement actif se réimprime : le reçu d'un versement annulé n'est plus valable. */
  canReprint(encashment: Encashment): boolean {
    return encashment.status === 'ACTIVE';
  }

  /** Libellé traduit du mode de règlement ; le code brut à défaut de libellé connu. */
  methodLabel(encashment: Encashment): string {
    const option = PAYMENT_METHOD_OPTIONS.find(candidate => candidate.value === encashment.paymentMethod);
    return option ? this.translate.instant(option.labelKey) : (encashment.paymentMethod ?? '');
  }

  trackById(_index: number, encashment: Encashment): number {
    return encashment.id;
  }

  /**
   * Réimprime le reçu d'un versement, relu au serveur.
   *
   * <p>Si le versement a été annulé depuis le chargement, rien n'est imprimé : la liste est
   * rechargée pour afficher son nouvel état.</p>
   */
  reprint(encashment: Encashment): void {
    if (!this.canReprint(encashment) || this.reprintingId !== null) {
      return;
    }
    this.reprintingId = encashment.id;
    this.encashmentService.getEncashment(encashment.id).subscribe({
      next: current => {
        if (!this.canReprint(current)) {
          this.reprintingId = null;
          this.notify(this.translate.instant('payment.encashments.cancelledMeanwhile',
            { number: current.receiptNumber }));
          this.reload();
          return;
        }
        // La génération est asynchrone (chargement du logo) : l'échec est capté sur la promesse.
        this.receiptPdfService.generateAndPrint(receiptFromEncashment(current, this.methodLabel(current)))
          .catch((err: unknown) => {
            console.error('Erreur lors de la réimpression du reçu :', err);
            this.notify(this.translate.instant('payment.encashments.reprintError'));
          })
          .finally(() => this.reprintingId = null);
      },
      error: (err: Error) => {
        this.reprintingId = null;
        this.notify(err.message || this.translate.instant('payment.encashments.reprintError'));
      }
    });
  }

  private notify(message: string): void {
    this.snackBar.open(message, this.translate.instant('common.close'), { duration: 6000 });
  }
}
