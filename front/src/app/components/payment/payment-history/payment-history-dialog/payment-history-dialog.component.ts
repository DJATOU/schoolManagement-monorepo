import { Component, Inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatOptionModule } from '@angular/material/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { StudentService } from '../../../student/services/student.service';
import { StudentFullHistoryDTO } from '../../../student/domain/StudentFullHistoryDTO';
import { GroupHistoryDTO } from '../../../../models/group/GroupHistoryDTO';
import { SeriesHistoryDTO } from '../../../../models/sessionSerie/SeriesHistoryDTO';
import { resolveLocale } from '../../../../shared/locale';
import {
  countBillableSessions,
  countExcludedSessions,
  isCatchUpBilled,
  isExcludedSession
} from '../../../../shared/session-billing';

// Importations pour pdfMake
import pdfMake from 'pdfmake/build/pdfmake';
import pdfFonts from 'pdfmake/build/vfs_fonts';

(pdfMake as any).vfs = pdfFonts.pdfMake.vfs;

import { Content, TDocumentDefinitions } from 'pdfmake/interfaces';

/** Statuts affichables, alignés sur les clés de traduction existantes. */
type DisplayStatus = 'paid' | 'partiallyPaid' | 'unpaid';

/** Ligne du tableau : une séance de la série sélectionnée. */
interface SessionPaymentRow {
  sessionName: string;
  paymentDate: string | null;
  amountPaid: number;
  status: DisplayStatus;
  catchUp: boolean;
  /**
   * Présence renseignée pour cette séance. Une séance sans présence n'est pas facturée :
   * la teinter en rouge « non payé » laissait croire à un impayé.
   */
  attendanceRecorded: boolean;
  /**
   * Séance écartée du coût au prorata : l'étudiant n'y était pas présent et elle n'est pas
   * facturée (exigences 11.3, 11.4). Elle porte un libellé et une icône dédiés, et non la
   * seule teinte grise : un impayé et une séance non facturée ne doivent pas se lire à la
   * couleur près.
   */
  excluded: boolean;
}

/**
 * Reçu de paiement d'un étudiant pour une série.
 *
 * <p>Les montants et les statuts proviennent de l'historique calculé par le serveur
 * ({@code GET /api/students/{id}/full-history}). Ce dialogue recalculait auparavant le
 * total et le statut de chaque séance côté navigateur, à partir du tarif catalogue et du
 * seul montant saisi sur la séance : il ignorait les réductions, n'affichait que les
 * séances portant un versement, et contredisait donc la fiche étudiante et l'historique
 * complet. Le serveur est la seule autorité sur ces calculs.</p>
 */
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
    MatProgressSpinnerModule,
    MatTableModule,
    MatIconModule,
    TranslateModule
  ]
})
export class PaymentHistoryDialogComponent implements OnInit {
  /** Suffixe monétaire, aligné sur le reste de l'application (« 8 000 DA »). */
  readonly currencySuffix = 'DA';

  studentGroups: GroupHistoryDTO[] = [];
  sessionSeries: SeriesHistoryDTO[] = [];
  paymentHistory = new MatTableDataSource<SessionPaymentRow>();

  selectedGroup: number | null = null;
  selectedSeries: number | null = null;

  seriesTotal = 0;
  seriesPaid = 0;
  seriesAllocated = 0;
  seriesOverpaid = 0;
  seriesRemaining = 0;
  seriesRefunded = 0;
  seriesStatus: DisplayStatus = 'unpaid';
  isCatchUpSeries = false;
  isExemptedSeries = false;
  /** Séances retenues dans le coût au prorata affiché (exigence 11.6). */
  billableSessions = 0;
  /** Prix net d'une séance, relayé par le serveur. Nul si la réponse ne le porte pas. */
  unitPriceNet: number | null = null;
  /** Séances écartées de la facturation, affichées mais non dues (exigence 11.3). */
  excludedSessions = 0;

  displayedColumns: string[] = ['session', 'paymentDate', 'amountPaid', 'paymentStatus'];

  studentName = '';
  loading = true;
  errorMessage = '';

  private fullHistory: StudentFullHistoryDTO | null = null;

  constructor(
    private studentService: StudentService,
    public dialogRef: MatDialogRef<PaymentHistoryDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { studentId: number },
    private translate: TranslateService
  ) {}

  ngOnInit(): void {
    this.loadHistory();
  }

  private loadHistory(): void {
    this.loading = true;
    this.errorMessage = '';
    this.studentService.getStudentFullHistory(this.data.studentId).subscribe({
      next: (history) => {
        this.fullHistory = history;
        this.studentName = history.studentName;
        this.studentGroups = history.groups ?? [];
        this.loading = false;
      },
      error: (error: Error) => {
        this.errorMessage = error.message || this.translate.instant('payment.history.loadError');
        this.loading = false;
      }
    });
  }

  /** Séries du groupe choisi, dans l'ordre chronologique fourni par le serveur. */
  loadSessionSeries(): void {
    const group = this.studentGroups.find(candidate => candidate.groupId === this.selectedGroup);
    this.sessionSeries = group?.series ?? [];
    this.selectedSeries = null;
    this.resetSeriesSummary();
  }

  /** Renseigne le récapitulatif et le tableau à partir de la série choisie. */
  loadPaymentHistory(): void {
    const series = this.sessionSeries.find(candidate => candidate.seriesId === this.selectedSeries);
    if (!series) {
      this.resetSeriesSummary();
      return;
    }

    const sessions = series.sessions ?? [];

    this.seriesTotal = series.totalCost ?? 0;
    // Prix unitaire relayé par le serveur : il sert à énoncer le calcul en clair, sans quoi
    // le total de la série ne s'explique pas.
    this.unitPriceNet = series.unitPriceNet ?? null;
    this.seriesPaid = series.totalAmountPaid ?? 0;
    // Le détail des séances ne justifie que la part affectée : on l'affiche à côté du
    // versement, et l'écart devient un trop-perçu explicite plutôt qu'un total inexpliqué.
    this.seriesAllocated = series.totalAllocated ?? this.seriesPaid;
    this.seriesOverpaid = series.totalOverpaid ?? Math.max(0, this.seriesPaid - this.seriesTotal);
    // Un trop-versé ne doit pas s'afficher comme un reste à payer négatif.
    this.seriesRemaining = Math.max(0, this.seriesTotal - this.seriesPaid);
    this.seriesRefunded = series.totalRefunded ?? 0;
    this.isExemptedSeries = series.isExempted === true;
    this.isCatchUpSeries = sessions.length > 0 && sessions.every(session => session.catchUpSession);
    this.seriesStatus = this.resolveSeriesStatus(series);
    // Coût au prorata et décompte facturable : le total ci-dessus porte les seules séances
    // facturables, il doit être annoncé comme tel plutôt que comme le coût de la série.
    this.billableSessions = countBillableSessions(series);
    this.excludedSessions = countExcludedSessions(series);

    this.paymentHistory.data = sessions.map(session => ({
      // Nom brut : l'étiquette « rattrapage » est portée par un badge à l'écran et par un
      // préfixe à l'impression, et ne concerne que les séances réellement facturées à ce
      // titre. L'apposer sur une séance écartée laisserait croire qu'elle est due.
      sessionName: session.sessionName,
      paymentDate: session.paymentDate ?? null,
      amountPaid: session.amountPaid ?? 0,
      status: this.toDisplayStatus(session.paymentStatus),
      catchUp: isCatchUpBilled(session),
      attendanceRecorded: session.attendanceStatus === 'PRESENT' || session.attendanceStatus === 'ABSENT',
      excluded: isExcludedSession(session)
    }));
  }

  private resetSeriesSummary(): void {
    this.paymentHistory.data = [];
    this.seriesTotal = 0;
    this.seriesPaid = 0;
    this.seriesAllocated = 0;
    this.seriesOverpaid = 0;
    this.seriesRemaining = 0;
    this.seriesRefunded = 0;
    this.seriesStatus = 'unpaid';
    this.isCatchUpSeries = false;
    this.isExemptedSeries = false;
    this.billableSessions = 0;
    this.unitPriceNet = null;
    this.excludedSessions = 0;
  }

  /**
   * Statut de la série : le serveur renvoie FULL ou PARTIAL. « Partiel » sans aucun
   * versement se lit plus clairement « non payé ».
   */
  private resolveSeriesStatus(series: SeriesHistoryDTO): DisplayStatus {
    if (series.paymentStatus === 'FULL') {
      return 'paid';
    }
    return (series.totalAmountPaid ?? 0) > 0 ? 'partiallyPaid' : 'unpaid';
  }

  /** Traduit un code de séance du serveur (PAID / PARTIAL / UNPAID) en statut affichable. */
  private toDisplayStatus(code: string | undefined | null): DisplayStatus {
    switch (code) {
      case 'PAID':
        return 'paid';
      case 'PARTIAL':
        return 'partiallyPaid';
      default:
        return 'unpaid';
    }
  }

  /** Nom de la série sélectionnée, pour l'en-tête du PDF. */
  private get selectedSeriesName(): string {
    return this.sessionSeries.find(series => series.seriesId === this.selectedSeries)?.seriesName ?? '';
  }

  /**
   * Montant formaté pour le PDF (« 2 100 DA »), selon la langue active.
   *
   * <p>Le séparateur de milliers du français est une espace fine insécable (U+202F), et
   * celui d'autres locales une espace insécable (U+00A0). La police embarquée du générateur
   * PDF ne possède pas ces glyphes : « 2 100 DA » s'affichait « 2⯑100⯑DA ». On les ramène
   * donc à une espace ordinaire.</p>
   */
  private formatAmount(amount: number): string {
    const formatted = amount.toLocaleString(resolveLocale(this.translate.currentLang), {
      maximumFractionDigits: 2
    });
    return `${formatted.replace(/[\u202F\u00A0]/g, ' ')} ${this.currencySuffix}`;
  }

  /** Date formatée selon la langue active, et non selon celle du navigateur. */
  private formatDate(value: string | Date | null | undefined): string {
    return value ? new Date(value).toLocaleDateString(resolveLocale(this.translate.currentLang)) : '—';
  }

  private getFillColorForRow(row: SessionPaymentRow): string {
    // Séance non facturée : aucune alerte de couleur. Le libellé de la colonne « statut »
    // porte l'information, la teinte seule ne suffirait pas à la distinguer d'un impayé.
    if (row.excluded || !row.attendanceRecorded) {
      return '#f5f5f5'; // Gris
    }
    switch (row.status) {
      case 'paid':
        return '#d0f0c0'; // Vert
      case 'partiallyPaid':
        return '#ffe4b5'; // Orange
      default:
        return '#ffcccb'; // Rouge
    }
  }

  /**
   * Intitulé de la séance à l'impression : la couleur de fond ne survit ni au noir et blanc
   * ni au daltonisme, les deux catégories sont donc nommées en clair dans la cellule.
   */
  private rowTitle(row: SessionPaymentRow): string {
    const name = row.sessionName || '—';
    if (row.excluded) {
      return `${this.translate.instant('payment.history.excluded.tag')} — ${name}`;
    }
    if (row.catchUp) {
      return `${this.translate.instant('payment.history.labels.catchUpPrefix')} ${name}`;
    }
    return name;
  }

  /**
   * Statut affiché : une séance écartée du prorata n'est pas jugée sur son paiement.
   *
   * <p>Le libellé nomme le motif — non présent, non facturée — au lieu du seul « non
   * facturée » : c'est ce qui empêche de la lire comme un impayé (exigences 11.3, 11.4).</p>
   */
  statusLabel(row: SessionPaymentRow): string {
    if (row.excluded || !row.attendanceRecorded) {
      return this.translate.instant('payment.history.excluded.reason');
    }
    return this.translate.instant('payment.history.status.' + row.status);
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

    // Le total d'une série ordinaire ne porte que les séances facturables à cet étudiant : le
    // nommer « Total » laissait croire à un coût nominal, et donc à un reste à payer manquant
    // (exigence 11.6). Le détail du calcul est imprimé juste en dessous, en clair.
    const totalLabel = this.translate.instant(
      this.isCatchUpSeries ? 'payment.history.labels.totalCatchUp' : 'payment.history.labels.seriesCost');

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
          text: `${this.translate.instant('payment.history.pdf.date')}: ${this.formatDate(new Date())}`,
          alignment: 'right'
        },
        { text: '\n' },
        {
          text: this.selectedSeriesName,
          style: 'sectionHeader'
        },
        ...(this.isCatchUpSeries ? [{
          text: this.translate.instant('payment.history.pdf.catchUpOnly'),
          style: 'catchUpNote',
          color: 'red',
          bold: true,
          margin: [0, 5, 0, 10]
        }] : []),
        ...(this.isExemptedSeries ? [{
          text: this.translate.instant('payment.history.exempted'),
          bold: true,
          margin: [0, 5, 0, 10]
        }] : []),
        // Décompte facturable : sans lui, le coût imprimé au prorata reste inexpliqué.
        {
          text: this.translate.instant('payment.history.pdf.billableSessions',
            { count: this.billableSessions }),
          margin: [0, 0, 0, 4]
        },
        // Séances écartées : affichées dans le tableau, mais explicitement hors dette.
        ...(this.excludedSessions > 0 ? [{
          text: this.translate.instant('payment.history.pdf.excludedSessions',
            { count: this.excludedSessions }),
          italics: true,
          margin: [0, 0, 0, 6]
        }] : []),
        {
          columns: [
            { text: `${totalLabel} : ${this.formatAmount(this.seriesTotal)}`, width: '50%' },
            {
              text: `${this.translate.instant('payment.history.labels.paid')} : `
                + this.formatAmount(this.seriesPaid),
              width: '50%'
            }
          ]
        },
        {
          columns: [
            {
              text: `${this.translate.instant('payment.history.labels.remaining')} : `
                + this.formatAmount(this.seriesRemaining),
              width: '50%'
            },
            {
              text: `${this.translate.instant('payment.history.labels.status')} : `
                + this.translate.instant('payment.history.status.' + this.seriesStatus),
              width: '50%'
            }
          ]
        },
        // Trop-perçu : sans cette ligne, le versement affiché en tête ne correspondait pas
        // à la somme des montants affectés dans le tableau des séances.
        ...(this.seriesOverpaid > 0 ? [{
          columns: [
            {
              text: `${this.translate.instant('payment.history.labels.allocated')} : `
                + this.formatAmount(this.seriesAllocated),
              width: '50%'
            },
            {
              text: `${this.translate.instant('payment.history.labels.overpaid')} : `
                + this.formatAmount(this.seriesOverpaid),
              width: '50%'
            }
          ]
        }] : []),
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
          text: `${this.translate.instant('payment.history.pdf.page')} ${currentPage} `
            + `${this.translate.instant('payment.history.pdf.of')} ${pageCount}`,
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
    if (this.paymentHistory.data.length > 0) {
      for (const row of this.paymentHistory.data) {
        const fillColor = this.getFillColorForRow(row);

        body.push([
          { text: this.rowTitle(row), fillColor },
          { text: this.formatDate(row.paymentDate), fillColor },
          { text: this.formatAmount(row.amountPaid), fillColor },
          { text: this.statusLabel(row), fillColor }
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
