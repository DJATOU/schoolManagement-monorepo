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
import { TranslateModule, TranslateService } from '@ngx-translate/core';

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
    TranslateModule,
  ],
  template: `
    <div class="header">
      <h2>{{ 'CATCH_UP.LIST.TITLE' | translate }}</h2>
      <button mat-raised-button color="primary" (click)="openCreateDialog()" appAdminOnly>
        {{ 'CATCH_UP.LIST.NEW' | translate }}
      </button>
    </div>
    <div class="filters">
      <mat-select [placeholder]="'CATCH_UP.LIST.STATUS_FILTER' | translate" [(value)]="statusFilter" (valueChange)="applyFilter()">
        <mat-option value="ALL">{{ 'CATCH_UP.LIST.ALL' | translate }}</mat-option>
        <mat-option value="PENDING">{{ 'CATCH_UP.LIST.STATUS.PENDING' | translate }}</mat-option>
        <mat-option value="SCHEDULED">{{ 'CATCH_UP.LIST.STATUS.SCHEDULED' | translate }}</mat-option>
        <mat-option value="COMPLETED">{{ 'CATCH_UP.LIST.STATUS.COMPLETED' | translate }}</mat-option>
        <mat-option value="CANCELLED">{{ 'CATCH_UP.LIST.STATUS.CANCELLED' | translate }}</mat-option>
      </mat-select>
    </div>
    <table mat-table [dataSource]="filteredRequests" class="mat-elevation-z8">
      <ng-container matColumnDef="student">
        <th mat-header-cell *matHeaderCellDef>{{ 'CATCH_UP.LIST.COLUMNS.STUDENT' | translate }}</th>
        <td mat-cell *matCellDef="let req">{{ req.studentName || req.studentId }}</td>
      </ng-container>
      <ng-container matColumnDef="originalSession">
        <th mat-header-cell *matHeaderCellDef>{{ 'CATCH_UP.LIST.COLUMNS.MISSED_SESSION' | translate }}</th>
        <td mat-cell *matCellDef="let req">{{ req.originalSessionName || req.originalSessionId }}</td>
      </ng-container>
      <ng-container matColumnDef="status">
        <th mat-header-cell *matHeaderCellDef>{{ 'CATCH_UP.LIST.COLUMNS.STATUS' | translate }}</th>
        <td mat-cell *matCellDef="let req">{{ req.status }}</td>
      </ng-container>
      <ng-container matColumnDef="actions">
        <th mat-header-cell *matHeaderCellDef>{{ 'CATCH_UP.LIST.COLUMNS.ACTIONS' | translate }}</th>
        <td mat-cell *matCellDef="let req">
          <button mat-button color="primary" (click)="openScheduleDialog(req)" [disabled]="req.status !== 'PENDING'" appAdminOnly>{{ 'CATCH_UP.LIST.SCHEDULE' | translate }}</button>
          <button mat-button color="accent" (click)="complete(req)" [disabled]="req.status !== 'SCHEDULED'" appAdminOnly>{{ 'CATCH_UP.LIST.COMPLETE' | translate }}</button>
          <button mat-button color="warn" (click)="cancel(req)" appAdminOnly>{{ 'common.cancel' | translate }}</button>
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
    private dialog: MatDialog,
    private translate: TranslateService
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
      error: () => this.snackBar.open(this.translate.instant('CATCH_UP.LIST.LOAD_ERROR'), this.translate.instant('common.close'), { duration: 4000 })
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
        this.snackBar.open(this.translate.instant('CATCH_UP.LIST.COMPLETED'), this.translate.instant('common.close'), { duration: 3000 });
        this.loadRequests();
      }
    });
  }

  cancel(request: CatchUpRequest): void {
    this.catchUpService.cancelCatchUp(request.id!).subscribe({
      next: () => {
        this.snackBar.open(this.translate.instant('CATCH_UP.LIST.CANCELLED'), this.translate.instant('common.close'), { duration: 3000 });
        this.loadRequests();
      }
    });
  }
}
