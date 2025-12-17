import { CommonModule } from '@angular/common';
import { Component, OnInit, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { HttpClient, HttpParams, HttpHeaders } from '@angular/common/http';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { MatPaginator, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { API_BASE_URL } from '../../../app.config';
import { GroupService } from '../../../services/group.service';
import { EditPaymentDetailDialogComponent } from './dialogs/edit-payment-detail-dialog.component';
import { PaymentDetailHistoryDialogComponent } from './dialogs/payment-detail-history-dialog.component';
import { finalize } from 'rxjs';

interface PaymentDetailView {
  id: number;
  studentName: string;
  groupName: string;
  seriesName?: string;
  amountPaid: number;
  active: boolean;
  dateCreation?: string;
  paymentId?: number;
  sessionId?: number;
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
    MatDatepickerModule,
    MatNativeDateModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatChipsModule,
    MatDialogModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    EditPaymentDetailDialogComponent,
    PaymentDetailHistoryDialogComponent
  ]
})
export class PaymentManagementComponent implements OnInit {
  displayedColumns: string[] = ['id', 'student', 'group', 'series', 'amount', 'active', 'dateCreation', 'actions'];
  dataSource = new MatTableDataSource<PaymentDetailView>([]);
  filterForm: FormGroup;

  groups: any[] = [];
  students: any[] = [];
  series: any[] = [];

  isLoading = false;
  totalElements = 0;
  pageIndex = 0;
  pageSize = 10;

  @ViewChild(MatPaginator) paginator!: MatPaginator;

  constructor(
    private fb: FormBuilder,
    private http: HttpClient,
    private dialog: MatDialog,
    private groupService: GroupService
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
    this.loadPaymentDetails();
    this.loadFilterOptions();
  }

  loadPaymentDetails(): void {
    this.isLoading = true;
    let params = new HttpParams()
      .set('page', this.pageIndex)
      .set('size', this.pageSize)
      .set('sort', 'id')
      .set('direction', 'DESC');

    Object.entries(this.filterForm.value).forEach(([key, value]) => {
      if (value !== null && value !== '') {
        params = params.set(key, value);
      }
    });

    this.http.get<any>(`${API_BASE_URL}/api/payment-details`, { params })
      .pipe(finalize(() => this.isLoading = false))
      .subscribe(response => {
        this.dataSource.data = response.content || [];
        this.totalElements = response.totalElements || 0;
        setTimeout(() => {
          if (this.paginator) {
            this.dataSource.paginator = this.paginator;
          }
        });
      });
  }

  loadFilterOptions(): void {
    this.groupService.getGroups().subscribe(groups => this.groups = groups || []);
    this.http.get<any[]>(`${API_BASE_URL}/api/students`).subscribe(students => this.students = students || []);
    this.http.get<any[]>(`${API_BASE_URL}/api/series`).subscribe(series => this.series = series || []);
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadPaymentDetails();
  }

  openEditDialog(detail: PaymentDetailView): void {
    const dialogRef = this.dialog.open(EditPaymentDetailDialogComponent, {
      width: '420px',
      data: detail
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.loadPaymentDetails();
      }
    });
  }

  openHistoryDialog(detail: PaymentDetailView): void {
    this.dialog.open(PaymentDetailHistoryDialogComponent, {
      width: '600px',
      data: detail
    });
  }

  deletePaymentDetail(detail: PaymentDetailView): void {
    const reason = window.prompt('Veuillez fournir une raison pour la suppression:');
    if (!reason) {
      return;
    }

    const headers = new HttpHeaders().set('X-Admin-Name', 'Admin');
    this.http.delete(`${API_BASE_URL}/api/payment-details/${detail.id}`, { headers, body: { reason } })
      .subscribe(() => this.loadPaymentDetails());
  }

  resetFilters(): void {
    this.filterForm.reset();
    this.pageIndex = 0;
    this.loadPaymentDetails();
  }

  exportToCSV(): void {
    // TODO: Implémenter l'export CSV
    window.alert('Export CSV à venir');
  }
}
