import { Component, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { MatPaginator, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatOptionModule } from '@angular/material/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSortModule } from '@angular/material/sort';
import { API_BASE_URL } from '../../../app.config';
import { Group } from '../../../models/group/group';
import { GroupService } from '../../../services/group.service';
import { Student } from '../../student/domain/student';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { EditPaymentDetailDialogComponent } from './dialogs/edit-payment-detail-dialog.component';
import { PaymentDetailHistoryDialogComponent } from './dialogs/payment-detail-history-dialog.component';

interface PaymentDetailRow {
  id: number;
  paymentId: number;
  studentName?: string;
  groupName?: string;
  seriesName?: string;
  amountPaid: number;
  active: boolean;
  dateCreation?: string;
}

@Component({
  selector: 'app-payment-management',
  standalone: true,
  templateUrl: './payment-management.component.html',
  styleUrls: ['./payment-management.component.scss'],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatOptionModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatSnackBarModule,
    MatDialogModule,
    MatChipsModule,
    MatTooltipModule,
    MatSortModule,
    MatProgressSpinnerModule,
    EditPaymentDetailDialogComponent,
    PaymentDetailHistoryDialogComponent
  ]
})
export class PaymentManagementComponent implements OnInit {
  displayedColumns: string[] = ['id', 'student', 'group', 'series', 'amount', 'active', 'dateCreation', 'actions'];
  dataSource = new MatTableDataSource<PaymentDetailRow>([]);
  filterForm: FormGroup;
  groups: Group[] = [];
  students: Student[] = [];
  isLoading = false;
  pageIndex = 0;
  pageSize = 10;
  totalElements = 0;

  @ViewChild(MatPaginator) paginator!: MatPaginator;

  constructor(
    private fb: FormBuilder,
    private http: HttpClient,
    private groupService: GroupService,
    private snackBar: MatSnackBar,
    private dialog: MatDialog
  ) {
    this.filterForm = this.fb.group({
      studentId: [null],
      groupId: [null],
      sessionSeriesId: [null],
      active: [null],
      dateFrom: [null],
      dateTo: [null]
    });
  }

  ngOnInit(): void {
    this.loadFilterOptions();
    this.loadPaymentDetails();
  }

  loadPaymentDetails(): void {
    this.isLoading = true;
    let params = new HttpParams()
      .set('page', this.pageIndex.toString())
      .set('size', this.pageSize.toString())
      .set('direction', 'DESC')
      .set('sort', 'dateCreation');

    Object.entries(this.filterForm.value).forEach(([key, value]) => {
      if (value !== null && value !== '') {
        params = params.set(key, value as any);
      }
    });

    this.http.get<{ content: PaymentDetailRow[]; totalElements: number }>(`${API_BASE_URL}/api/payment-details`, { params })
      .subscribe({
        next: (response) => {
          this.dataSource.data = response.content;
          this.totalElements = response.totalElements;
          if (this.paginator) {
            this.paginator.length = response.totalElements;
          }
        },
        error: () => this.snackBar.open('Erreur lors du chargement des paiements', 'Fermer', { duration: 3000 }),
        complete: () => this.isLoading = false
      });
  }

  loadFilterOptions(): void {
    this.groupService.getGroups().subscribe({
      next: groups => this.groups = groups,
      error: () => this.snackBar.open('Erreur lors du chargement des groupes', 'Fermer', { duration: 3000 })
    });

    this.http.get<Student[]>(`${API_BASE_URL}/api/students`).subscribe({
      next: students => this.students = students,
      error: () => this.snackBar.open('Erreur lors du chargement des étudiants', 'Fermer', { duration: 3000 })
    });
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadPaymentDetails();
  }

  openEditDialog(row: PaymentDetailRow): void {
    const dialogRef = this.dialog.open(EditPaymentDetailDialogComponent, {
      width: '420px',
      data: { id: row.id, amount: row.amountPaid, active: row.active }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.loadPaymentDetails();
      }
    });
  }

  openHistoryDialog(row: PaymentDetailRow): void {
    this.dialog.open(PaymentDetailHistoryDialogComponent, {
      width: '600px',
      data: { id: row.id }
    });
  }

  deletePaymentDetail(row: PaymentDetailRow): void {
    const reason = prompt('Veuillez fournir une raison pour la suppression :');
    if (!reason) {
      this.snackBar.open('La raison est obligatoire pour la suppression', 'Fermer', { duration: 3000 });
      return;
    }

    this.http.delete(`${API_BASE_URL}/api/payment-details/${row.id}`, {
      headers: { 'X-Admin-Name': 'Admin' },
      body: { reason }
    }).subscribe({
      next: () => {
        this.snackBar.open('Paiement supprimé avec succès', 'Fermer', { duration: 3000 });
        this.loadPaymentDetails();
      },
      error: () => this.snackBar.open('Erreur lors de la suppression', 'Fermer', { duration: 3000 })
    });
  }

  resetFilters(): void {
    this.filterForm.reset();
    this.pageIndex = 0;
    this.loadPaymentDetails();
  }

  exportToCSV(): void {
    this.snackBar.open('Export CSV à implémenter', 'Fermer', { duration: 3000 });
  }
}
