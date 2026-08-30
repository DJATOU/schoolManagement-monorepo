import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { AdminOnlyDirective } from '../../../shared/admin-only.directive';
import { CatchUpBillingService } from '../../../services/catch-up-billing.service';
import { PendingCatchUp } from '../../../models/catchUp/catch-up-billing';
import { CatchUpBillingDialogComponent } from '../catch-up-billing-dialog/catch-up-billing-dialog.component';

/**
 * Rattrapages en attente de décision de facturation.
 *
 * <p>Cette liste existe <strong>pour être vidée</strong> : chaque ligne est une séance consommée
 * que personne ne facture encore. La laisser invisible reproduirait le défaut d'origine sous une
 * autre forme — non plus une facturation silencieuse, mais un oubli silencieux.</p>
 *
 * <p>Le pointage n'est pas bloqué par ces lignes : une séance se valide normalement, et la décision
 * se prend ensuite, ici ou depuis la feuille de présence.</p>
 */
@Component({
  selector: 'app-catch-up-billing-list',
  standalone: true,
  templateUrl: './catch-up-billing-list.component.html',
  styleUrls: ['./catch-up-billing-list.component.scss'],
  imports: [
    CommonModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTooltipModule,
    TranslateModule,
    AdminOnlyDirective
  ]
})
export class CatchUpBillingListComponent implements OnInit {

  pending: PendingCatchUp[] = [];
  loading = true;
  errorMessage = '';

  readonly columns = ['student', 'session', 'hostGroup', 'levelSubject', 'actions'];

  constructor(
    private catchUpBillingService: CatchUpBillingService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
    private translate: TranslateService
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.errorMessage = '';
    this.catchUpBillingService.getPending().subscribe({
      next: pending => {
        this.pending = pending;
        this.loading = false;
      },
      error: (error: Error) => {
        this.errorMessage = error.message;
        this.loading = false;
      }
    });
  }

  /**
   * Ouvre la résolution d'un rattrapage.
   *
   * <p>La liste est rechargée après une résolution réussie : la ligne traitée doit disparaître,
   * sinon rien ne distingue « décidé » de « en attente » à l'écran.</p>
   */
  resolve(pending: PendingCatchUp): void {
    this.dialog.open(CatchUpBillingDialogComponent, {
      width: '560px',
      maxWidth: '95vw',
      maxHeight: '90vh',
      autoFocus: false,
      data: { pending, mode: 'resolve' }
    }).afterClosed().subscribe((resolved: PendingCatchUp | null) => {
      if (resolved) {
        this.snackBar.open(
          this.translate.instant('CATCH_UP_BILLING.MESSAGES.RESOLVED'),
          this.translate.instant('common.ok'),
          { duration: 3000, panelClass: ['snack-bar-success'] });
        this.load();
      }
    });
  }
}
