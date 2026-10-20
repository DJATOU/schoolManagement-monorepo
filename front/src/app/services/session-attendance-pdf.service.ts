import { Injectable } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import pdfMake from 'pdfmake/build/pdfmake';
import pdfFonts from 'pdfmake/build/vfs_fonts';
import { Content, TableCell, TDocumentDefinitions } from 'pdfmake/interfaces';
import { pdfInstant, pdfLocale } from '../shared/pdf-i18n';

/** Ligne étudiant utilisée pour la feuille de présence. */
export interface SessionAttendanceStudentRow {
  fullName: string;
  isPresent?: boolean;
  isJustified?: boolean;
  isCatchUp?: boolean;
  note?: string;
}

/** Métadonnées de la session imprimée. */
export interface SessionAttendanceInfo {
  title?: string;
  sessionType?: string;
  groupName?: string;
  roomName?: string;
  teacherName?: string;
  sessionTimeStart?: string | Date | null;
  sessionTimeEnd?: string | Date | null;
  isFinished?: boolean;
}

/**
 * Service d'impression PDF de la feuille de présence d'une session.
 *
 * La feuille peut être imprimée AVANT validation (colonnes de présence vides,
 * cases à cocher manuscrites) ou APRÈS validation (présence/justification
 * remplies automatiquement). Le rendu vise un design soigné et lisible.
 */
@Injectable({ providedIn: 'root' })
export class SessionAttendancePdfService {

  // Palette alignée sur les autres exports PDF de l'application.
  private static readonly PRIMARY = '#4f46e5';
  private static readonly PRIMARY_SOFT = '#6366f1';
  private static readonly GREEN = '#32a852';
  private static readonly AMBER = '#f59e0b';
  private static readonly RED = '#e60000';
  private static readonly GREY = '#64748b';

  constructor(private translate: TranslateService) {
    (pdfMake as any).vfs = pdfFonts.pdfMake.vfs;
  }

  /** Libellé du document : langue de l'interface en français / anglais, français sinon. */
  private t(key: string, params?: object): string {
    return pdfInstant(this.translate, key, params);
  }

  /**
   * Génère et ouvre le PDF de la feuille de présence.
   * @param info métadonnées de la session
   * @param students liste des étudiants (avec présence si déjà validée)
   */
  generateAttendanceSheet(info: SessionAttendanceInfo, students: SessionAttendanceStudentRow[]): void {
    const validated = !!info.isFinished;

    const content: Content[] = [
      this.buildHeader(info, validated),
      this.buildDivider(),
      this.buildSessionInfo(info),
      { text: this.t('SESSION_ATTENDANCE_PDF.SECTION_TITLE'), style: 'sectionHeader' },
      this.buildStudentsTable(students, validated),
      this.buildLegend(),
      this.buildSummary(students, validated),
      this.buildSignatures()
    ];

    const doc: TDocumentDefinitions = {
      content,
      pageMargins: [40, 60, 40, 60],
      styles: {
        header: { fontSize: 20, bold: true, color: SessionAttendancePdfService.PRIMARY },
        subtitle: { fontSize: 12, color: SessionAttendancePdfService.GREY, margin: [0, 4, 0, 0] },
        sectionHeader: { fontSize: 14, bold: true, color: SessionAttendancePdfService.PRIMARY, margin: [0, 12, 0, 8] },
        tableHeader: {
          bold: true, fontSize: 11, color: '#ffffff',
          fillColor: SessionAttendancePdfService.PRIMARY_SOFT, alignment: 'center', margin: [4, 6, 4, 6]
        },
        badge: { fontSize: 9, bold: true, color: '#ffffff' }
      },
      defaultStyle: { fontSize: 11 },
      footer: (current: number, total: number): Content => ({
        columns: [
          {
            text: this.t('SESSION_ATTENDANCE_PDF.GENERATED_ON', { date: new Date().toLocaleString(pdfLocale(this.translate)) }),
            fontSize: 8, color: SessionAttendancePdfService.GREY, margin: [40, 8, 0, 0]
          },
          {
            text: this.t('PROFILE_PDF.PAGE', { current, total }),
            alignment: 'right', fontSize: 8, color: SessionAttendancePdfService.GREY, margin: [0, 8, 40, 0]
          }
        ]
      })
    };

    pdfMake.createPdf(doc).getBlob((blob) => {
      window.open(URL.createObjectURL(blob), '_blank');
    });
  }

  /** Bandeau de titre avec l'état (validée / non validée). */
  private buildHeader(info: SessionAttendanceInfo, validated: boolean): Content {
    const statusText = this.t(validated ? 'SESSION_ATTENDANCE_PDF.VALIDATED' : 'SESSION_ATTENDANCE_PDF.NOT_VALIDATED');
    const statusColor = validated ? SessionAttendancePdfService.GREEN : SessionAttendancePdfService.AMBER;

    return {
      columns: [
        {
          stack: [
            { text: info.title || this.t('SESSION_ATTENDANCE_PDF.TITLE_FALLBACK'), style: 'header' },
            { text: info.sessionType || '', style: 'subtitle' }
          ]
        },
        {
          width: 'auto',
          stack: [
            {
              table: {
                body: [[{ text: statusText, style: 'badge', fillColor: statusColor, margin: [8, 4, 8, 4] }]]
              },
              layout: 'noBorders'
            },
            {
              text: new Date().toLocaleDateString(pdfLocale(this.translate)),
              alignment: 'right', fontSize: 9, color: SessionAttendancePdfService.GREY, margin: [0, 6, 0, 0]
            }
          ],
          alignment: 'right'
        }
      ]
    };
  }

  private buildDivider(): Content {
    return {
      canvas: [{ type: 'line', x1: 0, y1: 0, x2: 515, y2: 0, lineWidth: 1.5, lineColor: SessionAttendancePdfService.PRIMARY_SOFT }],
      margin: [0, 8, 0, 4]
    };
  }

  /** Bloc informations de la session (2 colonnes label/valeur). */
  private buildSessionInfo(info: SessionAttendanceInfo): Content {
    const row = (label: string, value: string): TableCell[] => ([
      { text: label, bold: true, fillColor: '#f1f5f9', margin: [6, 5, 6, 5] },
      { text: value || '—', margin: [6, 5, 6, 5] }
    ]);

    return {
      table: {
        widths: ['22%', '28%', '22%', '28%'],
        body: [
          [
            ...row(this.t('SESSION_ATTENDANCE_PDF.GROUP'), info.groupName || '—'),
            ...row(this.t('SESSION_ATTENDANCE_PDF.ROOM'), info.roomName || '—')
          ],
          [
            ...row(this.t('SESSION_ATTENDANCE_PDF.TEACHER'), info.teacherName || '—'),
            ...row(this.t('SESSION_ATTENDANCE_PDF.TYPE'), info.sessionType || '—')
          ],
          [
            ...row(this.t('SESSION_ATTENDANCE_PDF.START'), this.fmtDate(info.sessionTimeStart)),
            ...row(this.t('SESSION_ATTENDANCE_PDF.END'), this.fmtDate(info.sessionTimeEnd))
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

  /** Tableau des étudiants. Présence/justification remplies si validée, sinon cases vides. */
  private buildStudentsTable(students: SessionAttendanceStudentRow[], validated: boolean): Content {
    const header = [
      { text: this.t('SESSION_ATTENDANCE_PDF.COL_NUMBER'), style: 'tableHeader' },
      { text: this.t('SESSION_ATTENDANCE_PDF.COL_STUDENT'), style: 'tableHeader' },
      { text: this.t('SESSION_ATTENDANCE_PDF.COL_PRESENCE'), style: 'tableHeader' },
      { text: this.t('SESSION_ATTENDANCE_PDF.COL_JUSTIFIED'), style: 'tableHeader' },
      { text: this.t('SESSION_ATTENDANCE_PDF.COL_NOTE'), style: 'tableHeader' }
    ];

    const body: TableCell[][] = [header];

    if (!students || students.length === 0) {
      body.push([
        { text: this.t('SESSION_ATTENDANCE_PDF.NO_STUDENTS'), italics: true, color: SessionAttendancePdfService.GREY, colSpan: 5, alignment: 'center', margin: [4, 10, 4, 10] },
        {}, {}, {}, {}
      ]);
    } else {
      students.forEach((s, i) => {
        const fill = i % 2 === 0 ? '#f8fafc' : '#ffffff';
        const nameText = s.isCatchUp ? this.t('SESSION_ATTENDANCE_PDF.CATCH_UP_NAME', { name: s.fullName }) : s.fullName;

        body.push([
          { text: String(i + 1), alignment: 'center', fillColor: fill, margin: [4, 8, 4, 8] },
          { text: nameText, fillColor: fill, margin: [6, 8, 6, 8] },
          this.presenceCell(s, validated, fill),
          this.justifiedCell(s, validated, fill),
          { text: s.note || (validated ? '—' : ''), fillColor: fill, margin: [6, 8, 6, 8], color: SessionAttendancePdfService.GREY }
        ]);
      });
    }

    return {
      table: {
        headerRows: 1,
        widths: ['auto', '*', 'auto', 'auto', '30%'],
        body
      },
      layout: {
        hLineColor: () => '#e2e8f0',
        vLineColor: () => '#e2e8f0',
        hLineWidth: () => 0.5,
        vLineWidth: () => 0.5
      },
      margin: [0, 0, 0, 12]
    };
  }

  /** Cellule de présence : statut coloré si validée, cellule vide sinon (à remplir à la main). */
  private presenceCell(s: SessionAttendanceStudentRow, validated: boolean, fill: string): TableCell {
    if (!validated) {
      return { text: '', fillColor: fill, margin: [4, 12, 4, 12] };
    }
    const present = !!s.isPresent;
    return {
      text: this.t(present ? 'SESSION_ATTENDANCE_PDF.PRESENT' : 'SESSION_ATTENDANCE_PDF.ABSENT'),
      alignment: 'center',
      bold: true,
      color: present ? SessionAttendancePdfService.GREEN : SessionAttendancePdfService.RED,
      fillColor: fill,
      margin: [4, 8, 4, 8]
    };
  }

  /** Cellule de justification : uniquement pertinente pour un absent. Vide si non validée. */
  private justifiedCell(s: SessionAttendanceStudentRow, validated: boolean, fill: string): TableCell {
    if (!validated) {
      return { text: '', fillColor: fill, margin: [4, 12, 4, 12] };
    }
    if (s.isPresent) {
      return { text: '—', alignment: 'center', color: SessionAttendancePdfService.GREY, fillColor: fill, margin: [4, 8, 4, 8] };
    }
    const justified = !!s.isJustified;
    return {
      text: this.t(justified ? 'common.yes' : 'common.no'),
      alignment: 'center',
      bold: true,
      color: justified ? SessionAttendancePdfService.AMBER : SessionAttendancePdfService.RED,
      fillColor: fill,
      margin: [4, 8, 4, 8]
    };
  }

  /** Légende des couleurs / statuts (rendue via un petit tableau sans bordures). */
  private buildLegend(): Content {
    const cell = (color: string, label: string): TableCell[] => ([
      { text: '', fillColor: color, margin: [0, 0, 0, 0] },
      { text: label, fontSize: 9, margin: [4, 1, 12, 1] }
    ]);

    return {
      table: {
        widths: [12, 'auto', 12, 'auto', 12, 'auto'],
        body: [[
          ...cell(SessionAttendancePdfService.GREEN, this.t('SESSION_ATTENDANCE_PDF.PRESENT')),
          ...cell(SessionAttendancePdfService.RED, this.t('SESSION_ATTENDANCE_PDF.LEGEND_ABSENT_UNJUSTIFIED')),
          ...cell(SessionAttendancePdfService.AMBER, this.t('SESSION_ATTENDANCE_PDF.LEGEND_JUSTIFIED'))
        ]]
      },
      layout: 'noBorders',
      margin: [0, 0, 0, 10]
    };
  }

  /** Ligne de synthèse (comptages) affichée seulement après validation. */
  private buildSummary(students: SessionAttendanceStudentRow[], validated: boolean): Content {
    if (!validated || !students || students.length === 0) {
      return { text: '' };
    }
    const total = students.length;
    const present = students.filter(s => s.isPresent).length;
    const absent = total - present;
    const justified = students.filter(s => !s.isPresent && s.isJustified).length;

    return {
      table: {
        widths: ['*', '*', '*', '*'],
        body: [[
          this.summaryCell(this.t('SESSION_ATTENDANCE_PDF.SUMMARY_TOTAL'), String(total), SessionAttendancePdfService.PRIMARY),
          this.summaryCell(this.t('SESSION_ATTENDANCE_PDF.SUMMARY_PRESENT'), String(present), SessionAttendancePdfService.GREEN),
          this.summaryCell(this.t('SESSION_ATTENDANCE_PDF.SUMMARY_ABSENT'), String(absent), SessionAttendancePdfService.RED),
          this.summaryCell(this.t('SESSION_ATTENDANCE_PDF.SUMMARY_JUSTIFIED'), String(justified), SessionAttendancePdfService.AMBER)
        ]]
      },
      layout: 'noBorders',
      margin: [0, 4, 0, 16]
    };
  }

  private summaryCell(label: string, value: string, color: string): TableCell {
    return {
      stack: [
        { text: value, fontSize: 18, bold: true, color, alignment: 'center' },
        { text: label, fontSize: 9, color: SessionAttendancePdfService.GREY, alignment: 'center' }
      ],
      fillColor: '#f8fafc',
      margin: [4, 10, 4, 10]
    };
  }

  private buildSignatures(): Content {
    return {
      columns: [
        { text: this.t('SESSION_ATTENDANCE_PDF.TEACHER_SIGNATURE'), margin: [0, 40, 0, 0] },
        { text: this.t('PROFILE_PDF.ADMINISTRATION'), alignment: 'right', margin: [0, 40, 0, 0] }
      ]
    };
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
