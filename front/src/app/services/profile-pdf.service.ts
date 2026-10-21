import { Injectable, inject } from '@angular/core';
import pdfMake from 'pdfmake/build/pdfmake';
import pdfFonts from 'pdfmake/build/vfs_fonts';
import { Content, TDocumentDefinitions } from 'pdfmake/interfaces';
import { TranslateService } from '@ngx-translate/core';
import { bindPrintLanguage, pdfInstant, pdfLanguage, pdfLocale, withPrintLanguage } from '../shared/pdf-i18n';
import { PdfOutputService } from './pdf-output.service';

export interface PdfInfoRow {
  label: string;
  value: string | number | null | undefined;
}

/** Bloc de tableau : titre, colonnes, lignes. */
export interface PdfTable {
  title: string;
  columns: string[];
  rows: (string | number | null | undefined)[][];
  /** Note affichée sous le tableau (méthode de calcul, avertissement…). */
  note?: string;
}

/**
 * Page supplémentaire du document, démarrée sur une nouvelle feuille.
 *
 * Sert à imprimer les volets annexes d'une fiche (encaissements, séries) sans les mélanger
 * avec la fiche elle-même : chaque volet reste lisible et détachable.
 */
export interface PdfExtraPage {
  heading: string;
  subtitle?: string;
  sections?: { heading: string; rows: PdfInfoRow[] }[];
  tables?: PdfTable[];
  /** Message affiché lorsque la page n'a aucune donnée à présenter. */
  emptyMessage?: string;
}

/** Contenu d'une fiche : titre, sous-titre, sections, liste, tableau et volets annexes. */
export interface ProfilePdfOptions {
  title: string;
  subtitle?: string;
  sections: { heading: string; rows: PdfInfoRow[] }[];
  listTitle?: string;
  listItems?: string[];
  tableTitle?: string;
  tableColumns?: string[];
  tableRows?: (string | number | null | undefined)[][];
  /** Volets annexes, chacun sur sa propre page. */
  extraPages?: PdfExtraPage[];
}

@Injectable({ providedIn: 'root' })
export class ProfilePdfService {

  private readonly output = inject(PdfOutputService);

  constructor(private translate: TranslateService) {
    (pdfMake as any).vfs = pdfFonts.pdfMake.vfs;
  }

  /**
   * Génère une fiche PDF générique : titre, sous-titre, sections d'infos
   * (label/valeur), une liste optionnelle et/ou un tableau optionnel.
   *
   * <p>En arabe, la même fiche est imprimée par le navigateur (boîte d'impression, « Enregistrer en
   * PDF ») au lieu de s'ouvrir dans un onglet.</p>
   */
  generateProfilePdf(options: ProfilePdfOptions): void {
    const lang = pdfLanguage(this.translate);
    const doc = withPrintLanguage(lang, () => this.buildDocument(options));
    if (lang === 'ar') {
      this.output.printHtml(doc, options.title).catch((err: unknown) => console.error('Impression de la fiche impossible :', err));
      return;
    }

    pdfMake.createPdf(bindPrintLanguage(this.translate, doc, lang)).getBlob((blob) => {
      window.open(URL.createObjectURL(blob), '_blank');
    });
  }

  /** Définition de la fiche, publique pour être vérifiée sans passer par l'impression. */
  buildDocument(options: ProfilePdfOptions): TDocumentDefinitions {
    // Libellés et date dans la langue du document (voir pdfLanguage).
    const t = (key: string, params?: object) => pdfInstant(this.translate, key, params);
    const content: Content[] = [
      {
        columns: [
          { text: options.title, style: 'header' },
          { text: new Date().toLocaleDateString(pdfLocale(this.translate)), alignment: 'right', margin: [0, 8, 0, 0] }
        ]
      }
    ];

    if (options.subtitle) {
      content.push({ text: options.subtitle, style: 'subtitle' });
    }

    content.push({
      canvas: [{ type: 'line', x1: 0, y1: 0, x2: 515, y2: 0, lineWidth: 1.5, lineColor: '#6366f1' }],
      margin: [0, 8, 0, 12]
    });

    for (const section of options.sections) {
      content.push(...this.infoSection(section.heading, section.rows));
    }

    if (options.listTitle && options.listItems && options.listItems.length > 0) {
      content.push({ text: options.listTitle, style: 'sectionHeader' });
      content.push({
        ul: options.listItems,
        margin: [0, 0, 0, 14]
      });
    }

    // Tableau optionnel (ex. liste des étudiants d'un groupe)
    if (options.tableTitle && options.tableColumns && options.tableRows && options.tableRows.length > 0) {
      content.push(...this.tableBlock({
        title: options.tableTitle,
        columns: options.tableColumns,
        rows: options.tableRows
      }));
    }

    // Volets annexes : chacun démarre sur une nouvelle page pour rester détachable.
    for (const page of options.extraPages ?? []) {
      content.push(...this.extraPage(page));
    }

    content.push({
      columns: [
        { text: t('PROFILE_PDF.SIGNATURE'), margin: [0, 50, 0, 0] },
        { text: t('PROFILE_PDF.ADMINISTRATION'), alignment: 'right', margin: [0, 50, 0, 0] }
      ]
    });

    const doc: TDocumentDefinitions = {
      content,
      styles: {
        header: { fontSize: 20, bold: true, color: '#4f46e5' },
        pageHeader: { fontSize: 18, bold: true, color: '#4f46e5' },
        subtitle: { fontSize: 13, color: '#64748b', margin: [0, 4, 0, 0] },
        sectionHeader: { fontSize: 14, bold: true, color: '#4f46e5', margin: [0, 6, 0, 8] },
        note: { fontSize: 9, color: '#64748b', italics: true },
        tableHeader: { bold: true, fontSize: 11, color: '#ffffff', fillColor: '#6366f1', alignment: 'center', margin: [6, 6, 6, 6] }
      },
      defaultStyle: { fontSize: 11 },
      footer: (current: number, total: number): Content => ({
        text: t('PROFILE_PDF.PAGE', { current, total }),
        alignment: 'center',
        fontSize: 9,
        margin: [0, 8, 0, 0]
      })
    };
    return doc;
  }

  /** Section label/valeur, rendue en tableau à deux colonnes. */
  private infoSection(heading: string, rows: PdfInfoRow[]): Content[] {
    return [
      { text: heading, style: 'sectionHeader' },
      {
        table: {
          widths: ['35%', '65%'],
          body: rows.map(r => ([
            { text: r.label, bold: true, fillColor: '#f1f5f9', margin: [6, 5, 6, 5] },
            { text: this.fmt(r.value), margin: [6, 5, 6, 5] }
          ]))
        },
        layout: this.gridLayout(),
        margin: [0, 0, 0, 14]
      }
    ];
  }

  /** Tableau à en-tête, lignes alternées et première colonne centrée. */
  private tableBlock(table: PdfTable): Content[] {
    const header = table.columns.map(c => ({ text: c, style: 'tableHeader' }));
    const body = [
      header,
      ...table.rows.map((row, i) => row.map((cell, ci) => ({
        text: this.fmt(cell) === '—' ? '' : this.fmt(cell),
        margin: [6, 8, 6, 8] as [number, number, number, number],
        fillColor: i % 2 === 0 ? '#f8fafc' : '#ffffff',
        alignment: ci === 0 ? 'center' : 'left'
      })))
    ];

    const blocks: Content[] = [
      { text: table.title, style: 'sectionHeader' },
      {
        table: {
          headerRows: 1,
          widths: table.columns.map((_, i) => (i === 0 ? 'auto' : '*')),
          body
        },
        layout: this.gridLayout(),
        margin: [0, 0, 0, table.note ? 4 : 16]
      }
    ];

    if (table.note) {
      blocks.push({ text: table.note, style: 'note', margin: [0, 0, 0, 16] });
    }
    return blocks;
  }

  /** Volet annexe démarré sur une nouvelle page. */
  private extraPage(page: PdfExtraPage): Content[] {
    const blocks: Content[] = [
      { text: page.heading, style: 'pageHeader', pageBreak: 'before' }
    ];

    if (page.subtitle) {
      blocks.push({ text: page.subtitle, style: 'subtitle', margin: [0, 0, 0, 4] });
    }

    blocks.push({
      canvas: [{ type: 'line', x1: 0, y1: 0, x2: 515, y2: 0, lineWidth: 1.5, lineColor: '#6366f1' }],
      margin: [0, 8, 0, 12]
    });

    for (const section of page.sections ?? []) {
      blocks.push(...this.infoSection(section.heading, section.rows));
    }

    const tables = (page.tables ?? []).filter(t => t.rows.length > 0);
    for (const table of tables) {
      blocks.push(...this.tableBlock(table));
    }

    const hasContent = (page.sections?.length ?? 0) > 0 || tables.length > 0;
    if (!hasContent && page.emptyMessage) {
      blocks.push({ text: page.emptyMessage, style: 'note', margin: [0, 0, 0, 16] });
    }

    return blocks;
  }

  private gridLayout() {
    return {
      hLineColor: () => '#e2e8f0',
      vLineColor: () => '#e2e8f0',
      hLineWidth: () => 0.5,
      vLineWidth: () => 0.5
    };
  }

  private fmt(v: string | number | null | undefined): string {
    return v === null || v === undefined || v === '' ? '—' : String(v);
  }
}
