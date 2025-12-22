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
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { API_BASE_URL } from '../../../app.config';
import { GroupService } from '../../../services/group.service';
import { EditPaymentDetailDialogComponent } from './dialogs/edit-payment-detail-dialog.component';
import { PaymentDetailHistoryDialogComponent } from './dialogs/payment-detail-history-dialog.component';
import { LevelService } from '../../../services/level.service';
import { finalize } from 'rxjs';

interface PaymentDetailView {
  id: number;
  studentFirstName: string;
  studentLastName: string;
  studentId: number;
  groupName: string;
  groupId: number;
  seriesName?: string;
  seriesId?: number;
  sessionName?: string;
  sessionId?: number;
  amountPaid: number;
  active: boolean;
  permanentlyDeleted?: boolean;
  dateCreation?: Date;
  paymentDate?: Date;
  paymentId?: number;
  paymentStatus?: string;
  isCatchUp?: boolean;
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
    MatSnackBarModule,
    EditPaymentDetailDialogComponent,
    PaymentDetailHistoryDialogComponent
  ]
})
export class PaymentManagementComponent implements OnInit {
  displayedColumns: string[] = ['id', 'student', 'group', 'series', 'session', 'amount', 'status', 'dateCreation', 'actions'];
  dataSource = new MatTableDataSource<PaymentDetailView>([]);
  filterForm: FormGroup;

  groups: any[] = [];
  students: any[] = [];
  series: any[] = [];
  levels: any[] = [];

  isLoading = false;
  totalElements = 0;
  pageIndex = 0;
  pageSize = 10;

  @ViewChild(MatPaginator) paginator!: MatPaginator;

  constructor(
    private fb: FormBuilder,
    private http: HttpClient,
    private dialog: MatDialog,
    private groupService: GroupService,
    private levelService: LevelService,
    private snackBar: MatSnackBar
  ) {
    this.filterForm = this.fb.group({
      studentId: [null],
      groupId: [null],
      sessionSeriesId: [null],
      active: [null],
      dateFrom: [null],
      levelId: [null]
    });
  }

  ngOnInit(): void {
    this.loadPaymentDetails();
    this.loadFilterOptions();
  }

  loadPaymentDetails(): void {
    this.isLoading = true;
    let params = new HttpParams()
      .set('page', String(this.pageIndex))
      .set('size', String(this.pageSize))
      .set('sort', 'id')
      .set('direction', 'DESC');

    Object.entries(this.filterForm.value).forEach(([key, value]) => {
      const normalized = this.normalizeParamValue(value);
      if (normalized !== null) {
        params = params.set(key, normalized);
      }
    });

    this.http.get<any>(`${API_BASE_URL}/api/payment-details`, { params })
      .pipe(finalize(() => this.isLoading = false))
      .subscribe(response => {
        // Transform date arrays to JavaScript Date objects
        const content = (response.content || []).map((item: any) => ({
          ...item,
          dateCreation: this.convertToDate(item.dateCreation)
        }));
        this.dataSource.data = content;
        this.totalElements = response.totalElements || 0;
        setTimeout(() => {
          if (this.paginator) {
            this.dataSource.paginator = this.paginator;
          }
        });
      });
  }

  private normalizeParamValue(value: any): string | null {
    if (value === null || value === undefined || value === '') {
      return null;
    }

    // Handle Date objects (e.g., from date pickers) as yyyy-MM-dd
    if (value instanceof Date) {
      const yyyy = value.getFullYear();
      const mm = String(value.getMonth() + 1).padStart(2, '0');
      const dd = String(value.getDate()).padStart(2, '0');
      return `${yyyy}-${mm}-${dd}`;
    }

    // If it's an object from a select, try to use its id; otherwise skip
    if (typeof value === 'object') {
      const maybeId = (value as any)?.id;
      if (maybeId !== undefined && maybeId !== null) {
        return String(maybeId);
      }
      return null;
    }

    // Primitives: string/number/boolean
    return String(value);
  }

  private convertToDate(dateArray: any): Date | null {
    if (!dateArray) {
      return null;
    }

    // If it's already a Date or string, return it
    if (dateArray instanceof Date || typeof dateArray === 'string') {
      return new Date(dateArray);
    }

    // If it's an array [year, month, day, hour, minute, second, nano]
    if (Array.isArray(dateArray) && dateArray.length >= 3) {
      const [year, month, day, hour = 0, minute = 0, second = 0, nano = 0] = dateArray;
      // Month in JavaScript Date is 0-indexed, but Java LocalDateTime is 1-indexed
      return new Date(year, month - 1, day, hour, minute, second, Math.floor(nano / 1000000));
    }

    return null;
  }

  loadFilterOptions(): void {
    this.groupService.getGroups().subscribe(groups => this.groups = groups || []);
    this.http.get<any[]>(`${API_BASE_URL}/api/students`).subscribe(students => this.students = students || []);
    this.http.get<any[]>(`${API_BASE_URL}/api/series`).subscribe(series => this.series = series || []);
    this.levelService.getLevels().subscribe(levels => this.levels = levels || []);
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
        this.snackBar.open('Paiement modifié avec succès', 'Fermer', {
          duration: 3000,
          horizontalPosition: 'center',
          verticalPosition: 'bottom'
        });
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
    const reason = window.prompt('Veuillez fournir une raison pour la désactivation:');
    if (!reason) {
      return;
    }

    const headers = new HttpHeaders().set('X-Admin-Name', 'Admin');
    this.http.delete(`${API_BASE_URL}/api/payment-details/${detail.id}`, { headers, body: { reason } })
      .subscribe(() => {
        this.loadPaymentDetails();
        this.snackBar.open('Paiement désactivé avec succès. Vous pouvez maintenant repayer cette session.', 'Fermer', {
          duration: 5000,
          horizontalPosition: 'center',
          verticalPosition: 'bottom'
        });
      });
  }

  reactivatePaymentDetail(detail: PaymentDetailView): void {
    // Vérifier si c'est une suppression définitive
    if (detail.permanentlyDeleted) {
      this.snackBar.open('Ce paiement a été définitivement supprimé et ne peut pas être réactivé.', 'Fermer', {
        duration: 5000,
        horizontalPosition: 'center',
        verticalPosition: 'bottom',
        panelClass: ['error-snackbar']
      });
      return;
    }

    const reason = window.prompt('Veuillez fournir une raison pour la réactivation:');
    if (!reason) {
      return;
    }

    const headers = new HttpHeaders().set('X-Admin-Name', 'Admin');
    this.http.post(`${API_BASE_URL}/api/payment-details/${detail.id}/reactivate`, { reason }, { headers })
      .subscribe({
        next: () => {
          this.loadPaymentDetails();
          this.snackBar.open('Paiement réactivé avec succès', 'Fermer', {
            duration: 3000,
            horizontalPosition: 'center',
            verticalPosition: 'bottom'
          });
        },
        error: (error) => {
          const errorMessage = error.error?.message || 'Erreur lors de la réactivation du paiement';
          this.snackBar.open(errorMessage, 'Fermer', {
            duration: 5000,
            horizontalPosition: 'center',
            verticalPosition: 'bottom',
            panelClass: ['error-snackbar']
          });
        }
      });
  }

  resetFilters(): void {
    this.filterForm.reset();
    this.pageIndex = 0;
    this.loadPaymentDetails();
    this.snackBar.open('Filtres effacés avec succès', 'Fermer', {
      duration: 3000,
      horizontalPosition: 'center',
      verticalPosition: 'bottom'
    });
  }

  exportToCSV(): void {
    // TODO: Implémenter l'export CSV
    window.alert('Export CSV à venir');
  }

  getStudentFullName(detail: PaymentDetailView): string {
    return `${detail.studentFirstName} ${detail.studentLastName}`;
  }

  getStatusLabel(detail: PaymentDetailView): string {
    if (detail.permanentlyDeleted) {
      return 'Supprimé';
    }
    return detail.active ? 'Actif' : 'Inactif';
  }

  getStatusColor(detail: PaymentDetailView): string {
    if (detail.permanentlyDeleted) {
      return 'warn';
    }
    return detail.active ? 'primary' : 'accent';
  }
}
