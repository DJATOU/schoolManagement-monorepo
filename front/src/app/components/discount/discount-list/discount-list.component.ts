import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { DiscountService } from '../../../services/discount.service';
import { StudentDiscount } from '../../../models/discount/student-discount';
import { DiscountDialogComponent } from '../discount-dialog/discount-dialog.component';

@Component({
  selector: 'app-discount-list',
  standalone: true,
  imports: [CommonModule, MatTableModule, MatButtonModule, MatSnackBarModule, MatDialogModule],
  template: `
    <h2>Réductions</h2>
    <button mat-raised-button color="primary" (click)="openDialog()">Ajouter une réduction</button>
    <table mat-table [dataSource]="discounts" class="mat-elevation-z8">
      <ng-container matColumnDef="student">
        <th mat-header-cell *matHeaderCellDef>Étudiant</th>
        <td mat-cell *matCellDef="let discount">{{ discount.studentName || discount.studentId }}</td>
      </ng-container>
      <ng-container matColumnDef="type">
        <th mat-header-cell *matHeaderCellDef>Type</th>
        <td mat-cell *matCellDef="let discount">{{ discount.discountType }}</td>
      </ng-container>
      <ng-container matColumnDef="value">
        <th mat-header-cell *matHeaderCellDef>Valeur</th>
        <td mat-cell *matCellDef="let discount">{{ discount.discountValue }}</td>
      </ng-container>
      <ng-container matColumnDef="actions">
        <th mat-header-cell *matHeaderCellDef>Actions</th>
        <td mat-cell *matCellDef="let discount">
          <button mat-button (click)="openDialog(discount)">Modifier</button>
          <button mat-button color="warn" (click)="deactivate(discount)">Désactiver</button>
        </td>
      </ng-container>
      <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
      <tr mat-row *matRowDef="let row; columns: displayedColumns"></tr>
    </table>
  `,
  styles: [`table { width: 100%; margin-top: 12px; }`]
})
export class DiscountListComponent implements OnInit {
  discounts: StudentDiscount[] = [];
  displayedColumns = ['student', 'type', 'value', 'actions'];

  constructor(private discountService: DiscountService, private snackBar: MatSnackBar, private dialog: MatDialog) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.discountService.getAllDiscounts().subscribe({
      next: discounts => (this.discounts = discounts),
      error: () => this.snackBar.open('Impossible de charger les réductions', 'Fermer', { duration: 4000 })
    });
  }

  openDialog(discount?: StudentDiscount): void {
    this.dialog
      .open(DiscountDialogComponent, { data: { studentId: discount?.studentId ?? 0, discount } })
      .afterClosed()
      .subscribe(result => {
        if (result) {
          this.load();
        }
      });
  }

  deactivate(discount: StudentDiscount): void {
    if (!discount.id) return;
    this.discountService.deactivateDiscount(discount.id).subscribe({
      next: () => {
        this.snackBar.open('Réduction désactivée', 'Fermer', { duration: 3000 });
        this.load();
      }
    });
  }
}
