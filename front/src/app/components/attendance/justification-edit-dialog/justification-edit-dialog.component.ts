import { CommonModule } from '@angular/common';
import { Component, Inject, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatRadioModule } from '@angular/material/radio';
import { JustificationAudit, JustificationUpdateResult } from '../../../models/Attendance/justification';
import { AttendanceService } from '../../../services/attendance.service';

/** Données d'ouverture du dialogue de modification de justification. */
export interface JustificationEditDialogData {
  attendanceId: number;
  /** Valeur courante ; `null` si la justification n'a jamais été renseignée. */
  justified: boolean | null;
  /** Nom de la séance concernée, affiché en récapitulatif. */
  sessionName?: string;
  /** Date de la séance, affichée en récapitulatif. */
  sessionDate?: string | Date | null;
}

/**
 * Modification de la justification d'une absence, avec sa piste d'audit.
 *
 * <h2>Ce que ce dialogue dit explicitement, et pourquoi</h2>
 * Il affiche que la modification <strong>ne change aucun montant</strong>. Ce n'est pas une
 * précaution rhétorique : l'ambiguïté a réellement produit l'attente inverse — « si c'est justifié,
 * la séance n'est pas facturée ». Un administrateur qui croit exonérer une famille en cochant
 * « justifiée » lui fait une promesse que le système ne tient pas. Le dire ici, au moment du geste,
 * est le seul endroit où l'information arrive à temps.
 *
 * <h2>La piste d'audit est chargée à l'ouverture</h2>
 * Elle répond à la question qui motive ce dialogue : qui a modifié quoi, et pourquoi. La consulter
 * après coup, depuis un autre écran, obligerait à naviguer au moment précis où l'on cherche à
 * comprendre une contestation.
 */
@Component({
  selector: 'app-justification-edit-dialog',
  standalone: true,
  templateUrl: './justification-edit-dialog.component.html',
  styleUrls: ['./justification-edit-dialog.component.scss'],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatRadioModule,
    MatProgressSpinnerModule
  ]
})
export class JustificationEditDialogComponent implements OnInit {

  /** Longueur maximale du commentaire, alignée sur la contrainte du serveur. */
  static readonly MAX_COMMENT_LENGTH = 500;

  form: FormGroup;

  /** Piste d'audit, de la plus récente à la plus ancienne. */
  auditTrail: JustificationAudit[] = [];

  loadingAudit = true;
  submitting = false;
  errorMessage?: string;

  /** Vrai lorsque la requête n'a pas abouti faute de réponse : le résultat est inconnu. */
  outcomeUnknown = false;

  constructor(
    private fb: FormBuilder,
    private attendanceService: AttendanceService,
    public dialogRef: MatDialogRef<JustificationEditDialogComponent, JustificationUpdateResult | undefined>,
    @Inject(MAT_DIALOG_DATA) public data: JustificationEditDialogData
  ) {
    this.form = this.fb.group({
      // La valeur courante est présélectionnée : le dialogue sert le plus souvent à corriger, donc à
      // partir de ce qui est enregistré. Une justification jamais renseignée démarre sur « non ».
      justified: [this.data.justified ?? false, Validators.required],
      comment: ['', Validators.maxLength(JustificationEditDialogComponent.MAX_COMMENT_LENGTH)]
    });
  }

  ngOnInit(): void {
    this.loadAuditTrail();
  }

  /** Vrai si la valeur choisie diffère de la valeur enregistrée. */
  get hasChange(): boolean {
    return this.form.get('justified')?.value !== this.data.justified;
  }

  get canSubmit(): boolean {
    return this.form.valid && !this.submitting;
  }

  /** Enregistre la modification. */
  submit(): void {
    if (!this.canSubmit) {
      return;
    }
    this.submitting = true;
    this.errorMessage = undefined;
    this.outcomeUnknown = false;

    const justified = Boolean(this.form.get('justified')?.value);
    const comment = String(this.form.get('comment')?.value ?? '');

    this.attendanceService.updateJustification(this.data.attendanceId, justified, comment).subscribe({
      next: (result) => {
        this.submitting = false;
        this.dialogRef.close(result);
      },
      error: (err: Error) => {
        this.submitting = false;
        this.errorMessage = err.message;
        // Serveur injoignable : la modification a peut-être abouti. Le dire, plutôt que d'annoncer un
        // échec qui pousserait à recommencer sans vérifier.
        this.outcomeUnknown = err.message.includes('inconnu');
        // La piste est rechargée : elle dira si la modification a finalement été enregistrée.
        this.loadAuditTrail();
      }
    });
  }

  /** Libellé d'une valeur de justification. */
  valueLabel(value: boolean | null): string {
    if (value === null || value === undefined) {
      return 'Non renseignée';
    }
    return value ? 'Justifiée' : 'Non justifiée';
  }

  private loadAuditTrail(): void {
    this.loadingAudit = true;
    this.attendanceService.getJustificationAudit(this.data.attendanceId).subscribe({
      next: (trail) => {
        this.auditTrail = trail;
        this.loadingAudit = false;
      },
      error: () => {
        // Un historique indisponible ne doit pas empêcher la correction : c'est un complément
        // d'information, pas une condition.
        this.auditTrail = [];
        this.loadingAudit = false;
      }
    });
  }
}
