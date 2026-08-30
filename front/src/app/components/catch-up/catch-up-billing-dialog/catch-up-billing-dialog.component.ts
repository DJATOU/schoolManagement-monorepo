import { CommonModule } from '@angular/common';
import { Component, Inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatRadioModule } from '@angular/material/radio';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';

import { CatchUpBillingService } from '../../../services/catch-up-billing.service';
import { StudentAbsence } from '../../../models/catchUp/student-absence';
import { CatchUpBillingAudit, PendingCatchUp } from '../../../models/catchUp/catch-up-billing';

/**
 * Résolution (ou correction) de la facturation d'un rattrapage.
 *
 * <h2>Deux décisions, aucune présélection</h2>
 * L'administrateur doit désigner la <strong>séance manquée</strong> et trancher
 * <strong>« déjà payée ? »</strong>. Le second choix n'a volontairement aucune valeur par défaut :
 * un défaut juste dans la plupart des cas n'est jamais relu, et le jour où il est faux l'erreur
 * passe inaperçue — c'est-à-dire exactement le scénario de double facturation que cet écran existe
 * pour empêcher. Le bouton d'enregistrement reste donc inactif tant que les deux décisions
 * manquent.
 *
 * <h2>L'impayé informe, il ne bloque pas</h2>
 * L'état de paiement de la série d'origine est affiché à côté du choix. Il ne conditionne rien :
 * refuser un rattrapage au motif d'un impayé acculait l'administrateur, à qui l'école venait
 * peut-être d'accorder ce rattrapage.
 */
@Component({
  selector: 'app-catch-up-billing-dialog',
  standalone: true,
  templateUrl: './catch-up-billing-dialog.component.html',
  styleUrls: ['./catch-up-billing-dialog.component.scss'],
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatRadioModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    TranslateModule
  ]
})
export class CatchUpBillingDialogComponent implements OnInit {

  /** Séances manquées proposables, restreintes par le serveur au niveau et à la matière. */
  absences: StudentAbsence[] = [];

  /**
   * Séance manquée choisie. `null` tant qu'aucune sélection : le formulaire ne présume rien, même
   * lorsqu'une seule absence est proposée.
   */
  selectedMissedSessionId: number | null = null;

  /**
   * Décision « déjà payée ». `null` signifie **non tranchée**, et c'est un état légitime tant que
   * l'administrateur n'a pas répondu. Un `false` par défaut serait une décision prise à sa place.
   */
  alreadyPaid: boolean | null = null;

  comment = '';

  loading = true;
  saving = false;
  errorMessage = '';

  /** Piste d'audit, chargée uniquement en correction : en résolution il n'y a rien à relire. */
  auditTrail: CatchUpBillingAudit[] = [];

  constructor(
    private catchUpBillingService: CatchUpBillingService,
    public dialogRef: MatDialogRef<CatchUpBillingDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { pending: PendingCatchUp; mode: 'resolve' | 'correct' }
  ) {}

  ngOnInit(): void {
    this.loadAbsences();
    if (this.isCorrection) {
      this.loadAuditTrail();
    }
  }

  /** Correction d'une décision déjà prise, par opposition à une première résolution. */
  get isCorrection(): boolean {
    return this.data.mode === 'correct';
  }

  /**
   * Les deux décisions sont-elles prises ?
   *
   * <p>En correction, une seule suffit : on peut ne changer que la séance manquée, ou que la
   * décision de facturation. Réexiger les deux ferait ressaisir une valeur inchangée.</p>
   */
  get canSave(): boolean {
    if (this.saving) {
      return false;
    }
    if (this.isCorrection) {
      return this.selectedMissedSessionId !== null || this.alreadyPaid !== null;
    }
    return this.selectedMissedSessionId !== null && this.alreadyPaid !== null;
  }

  private loadAbsences(): void {
    const studentId = this.data.pending.studentId;
    const hostGroupId = this.data.pending.hostGroupId;

    if (studentId === null || hostGroupId === null) {
      // Sans étudiant ni groupe d'accueil, aucune séance manquée ne peut être proposée. Le dire
      // plutôt que d'afficher une liste vide, qui ressemblerait à « aucune absence ».
      this.loading = false;
      this.errorMessage = 'CATCH_UP_BILLING.ERRORS.MISSING_CONTEXT';
      return;
    }

    this.catchUpBillingService.getEligibleMissedSessions(studentId, hostGroupId).subscribe({
      next: absences => {
        this.absences = absences;
        this.loading = false;
      },
      error: (error: Error) => {
        this.errorMessage = error.message;
        this.loading = false;
      }
    });
  }

  private loadAuditTrail(): void {
    this.catchUpBillingService.getAuditTrail(this.data.pending.attendanceId).subscribe({
      next: trail => (this.auditTrail = trail),
      // L'absence de piste d'audit ne doit pas empêcher la correction elle-même.
      error: (error: Error) => console.error('Piste d\'audit indisponible :', error.message)
    });
  }

  save(): void {
    if (!this.canSave) {
      return;
    }

    this.saving = true;
    this.errorMessage = '';

    const attendanceId = this.data.pending.attendanceId;
    const request$ = this.isCorrection
      ? this.catchUpBillingService.correct(attendanceId, {
          missedSessionId: this.selectedMissedSessionId ?? undefined,
          alreadyPaid: this.alreadyPaid ?? undefined,
          comment: this.comment || undefined
        })
      : this.catchUpBillingService.resolve(attendanceId, {
          // `canSave` garantit que les deux décisions sont prises en mode résolution.
          missedSessionId: this.selectedMissedSessionId as number,
          alreadyPaid: this.alreadyPaid as boolean,
          comment: this.comment || undefined
        });

    request$.subscribe({
      next: resolved => this.dialogRef.close(resolved),
      error: (error: Error) => {
        this.errorMessage = error.message;
        this.saving = false;
      }
    });
  }

  cancel(): void {
    this.dialogRef.close(null);
  }
}
