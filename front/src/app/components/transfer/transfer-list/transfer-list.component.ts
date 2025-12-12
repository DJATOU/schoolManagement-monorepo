import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { GroupTransferService } from '../../../services/group-transfer.service';
import { GroupTransfer } from '../../../models/transfer/group-transfer';
import { TransferDialogComponent } from '../transfer-dialog/transfer-dialog.component';

@Component({
  selector: 'app-transfer-list',
  standalone: true,
  imports: [CommonModule, MatTableModule, MatButtonModule, MatSnackBarModule, MatDialogModule],
  template: `
    <h2>Transferts</h2>
    <table mat-table [dataSource]="transfers" class="mat-elevation-z8">
      <ng-container matColumnDef="student">
        <th mat-header-cell *matHeaderCellDef>Étudiant</th>
        <td mat-cell *matCellDef="let transfer">{{ transfer.studentName || transfer.studentId }}</td>
      </ng-container>
      <ng-container matColumnDef="from">
        <th mat-header-cell *matHeaderCellDef>De</th>
        <td mat-cell *matCellDef="let transfer">{{ transfer.fromGroupName || transfer.fromGroupId }}</td>
      </ng-container>
      <ng-container matColumnDef="to">
        <th mat-header-cell *matHeaderCellDef>Vers</th>
        <td mat-cell *matCellDef="let transfer">{{ transfer.toGroupName || transfer.toGroupId }}</td>
      </ng-container>
      <ng-container matColumnDef="status">
        <th mat-header-cell *matHeaderCellDef>Statut</th>
        <td mat-cell *matCellDef="let transfer">{{ transfer.status }}</td>
      </ng-container>
      <ng-container matColumnDef="actions">
        <th mat-header-cell *matHeaderCellDef>Actions</th>
        <td mat-cell *matCellDef="let transfer">
          <button mat-button (click)="openDialog(transfer)">Impacter</button>
          <button mat-button color="warn" (click)="cancel(transfer)">Annuler</button>
        </td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
      <tr mat-row *matRowDef="let row; columns: displayedColumns"></tr>
    </table>
  `,
  styles: [`table { width: 100%; }`]
})
export class TransferListComponent implements OnInit {
  transfers: GroupTransfer[] = [];
  displayedColumns = ['student', 'from', 'to', 'status', 'actions'];

  constructor(private transferService: GroupTransferService, private snackBar: MatSnackBar, private dialog: MatDialog) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.transferService.getStudentTransfers(0).subscribe({
      next: transfers => (this.transfers = transfers),
      error: () => this.snackBar.open('Impossible de charger les transferts', 'Fermer', { duration: 4000 })
    });
  }

  openDialog(transfer?: GroupTransfer): void {
    this.dialog
      .open(TransferDialogComponent, { data: { studentId: transfer?.studentId ?? 0, fromGroupId: transfer?.fromGroupId ?? 0 } })
      .afterClosed()
      .subscribe(result => {
        if (result) {
          this.load();
        }
      });
  }

  cancel(transfer: GroupTransfer): void {
    if (!transfer.id) return;
    this.transferService.cancelTransfer(transfer.id).subscribe({
      next: () => {
        this.snackBar.open('Transfert annulé', 'Fermer', { duration: 3000 });
        this.load();
      }
    });
  }
}
