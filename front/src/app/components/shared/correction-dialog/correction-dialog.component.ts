import { CommonModule } from '@angular/common';
import { Component, Inject, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Observable, Subscription } from 'rxjs';

import {
  BlockingRefund,
  CorrectionPreview,
  CorrectionReason,
  CorrectionReasonType,
  CorrectionResponse
} from '../../../models/correction/correction';
import { CorrectionError } from '../../../models/correction/correction-error';
import { CorrectionStep } from '../../../services/encashment.service';
import { CorrectionPreviewComponent } from '../correction-preview/correction-preview.component';

/** Longueur maximale du texte d'un Motif, alignée sur le serveur. */
export const MAX_REASON_TEXT_LENGTH = 500;

/**
 * Ce que le dialogue a besoin de savoir d'une correction.
 *
 * @param titleKey  clé du titre (« Annuler le versement »)
 * @param subject   ce qui est corrigé, déjà rédigé (« Reçu RECU-2030-0001 de 3 000,00 DA »)
 * @param reasons   Motifs proposés, dans l'ordre d'affichage
 * @param run       exécute la correction : Aperçu, ou confirmation avec le jeton de l'Aperçu lu
 * @param allowBack vrai pour proposer « Modifier », qui ferme le dialogue en demandant de revenir
 *                  à la saisie
 */
export interface CorrectionDialogData<T> {
  titleKey: string;
  subject: string;
  reasons: CorrectionReasonType[];
  run: (step: CorrectionStep, reason: CorrectionReason, previewToken?: string) => Observable<CorrectionResponse<T>>;
  allowBack?: boolean;
}

/** Issue du dialogue : correction confirmée, retour à la saisie, ou fermeture (`undefined`). */
export type CorrectionDialogResult<T> = { kind: 'confirmed'; result: T } | { kind: 'back' };

/**
 * Mener une correction jusqu'au bout : choisir le Motif, lire l'Aperçu, confirmer (spec
 * admin-corrections, exigences 4 et 11.1-11.2, D7).
 *
 * <p>Composant commun à toutes les corrections. Il ne connaît d'elles que leur fonction
 * {@link CorrectionDialogData.run} : l'Aperçu et la confirmation passent par le même appel serveur,
 * seul le mode change.</p>
 *
 * <ul>
 *   <li>Aucun Motif par défaut : il est choisi, et « Autre » exige un texte.</li>
 *   <li>Le jeton est lié au Motif : changer de Motif efface l'Aperçu, qu'il faut relire.</li>
 *   <li>Si les données ont changé depuis l'Aperçu (409), le nouvel Aperçu remplace l'ancien, avec
 *       un avertissement ; l'administratrice confirme de nouveau, en connaissance de cause.</li>
 *   <li>Un refus (plancher des remboursements, année close…) s'affiche tel que le serveur le
 *       rédige, remboursements en cause nommés.</li>
 * </ul>
 */
@Component({
  selector: 'app-correction-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    TranslateModule,
    CorrectionPreviewComponent
  ],
  templateUrl: './correction-dialog.component.html',
  styleUrls: ['./correction-dialog.component.scss']
})
export class CorrectionDialogComponent<T> implements OnDestroy {
  readonly maxReasonTextLength = MAX_REASON_TEXT_LENGTH;
  readonly form: FormGroup;

  preview: CorrectionPreview | null = null;
  previewToken: string | null = null;
  /** Vrai quand l'Aperçu affiché remplace un Aperçu périmé. */
  staleNotice = false;
  errorMessage = '';
  blockingRefunds: BlockingRefund[] = [];
  busy = false;

  private readonly subscription: Subscription;

  constructor(
    private fb: FormBuilder,
    private dialogRef: MatDialogRef<CorrectionDialogComponent<T>, CorrectionDialogResult<T>>,
    private translate: TranslateService,
    @Inject(MAT_DIALOG_DATA) public data: CorrectionDialogData<T>
  ) {
    this.form = this.fb.group({
      reasonType: [null, Validators.required],
      reasonText: ['', Validators.maxLength(MAX_REASON_TEXT_LENGTH)]
    });
    // Le jeton couvre le Motif : un autre Motif est une autre correction, à prévisualiser.
    this.subscription = this.form.valueChanges.subscribe(() => this.resetPreview());
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  get otherSelected(): boolean {
    return this.form.get('reasonType')!.value === 'OTHER';
  }

  /** Le Motif est complet : un type, et un texte pour « Autre ». */
  get reasonComplete(): boolean {
    return this.form.valid && (!this.otherSelected || this.reasonText().length > 0);
  }

  reasonKey(type: CorrectionReasonType): string {
    return `correction.reason.${type}`;
  }

  requestPreview(): void {
    if (!this.reasonComplete || this.busy) {
      this.form.markAllAsTouched();
      return;
    }
    this.begin();
    this.data.run('preview', this.reason()).subscribe({
      next: response => {
        this.busy = false;
        this.preview = response.preview;
        this.previewToken = response.previewToken;
      },
      error: (err: unknown) => this.fail(err)
    });
  }

  confirm(): void {
    if (!this.previewToken || this.busy) {
      return;
    }
    this.begin();
    this.data.run('confirm', this.reason(), this.previewToken).subscribe({
      next: response => {
        this.busy = false;
        this.dialogRef.close({ kind: 'confirmed', result: response.result as T });
      },
      error: (err: unknown) => {
        if (err instanceof CorrectionError && err.stale) {
          // Le nouvel Aperçu remplace l'ancien ; la confirmation reste à faire, sur lui.
          this.busy = false;
          this.preview = err.preview;
          this.previewToken = err.previewToken;
          this.staleNotice = true;
          return;
        }
        this.fail(err);
      }
    });
  }

  back(): void {
    this.dialogRef.close({ kind: 'back' });
  }

  cancel(): void {
    this.dialogRef.close();
  }

  private reason(): CorrectionReason {
    const text = this.reasonText();
    return { type: this.form.get('reasonType')!.value, text: text.length > 0 ? text : null };
  }

  private reasonText(): string {
    return (this.form.get('reasonText')!.value ?? '').trim();
  }

  private begin(): void {
    this.busy = true;
    this.errorMessage = '';
    this.blockingRefunds = [];
  }

  private fail(err: unknown): void {
    this.busy = false;
    this.preview = null;
    this.previewToken = null;
    this.staleNotice = false;
    if (err instanceof CorrectionError) {
      this.errorMessage = err.message;
      this.blockingRefunds = err.blockingRefunds;
    } else {
      this.errorMessage = this.translate.instant('correction.dialog.error');
    }
  }

  private resetPreview(): void {
    this.preview = null;
    this.previewToken = null;
    this.staleNotice = false;
    this.errorMessage = '';
    this.blockingRefunds = [];
  }
}
