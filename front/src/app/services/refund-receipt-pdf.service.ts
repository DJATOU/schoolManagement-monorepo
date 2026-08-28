import { Injectable } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import pdfMake from 'pdfmake/build/pdfmake';
import pdfFonts from 'pdfmake/build/vfs_fonts';
import { Content, TDocumentDefinitions } from 'pdfmake/interfaces';
import { RefundReceipt } from '../models/refund/refund';

/**
 * Génère le reçu d'un remboursement, prêt à imprimer et à remettre au bénéficiaire.
 *
 * <h2>Pourquoi un service distinct du reçu de versement</h2>
 * Les deux documents se ressemblent volontairement — même identité visuelle, même format A5 — mais
 * ils attestent de mouvements <strong>opposés</strong>. Un reçu de remboursement confondu avec un
 * reçu de versement ferait croire à un encaissement. La distinction repose donc sur trois éléments
 * délibérés : un titre différent, une mention explicite de sortie de caisse, et un libellé qui parle
 * d'un montant <em>remboursé</em> et non <em>reçu</em>. La couleur diffère aussi, mais elle ne suffit
 * pas : une impression en noir et blanc doit rester non ambiguë.
 *
 * <h2>Ce service ne décide de rien</h2>
 * Toutes les valeurs viennent du serveur, mentions de repli comprises. Il n'y a ici aucun calcul et
 * aucun choix d'affichage conditionnel sur les données : deux impressions de la même pièce doivent
 * être identiques.
 */
@Injectable({ providedIn: 'root' })
export class RefundReceiptPdfService {

  /** Logo de l'école, partagé avec les autres imprimés de l'application. */
  private static readonly LOGO_URL = 'assets/succes_assistance.png';

  /**
   * Palette distincte de celle du reçu de versement (indigo).
   *
   * <p>Une sortie de caisse se lit en rouge sombre : le repère est immédiat quand deux documents
   * sont posés côte à côte. Ce n'est qu'un renfort, la distinction textuelle restant primordiale.
   */
  private static readonly PRIMARY = '#b91c1c';
  private static readonly PRIMARY_SOFT = '#dc2626';
  private static readonly GREY = '#64748b';

  constructor(private translate: TranslateService) {
    (pdfMake as any).vfs = pdfFonts.pdfMake.vfs;
  }

  /**
   * Génère le reçu et ouvre la boîte d'impression du navigateur.
   *
   * <p>L'impression passe par une iframe masquée plutôt que par `pdfMake.print()` : cette dernière
   * ouvre un onglet via `window.open`, que le navigateur bloque en l'absence de clic direct — le cas
   * ici, l'appel suivant la réponse HTTP. En cas d'échec, le reçu est téléchargé afin qu'il ne soit
   * jamais perdu : l'argent est déjà sorti de la caisse, le justificatif doit exister.</p>
   */
  async generateAndPrint(receipt: RefundReceipt): Promise<void> {
    const logo = await this.loadLogo();
    const doc = this.buildDocument(receipt, logo);

    pdfMake.createPdf(doc).getBlob((blob: Blob) => {
      try {
        this.printViaHiddenIframe(blob, receipt.fileName);
      } catch {
        pdfMake.createPdf(doc).download(receipt.fileName);
      }
    });
  }

  /** Télécharge le reçu sans passer par l'impression. */
  async download(receipt: RefundReceipt): Promise<void> {
    const logo = await this.loadLogo();
    pdfMake.createPdf(this.buildDocument(receipt, logo)).download(receipt.fileName);
  }

  /**
   * Reçu au format A5 : il tient sur une demi-feuille, format habituel d'un justificatif remis en
   * main propre, et deux reçus s'impriment sur une A4.
   */
  private buildDocument(receipt: RefundReceipt, logo: string): TDocumentDefinitions {
    return {
      pageSize: 'A5',
      pageMargins: [32, 32, 32, 40],
      content: [
        this.buildHeader(receipt, logo),
        this.buildDivider(),
        this.buildAmountBanner(receipt),
        ...this.buildDetails(receipt),
        ...this.buildPaymentReference(receipt),
        this.buildSignatures(receipt)
      ],
      styles: {
        header: { fontSize: 16, bold: true, color: RefundReceiptPdfService.PRIMARY },
        subtitle: { fontSize: 9, color: RefundReceiptPdfService.GREY, margin: [0, 3, 0, 0] },
        sectionHeader: {
          fontSize: 11, bold: true,
          color: RefundReceiptPdfService.PRIMARY, margin: [0, 10, 0, 6]
        },
        amount: { fontSize: 20, bold: true, color: RefundReceiptPdfService.PRIMARY },
        amountLabel: { fontSize: 9, color: RefundReceiptPdfService.GREY },
        note: { fontSize: 8, color: RefundReceiptPdfService.GREY, italics: true },
        duplicate: { fontSize: 9, bold: true, color: RefundReceiptPdfService.PRIMARY_SOFT }
      },
      defaultStyle: { fontSize: 9 },
      footer: (): Content => ({
        text: this.t('refund.receipt.footerNote'),
        alignment: 'center',
        fontSize: 7,
        color: RefundReceiptPdfService.GREY,
        margin: [32, 10, 32, 0]
      })
    };
  }

  /**
   * En-tête : logo, titre, numéro de pièce, date, et mention « Duplicata » le cas échéant.
   *
   * <p>Le duplicata est signalé en en-tête, à l'endroit le plus visible : un reçu réimprimé ne doit
   * pas pouvoir être présenté comme l'original.</p>
   */
  private buildHeader(receipt: RefundReceipt, logo: string): Content {
    const titleBlock: Content = {
      stack: [
        { text: this.t('refund.receipt.title'), style: 'header' },
        {
          text: this.t('refund.receipt.number', { reference: receipt.refundNumber }),
          style: 'subtitle'
        },
        // Mention de sortie de caisse : c'est elle qui empêche la confusion avec un encaissement,
        // y compris sur une impression en noir et blanc.
        { text: this.t('refund.receipt.outflowNotice'), style: 'note' }
      ]
    };

    const dateBlock: Content = {
      stack: [
        { text: this.formatDate(receipt.refundDate), alignment: 'right', fontSize: 9 },
        receipt.issuanceRank > 1
          ? {
              text: this.t('refund.receipt.duplicate', {
                rank: receipt.issuanceRank,
                date: this.formatDateTime(receipt.issuedAt)
              }),
              alignment: 'right',
              style: 'duplicate'
            }
          : { text: '' }
      ]
    };

    const columns: Content[] = logo
      ? [{ image: logo, width: 46, margin: [0, 0, 10, 0] }, titleBlock, dateBlock]
      : [titleBlock, dateBlock];

    return { columns, columnGap: 0 };
  }

  private buildDivider(): Content {
    return {
      canvas: [{
        type: 'line', x1: 0, y1: 0, x2: 355, y2: 0,
        lineWidth: 1.5, lineColor: RefundReceiptPdfService.PRIMARY_SOFT
      }],
      margin: [0, 8, 0, 10]
    };
  }

  /** Montant remboursé mis en avant, avec un libellé qui dit bien « remboursé ». */
  private buildAmountBanner(receipt: RefundReceipt): Content {
    return {
      table: {
        widths: ['*'],
        body: [[{
          stack: [
            { text: this.t('refund.receipt.amountRefunded'), style: 'amountLabel' },
            { text: this.formatAmount(receipt.amount), style: 'amount' }
          ],
          fillColor: '#fef2f2',
          margin: [10, 8, 10, 10],
          border: [false, false, false, false]
        }]]
      },
      layout: 'noBorders',
      margin: [0, 0, 0, 4]
    };
  }

  private buildDetails(receipt: RefundReceipt): Content[] {
    const student = `${receipt.studentFirstName} ${receipt.studentLastName}`.trim();
    const rows: [string, string][] = [
      [this.t('refund.receipt.student'), student],
      [this.t('refund.receipt.group'), receipt.groupName],
      [this.t('refund.receipt.series'), receipt.seriesName],
      // Le motif est imprimé intégralement : c'est ce qui justifie la sortie de caisse lors d'un
      // contrôle. Un motif absent est signalé explicitement plutôt que laissé en blanc.
      [
        this.t('refund.receipt.reason'),
        receipt.reason?.trim() || this.t('refund.receipt.noReason')
      ]
    ];
    return [
      { text: this.t('refund.receipt.detailsHeading'), style: 'sectionHeader' },
      this.labelValueTable(rows)
    ];
  }

  /**
   * Rattache le remboursement au versement d'origine.
   *
   * <p>Sans cette référence, la famille ne peut pas relier la somme rendue à ce qu'elle avait
   * versé, et le reçu perd sa valeur de justificatif.</p>
   */
  private buildPaymentReference(receipt: RefundReceipt): Content[] {
    const rows: [string, string][] = [
      [this.t('refund.receipt.paymentAmount'), this.formatAmount(receipt.amountPaid)]
    ];
    if (receipt.paymentDate) {
      rows.push([this.t('refund.receipt.paymentDate'), this.formatDate(receipt.paymentDate)]);
    }
    return [
      { text: this.t('refund.receipt.paymentHeading'), style: 'sectionHeader' },
      this.labelValueTable(rows)
    ];
  }

  /**
   * Double signature : l'administrateur qui rend l'argent et le bénéficiaire qui le reçoit.
   *
   * <p>C'est la raison d'être du document : attester la remise <strong>des deux côtés</strong>. Une
   * sortie de caisse signée d'un seul côté ne prouve rien.</p>
   */
  private buildSignatures(receipt: RefundReceipt): Content {
    return {
      columns: [
        {
          stack: [
            { text: this.t('refund.receipt.beneficiarySignature'), style: 'amountLabel' },
            { text: '________________________', margin: [0, 22, 0, 0] }
          ]
        },
        {
          stack: [
            {
              text: this.t('refund.receipt.adminSignature'),
              style: 'amountLabel', alignment: 'right'
            },
            {
              text: this.clean(receipt.recordedBy),
              bold: true, alignment: 'right', margin: [0, 2, 0, 0]
            },
            { text: '________________________', alignment: 'right', margin: [0, 14, 0, 0] }
          ]
        }
      ],
      margin: [0, 24, 0, 0]
    };
  }

  /** Tableau à deux colonnes label/valeur, sans bordures verticales. */
  private labelValueTable(rows: [string, string][]): Content {
    return {
      table: {
        widths: ['38%', '62%'],
        body: rows.map(([label, value]) => [
          { text: label, color: RefundReceiptPdfService.GREY, margin: [0, 2, 0, 2] },
          { text: this.clean(value || '') || '—', bold: true, margin: [0, 2, 0, 2] }
        ])
      },
      layout: {
        hLineWidth: (i: number, node: any) => (i === 0 || i === node.table.body.length ? 0 : 0.5),
        vLineWidth: () => 0,
        hLineColor: () => '#e2e8f0',
        paddingLeft: () => 0,
        paddingRight: () => 4
      },
      margin: [0, 0, 0, 4]
    };
  }

  private async loadLogo(): Promise<string> {
    try {
      return await this.convertImageToBase64(RefundReceiptPdfService.LOGO_URL);
    } catch (err) {
      // Un logo indisponible ne doit pas empêcher la remise du justificatif.
      console.error('Logo indisponible, reçu généré sans logo :', err);
      return '';
    }
  }

  private convertImageToBase64(url: string): Promise<string> {
    return new Promise((resolve, reject) => {
      const img = new Image();
      img.crossOrigin = 'Anonymous';
      img.onload = () => {
        const canvas = document.createElement('canvas');
        canvas.width = img.width;
        canvas.height = img.height;
        const ctx = canvas.getContext('2d');
        if (!ctx) {
          reject(new Error('Canvas 2D indisponible'));
          return;
        }
        ctx.drawImage(img, 0, 0);
        resolve(canvas.toDataURL('image/png'));
      };
      img.onerror = (error) => reject(error);
      img.src = url;
    });
  }

  private printViaHiddenIframe(blob: Blob, fileName: string): void {
    const url = URL.createObjectURL(blob);
    const iframe = document.createElement('iframe');
    iframe.style.position = 'fixed';
    iframe.style.right = '0';
    iframe.style.bottom = '0';
    iframe.style.width = '0';
    iframe.style.height = '0';
    iframe.style.border = '0';
    iframe.title = fileName;

    iframe.onload = () => {
      try {
        iframe.contentWindow?.focus();
        iframe.contentWindow?.print();
      } catch {
        this.downloadBlob(blob, fileName);
      }
    };

    iframe.src = url;
    document.body.appendChild(iframe);

    // L'iframe est retirée après un délai : la supprimer immédiatement annulerait la boîte
    // d'impression, qui est asynchrone.
    window.setTimeout(() => {
      iframe.remove();
      URL.revokeObjectURL(url);
    }, 60_000);
  }

  private downloadBlob(blob: Blob, fileName: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    link.click();
    URL.revokeObjectURL(url);
  }

  private t(key: string, params?: Record<string, unknown>): string {
    return this.clean(this.translate.instant(key, params));
  }

  /**
   * Remplace les espaces Unicode exotiques par une espace ordinaire.
   *
   * <p>Les polices embarquées par pdfmake ne les contiennent pas : ils s'impriment en carré. Le cas
   * se produit systématiquement sur les montants, `Intl.NumberFormat` séparant les milliers par une
   * espace fine insécable en français.</p>
   */
  private clean(value: string): string {
    return (value ?? '').replace(/[\u00A0\u2000-\u200A\u202F\u205F\u3000]/g, ' ');
  }

  /** Montant à deux décimales systématiques, séparateurs nettoyés. */
  private formatAmount(value: number): string {
    return this.t('refund.receipt.amountFormat', {
      amount: this.clean(new Intl.NumberFormat(this.locale(), {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
      }).format(value))
    });
  }

  private formatDate(value: Date | string): string {
    return this.clean(new Intl.DateTimeFormat(this.locale(), { dateStyle: 'short' })
      .format(new Date(value)));
  }

  private formatDateTime(value: Date | string): string {
    return this.clean(new Intl.DateTimeFormat(this.locale(), {
      dateStyle: 'short', timeStyle: 'short'
    }).format(new Date(value)));
  }

  private locale(): string {
    return this.translate.currentLang === 'en' ? 'en-GB' : 'fr-FR';
  }
}
