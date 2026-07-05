import { Injectable } from '@angular/core';
import pdfMake from 'pdfmake/build/pdfmake';
import pdfFonts from 'pdfmake/build/vfs_fonts';
import { Content, TDocumentDefinitions } from 'pdfmake/interfaces';

export interface PdfInfoRow {
  label: string;
  value: string | number | null | undefined;
}

@Injectable({ providedIn: 'root' })
export class ProfilePdfService {

  constructor() {
    (pdfMake as any).vfs = pdfFonts.pdfMake.vfs;
  }

  /**
   * Génère une fiche PDF générique : titre, sous-titre, sections d'infos
   * (label/valeur), une liste optionnelle et/ou un tableau optionnel.
   */
  generateProfilePdf(options: {
    title: string;
    subtitle?: string;
    sections: { heading: string; rows: PdfInfoRow[] }[];
    listTitle?: string;
    listItems?: string[];
    tableTitle?: string;
    tableColumns?: string[];
    tableRows?: (string | number | null | undefined)[][];
  }): void {
    const content: Content[] = [
      {
        columns: [
          { text: options.title, style: 'header' },
          { text: new Date().toLocaleDateString('fr-FR'), alignment: 'right', margin: [0, 8, 0, 0] }
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
      content.push({ text: section.heading, style: 'sectionHeader' });
      content.push({
        table: {
          widths: ['35%', '65%'],
          body: section.rows.map(r => ([
            { text: r.label, bold: true, fillColor: '#f1f5f9', margin: [6, 5, 6, 5] },
            { text: this.fmt(r.value), margin: [6, 5, 6, 5] }
          ]))
        },
        layout: {
          hLineColor: () => '#e2e8f0',
          vLineColor: () => '#e2e8f0',
          hLineWidth: () => 0.5,
          vLineWidth: () => 0.5
        },
        margin: [0, 0, 0, 14]
      });
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
      content.push({ text: options.tableTitle, style: 'sectionHeader' });

      const header = options.tableColumns.map(c => ({ text: c, style: 'tableHeader' }));
      const body = [
        header,
        ...options.tableRows.map((row, i) => row.map((cell, ci) => ({
          text: this.fmt(cell) === '—' ? '' : this.fmt(cell),
          margin: [6, 8, 6, 8] as [number, number, number, number],
          fillColor: i % 2 === 0 ? '#f8fafc' : '#ffffff',
          alignment: ci === 0 ? 'center' : 'left'
        })))
      ];

      content.push({
        table: {
          headerRows: 1,
          widths: options.tableColumns.map((_, i) => (i === 0 ? 'auto' : '*')),
          body
        },
        layout: {
          hLineColor: () => '#e2e8f0',
          vLineColor: () => '#e2e8f0',
          hLineWidth: () => 0.5,
          vLineWidth: () => 0.5
        },
        margin: [0, 0, 0, 16]
      });
    }

    content.push({
      columns: [
        { text: 'Signature : ________________________', margin: [0, 50, 0, 0] },
        { text: 'Administration : ________________________', alignment: 'right', margin: [0, 50, 0, 0] }
      ]
    });

    const doc: TDocumentDefinitions = {
      content,
      styles: {
        header: { fontSize: 20, bold: true, color: '#4f46e5' },
        subtitle: { fontSize: 13, color: '#64748b', margin: [0, 4, 0, 0] },
        sectionHeader: { fontSize: 14, bold: true, color: '#4f46e5', margin: [0, 6, 0, 8] },
        tableHeader: { bold: true, fontSize: 11, color: '#ffffff', fillColor: '#6366f1', alignment: 'center', margin: [6, 6, 6, 6] }
      },
      defaultStyle: { fontSize: 11 },
      footer: (current: number, total: number): Content => ({
        text: `Page ${current} / ${total}`,
        alignment: 'center',
        fontSize: 9,
        margin: [0, 8, 0, 0]
      })
    };

    pdfMake.createPdf(doc).getBlob((blob) => {
      window.open(URL.createObjectURL(blob), '_blank');
    });
  }

  private fmt(v: string | number | null | undefined): string {
    return v === null || v === undefined || v === '' ? '—' : String(v);
  }
}
