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
  ],
  template: `
    <h2>Demandes de rattrapage</h2>
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
          <button mat-button color="primary" (click)="openScheduleDialog(req)" [disabled]="req.status !== 'PENDING'">Planifier</button>
          <button mat-button color="accent" (click)="complete(req)" [disabled]="req.status !== 'SCHEDULED'">Compléter</button>
          <button mat-button color="warn" (click)="cancel(req)">Annuler</button>
        </td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
      <tr mat-row *matRowDef="let row; columns: displayedColumns"></tr>
    </table>
  `,
  styles: [`.filters { margin: 12px 0; } table { width: 100%; }`]
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
    this.loadPending();
  }

  applyFilter(): void {
    if (this.statusFilter === 'ALL') {
      this.filteredRequests = this.requests;
      return;
    }
    this.filteredRequests = this.requests.filter(req => req.status === this.statusFilter);
  }

  private loadPending(): void {
    this.catchUpService.getPendingRequests().subscribe({
      next: requests => {
        this.requests = requests;
        this.applyFilter();
      },
      error: () => this.snackBar.open('Impossible de charger les rattrapages', 'Fermer', { duration: 4000 })
    });
  }

  openScheduleDialog(request: CatchUpRequest): void {
    this.dialog.open(CatchUpDialogComponent, {
      data: { studentId: request.studentId, attendance: {
        id: request.originalAttendanceId,
        studentId: request.studentId,
        sessionId: request.originalSessionId,
        groupId: request.originalGroupId,
        isPresent: false,
        isJustified: true,
        isCatchUp: false,
        paymentStatus: 'PENDING',
        dateCreation: new Date(),
        dateUpdate: new Date(),
        createdBy: '',
        updatedBy: '',
        active: true
      }}
    }).afterClosed().subscribe(() => this.loadPending());
  }

  complete(request: CatchUpRequest): void {
    this.catchUpService.completeCatchUp(request.id!).subscribe({
      next: () => {
        this.snackBar.open('Rattrapage complété', 'Fermer', { duration: 3000 });
        this.loadPending();
      }
    });
  }

  cancel(request: CatchUpRequest): void {
    this.catchUpService.cancelCatchUp(request.id!).subscribe({
      next: () => {
        this.snackBar.open('Rattrapage annulé', 'Fermer', { duration: 3000 });
        this.loadPending();
      }
    });
  }
}
