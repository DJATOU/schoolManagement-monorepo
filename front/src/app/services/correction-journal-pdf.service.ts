import { Injectable } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Content, TableCell, TDocumentDefinitions } from 'pdfmake/interfaces';

import { CorrectionJournal, JournalEntry } from '../models/correction/journal';
import { formatCalendarDay } from '../utils/calendar-day';
import { formatJournalInstant } from '../utils/journal-format';
import { pdfSafeText } from '../utils/pdf-print';
import { PdfOutputService } from './pdf-output.service';

/**
 * Journal des corrections d'un élève, imprimé sur la période choisie (spec admin-corrections, D.5 ;
 * exigence 12.4) : ce qu'on remet à un parent qui conteste l'historique de son enfant.
 *
 * <p>Une ligne par entrée, du plus récent au plus ancien, comme à l'écran : date, correction, effet
 * sur le dû, Motif, auteur. Le document imprime exactement le Journal reçu : la période, l'ordre et
 * les phrases sont ceux du serveur.</p>
 *
 * <p>Paysage A4 : la phrase d'une correction et son effet, sur plusieurs séries, tiennent sans
 * écraser la date.</p>
 */
@Injectable({ providedIn: 'root' })
export class CorrectionJournalPdfService {

  /** Logo de l'école, partagé avec les autres documents imprimés de l'application. */
  private static readonly LOGO_URL = 'assets/succes_assistance.png';

  private static readonly PRIMARY = '#4f46e5';
  private static readonly PRIMARY_SOFT = '#6366f1';
  private static readonly GREY = '#64748b';

  constructor(private translate: TranslateService, private output: PdfOutputService) {}

  /** Compose le Journal et ouvre la boîte d'impression ; sans logo s'il ne se charge pas. */
  async print(journal: CorrectionJournal, printedAt: Date = new Date()): Promise<void> {
    const logo = await this.output.imageDataUrl(CorrectionJournalPdfService.LOGO_URL);
    this.output.print(this.buildDocument(journal, logo, printedAt), this.fileName(journal));
  }

  /**
   * Définition du document, publique pour être vérifiée sans passer par l'impression : c'est ce que
   * la famille aura entre les mains.
   */
  buildDocument(journal: CorrectionJournal, logo: string, printedAt: Date): TDocumentDefinitions {
    const printed = this.instant(printedAt);
    return {
      pageSize: 'A4',
      pageOrientation: 'landscape',
      pageMargins: [36, 40, 36, 44],
      info: { title: this.t('journal.title') + ' — ' + journal.studentName },
      content: [
        this.header(journal, logo),
        this.divider(),
        this.table(journal.entries)
      ],
      styles: {
        title: { fontSize: 18, bold: true, color: CorrectionJournalPdfService.PRIMARY },
        subtitle: { fontSize: 11, color: CorrectionJournalPdfService.GREY, margin: [0, 3, 0, 0] },
        tableHeader: {
          bold: true, fontSize: 9.5, color: '#ffffff', fillColor: CorrectionJournalPdfService.PRIMARY_SOFT,
          margin: [4, 5, 4, 5]
        },
        category: { fontSize: 8, bold: true, color: CorrectionJournalPdfService.PRIMARY },
        muted: { color: CorrectionJournalPdfService.GREY }
      },
      defaultStyle: { fontSize: 9.5 },
      footer: (current: number, total: number): Content => ({
        columns: [
          { text: this.t('journal.generatedOn', { date: printed }), fontSize: 8, color: CorrectionJournalPdfService.GREY,
            margin: [36, 12, 0, 0] },
          { text: this.t('journal.page', { current, total }), alignment: 'right', fontSize: 8,
            color: CorrectionJournalPdfService.GREY, margin: [0, 12, 36, 0] }
        ]
      })
    };
  }

  /** « Du 01/01/2030 au 31/01/2030 », « Depuis le … », « Jusqu'au … », « Toute période ». */
  period(journal: CorrectionJournal): string {
    const from = formatCalendarDay(journal.from);
    const to = formatCalendarDay(journal.to);
    if (journal.from && journal.to) {
      return this.t('journal.period.between', { from, to });
    }
    if (journal.from) {
      return this.t('journal.period.from', { from });
    }
    return journal.to ? this.t('journal.period.to', { to }) : this.t('journal.period.all');
  }

  /** Logo à gauche s'il est chargé, titre, élève et période, nombre d'entrées à droite. */
  private header(journal: CorrectionJournal, logo: string): Content {
    const title: Content = {
      stack: [
        { text: this.t('journal.title'), style: 'title' },
        { text: pdfSafeText(journal.studentName), style: 'subtitle', bold: true },
        { text: this.period(journal), style: 'subtitle' }
      ]
    };
    const count: Content = {
      text: this.t('journal.count', { count: journal.entries.length }), alignment: 'right',
      fontSize: 10, color: CorrectionJournalPdfService.GREY, margin: [0, 6, 0, 0]
    };
    return {
      columns: logo
        ? [{ image: logo, width: 46, margin: [0, 0, 12, 0] }, title, count]
        : [title, count]
    };
  }

  private divider(): Content {
    return {
      canvas: [{ type: 'line', x1: 0, y1: 0, x2: 770, y2: 0, lineWidth: 1.2,
        lineColor: CorrectionJournalPdfService.PRIMARY_SOFT }],
      margin: [0, 8, 0, 10]
    };
  }

  private table(entries: JournalEntry[]): Content {
    const header: TableCell[] = ['date', 'correction', 'effect', 'reason', 'by']
      .map(column => ({ text: this.t('journal.columns.' + column), style: 'tableHeader' }));
    const body: TableCell[][] = [header];
    if (entries.length === 0) {
      body.push([
        { text: this.t('journal.empty'), italics: true, style: 'muted', colSpan: 5, alignment: 'center',
          margin: [4, 12, 4, 12] },
        {}, {}, {}, {}
      ]);
    }
    entries.forEach((entry, index) => body.push(this.row(entry, index % 2 === 0 ? '#f8fafc' : '#ffffff')));
    return {
      table: { headerRows: 1, dontBreakRows: true, widths: [62, '*', 190, 120, 62], body },
      layout: {
        hLineColor: () => '#e2e8f0',
        vLineColor: () => '#e2e8f0',
        hLineWidth: () => 0.5,
        vLineWidth: () => 0.5
      }
    };
  }

  private row(entry: JournalEntry, fill: string): TableCell[] {
    const cell = (content: Record<string, unknown>): TableCell => ({ ...content, fillColor: fill, margin: [4, 5, 4, 5] });
    return [
      cell({ text: formatJournalInstant(entry.performedAt) }),
      cell({ stack: [
        { text: this.t('journal.category.' + entry.category).toUpperCase(), style: 'category' },
        { text: pdfSafeText(entry.description) }
      ] }),
      cell({ stack: this.effect(entry) }),
      cell({ stack: this.reason(entry) }),
      cell({ text: pdfSafeText(entry.performedBy) })
    ];
  }

  /**
   * Une ligne par série ; « Sans effet sur le dû » pour une justification, qui est documentaire ;
   * un tiret quand l'effet n'a pas été mesuré.
   */
  private effect(entry: JournalEntry): Content[] {
    if (entry.amountEffect) {
      return entry.amountEffect.split(' ; ').map(part => ({ text: pdfSafeText(part) }));
    }
    return [{ text: entry.category === 'JUSTIFICATION' ? this.t('journal.noEffect') : '—', style: 'muted' }];
  }

  /** Motif traduit, puis son texte ou le commentaire saisi ; un tiret s'il n'y a ni l'un ni l'autre. */
  private reason(entry: JournalEntry): Content[] {
    const lines: Content[] = [];
    if (entry.reasonType) {
      lines.push({ text: this.t('correction.reason.' + entry.reasonType), bold: true });
    }
    if (entry.reasonText) {
      lines.push({ text: pdfSafeText(entry.reasonText), italics: true });
    }
    return lines.length > 0 ? lines : [{ text: '—', style: 'muted' }];
  }

  /** « journal_Amine_Belkacem_2030-01-01_2030-01-31.pdf ». */
  fileName(journal: CorrectionJournal): string {
    const slug = journal.studentName.trim().replace(/\s+/g, '_') || String(journal.studentId);
    const period = [journal.from, journal.to].filter(Boolean).join('_');
    return `${this.t('journal.fileName')}_${slug}${period ? '_' + period : ''}.pdf`;
  }

  /** Heure d'édition, au poste : `dd/MM/yyyy HH:mm`. */
  private instant(date: Date): string {
    const pad = (value: number): string => String(value).padStart(2, '0');
    return `${pad(date.getDate())}/${pad(date.getMonth() + 1)}/${date.getFullYear()} ${pad(date.getHours())}:`
      + pad(date.getMinutes());
  }

  private t(key: string, params?: Record<string, unknown>): string {
    return pdfSafeText(this.translate.instant(key, params));
  }
}
