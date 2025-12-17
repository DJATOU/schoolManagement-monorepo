import { Component, Inject, OnInit } from '@angular/core';
import { PaymentDetail } from '../../../../models/paymentDetail/paymentDetail';
import { Group } from '../../../../models/group/group';
import { SessionSeries } from '../../../../models/sessionSerie/sessionSerie';
import { PaymentService } from '../../../../services/payment.service';
import { SeriesService } from '../../../../services/series.service';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatOptionModule } from '@angular/material/core';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { StudentService } from '../../../student/services/student.service';
import { PricingService } from '../../../../services/pricing.service';
import { AttendanceService } from '../../../../services/attendance.service';
import { Observable, forkJoin } from 'rxjs';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

// Importations pour pdfMake
import pdfMake from 'pdfmake/build/pdfmake';
import pdfFonts from 'pdfmake/build/vfs_fonts';

(pdfMake as any).vfs = pdfFonts.pdfMake.vfs;

import { Content, TDocumentDefinitions } from 'pdfmake/interfaces';

@Component({
  selector: 'app-payment-history-dialog',
  standalone: true,
  templateUrl: './payment-history-dialog.component.html',
  styleUrls: ['./payment-history-dialog.component.scss'],
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    MatOptionModule,
    MatTableModule,
    TranslateModule
  ]
})
export class PaymentHistoryDialogComponent implements OnInit {
  studentGroups: Group[] = [];
  sessionSeries: SessionSeries[] = [];
  paymentHistory = new MatTableDataSource<PaymentDetail>();

  selectedGroup: number | null = null;
  selectedSeries: number | null = null;

  seriesTotal = 0;
  seriesPaid = 0;
  seriesRemaining = 0;
  seriesStatus: 'paid' | 'partiallyPaid' | 'unpaid' = 'unpaid';
  isCatchUpSeries = false;

  displayedColumns: string[] = ['session', 'paymentDate', 'amountPaid', 'paymentStatus'];

  studentName: string = '';

  constructor(
    private paymentService: PaymentService,
    private studentService: StudentService,
    private seriesService: SeriesService,
    private pricingService: PricingService,
    private attendanceService: AttendanceService,
    public dialogRef: MatDialogRef<PaymentHistoryDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { studentId: number },
    private translate: TranslateService
  ) {}

  ngOnInit(): void {
    this.loadStudentInfo();
    this.loadGroups();
  }

  private loadStudentInfo(): void {
    this.studentService.getStudentById(this.data.studentId).subscribe({
      next: (student) => {
        this.studentName = `${student.firstName} ${student.lastName}`;
      },
      error: (error) => {
        console.error('Error loading student info:', error);
      }
    });
  }

  private loadGroups(): void {
    this.studentService.getGroupsForStudent(this.data.studentId).subscribe({
      next: (groups) => {
        this.studentGroups = groups;
      },
      error: (error) => {
        console.error('Error loading groups:', error);
      }
    });
  }

  loadSessionSeries(): void {
    if (this.selectedGroup) {
      this.seriesService.getSessionSeriesByGroupId(this.selectedGroup).subscribe({
        next: (series) => {
          this.sessionSeries = series;
          this.selectedSeries = null;
          this.paymentHistory.data = [];
        },
        error: (error) => {
          console.error('Error loading session series:', error);
        }
      });
    }
  }

  loadPaymentHistory(): void {
    if (this.selectedSeries && this.selectedGroup) {
      const selectedGroupObject = this.studentGroups.find(group => group.id === this.selectedGroup);

      if (!selectedGroupObject) {
        console.error('Selected group not found in studentGroups array.');
        return;
      }

      const pricingId = selectedGroupObject.priceId;

      // Charger en parallèle : pricing, attendances, payment history et détails de paiement
      forkJoin({
        pricing: this.loadGroupPricing(pricingId),
        attendances: this.attendanceService.getAttendanceByStudentAndSeries(this.data.studentId, this.selectedSeries),
        paymentHistory: this.paymentService.getPaymentHistoryForSeries(this.data.studentId, this.selectedSeries),
        paymentDetails: this.paymentService.getPaymentDetailsForSeries(this.data.studentId, this.selectedSeries)
      }).subscribe({
        next: ({ pricing, attendances, paymentHistory, paymentDetails }) => {
          const sessionPrice = pricing.price ?? 0;

          // Déterminer si l'étudiant est en rattrapage pour cette série
          this.isCatchUpSeries = attendances.length > 0 && attendances.every(a => a.isCatchUp);

          let totalSessions: number;

          // Préparer les sessions de rattrapage effectivement suivies (présent)
          const attendedCatchUpSessionIds = new Set(
            attendances
              .filter(a => a.isCatchUp && a.isPresent)
              .map(a => a.sessionId)
          );

          if (this.isCatchUpSeries) {
            // RATTRAPAGE : Compter uniquement les sessions où l'étudiant est PRÉSENT
            totalSessions = attendances.filter(a => a.isCatchUp && a.isPresent).length;
          } else {
            // NORMAL : Utiliser le nombre total de sessions de la série
            totalSessions = this.sessionSeries.find(series => series.id === this.selectedSeries)?.totalSessions ?? 0;
          }

          this.seriesTotal = totalSessions * sessionPrice;
          this.seriesPaid = (paymentDetails || [])
            .filter(detail => !this.isCatchUpSeries || detail.isCatchUp)
            .reduce((acc, payment) => acc + (payment.amountPaid || 0), 0);
          this.seriesRemaining = this.seriesTotal - this.seriesPaid;
          this.seriesStatus = this.getSeriesStatus();

          this.loadSessionPaymentDetails(sessionPrice, paymentDetails || []);
        },
        error: (error: Error) => {
          console.error('Error loading payment history data:', error);
        }
      });
    } else {
      console.error('Selected series or group is null or undefined.');
    }
  }

  private loadSessionPaymentDetails(sessionPrice: number, paymentDetails: PaymentDetail[]): void {
    if (this.selectedGroup === null || this.selectedSeries === null) {
      console.error('Selected group or selected series is null or undefined.');
      return;
    }

    this.paymentHistory.data = paymentDetails.map(detail => ({
      sessionId: detail.sessionId,
      sessionName: detail.isCatchUp ? `${this.translate.instant('payment.history.labels.catchUpPrefix')} ${detail.sessionName}` : detail.sessionName,
      paymentMethod: detail.paymentMethod || this.translate.instant('payment.history.labels.cash'),
      description: detail.description || this.translate.instant('payment.history.labels.noDescription'),
      paymentDate: detail.paymentDate,
      amountPaid: detail.amountPaid,
      status: this.getPaymentStatusWithPrice(detail, sessionPrice),
      sessionPrice: sessionPrice,
      isCatchUp: detail.isCatchUp
    }));
  }

  private getPaymentStatusWithPrice(detail: PaymentDetail, sessionPrice: number): string {
    if (detail.amountPaid >= sessionPrice) {
      return 'paid';
    } else if (detail.amountPaid > 0 && detail.amountPaid < sessionPrice) {
      return 'partiallyPaid';
    } else {
      return 'unpaid';
    }
  }

  private getSeriesStatus(): string {
    if (this.seriesRemaining === 0) {
      return 'paid';
    } else if (this.seriesPaid > 0) {
      return 'partiallyPaid';
    } else {
      return 'unpaid';
    }
  }

  private loadGroupPricing(groupId: number): Observable<{ price: number }> {
    return this.pricingService.getPricingById(groupId);
  }

  private getFillColorForStatus(status: string): string {
    switch (status) {
      case 'paid':
        return '#d0f0c0'; // Vert
      case 'partiallyPaid':
        return '#ffe4b5'; // Orange
      case 'unpaid':
        return '#ffcccb'; // Rouge
      default:
        return '#ffffff'; // Blanc
    }
  }

  // Méthode pour convertir l'image en Base64
  private convertImageToBase64(url: string): Promise<string> {
    return new Promise((resolve, reject) => {
      const img = new Image();
      img.crossOrigin = 'Anonymous';
      img.src = url;
      img.onload = () => {
        const canvas = document.createElement('canvas');
        canvas.width = img.width;
        canvas.height = img.height;
        const ctx = canvas.getContext('2d');
        ctx?.drawImage(img, 0, 0);
        const dataURL = canvas.toDataURL('image/png');
        resolve(dataURL);
      };
      img.onerror = error => {
        reject(error);
      };
    });
  }

  async generatePdf(): Promise<void> {
    let logoBase64 = '';
    try {
      logoBase64 = await this.convertImageToBase64('assets/succes_assistance.png');
    } catch (error) {
      console.error('Erreur lors du chargement du logo :', error);
    }

    const documentDefinition: TDocumentDefinitions = {
      content: [
        {
          columns: [
            {
              image: logoBase64,
              width: 100
            },
            {
              text: this.translate.instant('payment.history.pdf.title'),
              style: 'header',
              alignment: 'right'
            }
          ]
        },
        { text: '\n\n' },
        {
          text: `${this.translate.instant('payment.history.pdf.student')}: ${this.studentName}`,
          style: 'subheader'
        },
        {
          text: `${this.translate.instant('payment.history.pdf.date')}: ${new Date().toLocaleDateString()}`,
          alignment: 'right'
        },
        { text: '\n' },
        {
          text: `${this.sessionSeries.find(series => series.id === this.selectedSeries)?.name}`,
          style: 'sectionHeader'
        },
        ...(this.isCatchUpSeries ? [{
          text: this.translate.instant('payment.history.pdf.catchUpOnly'),
          style: 'catchUpNote',
          color: 'red',
          bold: true,
          margin: [0, 5, 0, 10]
        }] : []),
        {
          columns: [
            { text: `${this.translate.instant(this.isCatchUpSeries ? 'payment.history.labels.totalCatchUp' : 'payment.history.labels.total')} : ${this.seriesTotal} DA`, width: '50%' },
            { text: `${this.translate.instant('payment.history.labels.paid')} : ${this.seriesPaid} DA`, width: '50%' }
          ]
        },
        {
          columns: [
            { text: `${this.translate.instant('payment.history.labels.remaining')} : ${this.seriesRemaining} DA`, width: '50%' },
            { text: `${this.translate.instant('payment.history.labels.status')} : ${this.translate.instant('payment.history.status.' + this.seriesStatus)}`, width: '50%' }
          ]
        },
        { text: '\n' },
        {
          text: this.translate.instant('payment.history.pdf.details'),
          style: 'sectionHeader'
        },
        this.getPaymentHistoryTable(),
        { text: '\n\n' },
        {
          text: this.translate.instant('payment.history.pdf.studentSignature'),
          alignment: 'right',
          margin: [0, 50, 0, 0]
        },
        {
          text: this.translate.instant('payment.history.pdf.adminSignature'),
          alignment: 'right',
          margin: [0, 50, 0, 0]
        }
      ],
      styles: {
        header: {
          fontSize: 22,
          bold: true,
          color: '#2F5496',
          margin: [0, 0, 0, 10]
        },
        subheader: {
          fontSize: 16,
          bold: true,
          margin: [0, 10, 0, 5]
        },
        sectionHeader: {
          fontSize: 18,
          bold: true,
          color: '#2F5496',
          margin: [0, 15, 0, 10]
        },
        tableHeader: {
          bold: true,
          fontSize: 12,
          color: 'white',
          fillColor: '#4F81BD',
          alignment: 'center'
        },
        tableCell: {
          margin: [0, 5, 0, 5]
        }
      },
      footer: (currentPage: number, pageCount: number): Content => {
        return {
          text: `${this.translate.instant('payment.history.pdf.page')} ${currentPage} ${this.translate.instant('payment.history.pdf.of')} ${pageCount}`,
          alignment: 'center',
          fontSize: 10,
          margin: [0, 10, 0, 0]
        } as Content;
      }
    };

    const pdfDocGenerator = pdfMake.createPdf(documentDefinition);

    pdfDocGenerator.getBlob((blob) => {
      const blobUrl = URL.createObjectURL(blob);
      window.open(blobUrl, '_blank');
    });
  }

  private getPaymentHistoryTable(): any {
    const body = [];

    // En-têtes du tableau
    body.push([
      { text: this.translate.instant('payment.history.table.session'), style: 'tableHeader' },
      { text: this.translate.instant('payment.history.table.paymentDate'), style: 'tableHeader' },
      { text: this.translate.instant('payment.history.table.amountPaid'), style: 'tableHeader' },
      { text: this.translate.instant('payment.history.table.paymentStatus'), style: 'tableHeader' }
    ]);

    // Données du tableau
    if (this.paymentHistory.data && this.paymentHistory.data.length > 0) {
      for (const payment of this.paymentHistory.data) {
        const status = payment.status || 'N/A';
        const fillColor = this.getFillColorForStatus(status);

        body.push([
          { text: payment.sessionName || 'N/A', fillColor },
          { text: payment.paymentDate ? new Date(payment.paymentDate).toLocaleDateString() : 'N/A', fillColor },
          { text: `${payment.amountPaid} DA`, fillColor },
          { text: this.translate.instant('payment.history.status.' + status), fillColor }
        ]);
      }
    } else {
      body.push([
        { text: this.translate.instant('payment.history.table.empty'), colSpan: 4, alignment: 'center' }
      ]);
    }

    return {
      table: {
        headerRows: 1,
        widths: ['*', '*', '*', '*'],
        body: body
      },
      layout: 'lightHorizontalLines'
    };
  }
}
