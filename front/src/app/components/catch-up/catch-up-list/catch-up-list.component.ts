import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { CatchUpService } from '../../../services/catch-up.service';
import { CatchUpRequest } from '../../../models/catchUp/catch-up-request';
import { CatchUpDialogComponent } from '../catch-up-dialog/catch-up-dialog.component';
import { CatchUpCreateDialogComponent } from '../catch-up-create-dialog/catch-up-create-dialog.component';
import { AdminOnlyDirective } from '../../../shared/admin-only.directive';

@Component({
  selector: 'app-catch-up-list',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatButtonModule,
    MatSelectModule,
    MatSnackBarModule,
    MatDialogModule,
    AdminOnlyDirective,
  ],
  template: `
    <div class="header">
      <h2>Demandes de rattrapage</h2>
      <button mat-raised-button color="primary" (click)="openCreateDialog()" appAdminOnly>
        Nouvelle demande
      </button>
    </div>
    <div class="filters">
      <mat-select placeholder="Filtrer par statut" [(value)]="statusFilter" (valueChange)="applyFilter()">
        <mat-option value="ALL">Tous</mat-option>
        <mat-option value="PENDING">En attente</mat-option>
        <mat-option value="SCHEDULED">Planifié</mat-option>
        <mat-option value="COMPLETED">Complété</mat-option>
        <mat-option value="CANCELLED">Annulé</mat-option>
      </mat-select>
    </div>
    <table mat-table [dataSource]="filteredRequests" class="mat-elevation-z8">
      <ng-container matColumnDef="student">
        <th mat-header-cell *matHeaderCellDef>Étudiant</th>
        <td mat-cell *matCellDef="let req">{{ req.studentName || req.studentId }}</td>
      </ng-container>
      <ng-container matColumnDef="originalSession">
        <th mat-header-cell *matHeaderCellDef>Session manquée</th>
        <td mat-cell *matCellDef="let req">{{ req.originalSessionName || req.originalSessionId }}</td>
      </ng-container>
      <ng-container matColumnDef="status">
        <th mat-header-cell *matHeaderCellDef>Statut</th>
        <td mat-cell *matCellDef="let req">{{ req.status }}</td>
      </ng-container>
      <ng-container matColumnDef="actions">
        <th mat-header-cell *matHeaderCellDef>Actions</th>
        <td mat-cell *matCellDef="let req">
          <button mat-button color="primary" (click)="openScheduleDialog(req)" [disabled]="req.status !== 'PENDING'" appAdminOnly>Planifier</button>
          <button mat-button color="accent" (click)="complete(req)" [disabled]="req.status !== 'SCHEDULED'" appAdminOnly>Compléter</button>
          <button mat-button color="warn" (click)="cancel(req)" appAdminOnly>Annuler</button>
        </td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
      <tr mat-row *matRowDef="let row; columns: displayedColumns"></tr>
    </table>
  `,
  styles: [`
    .header { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
    .filters { margin: 12px 0; }
    table { width: 100%; }
  `]
})
export class CatchUpListComponent implements OnInit {
  requests: CatchUpRequest[] = [];
  filteredRequests: CatchUpRequest[] = [];
  displayedColumns = ['student', 'originalSession', 'status', 'actions'];
  statusFilter: 'ALL' | CatchUpRequest['status'] = 'ALL';

  constructor(
    private catchUpService: CatchUpService,
    private snackBar: MatSnackBar,
    private dialog: MatDialog
  ) {}

  ngOnInit(): void {
    this.loadRequests();
  }

  applyFilter(): void {
    if (this.statusFilter === 'ALL') {
      this.filteredRequests = this.requests;
      return;
    }
    this.filteredRequests = this.requests.filter(req => req.status === this.statusFilter);
  }

  private loadRequests(): void {
    this.catchUpService.getAllRequests().subscribe({
      next: requests => {
        this.requests = requests;
        this.applyFilter();
      },
      error: () => this.snackBar.open('Impossible de charger les rattrapages', 'Fermer', { duration: 4000 })
    });
  }

  openCreateDialog(): void {
    this.dialog.open(CatchUpCreateDialogComponent, { width: '480px' })
      .afterClosed()
      .subscribe(created => {
        if (created) {
          this.loadRequests();
        }
      });
  }

  openScheduleDialog(request: CatchUpRequest): void {
    this.dialog.open(CatchUpDialogComponent, {
      data: {
        requestId: request.id,
        studentId: request.studentId,
        originalSessionId: request.originalSessionId,
        originalSessionName: request.originalSessionName,
        originalGroupId: request.originalGroupId
      }
    }).afterClosed().subscribe(() => this.loadRequests());
  }

  complete(request: CatchUpRequest): void {
    this.catchUpService.completeCatchUp(request.id!).subscribe({
      next: () => {
        this.snackBar.open('Rattrapage complété', 'Fermer', { duration: 3000 });
        this.loadRequests();
      }
    });
  }

  cancel(request: CatchUpRequest): void {
    this.catchUpService.cancelCatchUp(request.id!).subscribe({
      next: () => {
        this.snackBar.open('Rattrapage annulé', 'Fermer', { duration: 3000 });
        this.loadRequests();
      }
    });
  }
}
