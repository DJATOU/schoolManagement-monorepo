import { Injectable } from '@angular/core';
import pdfMake from 'pdfmake/build/pdfmake';
import pdfFonts from 'pdfmake/build/vfs_fonts';
import { Content, TDocumentDefinitions } from 'pdfmake/interfaces';
import { TranslateService } from '@ngx-translate/core';
import { StudentFullHistoryDTO } from '../domain/StudentFullHistoryDTO';
import { SessionHistoryDTO } from '../../../models/session/SessionHistoryDTO';
import { resolveLocale } from '../../../shared/locale';
import {
  countBillableSessions,
  countExcludedSessions,
  isCatchUpBilled,
  isExcludedSession
} from '../../../shared/session-billing';

@Injectable({
  providedIn: 'root'
})
export class PdfGeneratorService {

  // Couleur dédiée à la légende « Présent et exempté » (réduction 100 %).
  // Bleu distinct des autres couleurs de la légende.
  private static readonly EXEMPTED_PRESENT_COLOR = '#1e88e5';

  constructor(private translate: TranslateService) {
    (pdfMake as any).vfs = pdfFonts.pdfMake.vfs;
  }

  /** Raccourci de traduction pour les clés de l'historique étudiant. */
  private t(key: string, params?: Record<string, unknown>): string {
    return this.translate.instant(`studentHistory.${key}`, params);
  }

  /** Locale courante, pour le formatage des dates du document. */
  private get locale(): string {
    return resolveLocale(this.translate.currentLang);
  }

  /** Date formatée selon la langue active (et non selon celle du navigateur). */
  private formatDate(value: string | Date | undefined | null): string {
    return value ? new Date(value).toLocaleDateString(this.locale) : '—';
  }

  /**
   * Montant à deux décimales, prêt pour l'impression.
   *
   * <p>Les séparateurs de milliers du français (U+202F) et d'autres locales (U+00A0) sont
   * ramenés à une espace ordinaire : la police embarquée du générateur ne possède pas ces
   * glyphes et imprimait « 2⯑100 ».</p>
   */
  private amount(value: number | null | undefined): string {
    const formatted = (value ?? 0).toLocaleString(this.locale, {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    });
    return formatted.replace(/[\u202F\u00A0]/g, ' ');
  }

  /**
   * Libellé traduit d'un code renvoyé par le backend.
   *
   * <p>Le backend expose des codes stables (PRESENT, PAID, FULL...). Une valeur inconnue
   * est affichée telle quelle plutôt que sous forme de clé technique.</p>
   */
  private label(group: string, code: string | undefined | null): string {
    if (!code) {
      return '—';
    }
    const key = `studentHistory.${group}.${code}`;
    const translated = this.translate.instant(key);
    return translated === key ? code : translated;
  }

  async generateFullHistoryPdf(fullHistory: StudentFullHistoryDTO, logoUrl: string): Promise<void> {

    let logoBase64 = '';
    try {
      logoBase64 = await this.convertImageToBase64(logoUrl);
    } catch (error) {
      console.error('Erreur lors du chargement du logo :', error);
    }

    const content: Content[] = [
      {
        columns: [
          {
            image: logoBase64,
            width: 100
          },
          {
            text: this.t('pdf.title'),
            style: 'header',
            alignment: 'right'
          }
        ]
      },
      { text: '\n\n' },
      {
        text: this.t('pdf.student', { name: fullHistory.studentName }),
        style: 'subheader'
      },
      {
        text: this.t('pdf.date', { date: this.formatDate(new Date()) }),
        alignment: 'right'
      },
      { text: '\n' },
      ...this.getFullHistoryContent(fullHistory),
      { text: '\n\n' },
      { text: this.t('pdf.legend.title'), style: 'subheader', alignment: 'left' },
      {
        table: {
          widths: ['auto', '*'],
          body: [
            [
              { text: '', fillColor: '#32a852', width: 15, height: 15 },
              { text: this.t('pdf.legend.presentPaid') }
            ],
            [
              { text: '', fillColor: '#ff6347', width: 15, height: 15 },
              { text: this.t('pdf.legend.absentPaid') }
            ],
            [
              { text: '', fillColor: '#ffd700', width: 15, height: 15 },
              { text: this.t('pdf.legend.presentPartial') }
            ],
            [
              { text: '', fillColor: '#ff4500', width: 15, height: 15 },
              { text: this.t('pdf.legend.absentPartial') }
            ],
            [
              { text: '', fillColor: '#f5f5f5', width: 15, height: 15 },
              { text: this.t('pdf.legend.unknownAttendance') }
            ],
            [
              { text: '', fillColor: '#e60000', width: 15, height: 15 },
              { text: this.t('pdf.legend.presentUnpaid') }
            ],
            [
              { text: '', fillColor: PdfGeneratorService.EXEMPTED_PRESENT_COLOR, width: 15, height: 15 },
              { text: this.t('pdf.legend.presentExempted') }
            ],
            [
              { text: 'Rattrapage', bold: true },
              { text: 'Session de rattrapage (préfixe « Session de rattrapage: »)' }
            ],
            // Séance écartée du prorata : la légende la nomme, car à l'impression noir et
            // blanc son fond gris ne se distingue pas de « présence non renseignée ».
            [
              { text: this.t('excludedTag'), bold: true },
              { text: this.t('pdf.legend.excludedSession') }
            ]
          ]
        },
        layout: 'noBorders',
        margin: [0, 0, 0, 20]
      },
      {
        columns: [
          {
            text: 'Signature de l\'Étudiant : ________________________',
            alignment: 'left',
            margin: [0, 50, 0, 0]
          },
          {
            text: 'Signature de l\'Administration : ________________________',
            alignment: 'right',
            margin: [0, 50, 0, 0]
          }
        ]
      }
    ];

    const documentDefinition: TDocumentDefinitions = {
      content: content,
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
          margin: [0, 20, 0, 10],
          decoration: 'underline'
        },
        subsectionHeader: {
          fontSize: 16,
          bold: true,
          color: '#2F5496',
          margin: [0, 15, 0, 5]
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
      defaultStyle: {
        fontSize: 11
      },
      footer: (currentPage: number, pageCount: number): Content => {
        return {
          text: `Page ${currentPage} sur ${pageCount}`,
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

  private async convertImageToBase64(url: string): Promise<string> {
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

  private getFullHistoryContent(fullHistory: StudentFullHistoryDTO): Content[] {
    const content: Content[] = [];

    if (fullHistory.groups && fullHistory.groups.length > 0) {
      // Trier les groupes par nom, sur une copie : `sort` trie en place, et trier directement
      // `fullHistory.groups` réordonnait les données du composant appelant à chaque impression.
      const sortedGroups = [...fullHistory.groups]
        .sort((a, b) => a.groupName.localeCompare(b.groupName));
      sortedGroups.forEach((group, index) => {

        // Chaque groupe démarre sur une page neuve (sauf le premier, qui suit l'en-tête de
        // l'étudiant). En flux continu, le titre du groupe se retrouvait seul en bas de page,
        // ses séries commençant à la page suivante : le lecteur devait tourner la page pour
        // savoir à quel groupe appartenait un tableau. Un groupe = une page rend aussi le
        // document consultable groupe par groupe.
        content.push(
          {
            ...(index > 0 ? { pageBreak: 'before' as const } : {}),
            canvas: [
              {
                type: 'line',
                x1: 0, y1: 0,
                x2: 515, y2: 0,
                lineWidth: 2,
                lineColor: '#2F5496'
              }
            ],
            // Marge haute réduite en tête de page : les 20 points d'origine servaient à
            // détacher le trait du bloc précédent, qui n'existe plus ici.
            margin: [0, index > 0 ? 0 : 20, 0, 10]
          },
          {
            text: this.t('pdf.group', { name: group.groupName }),
            style: 'sectionHeader',
            alignment: 'left'
          },
          { text: '\n' }
        );

        // S'il y a des séries
        if (group.series && group.series.length > 0) {
          group.series.forEach(series => {

            // Vérifier si la série a des sessions
            if (!series.sessions || series.sessions.length === 0) {
              // Aucune session => message
              content.push({
                text: this.t('pdf.noSessionInSeries'),
                italics: true,
                margin: [0, 0, 0, 10]
              });
            } else {
              // sessions présentes => distinguer rattrapage ou normal
              // Test : si toutes les sessions sont catchUpSession = true => rattrapage
              const allAreCatchUp = series.sessions.every(s => s.catchUpSession);

              if (allAreCatchUp) {
                // Affichage "Session de rattrapage"
                content.push({
                  text: this.t('pdf.catchUpSeries', { name: series.seriesName }),
                  style: 'subsectionHeader',
                  alignment: 'left'
                });
              } else {
                // Série “normale” => afficher paiement
                const seriesTitle = series.isExempted
                  ? this.t('pdf.seriesExempted', { name: series.seriesName })
                  : this.t('pdf.series', { name: series.seriesName });
                content.push(
                  {
                    columns: [
                      { text: seriesTitle, style: 'subsectionHeader', alignment: 'left' },
                      {
                        text: this.t('pdf.seriesPayment',
                          { status: this.label('seriesStatus', series.paymentStatus) }),
                        alignment: 'right',
                        style: 'subsectionHeader'
                      }
                    ]
                  },
                  {
                    text: this.t('pdf.amountPaidOfTotal', {
                      paid: this.amount(series.totalAmountPaid),
                      total: this.amount(series.totalCost)
                    }),
                    alignment: 'right',
                    margin: [0, 0, 0, 2]
                  },
                  // Coût énoncé en clair, « 2 séances × 6 000 DA = 12 000 DA » : sans lui, un
                  // total inférieur au coût nominal de la série reste inexplicable. Le mot
                  // « prorata » n'apparaît pas sur un document remis à la famille.
                  {
                    text: this.t('pdf.costBreakdown', {
                      amount: this.amount(series.totalCost),
                      count: countBillableSessions(series),
                      price: this.amount(series.unitPriceNet ?? 0)
                    }),
                    alignment: 'right',
                    margin: [0, 0, 0, 10]
                  }
                );

                // Séances écartées : elles figurent dans le tableau, il faut dire
                // explicitement qu'elles ne sont pas dues.
                const excluded = countExcludedSessions(series);
                if (excluded > 0) {
                  content.push({
                    text: this.t('pdf.excludedSessions', { count: excluded }),
                    alignment: 'right',
                    italics: true,
                    margin: [0, 0, 0, 10]
                  });
                }

                // Trop-perçu : la somme des montants affectés aux séances est inférieure au
                // versement. Sans cette ligne, le total annoncé restait injustifiable à la
                // lecture du tableau.
                if (series.totalOverpaid != null && series.totalOverpaid > 0) {
                  content.push({
                    text: this.t('pdf.amountAllocatedAndOverpaid', {
                      allocated: this.amount(series.totalAllocated),
                      overpaid: this.amount(series.totalOverpaid)
                    }),
                    alignment: 'right',
                    color: '#00695c',
                    margin: [0, 0, 0, 10]
                  });
                }

                // Afficher le total remboursé lorsqu'il est strictement positif.
                if (series.totalRefunded != null && series.totalRefunded > 0) {
                  content.push({
                    text: this.t('pdf.amountRefunded', { amount: this.amount(series.totalRefunded) }),
                    alignment: 'right',
                    color: '#e60000',
                    margin: [0, 0, 0, 10]
                  });
                }
              }

              // Afficher le tableau des sessions
              content.push(this.getSessionsTable(series.sessions));
              content.push({ text: '\n' });
            }
          });
        } else {
          // Pas de séries du tout
          content.push({
            text: this.t('pdf.noSessionInGroup'),
            italics: true,
            margin: [0, 0, 0, 10]
          });
        }

        content.push({ text: '\n' });
      });
    } else {
      // Aucun groupe dans fullHistory
      content.push({
        text: this.t('pdf.noGroup'),
        italics: true
      });
    }

    return content;
  }


  private getSessionsTable(sessions: SessionHistoryDTO[]): Content {

    // IMPORTANT: Filtrer les sessions avec paiement CANCELLED
    const activeSessions = sessions.filter(session => session.paymentStatus !== 'CANCELLED');

    const body: any[] = [];

    // Définir la ligne d'en-tête
    const headerRow: any[] = [
      { text: this.t('pdf.table.session'), style: 'tableHeader' },
      { text: this.t('pdf.table.date'), style: 'tableHeader' },
      { text: this.t('pdf.table.attendance'), style: 'tableHeader' },
      { text: this.t('pdf.table.justified'), style: 'tableHeader' },
      { text: this.t('pdf.table.description'), style: 'tableHeader' },
      { text: this.t('pdf.table.paymentDate'), style: 'tableHeader' },
      { text: this.t('pdf.table.payment'), style: 'tableHeader' },
      { text: this.t('pdf.table.amountPaid'), style: 'tableHeader' }
    ];

    body.push(headerRow);

    // Ajouter les lignes de données avec couleurs (sans les CANCELLED)
    activeSessions.forEach(session => {
      const fillColor = this.getFillColorForAttendance(session);

      // Étiquetage du titre : « non facturée » pour une séance écartée du prorata,
      // « rattrapage » pour une séance antérieure à l'inscription facturée parce que suivie
      // (exigences 11.3, 11.5). Le fond gris seul ne distinguerait pas la première d'une
      // séance due, notamment à l'impression noir et blanc.
      const excluded = isExcludedSession(session);
      let sessionTitle: string;
      if (excluded) {
        sessionTitle = this.t('pdf.excludedSession', { name: session.sessionName || '—' });
      } else if (isCatchUpBilled(session)) {
        sessionTitle = this.t('pdf.catchUpSession', { name: session.sessionName || '—' });
      } else {
        sessionTitle = session.sessionName || '—';
      }

      // La justification ne concerne que les absences.
      const justificationText = session.attendanceStatus === 'ABSENT'
        ? this.t(session.isJustified ? 'common.yes' : 'common.no')
        : '';

      // Statut de paiement : une séance écartée n'en a pas. Le code « UNPAID » que le
      // serveur renvoie par défaut la faisait imprimer « Non payé », donc comme une dette.
      const paymentText = excluded
        ? this.t('excludedTag')
        : this.label('payment', session.paymentStatus);
      const amountText = excluded
        ? '—'
        : this.t('pdf.amount', { amount: this.amount(session.amountPaid) });

      const row: any[] = [
        { text: sessionTitle, fillColor },
        { text: this.formatDate(session.sessionDate), fillColor },
        { text: this.label('attendance', session.attendanceStatus), fillColor },
        { text: justificationText, fillColor },
        { text: session.description || '', fillColor },
        { text: this.formatDate(session.paymentDate), fillColor },
        { text: paymentText, fillColor },
        { text: amountText, fillColor }
      ];

      body.push(row);
    });

    return {
      table: {
        headerRows: 1,
        widths: ['auto', 'auto', 'auto', 'auto', '*', 'auto', 'auto', 'auto'],
        body: body
      },
      layout: 'noBorders',
      alignment: 'center',
      margin: [0, 10, 0, 10]
    };
  }

  /**
   * Couleur de la ligne, choisie sur les CODES du backend.
   *
   * <p>La version précédente comparait des libellés affichables accentués
   * (« présent », « en cours ») : la moindre traduction ou reformulation faisait
   * silencieusement tomber toutes les lignes en blanc.</p>
   */
  private getFillColorForAttendance(session: SessionHistoryDTO): string {
    const isPresent = session.attendanceStatus === 'PRESENT';
    const isAbsent = session.attendanceStatus === 'ABSENT';
    const isCompleted = session.paymentStatus === 'PAID';
    const isInProgress = session.paymentStatus === 'PARTIAL';
    const isUnpaid = session.paymentStatus === 'UNPAID';

    // Séance écartée du prorata : fond neutre, jamais le rouge des séances dues. Le titre
    // et la colonne de paiement portent l'étiquette « non facturée » (exigence 11.4).
    if (isExcludedSession(session)) {
      return '#f5f5f5';
    }

    // Présent et exempté (réduction 100 %) : couleur dédiée, prioritaire sur le vert « payé ».
    if (session.isExempted === true && isPresent) {
      return PdfGeneratorService.EXEMPTED_PRESENT_COLOR; // Présent + exempté
    }

    if (isCompleted && isPresent) {
      return '#32a852'; // Présent + payé
    } else if (isCompleted && isAbsent) {
      return '#ff6347'; // Absent + payé
    } else if (isInProgress && isPresent) {
      return '#ffd700'; // Présent + en cours
    } else if (isInProgress && isAbsent) {
      return '#ff4500'; // Absent + en cours
    } else if (isUnpaid && isPresent) {
      return '#e60000'; // Présent + non payé
    } else if (!session.attendanceStatus || session.attendanceStatus === 'UNKNOWN') {
      return '#f5f5f5'; // Présence non renseignée
    } else {
      return '#ffffff';
    }
  }
}
