import { Injectable } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import pdfMake from 'pdfmake/build/pdfmake';
import pdfFonts from 'pdfmake/build/vfs_fonts';
import { Content, TDocumentDefinitions } from 'pdfmake/interfaces';

/**
 * Une part de versement reportée sur une autre série, telle qu'imprimée sur le reçu.
 *
 * <p>Volontairement réduite au nom de la série et au montant : ce sont les deux seules
 * informations que la famille peut vérifier. Le type est structurellement compatible avec
 * {@code CarriedOverAmount} du modèle de répartition, si bien que la liste renvoyée par le
 * serveur se passe telle quelle, sans transformation intermédiaire susceptible de dériver.</p>
 */
export interface ReceiptCarryOver {
  /** Nom de la série créditée par report (exigence 7.2). */
  seriesName: string;
  /** Montant reporté sur cette série. */
  amount: number;
}

/**
 * Données d'un reçu de paiement.
 *
 * <p>Toutes les valeurs sont fournies par l'appelant : le service ne recalcule rien et
 * n'interroge pas le serveur. En particulier {@link amountPaid} est le montant du
 * <strong>versement courant</strong>, pas le cumul de la série.</p>
 */
export interface PaymentReceiptData {
  /** Référence du reçu, imprimée en en-tête (voir PaymentReceiptPdfService.buildReference). */
  reference: string;
  /** Date d'encaissement retenue (date serveur si disponible, sinon date locale). */
  issuedAt: Date;
  studentName: string;
  groupName: string;
  seriesName: string;
  /** Montant de CE versement. */
  amountPaid: number;
  /** Libellé déjà traduit du mode de règlement. */
  paymentMethodLabel: string;
  description?: string;
  /** Versement portant sur des séances de rattrapage uniquement. */
  isCatchUp: boolean;
  /**
   * Coût au prorata de la série, réduction appliquée (exigence 7.1).
   *
   * <p>C'est le coût des seules séances <strong>facturables</strong> à cet étudiant, pas le coût
   * nominal de la série : une séance tenue avant son arrivée et à laquelle il n'a pas assisté ne
   * lui est pas facturée. En mode rattrapage, l'appelant y place le montant dû à ce jour.</p>
   */
  seriesTotalCost?: number;
  /**
   * Nombre de séances facturables retenues dans le coût au prorata (exigence 7.1).
   *
   * <p>Imprimé à côté du coût : sans ce décompte, un coût inférieur au coût nominal de la série
   * paraîtrait arbitraire et la famille ne pourrait pas le vérifier.</p>
   */
  billableSessions?: number;
  /** Cumul versé sur la série après ce versement, part reportée exclue. */
  totalPaidAfter?: number;
  /**
   * Reste à payer : coût au prorata diminué du montant versé de la série (exigence 7.3).
   *
   * <p>Le montant versé retenu est celui de la série, donc la part réellement
   * <strong>imputée</strong> ici — les parts reportées créditent d'autres séries et ne réduisent
   * pas la dette de celle-ci.</p>
   */
  remainingAfter?: number;
  /**
   * Part du versement imputée sur la série visée (exigence 7.2).
   *
   * <p>Absente pour les chemins qui ne renvoient pas de répartition, comme le rattrapage : le
   * reçu se limite alors au montant reçu.</p>
   */
  amountAllocated?: number;
  /**
   * Parts reportées sur les séries suivantes, avec leur série destinataire (exigence 7.2).
   *
   * <p>Liste vide en l'absence de report : le reçu imprime alors un montant reporté nul plutôt
   * que de taire la ligne (exigence 7.4), afin que la famille constate que rien n'a quitté la
   * série qu'elle a réglée.</p>
   */
  carryOvers?: ReceiptCarryOver[];
  /** Identifiant de l'admin qui a encaissé CE versement. */
  adminUsername: string;
}

/**
 * Génère le reçu d'un paiement, prêt à imprimer et à remettre à l'étudiant.
 *
 * <p>Le reçu ne couvre que le versement qui vient d'être encaissé et porte le nom de
 * l'administrateur qui l'a enregistré, avec une zone de signature manuscrite.</p>
 */
@Injectable({ providedIn: 'root' })
export class PaymentReceiptPdfService {

  /** Logo de l'école, partagé avec les autres documents imprimés de l'application. */
  private static readonly LOGO_URL = 'assets/succes_assistance.png';

  private static readonly PRIMARY = '#4f46e5';
  private static readonly PRIMARY_SOFT = '#6366f1';
  private static readonly GREY = '#64748b';

  constructor(private translate: TranslateService) {
    (pdfMake as any).vfs = pdfFonts.pdfMake.vfs;
  }

  /**
   * Construit une référence de reçu.
   *
   * <p>L'identifiant de paiement seul ne suffit pas : le backend regroupe tous les versements
   * d'une même série sur une seule ligne de paiement, si bien que deux versements successifs
   * partagent cet identifiant. On y adjoint donc l'horodatage d'encaissement pour distinguer
   * les reçus tout en restant traçable jusqu'à la ligne de paiement.</p>
   */
  buildReference(paymentId: number | undefined, issuedAt: Date): string {
    const pad = (n: number) => String(n).padStart(2, '0');
    const stamp = `${issuedAt.getFullYear()}${pad(issuedAt.getMonth() + 1)}${pad(issuedAt.getDate())}`
      + `-${pad(issuedAt.getHours())}${pad(issuedAt.getMinutes())}${pad(issuedAt.getSeconds())}`;
    return paymentId != null ? `${paymentId}-${stamp}` : stamp;
  }

  /**
   * Génère le reçu et ouvre la boîte d'impression du navigateur.
   *
   * <p>L'impression passe par une iframe masquée et non par {@code pdfMake.print()} : cette
   * dernière ouvre un onglet via {@code window.open}, que le navigateur bloque lorsqu'aucun
   * clic direct n'est en cours — c'est le cas ici, l'appel suivant la réponse HTTP du
   * paiement. En cas d'échec, le reçu est téléchargé afin qu'il ne soit jamais perdu.</p>
   */
  async generateAndPrint(data: PaymentReceiptData): Promise<void> {
    // Garde-fou : un reçu atteste d'une somme reçue. À 0 (ou moins), il n'y a rien à
    // attester et le document laisserait croire à un paiement. L'appelant décide du
    // message affiché ; ici on refuse simplement d'imprimer.
    if (!(data.amountPaid > 0)) {
      return;
    }

    // Un logo indisponible ne doit pas empêcher la remise du justificatif : le reçu est
    // alors composé sans lui.
    const logo = await this.loadLogo();
    const doc = this.buildDocument(data, logo);
    const fileName = this.fileName(data);

    pdfMake.createPdf(doc).getBlob((blob: Blob) => {
      try {
        this.printViaHiddenIframe(blob, fileName);
      } catch {
        this.download(doc, fileName);
      }
    });
  }

  /**
   * Charge le logo de l'école en base64, ou renvoie une chaîne vide en cas d'échec.
   *
   * <p>Même source que les autres documents de l'application ({@code assets/succes_assistance.png}),
   * pour que tous les imprimés portent la même identité.</p>
   */
  private async loadLogo(): Promise<string> {
    try {
      return await this.convertImageToBase64(PaymentReceiptPdfService.LOGO_URL);
    } catch (err) {
      console.error('Logo indisponible, reçu généré sans logo :', err);
      return '';
    }
  }

  /** Convertit une image en data URL PNG via un canvas. */
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

  /** Nom de fichier du reçu, utilisé en repli de téléchargement. */
  private fileName(data: PaymentReceiptData): string {
    const slug = data.studentName.trim().replace(/\s+/g, '_') || 'etudiant';
    return `${this.t('payment.receipt.fileName')}_${slug}_${data.reference}.pdf`;
  }

  /**
   * Charge le PDF dans une iframe masquée puis déclenche l'impression.
   *
   * <p>L'iframe est retirée après un délai : la supprimer immédiatement annulerait la boîte
   * d'impression, qui est asynchrone.</p>
   */
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
        // Navigateur incapable d'imprimer un PDF en iframe : on laisse le reçu accessible.
        this.downloadBlob(blob, fileName);
      }
    };

    iframe.src = url;
    document.body.appendChild(iframe);

    window.setTimeout(() => {
      iframe.remove();
      URL.revokeObjectURL(url);
    }, 60_000);
  }

  private download(doc: TDocumentDefinitions, fileName: string): void {
    pdfMake.createPdf(doc).download(fileName);
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
   * Remplace les espaces Unicode « exotiques » par une espace ordinaire.
   *
   * <p>Les polices embarquées par pdfmake (Roboto) ne contiennent pas ces caractères : ils
   * s'impriment en carré « glyphe manquant ». Le cas se produit systématiquement sur les
   * montants, {@code Intl.NumberFormat} séparant les milliers par une espace fine insécable
   * (U+202F) en français — « 12 000 DA » sortait « 12□000 DA ». Les locales anglaises
   * insèrent de même une espace fine avant AM/PM dans les heures.</p>
   */
  private clean(value: string): string {
    // U+00A0 insécable, U+2000-U+200A espaces typographiques, U+202F fine insécable,
    // U+205F espace mathématique moyenne, U+3000 cadratin idéographique.
    return value.replace(/[\u00A0\u2000-\u200A\u202F\u205F\u3000]/g, ' ');
  }

  /**
   * Reçu au format A5 : il tient sur une demi-feuille, format habituel d'un justificatif
   * remis en main propre, et deux reçus s'impriment sur une A4.
   */
  private buildDocument(data: PaymentReceiptData, logo: string): TDocumentDefinitions {
    return {
      pageSize: 'A5',
      pageMargins: [32, 32, 32, 40],
      content: [
        this.buildHeader(data, logo),
        this.buildDivider(),
        this.buildAmountBanner(data),
        ...this.buildDetails(data),
        ...this.buildSeriesContext(data),
        ...this.buildAllocation(data),
        this.buildSignatures(data)
      ],
      styles: {
        header: { fontSize: 16, bold: true, color: PaymentReceiptPdfService.PRIMARY },
        subtitle: { fontSize: 9, color: PaymentReceiptPdfService.GREY, margin: [0, 3, 0, 0] },
        sectionHeader: {
          fontSize: 11, bold: true,
          color: PaymentReceiptPdfService.PRIMARY, margin: [0, 10, 0, 6]
        },
        amount: { fontSize: 20, bold: true, color: PaymentReceiptPdfService.PRIMARY },
        amountLabel: { fontSize: 9, color: PaymentReceiptPdfService.GREY },
        note: { fontSize: 8, color: PaymentReceiptPdfService.GREY, italics: true }
      },
      defaultStyle: { fontSize: 9 },
      footer: (): Content => ({
        text: this.t('payment.receipt.footerNote'),
        alignment: 'center',
        fontSize: 7,
        color: PaymentReceiptPdfService.GREY,
        margin: [32, 10, 32, 0]
      })
    };
  }

  /**
   * En-tête : logo à gauche, titre et référence au centre, date à droite.
   *
   * <p>La colonne du logo est omise s'il n'a pas pu être chargé, pour ne pas laisser un
   * blanc décalant la mise en page.</p>
   */
  private buildHeader(data: PaymentReceiptData, logo: string): Content {
    const titleBlock: Content = {
      stack: [
        { text: this.t('payment.receipt.title'), style: 'header' },
        {
          text: this.t('payment.receipt.reference', { reference: data.reference }),
          style: 'subtitle'
        }
      ]
    };

    const dateBlock: Content = {
      stack: [
        { text: this.formatDateTime(data.issuedAt), alignment: 'right', fontSize: 9 },
        data.isCatchUp
          ? { text: this.t('payment.receipt.catchUpBadge'), alignment: 'right', style: 'note' }
          : { text: '' }
      ]
    };

    const columns: Content[] = logo
      ? [
          { image: logo, width: 46, margin: [0, 0, 10, 0] },
          titleBlock,
          dateBlock
        ]
      : [titleBlock, dateBlock];

    return { columns, columnGap: 0 };
  }

  private buildDivider(): Content {
    return {
      canvas: [{
        type: 'line', x1: 0, y1: 0, x2: 355, y2: 0,
        lineWidth: 1.5, lineColor: PaymentReceiptPdfService.PRIMARY_SOFT
      }],
      margin: [0, 8, 0, 10]
    };
  }

  /** Montant encaissé mis en avant : c'est l'information que l'étudiant vérifie en premier. */
  private buildAmountBanner(data: PaymentReceiptData): Content {
    return {
      table: {
        widths: ['*'],
        body: [[{
          stack: [
            { text: this.t('payment.receipt.amountReceived'), style: 'amountLabel' },
            { text: this.formatAmount(this.receivedTotal(data)), style: 'amount' }
          ],
          fillColor: '#eef2ff',
          margin: [10, 8, 10, 10],
          border: [false, false, false, false]
        }]]
      },
      layout: 'noBorders',
      margin: [0, 0, 0, 4]
    };
  }

  private buildDetails(data: PaymentReceiptData): Content[] {
    const rows: [string, string][] = [
      [this.t('payment.receipt.student'), data.studentName],
      [this.t('payment.receipt.group'), data.groupName],
      [this.t('payment.receipt.series'), data.seriesName],
      [this.t('payment.receipt.method'), data.paymentMethodLabel]
    ];
    if (data.description?.trim()) {
      rows.push([this.t('payment.receipt.description'), data.description.trim()]);
    }
    return [
      { text: this.t('payment.receipt.detailsHeading'), style: 'sectionHeader' },
      this.labelValueTable(rows)
    ];
  }

  /**
   * Situe le versement dans la série (coût total, cumul versé, reste dû).
   *
   * <p>Omis si l'appelant n'a pas fourni ces montants : mieux vaut un reçu sans contexte
   * qu'un reçu affichant des zéros trompeurs.</p>
   */
  private buildSeriesContext(data: PaymentReceiptData): Content[] {
    const rows: [string, string][] = [];
    if (data.seriesTotalCost != null) {
      rows.push([this.t('payment.receipt.seriesTotal'), this.formatAmount(data.seriesTotalCost)]);
    }
    // Décompte des séances retenues dans le coût au prorata : il justifie un coût inférieur au
    // coût nominal de la série (exigence 7.1). Le zéro est imprimé, il est significatif.
    if (data.billableSessions != null) {
      rows.push([
        this.t('payment.receipt.billableSessions'),
        this.clean(String(data.billableSessions))
      ]);
    }
    if (data.totalPaidAfter != null) {
      rows.push([this.t('payment.receipt.totalPaid'), this.formatAmount(data.totalPaidAfter)]);
    }
    if (data.remainingAfter != null) {
      rows.push([this.t('payment.receipt.remaining'), this.formatAmount(data.remainingAfter)]);
    }
    if (rows.length === 0) {
      return [];
    }
    return [
      { text: this.t('payment.receipt.situationHeading'), style: 'sectionHeader' },
      this.labelValueTable(rows)
    ];
  }

  /**
   * Répartition du versement : part imputée sur la série réglée et parts reportées, chacune
   * nommant sa série destinataire (exigence 7.2).
   *
   * <p>La ligne « total reporté » est imprimée même à zéro (exigence 7.4) : c'est la preuve, pour
   * la famille, que l'intégralité du versement est restée sur la série qu'elle a réglée. La
   * taire laisserait planer un doute qu'un reçu est justement censé lever.</p>
   *
   * <p>Le bloc est omis quand l'appelant ne fournit aucune répartition — chemin rattrapage, qui
   * ne reporte rien : imprimer « imputé : montant total, reporté : 0 » n'apporterait rien.</p>
   */
  private buildAllocation(data: PaymentReceiptData): Content[] {
    if (data.amountAllocated == null) {
      return [];
    }

    const carryOvers = data.carryOvers ?? [];
    const rows: [string, string][] = [
      [
        this.t('payment.receipt.allocated', { series: data.seriesName }),
        this.formatAmount(data.amountAllocated)
      ]
    ];
    carryOvers.forEach(carryOver => rows.push([
      this.t('payment.receipt.carriedOverTo', { series: carryOver.seriesName }),
      this.formatAmount(carryOver.amount)
    ]));
    rows.push([
      this.t('payment.receipt.carriedOverTotal'),
      this.formatAmount(this.carriedOverTotal(data))
    ]);

    return [
      { text: this.t('payment.receipt.allocationHeading'), style: 'sectionHeader' },
      this.labelValueTable(rows)
    ];
  }

  /** Somme des parts reportées, nulle en l'absence de report. */
  private carriedOverTotal(data: PaymentReceiptData): number {
    const total = (data.carryOvers ?? [])
      .reduce((sum, carryOver) => sum + carryOver.amount, 0);
    return this.round(total);
  }

  /**
   * Montant reçu imprimé : somme de la part imputée et des parts reportées (exigence 7.5).
   *
   * <p>Le montant est <strong>dérivé</strong> de la répartition plutôt que recopié, afin que
   * l'égalité soit vraie sur le document par construction et non par coïncidence. Un écart avec
   * le montant annoncé par l'appelant signale une répartition incomplète : le serveur refusant
   * tout encaissement partiel, il ne doit jamais s'en produire, d'où la trace en console.</p>
   *
   * <p>Sans répartition — chemin rattrapage — le montant du versement fait foi.</p>
   */
  private receivedTotal(data: PaymentReceiptData): number {
    if (data.amountAllocated == null) {
      return data.amountPaid;
    }
    const total = this.round(data.amountAllocated + this.carriedOverTotal(data));
    if (Math.abs(total - this.round(data.amountPaid)) > 0.005) {
      console.error(
        'Reçu : la répartition ne totalise pas le montant reçu '
        + `(imputé + reporté = ${total}, versement = ${data.amountPaid}).`
      );
    }
    return total;
  }

  /** Arrondi monétaire à deux décimales, échelle des montants du domaine. */
  private round(value: number): number {
    return Math.round(value * 100) / 100;
  }

  /** Tableau à deux colonnes label/valeur, sans bordures verticales. */
  private labelValueTable(rows: [string, string][]): Content {
    return {
      table: {
        widths: ['38%', '62%'],
        // Les valeurs viennent en partie de la base (noms, description) : elles sont
        // nettoyées comme les libellés, un copier-coller pouvant y avoir laissé une
        // espace insécable qui s'imprimerait en carré.
        body: rows.map(([label, value]) => [
          { text: label, color: PaymentReceiptPdfService.GREY, margin: [0, 2, 0, 2] },
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

  /**
   * Zone de signature : l'admin qui a encaissé ce versement est nommé, et signe à la main.
   *
   * <p>Le nom imprimé est celui de l'administrateur connecté au moment de l'encaissement,
   * donc bien celui de CE versement — et non celui du premier versement de la série, que
   * porte le champ d'audit de la ligne de paiement.</p>
   */
  private buildSignatures(data: PaymentReceiptData): Content {
    return {
      columns: [
        {
          stack: [
            { text: this.t('payment.receipt.studentSignature'), style: 'amountLabel' },
            { text: '________________________', margin: [0, 22, 0, 0] }
          ]
        },
        {
          stack: [
            { text: this.t('payment.receipt.adminSignature'), style: 'amountLabel', alignment: 'right' },
            { text: this.clean(data.adminUsername), bold: true, alignment: 'right', margin: [0, 2, 0, 0] },
            { text: '________________________', alignment: 'right', margin: [0, 14, 0, 0] }
          ]
        }
      ],
      margin: [0, 24, 0, 0]
    };
  }

  /**
   * Montant formaté à deux décimales, séparateurs de milliers nettoyés.
   *
   * <p>Deux décimales systématiques : sur un même reçu, « 240 DA » à côté de « 12.50 DA » laisse
   * croire à deux précisions différentes, et un report vaut rarement un compte rond.</p>
   *
   * <p>Le nettoyage est indispensable : {@code Intl.NumberFormat('fr-FR')} sépare les milliers
   * par une espace fine insécable (U+202F), absente de la police embarquée par pdfmake — sans
   * lui, « 12 000 DA » s'imprime « 12□000 DA ».</p>
   */
  private formatAmount(value: number): string {
    return this.t('payment.receipt.amountFormat', {
      amount: this.clean(new Intl.NumberFormat(this.locale(), {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
      }).format(value))
    });
  }

  private formatDateTime(date: Date): string {
    return this.clean(new Intl.DateTimeFormat(this.locale(), {
      dateStyle: 'short',
      timeStyle: 'short'
    }).format(date));
  }

  private locale(): string {
    return this.translate.currentLang === 'en' ? 'en-GB' : 'fr-FR';
  }
}
