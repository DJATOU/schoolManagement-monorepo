import { Injectable } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Content, TDocumentDefinitions } from 'pdfmake/interfaces';
import { Payout, PayoutSlip, payoutNature } from '../models/payroll/payroll';
import { formatAmount } from '../pipes/amount.pipe';
import { resolveLocale } from '../shared/locale';
import { pdfSafeText } from '../utils/pdf-print';
import { PdfOutputService } from './pdf-output.service';

/**
 * Bordereau d'une paie d'enseignant, à signer des deux côtés (spec teacher-payroll, exigence 5).
 *
 * <p>Il atteste la remise de l'argent : numéro, date, enseignant, groupe, série, encaissé, taux, les
 * deux parts, l'auteur, et deux lignes de signature. Une réimpression porte « DUPLICATA » et son
 * rang ; une paie annulée, le tampon « ANNULÉE » et sa remplaçante. Une régularisation négative
 * s'intitule « Retenue » et se lit comme une somme due par l'enseignant.</p>
 *
 * <p>Le document n'imprime que ce que le serveur a enregistré : aucun montant n'est recalculé ici.
 * Deux impressions de la même pièce ne diffèrent que par la mention du duplicata.</p>
 */
@Injectable({ providedIn: 'root' })
export class PayoutSlipPdfService {

  /** Logo de l'école, partagé avec les autres documents imprimés de l'application. */
  private static readonly LOGO_URL = 'assets/succes_assistance.png';

  private static readonly PRIMARY = '#4f46e5';
  private static readonly DEDUCTION = '#b91c1c';
  private static readonly GREY = '#64748b';
  private static readonly CANCELLED = '#dc2626';

  constructor(private translate: TranslateService, private output: PdfOutputService) {}

  /** Compose le bordereau et ouvre la boîte d'impression ; sans logo s'il ne se charge pas. */
  async print(slip: PayoutSlip): Promise<void> {
    const logo = await this.output.imageDataUrl(PayoutSlipPdfService.LOGO_URL);
    this.output.print(this.buildDocument(slip, logo), slip.fileName);
  }

  /** Définition du document, publique pour être vérifiée sans passer par l'impression. */
  buildDocument(slip: PayoutSlip, logo: string): TDocumentDefinitions {
    const payout = slip.payout;
    const cancelled = payout.status === 'CANCELLED';
    return {
      pageSize: 'A5',
      pageMargins: [32, 32, 32, 40],
      info: { title: `${this.title(payout)} ${payout.payoutNumber}` },
      ...(cancelled ? {
        watermark: {
          text: this.t('teacherPayroll.slip.cancelledStamp'),
          color: PayoutSlipPdfService.CANCELLED, opacity: 0.25, bold: true, angle: -35
        }
      } : {}),
      content: [
        this.header(slip, logo),
        this.divider(),
        ...(cancelled ? [this.cancellation(payout)] : []),
        { text: this.t('teacherPayroll.slip.partiesHeading'), style: 'sectionHeader' },
        this.labelValueTable(this.parties(payout)),
        { text: this.t('teacherPayroll.slip.calculationHeading'), style: 'sectionHeader' },
        this.labelValueTable(this.calculation(payout)),
        this.amountBox(payout),
        ...(payout.note ? [{ text: pdfSafeText(payout.note), style: 'note' } as Content] : []),
        { text: this.t('teacherPayroll.slip.recordedBy', { user: payout.paidBy }), style: 'muted', margin: [0, 8, 0, 0] },
        this.signatures()
      ],
      styles: {
        title: { fontSize: 15, bold: true, color: this.accent(payout) },
        subtitle: { fontSize: 10, color: PayoutSlipPdfService.GREY, margin: [0, 2, 0, 0] },
        duplicate: { fontSize: 9, bold: true, color: PayoutSlipPdfService.CANCELLED },
        sectionHeader: { fontSize: 10, bold: true, color: PayoutSlipPdfService.PRIMARY, margin: [0, 10, 0, 4] },
        amountLabel: { fontSize: 9, color: PayoutSlipPdfService.GREY },
        amount: { fontSize: 18, bold: true, color: this.accent(payout) },
        note: { fontSize: 9, italics: true, margin: [0, 8, 0, 0] },
        muted: { fontSize: 8.5, color: PayoutSlipPdfService.GREY }
      },
      defaultStyle: { fontSize: 9 },
      footer: (): Content => ({
        text: this.t('teacherPayroll.slip.footer'), alignment: 'center', fontSize: 7.5,
        color: PayoutSlipPdfService.GREY, margin: [32, 12, 32, 0]
      })
    };
  }

  /** « Bordereau de paie », « … de complément », « … de retenue ». */
  title(payout: Pick<Payout, 'kind' | 'teacherAmount'>): string {
    return this.t('teacherPayroll.slip.title.' + payoutNature(payout));
  }

  private accent(payout: Payout): string {
    return payoutNature(payout) === 'DEDUCTION' ? PayoutSlipPdfService.DEDUCTION : PayoutSlipPdfService.PRIMARY;
  }

  /** Logo, titre et numéro ; date de la paie et mention du duplicata à droite. */
  private header(slip: PayoutSlip, logo: string): Content {
    const payout = slip.payout;
    const title: Content = {
      stack: [
        { text: this.title(payout), style: 'title' },
        { text: this.t('teacherPayroll.slip.number', { number: payout.payoutNumber }), style: 'subtitle', bold: true }
      ]
    };
    const right: Content = {
      stack: [
        { text: this.t('teacherPayroll.slip.paidOn', { date: this.dateTime(payout.paidAt) }), alignment: 'right',
          style: 'subtitle' },
        slip.issuanceRank > 1
          ? { text: this.t('teacherPayroll.slip.duplicate', { rank: slip.issuanceRank, date: this.dateTime(slip.issuedAt) }),
              alignment: 'right', style: 'duplicate', margin: [0, 4, 0, 0] }
          : { text: '' }
      ]
    };
    return { columns: logo ? [{ image: logo, width: 40, margin: [0, 0, 10, 0] }, title, right] : [title, right] };
  }

  private divider(): Content {
    return {
      canvas: [{ type: 'line', x1: 0, y1: 0, x2: 356, y2: 0, lineWidth: 1, lineColor: PayoutSlipPdfService.PRIMARY }],
      margin: [0, 8, 0, 4]
    };
  }

  /** Annulée le … par …, motif, remplaçante : pour reprendre un bordereau remis par erreur. */
  private cancellation(payout: Payout): Content {
    const lines: Content[] = [{
      text: this.t('teacherPayroll.slip.cancelledOn', {
        date: payout.cancelledAt ? this.dateTime(payout.cancelledAt) : '—', user: payout.cancelledBy ?? '—'
      }), bold: true
    }];
    if (payout.cancelReasonText) {
      lines.push({ text: pdfSafeText(payout.cancelReasonText), italics: true });
    }
    if (payout.replacedByNumber) {
      lines.push({ text: this.t('teacherPayroll.slip.replacedBy', { number: payout.replacedByNumber }) });
    }
    return {
      table: { widths: ['*'], body: [[{ stack: lines, fillColor: '#fef2f2', color: '#991b1b', margin: [8, 6, 8, 6] }]] },
      layout: 'noBorders',
      margin: [0, 4, 0, 4]
    };
  }

  /** Qui est payé, pour quoi, à quel taux ; la paie dont celle-ci découle. */
  private parties(payout: Payout): [string, string][] {
    const rows: [string, string][] = [
      [this.t('teacherPayroll.slip.teacher'), payout.teacherName],
      [this.t('teacherPayroll.slip.group'), payout.groupName],
      [this.t('teacherPayroll.slip.series'), payout.seriesName],
      [this.t('teacherPayroll.slip.rate'), this.t('teacherPayroll.slip.rateValue', {
        label: payout.rateLabel, percent: this.percent(payout.teacherPercent)
      })]
    ];
    if (payout.initialPayoutNumber) {
      rows.push([this.t('teacherPayroll.slip.initialPayout'), payout.initialPayoutNumber]);
    }
    if (payout.replacesNumber) {
      rows.push([this.t('teacherPayroll.slip.replaces'), payout.replacesNumber]);
    }
    return rows;
  }

  /** Encaissé de la série, puis ce que cette paie partage, et le calcul en clair. */
  private calculation(payout: Payout): [string, string][] {
    const rows: [string, string][] = [
      [this.t('teacherPayroll.slip.collectedGross'), this.money(payout.collectedGross)],
      [this.t('teacherPayroll.slip.refunded'), this.money(payout.refunded)],
      [this.t('teacherPayroll.slip.collectedNet'), this.money(payout.collectedNet)]
    ];
    if (payout.kind === 'INITIAL') {
      rows.push([this.t('teacherPayroll.slip.calculation'), this.t('teacherPayroll.slip.calculationValue', {
        base: this.amount(payout.baseDelta), percent: this.percent(payout.teacherPercent),
        teacher: this.amount(payout.teacherAmount)
      })]);
    } else {
      rows.push([this.t('teacherPayroll.slip.baseDelta'), this.money(payout.baseDelta)]);
    }
    return rows;
  }

  /** Le montant remis, ou dû par l'enseignant pour une retenue, et la part de l'école. */
  private amountBox(payout: Payout): Content {
    const deduction = payoutNature(payout) === 'DEDUCTION';
    return {
      table: {
        widths: ['*'],
        body: [[{
          stack: [
            { text: this.t(deduction ? 'teacherPayroll.slip.deductionAmount' : 'teacherPayroll.slip.teacherAmount'),
              style: 'amountLabel' },
            { text: this.money(Math.abs(payout.teacherAmount)), style: 'amount' },
            { text: this.t('teacherPayroll.slip.schoolAmount', { amount: this.amount(payout.schoolAmount) }),
              style: 'muted', margin: [0, 2, 0, 0] }
          ],
          fillColor: deduction ? '#fef2f2' : '#eef2ff',
          margin: [10, 8, 10, 10]
        }]]
      },
      layout: 'noBorders',
      margin: [0, 10, 0, 0]
    };
  }

  /** Deux signatures : l'enseignant qui reçoit (ou rend), l'école qui remet (ou reprend). */
  private signatures(): Content {
    const block = (key: string): Content => ({
      stack: [
        { text: this.t(key), style: 'muted' },
        { canvas: [{ type: 'line', x1: 0, y1: 34, x2: 160, y2: 34, lineWidth: 0.6, lineColor: PayoutSlipPdfService.GREY }] }
      ]
    });
    return {
      columns: [block('teacherPayroll.slip.signatureTeacher'), block('teacherPayroll.slip.signatureSchool')],
      columnGap: 24,
      margin: [0, 18, 0, 0]
    };
  }

  private labelValueTable(rows: [string, string][]): Content {
    return {
      table: {
        widths: ['40%', '60%'],
        body: rows.map(([label, value]) => [
          { text: label, color: PayoutSlipPdfService.GREY, margin: [0, 2, 0, 2] },
          { text: pdfSafeText(value || '') || '—', bold: true, margin: [0, 2, 0, 2] }
        ])
      },
      layout: 'noBorders'
    };
  }

  // ------------------------------------------------------------------

  private t(key: string, params?: Record<string, unknown>): string {
    return pdfSafeText(this.translate.instant(key, params));
  }

  private lang(): string {
    return this.translate.currentLang || this.translate.defaultLang || 'fr';
  }

  /** « 2 400,00 » : séparateur de la langue, espaces exotiques ramenés à une espace ordinaire. */
  private amount(value: number): string {
    return pdfSafeText(formatAmount(value, this.lang()));
  }

  /** « 2 400,00 DA ». */
  private money(value: number): string {
    return this.t('teacherPayroll.slip.money', { amount: this.amount(value) });
  }

  /** « 60 », « 62,5 ». */
  private percent(value: number): string {
    return pdfSafeText(new Intl.NumberFormat(resolveLocale(this.lang()), { maximumFractionDigits: 2 }).format(value));
  }

  private dateTime(value: string): string {
    return pdfSafeText(new Intl.DateTimeFormat(resolveLocale(this.lang()), {
      dateStyle: 'short', timeStyle: 'short'
    }).format(new Date(value)));
  }
}
