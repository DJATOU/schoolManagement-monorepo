import { Injectable, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import pdfMake from 'pdfmake/build/pdfmake';
import pdfFonts from 'pdfmake/build/vfs_fonts';
import { Content, TableCell, TDocumentDefinitions } from 'pdfmake/interfaces';
import { bindPrintLanguage, pdfInstant, pdfLanguage, pdfLocale, withPrintLanguage } from '../shared/pdf-i18n';
import { PdfOutputService } from './pdf-output.service';

/** Informations de la série imprimée. */
export interface SeriesPdfInfo {
  name?: string;
  groupName?: string;
  totalSessions?: number;
  sessionsCompleted?: number;
  numberOfSessionsCreated?: number;
  serieTimeStart?: string | Date | null;
  serieTimeEnd?: string | Date | null;
}

/** Ligne de session pour le tableau PDF. */
export interface SeriesSessionRow {
  title?: string;
  sessionType?: string;
  teacherName?: string;
  roomName?: string;
  sessionTimeStart?: string | Date | null;
  sessionTimeEnd?: string | Date | null;
  isFinished?: boolean;
}

/**
 * Service d'impression PDF de la liste des sessions d'une série.
 * Rendu soigné : en-tête, bloc d'infos série, tableau zébré des sessions,
 * synthèse et signatures.
 */
@Injectable({ providedIn: 'root' })
export class SeriesSessionsPdfService {

  private static readonly PRIMARY = '#4f46e5';
  private static readonly PRIMARY_SOFT = '#6366f1';
  private static readonly GREEN = '#32a852';
  private static readonly AMBER = '#f59e0b';
  private static readonly GREY = '#64748b';

  private readonly output = inject(PdfOutputService);

  constructor(private translate: TranslateService) {
    (pdfMake as any).vfs = pdfFonts.pdfMake.vfs;
  }

  /** Libellé du document, dans sa langue (voir pdfLanguage). */
  private t(key: string, params?: object): string {
    return pdfInstant(this.translate, key, params);
  }

  /**
   * Génère et ouvre le PDF de la liste des sessions d'une série. En arabe, le navigateur imprime le
   * même document : la boîte d'impression s'ouvre, « Enregistrer en PDF » donne le fichier.
   */
  generate(info: SeriesPdfInfo, sessions: SeriesSessionRow[]): void {
    const lang = pdfLanguage(this.translate);
    const doc = withPrintLanguage(lang, () => this.buildDocument(info, sessions));
    if (lang === 'ar') {
      const title = withPrintLanguage(lang, () => info.name || this.t('SERIES_SESSIONS_PDF.TITLE_FALLBACK'));
      this.output.printHtml(doc, title).catch((err: unknown) => console.error('Impression de la série impossible :', err));
      return;
    }

    pdfMake.createPdf(bindPrintLanguage(this.translate, doc, lang)).getBlob((blob) => {
      window.open(URL.createObjectURL(blob), '_blank');
    });
  }

  /** Définition du document, publique pour être vérifiée sans passer par l'impression. */
  buildDocument(info: SeriesPdfInfo, sessions: SeriesSessionRow[]): TDocumentDefinitions {
    const content: Content[] = [
      this.buildHeader(info),
      this.buildDivider(),
      this.buildSeriesInfo(info),
      { text: this.t('SERIES_SESSIONS_PDF.SECTION_TITLE'), style: 'sectionHeader' },
      this.buildSessionsTable(sessions),
      this.buildSignatures()
    ];

    const doc: TDocumentDefinitions = {
      content,
      pageMargins: [40, 60, 40, 60],
      styles: {
        header: { fontSize: 20, bold: true, color: SeriesSessionsPdfService.PRIMARY },
        subtitle: { fontSize: 12, color: SeriesSessionsPdfService.GREY, margin: [0, 4, 0, 0] },
        sectionHeader: { fontSize: 14, bold: true, color: SeriesSessionsPdfService.PRIMARY, margin: [0, 12, 0, 8] },
        tableHeader: {
          bold: true, fontSize: 11, color: '#ffffff',
          fillColor: SeriesSessionsPdfService.PRIMARY_SOFT, alignment: 'center', margin: [4, 6, 4, 6]
        }
      },
      defaultStyle: { fontSize: 11 },
      footer: (current: number, total: number): Content => ({
        columns: [
          {
            text: this.t('SERIES_SESSIONS_PDF.GENERATED_ON', { date: new Date().toLocaleString(pdfLocale(this.translate)) }),
            fontSize: 8, color: SeriesSessionsPdfService.GREY, margin: [40, 8, 0, 0]
          },
          {
            text: this.t('PROFILE_PDF.PAGE', { current, total }),
            alignment: 'right', fontSize: 8, color: SeriesSessionsPdfService.GREY, margin: [0, 8, 40, 0]
          }
        ]
      })
    };
    return doc;
  }

  private buildHeader(info: SeriesPdfInfo): Content {
    return {
      columns: [
        {
          stack: [
            { text: info.name || this.t('SERIES_SESSIONS_PDF.TITLE_FALLBACK'), style: 'header' },
            {
              text: info.groupName ? this.t('SERIES_SESSIONS_PDF.GROUP_SUBTITLE', { name: info.groupName }) : '',
              style: 'subtitle'
            }
          ]
        },
        {
          text: new Date().toLocaleDateString(pdfLocale(this.translate)),
          alignment: 'right', fontSize: 9, color: SeriesSessionsPdfService.GREY, margin: [0, 8, 0, 0]
        }
      ]
    };
  }

  private buildDivider(): Content {
    return {
      canvas: [{ type: 'line', x1: 0, y1: 0, x2: 515, y2: 0, lineWidth: 1.5, lineColor: SeriesSessionsPdfService.PRIMARY_SOFT }],
      margin: [0, 8, 0, 4]
    };
  }

  private buildSeriesInfo(info: SeriesPdfInfo): Content {
    const row = (label: string, value: string): TableCell[] => ([
      { text: label, bold: true, fillColor: '#f1f5f9', margin: [6, 5, 6, 5] },
      { text: value || '—', margin: [6, 5, 6, 5] }
    ]);

    return {
      table: {
        widths: ['25%', '25%', '25%', '25%'],
        body: [
          [
            ...row(this.t('SERIES_SESSIONS_PDF.GROUP'), info.groupName || '—'),
            ...row(this.t('SERIES_SESSIONS_PDF.PLANNED'), this.num(info.totalSessions))
          ],
          [
            ...row(this.t('SERIES_SESSIONS_PDF.CREATED'), this.num(info.numberOfSessionsCreated)),
            ...row(this.t('SERIES_SESSIONS_PDF.COMPLETED'), this.num(info.sessionsCompleted))
          ],
          [
            ...row(this.t('SERIES_SESSIONS_PDF.START'), this.fmtDate(info.serieTimeStart)),
            ...row(this.t('SERIES_SESSIONS_PDF.END'), this.fmtDate(info.serieTimeEnd))
          ]
        ]
      },
      layout: {
        hLineColor: () => '#e2e8f0',
        vLineColor: () => '#e2e8f0',
        hLineWidth: () => 0.5,
        vLineWidth: () => 0.5
      },
      margin: [0, 6, 0, 4]
    };
  }

  private buildSessionsTable(sessions: SeriesSessionRow[]): Content {
    const header: TableCell[] = [
      { text: this.t('SERIES_SESSIONS_PDF.COL_NUMBER'), style: 'tableHeader' },
      { text: this.t('SERIES_SESSIONS_PDF.COL_TITLE'), style: 'tableHeader' },
      { text: this.t('SERIES_SESSIONS_PDF.COL_TYPE'), style: 'tableHeader' },
      { text: this.t('SERIES_SESSIONS_PDF.COL_TEACHER'), style: 'tableHeader' },
      { text: this.t('SERIES_SESSIONS_PDF.COL_ROOM'), style: 'tableHeader' },
      { text: this.t('SERIES_SESSIONS_PDF.COL_DATE'), style: 'tableHeader' },
      { text: this.t('SERIES_SESSIONS_PDF.COL_STATUS'), style: 'tableHeader' }
    ];

    const body: TableCell[][] = [header];

    if (!sessions || sessions.length === 0) {
      body.push([
        { text: this.t('SERIES_SESSIONS_PDF.NO_SESSIONS'), italics: true, color: SeriesSessionsPdfService.GREY, colSpan: 7, alignment: 'center', margin: [4, 10, 4, 10] },
        {}, {}, {}, {}, {}, {}
      ]);
    } else {
      sessions.forEach((s, i) => {
        const fill = i % 2 === 0 ? '#f8fafc' : '#ffffff';
        const finished = !!s.isFinished;
        body.push([
          { text: String(i + 1), alignment: 'center', fillColor: fill, margin: [4, 8, 4, 8] },
          { text: s.title || '—', fillColor: fill, margin: [6, 8, 6, 8] },
          { text: s.sessionType || '—', fillColor: fill, margin: [6, 8, 6, 8] },
          { text: s.teacherName || '—', fillColor: fill, margin: [6, 8, 6, 8] },
          { text: s.roomName || '—', fillColor: fill, margin: [6, 8, 6, 8] },
          { text: this.fmtDate(s.sessionTimeStart), fillColor: fill, margin: [6, 8, 6, 8] },
          {
            text: this.t(finished ? 'SERIES_SESSIONS_PDF.VALIDATED' : 'SERIES_SESSIONS_PDF.SCHEDULED'),
            bold: true,
            color: finished ? SeriesSessionsPdfService.GREEN : SeriesSessionsPdfService.AMBER,
            alignment: 'center',
            fillColor: fill,
            margin: [4, 8, 4, 8]
          }
        ]);
      });
    }

    return {
      table: {
        headerRows: 1,
        widths: ['auto', '*', 'auto', 'auto', 'auto', 'auto', 'auto'],
        body
      },
      layout: {
        hLineColor: () => '#e2e8f0',
        vLineColor: () => '#e2e8f0',
        hLineWidth: () => 0.5,
        vLineWidth: () => 0.5
      },
      margin: [0, 0, 0, 14]
    };
  }

  private buildSignatures(): Content {
    return {
      columns: [
        { text: this.t('SERIES_SESSIONS_PDF.TEACHER_SIGNATURE'), margin: [0, 40, 0, 0] },
        { text: this.t('PROFILE_PDF.ADMINISTRATION'), alignment: 'right', margin: [0, 40, 0, 0] }
      ]
    };
  }

  private num(v: number | null | undefined): string {
    return v === null || v === undefined ? '—' : String(v);
  }

  private fmtDate(value: string | Date | null | undefined): string {
    if (!value) { return '—'; }
    const d = new Date(value);
    if (isNaN(d.getTime())) { return '—'; }
    return d.toLocaleString(pdfLocale(this.translate), {
      day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit'
    });
  }
}
