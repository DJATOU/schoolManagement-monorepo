import { CommonModule } from '@angular/common';
import { Component, Inject, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Refund, RefundCap } from '../../../models/refund/refund';
import { RefundReceiptPdfService } from '../../../services/refund-receipt-pdf.service';
import { RefundService } from '../../../services/refund.service';

/** Données d'ouverture du dialogue de remboursement. */
export interface RefundCreateDialogData {
  paymentId: number;
  studentId: number;
  /** Nom de l'étudiant, affiché en récapitulatif. */
  studentName?: string;
  /** Nom de la série du versement, affiché en récapitulatif. */
  seriesName?: string;
}

/**
 * Enregistrement d'un remboursement depuis l'historique de paiement.
 *
 * <h2>Trois protections qui vont au-delà de la validation de formulaire</h2>
 * Rendre de l'argent est <strong>irréversible</strong> dans cette application : l'annulation d'un
 * remboursement n'existe pas. Le dialogue en tire trois conséquences.
 *
 * <p>Une <strong>confirmation explicite</strong> est demandée avant l'envoi, rappelant le montant, le
 * motif et le caractère non annulable. Un clic malencontreux sur « Enregistrer » ne doit pas sortir
 * d'argent de la caisse.</p>
 *
 * <p>La validation est <strong>verrouillée pendant la requête</strong>. Sans cela, un double clic
 * produit deux remboursements distincts, tous deux valides du point de vue du serveur.</p>
 *
 * <p>Le <strong>rejet serveur est affiché même après un blocage client réussi</strong>. Le plafond
 * peut avoir changé depuis l'ouverture du formulaire — un encaissement concurrent, ou un autre
 * remboursement — et le client ne peut pas le savoir. Les montants affichés sont alors remplacés par
 * ceux que le serveur renvoie.</p>
 */
@Component({
  selector: 'app-refund-create-dialog',
  standalone: true,
  templateUrl: './refund-create-dialog.component.html',
  styleUrls: ['./refund-create-dialog.component.scss'],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    TranslateModule
  ]
})
export class RefundCreateDialogComponent implements OnInit {

  /** Longueur maximale du motif, alignée sur la contrainte du serveur. */
  static readonly MAX_REASON_LENGTH = 500;

  form: FormGroup;

  /** Plafond courant, rechargé après un rejet serveur. */
  cap?: RefundCap;

  /** Chargement du plafond à l'ouverture. */
  loadingCap = true;

  /** Requête d'enregistrement en cours : verrouille la validation (double envoi). */
  submitting = false;

  /** Confirmation affichée avant l'envoi définitif. */
  confirming = false;

  /** Message d'erreur serveur, conservé tel quel : il nomme les montants en jeu. */
  errorMessage?: string;

  /** Vrai lorsque la requête n'a pas répondu à temps : le résultat est inconnu, pas échoué. */
  outcomeUnknown = false;

  constructor(
    private fb: FormBuilder,
    private refundService: RefundService,
    private receiptPdf: RefundReceiptPdfService,
    private translate: TranslateService,
    public dialogRef: MatDialogRef<RefundCreateDialogComponent, Refund | undefined>,
    @Inject(MAT_DIALOG_DATA) public data: RefundCreateDialogData
  ) {
    this.form = this.fb.group({
      amount: [null, [Validators.required, Validators.min(0.01)]],
      reason: ['', [
        Validators.required,
        Validators.maxLength(RefundCreateDialogComponent.MAX_REASON_LENGTH)
      ]]
    });
  }

  ngOnInit(): void {
    this.loadCap();
  }

  /** Montant restant remboursable, 0 tant que le plafond n'est pas connu. */
  get refundableCap(): number {
    return this.cap?.refundableCap ?? 0;
  }

  /** Vrai si plus rien n'est remboursable : le formulaire est alors inutilisable. */
  get capExhausted(): boolean {
    return !this.loadingCap && this.refundableCap <= 0;
  }

  /** Montant saisi, normalisé. */
  get amount(): number {
    const raw = this.form.get('amount')?.value;
    return typeof raw === 'number' ? raw : Number(raw);
  }

  get reason(): string {
    return String(this.form.get('reason')?.value ?? '').trim();
  }

  /**
   * Vrai lorsque le montant saisi dépasse le plafond connu.
   *
   * <p>Blocage côté client, qui évite un aller-retour inutile — mais ne dispense pas du contrôle
   * serveur, le plafond ayant pu changer entre-temps.</p>
   */
  get exceedsCap(): boolean {
    const amount = this.amount;
    return Number.isFinite(amount) && amount > this.refundableCap;
  }

  /** La validation n'est possible que si tout est réuni et qu'aucune requête n'est en cours. */
  get canSubmit(): boolean {
    return this.form.valid
      && !this.exceedsCap
      && !this.capExhausted
      && !this.submitting
      && !this.loadingCap;
  }

  /** Étape 1 : demande de confirmation, sans aucun appel serveur. */
  requestConfirmation(): void {
    if (!this.canSubmit) {
      return;
    }
    this.errorMessage = undefined;
    this.confirming = true;
  }

  /** Renonciation : aucune requête n'est transmise et les saisies sont conservées. */
  cancelConfirmation(): void {
    this.confirming = false;
  }

  /** Étape 2 : enregistrement effectif, après confirmation explicite. */
  confirmAndSubmit(): void {
    if (this.submitting) {
      return;
    }
    this.submitting = true;
    this.errorMessage = undefined;
    this.outcomeUnknown = false;

    this.refundService.createRefund({
      paymentId: this.data.paymentId,
      studentId: this.data.studentId,
      amount: this.amount,
      reason: this.reason
    }).subscribe({
      next: (refund) => {
        this.submitting = false;
        this.confirming = false;
        // Le reçu est proposé immédiatement : l'argent est sorti, le justificatif doit suivre.
        this.offerReceipt(refund);
        this.dialogRef.close(refund);
      },
      error: (err: Error) => {
        this.submitting = false;
        this.confirming = false;
        this.errorMessage = err.message;
        // Serveur injoignable : la demande a peut-être abouti. Le dire, plutôt que d'annoncer un
        // échec qui pourrait pousser l'administrateur à ressaisir le remboursement.
        this.outcomeUnknown = err.message.includes('inconnu');
        // Le plafond a pu changer : on le recharge pour afficher la réalité du serveur.
        this.loadCap();
      }
    });
  }

  /**
   * Produit le reçu du remboursement enregistré.
   *
   * <p>Un échec de génération n'annule rien et n'est pas remonté comme une erreur d'enregistrement :
   * le remboursement est bien enregistré, et le reçu reste réimprimable depuis l'historique.</p>
   */
  private offerReceipt(refund: Refund): void {
    this.refundService.issueReceipt(refund.id).subscribe({
      next: (receipt) => void this.receiptPdf.generateAndPrint(receipt),
      error: (err: Error) => console.error('Reçu de remboursement indisponible :', err.message)
    });
  }

  /** Charge le plafond du versement. */
  private loadCap(): void {
    this.loadingCap = true;
    this.refundService.getRefundableCap(this.data.paymentId).subscribe({
      next: (cap) => {
        this.cap = cap;
        this.loadingCap = false;
      },
      error: (err: Error) => {
        this.loadingCap = false;
        this.errorMessage = err.message;
      }
    });
  }

  /** Montant à rendre, tel qu'annoncé dans la confirmation. */
  confirmationAmount(): string {
    return this.translate.instant('refund.dialog.confirmAmount', {
      amount: this.amount?.toFixed(2)
    });
  }
}
