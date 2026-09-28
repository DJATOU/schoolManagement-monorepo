import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { DiscountService } from '../../../services/discount.service';
import { Discount } from '../../../models/discount/student-discount';
import { DiscountDialogComponent } from '../discount-dialog/discount-dialog.component';
import { DiscountEditDialogComponent } from '../discount-edit-dialog/discount-edit-dialog.component';
import { ConfirmationDialogComponent } from '../../shared/confirmation-dialog/confirmation-dialog.component';
import { AdminOnlyDirective } from '../../../shared/admin-only.directive';

/**
 * Liste des réductions accordées aux étudiants (portée + cible + taux).
 *
 * <p>Le backend expose désormais le listing enrichi (noms de l'étudiant et de la cible),
 * la création, la mise à jour du taux et la suppression. Les commandes d'écriture sont
 * grisées pour un VIEWER via {@code appAdminOnly}.</p>
 */
@Component({
  selector: 'app-discount-list',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatDialogModule,
    TranslateModule,
    AdminOnlyDirective
  ],
  template: `
    <div class="page">
      <!-- En-tête -->
      <div class="page-header">
        <div class="header-text">
          <h1 class="page-title">
            <mat-icon class="title-icon">sell</mat-icon>
            {{ 'discount.title' | translate }}
          </h1>
          <p class="page-subtitle">{{ 'discount.subtitle' | translate }}</p>
        </div>
        <button mat-flat-button color="primary" class="add-btn" (click)="openCreateDialog()" appAdminOnly>
          <mat-icon>add</mat-icon>
          {{ 'discount.add' | translate }}
        </button>
      </div>

      <mat-card class="table-card">
        <!-- Compteur -->
        <div class="table-toolbar" *ngIf="!loading && discounts.length > 0">
          <span class="count-chip">
            {{ 'discount.count' | translate: { count: discounts.length } }}
          </span>
        </div>

        <!-- Chargement -->
        <div class="state-block" *ngIf="loading">
          <mat-spinner diameter="36"></mat-spinner>
        </div>

        <!-- Vide -->
        <div class="state-block empty" *ngIf="!loading && discounts.length === 0">
          <mat-icon class="empty-icon">sell</mat-icon>
          <p class="empty-title">{{ 'discount.empty' | translate }}</p>
          <p class="empty-hint">{{ 'discount.emptyHint' | translate }}</p>
        </div>

        <!-- Tableau -->
        <div class="table-wrapper" *ngIf="!loading && discounts.length > 0">
          <table mat-table [dataSource]="discounts" class="discount-table">
            <ng-container matColumnDef="student">
              <th mat-header-cell *matHeaderCellDef>{{ 'discount.columns.student' | translate }}</th>
              <td mat-cell *matCellDef="let d">
                <div class="student-cell">
                  <span class="avatar" [style.background]="avatarColor(d)">{{ initials(d) }}</span>
                  <span class="student-name">{{ studentLabel(d) }}</span>
                </div>
              </td>
            </ng-container>

            <ng-container matColumnDef="scope">
              <th mat-header-cell *matHeaderCellDef>{{ 'discount.columns.scope' | translate }}</th>
              <td mat-cell *matCellDef="let d">
                <span class="scope-chip" [ngClass]="'scope-' + (d.scope || '').toLowerCase()">
                  <mat-icon>{{ scopeIcon(d.scope) }}</mat-icon>
                  {{ 'discount.scopes.' + d.scope | translate }}
                </span>
              </td>
            </ng-container>

            <ng-container matColumnDef="target">
              <th mat-header-cell *matHeaderCellDef>{{ 'discount.columns.target' | translate }}</th>
              <td mat-cell *matCellDef="let d">
                <span class="target-name">{{ d.targetName || '—' }}</span>
              </td>
            </ng-container>

            <ng-container matColumnDef="rate">
              <th mat-header-cell *matHeaderCellDef>{{ 'discount.columns.rate' | translate }}</th>
              <td mat-cell *matCellDef="let d">
                <div class="rate-cell">
                  <span class="rate-badge" [class.full]="d.rate >= 1" [class.none]="d.rate <= 0">
                    {{ (d.rate * 100) | number:'1.0-2' }}%
                  </span>
                  <span class="rate-bar" aria-hidden="true">
                    <span class="rate-fill" [class.full]="d.rate >= 1"
                          [style.width.%]="ratePercent(d)"></span>
                  </span>
                  <span class="exemption-tag" *ngIf="d.rate >= 1">
                    {{ 'discount.exemption' | translate }}
                  </span>
                </div>
              </td>
            </ng-container>

            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef class="actions-header">
                {{ 'discount.columns.actions' | translate }}
              </th>
              <td mat-cell *matCellDef="let d" class="actions-cell">
                <button mat-icon-button class="act act-edit" (click)="openEditDialog(d)"
                        [matTooltip]="'discount.actions.edit' | translate" appAdminOnly>
                  <mat-icon>edit</mat-icon>
                </button>
                <button mat-icon-button class="act act-delete" (click)="confirmDelete(d)"
                        [matTooltip]="'discount.actions.delete' | translate" appAdminOnly>
                  <mat-icon>delete</mat-icon>
                </button>
              </td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: displayedColumns" class="data-row"></tr>
          </table>
        </div>
      </mat-card>
    </div>
  `,
  styles: [`
    :host {
      --dc-primary: #4f46e5;
      --dc-ink: #111827;
      --dc-muted: #6b7280;
      --dc-line: #eef0f4;
      display: block;
    }

    .page {
      padding: 24px 28px 32px;
      max-width: 1180px;
      margin: 0 auto;
    }

    /* ===== En-tête ===== */
    .page-header {
      display: flex;
      align-items: flex-end;
      justify-content: space-between;
      gap: 18px;
      margin-bottom: 18px;
      flex-wrap: wrap;
    }
    .page-title {
      display: flex;
      align-items: center;
      gap: 10px;
      margin: 0;
      font-size: 24px;
      font-weight: 700;
      color: var(--dc-ink);
    }
    .title-icon {
      font-size: 26px;
      width: 26px;
      height: 26px;
      color: var(--dc-primary);
    }
    .page-subtitle {
      margin: 4px 0 0 36px;
      font-size: 13px;
      color: var(--dc-muted);
    }
    .add-btn {
      display: inline-flex;
      align-items: center;
      gap: 7px;
      height: 42px;
      border-radius: 10px;
      font-weight: 600;
    }
    .add-btn mat-icon {
      font-size: 19px;
      width: 19px;
      height: 19px;
    }

    /* ===== Carte ===== */
    .table-card {
      padding: 0 !important;
      border-radius: 14px;
      overflow: hidden;
      box-shadow: 0 1px 3px rgba(16, 24, 40, 0.08), 0 8px 24px rgba(16, 24, 40, 0.06);
    }
    .table-toolbar {
      display: flex;
      align-items: center;
      padding: 14px 18px;
      border-bottom: 1px solid var(--dc-line);
      background: #fbfcfe;
    }
    .count-chip {
      padding: 4px 11px;
      font-size: 12px;
      font-weight: 600;
      color: #3730a3;
      background: #eef2ff;
      border-radius: 999px;
    }

    /* ===== États ===== */
    .state-block {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 6px;
      padding: 56px 20px;
    }
    .empty-icon {
      font-size: 44px;
      width: 44px;
      height: 44px;
      color: #cbd5e1;
      margin-bottom: 6px;
    }
    .empty-title {
      margin: 0;
      font-size: 15px;
      font-weight: 600;
      color: #374151;
    }
    .empty-hint {
      margin: 0;
      font-size: 13px;
      color: var(--dc-muted);
    }

    /* ===== Tableau ===== */
    .table-wrapper { overflow-x: auto; }
    .discount-table { width: 100%; }
    .discount-table ::ng-deep th.mat-mdc-header-cell {
      font-size: 11.5px;
      font-weight: 700;
      letter-spacing: 0.4px;
      text-transform: uppercase;
      color: var(--dc-muted);
      background: #fbfcfe;
      border-bottom: 1px solid var(--dc-line);
      padding: 12px 16px;
    }
    .discount-table ::ng-deep td.mat-mdc-cell {
      padding: 12px 16px;
      border-bottom: 1px solid var(--dc-line);
      font-size: 13.5px;
      color: var(--dc-ink);
    }
    .data-row:hover ::ng-deep td.mat-mdc-cell { background: #fafbff; }

    /* Étudiant */
    .student-cell {
      display: flex;
      align-items: center;
      gap: 10px;
      min-width: 0;
    }
    .avatar {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 30px;
      height: 30px;
      flex: 0 0 30px;
      border-radius: 50%;
      font-size: 11.5px;
      font-weight: 700;
      color: #fff;
      letter-spacing: 0.3px;
    }
    .student-name {
      font-weight: 600;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    /* Portée */
    .scope-chip {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      padding: 4px 10px;
      font-size: 12px;
      font-weight: 600;
      border-radius: 999px;
      white-space: nowrap;
    }
    .scope-chip mat-icon {
      font-size: 15px;
      width: 15px;
      height: 15px;
    }
    .scope-group { color: #1d4ed8; background: #eff6ff; }
    .scope-series { color: #7e22ce; background: #faf5ff; }
    .scope-session { color: #0f766e; background: #f0fdfa; }

    .target-name { color: #374151; }

    /* Taux */
    .rate-cell {
      display: flex;
      align-items: center;
      gap: 10px;
      min-width: 190px;
    }
    .rate-badge {
      flex: 0 0 auto;
      min-width: 52px;
      padding: 3px 9px;
      font-size: 12.5px;
      font-weight: 700;
      text-align: center;
      color: #3730a3;
      background: #eef2ff;
      border-radius: 7px;

      &.full { color: #92400e; background: #fffbeb; }
      &.none { color: var(--dc-muted); background: #f3f4f6; }
    }
    .rate-bar {
      flex: 1 1 auto;
      height: 6px;
      max-width: 84px;
      background: #eef0f4;
      border-radius: 999px;
      overflow: hidden;
    }
    .rate-fill {
      display: block;
      height: 100%;
      background: linear-gradient(90deg, #6366f1, #8b5cf6);
      border-radius: 999px;
      transition: width 0.25s ease;

      &.full { background: linear-gradient(90deg, #f59e0b, #d97706); }
    }
    .exemption-tag {
      flex: 0 0 auto;
      padding: 2px 8px;
      font-size: 10.5px;
      font-weight: 700;
      letter-spacing: 0.3px;
      text-transform: uppercase;
      color: #92400e;
      background: #fef3c7;
      border-radius: 999px;
    }

    /* Actions */
    .actions-header { text-align: right; }
    .actions-cell {
      text-align: right;
      white-space: nowrap;
    }
    .act {
      width: 34px;
      height: 34px;
      line-height: 34px;
    }
    .act mat-icon {
      font-size: 18px;
      width: 18px;
      height: 18px;
    }
    .act-edit { color: var(--dc-primary); }
    .act-delete { color: #dc2626; }

    @media (max-width: 640px) {
      .page { padding: 16px 12px 24px; }
      .page-subtitle { margin-left: 0; }
      .add-btn { width: 100%; justify-content: center; }
    }
  `]
})
export class DiscountListComponent implements OnInit {
  discounts: Discount[] = [];
  loading = true;
  displayedColumns = ['student', 'scope', 'target', 'rate', 'actions'];

  /** Palette des pastilles d'initiales (choix déterministe par étudiant). */
  private readonly avatarColors = [
    '#6366f1', '#8b5cf6', '#ec4899', '#ef4444', '#f97316',
    '#eab308', '#22c55e', '#14b8a6', '#06b6d4', '#3b82f6'
  ];

  constructor(
    private discountService: DiscountService,
    private snackBar: MatSnackBar,
    private dialog: MatDialog,
    private translate: TranslateService
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.discountService.getAllDiscounts().subscribe({
      next: discounts => {
        this.discounts = discounts;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.notify(this.translate.instant('discount.loadError'));
      }
    });
  }

  openCreateDialog(): void {
    this.dialog
      .open(DiscountDialogComponent, { data: {} })
      .afterClosed()
      .subscribe(result => {
        if (result) {
          this.load();
        }
      });
  }

  openEditDialog(discount: Discount): void {
    this.dialog
      .open(DiscountEditDialogComponent, {
        width: '480px',
        maxWidth: '95vw',
        autoFocus: false,
        data: discount
      })
      .afterClosed()
      .subscribe(saved => {
        if (saved) {
          this.load();
          this.notify(this.translate.instant('discount.edit.success'));
        }
      });
  }

  confirmDelete(discount: Discount): void {
    this.dialog
      .open(ConfirmationDialogComponent, {
        data: {
          title: this.translate.instant('discount.delete.title'),
          message: this.translate.instant('discount.delete.message'),
          confirmText: this.translate.instant('discount.delete.confirm'),
          cancelText: this.translate.instant('common.cancel'),
          confirmColor: 'warn'
        }
      })
      .afterClosed()
      .subscribe((confirmed: boolean) => {
        if (confirmed && discount.id !== undefined) {
          this.deleteDiscount(discount.id);
        }
      });
  }

  private deleteDiscount(id: number): void {
    this.discountService.deleteDiscount(id).subscribe({
      next: () => {
        this.load();
        this.notify(this.translate.instant('discount.delete.success'));
      },
      error: (error: Error) =>
        this.notify(error?.message || this.translate.instant('discount.delete.error'))
    });
  }

  /** Libellé de l'étudiant : son nom si résolu, sinon un repli sur l'identifiant. */
  studentLabel(discount: Discount): string {
    return discount.studentName
      || this.translate.instant('discount.unknownStudent', { id: discount.studentId });
  }

  /** Initiales affichées dans la pastille (deux lettres au plus). */
  initials(discount: Discount): string {
    const name = discount.studentName?.trim();
    if (!name) {
      return '#';
    }
    const words = name.split(/\s+/);
    if (words.length >= 2) {
      return (words[0][0] + words[1][0]).toUpperCase();
    }
    return name.substring(0, 2).toUpperCase();
  }

  /** Couleur de pastille stable, dérivée du nom (ou de l'identifiant à défaut). */
  avatarColor(discount: Discount): string {
    const seed = discount.studentName || String(discount.studentId ?? 0);
    const hash = seed.split('').reduce((acc, char) => acc + char.charCodeAt(0), 0);
    return this.avatarColors[hash % this.avatarColors.length];
  }

  /** Icône illustrant la portée de la réduction. */
  scopeIcon(scope: string): string {
    switch (scope) {
      case 'GROUP': return 'groups';
      case 'SERIES': return 'calendar_month';
      case 'SESSION': return 'event_note';
      default: return 'help_outline';
    }
  }

  /** Largeur de la jauge de taux, bornée à [0, 100]. */
  ratePercent(discount: Discount): number {
    return Math.max(0, Math.min(100, (discount.rate ?? 0) * 100));
  }

  private notify(message: string): void {
    this.snackBar.open(message, this.translate.instant('common.close'), { duration: 4000 });
  }
}
