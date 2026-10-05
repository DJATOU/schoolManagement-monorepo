import { TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import frTranslations from '../../../../assets/i18n/fr.json';
import pdfMake from 'pdfmake/build/pdfmake';

import { PdfGeneratorService } from './pdf-generator.service';
import { SessionHistoryDTO } from '../../../models/session/SessionHistoryDTO';
import { StudentFullHistoryDTO } from '../domain/StudentFullHistoryDTO';
import { StudentRefund } from '../../../models/refund/refund';
import { SeriesHistoryDTO } from '../../../models/sessionSerie/SeriesHistoryDTO';
import { TDocumentDefinitions } from 'pdfmake/interfaces';

/**
 * Tests unitaires du PdfGeneratorService (tâche 18.3).
 * Couvre :
 *  - la couleur « Présent et exempté » retournée par getFillColorForAttendance ;
 *  - les couleurs existantes inchangées ;
 *  - l'indicateur de rattrapage (préfixe traduit « Séance de rattrapage : ») ;
 *  - le rendu de l'historique (rattrapages / exemptions / remboursements).
 */
describe('PdfGeneratorService', () => {
  let service: PdfGeneratorService;

  const EXEMPTED_COLOR = '#1e88e5';
  const ABSENT_PAID_COLOR = '#dfe3e8';

  const baseSession = (overrides: Partial<SessionHistoryDTO> = {}): SessionHistoryDTO => ({
    catchUpSession: false,
    sessionId: 1,
    sessionName: 'Séance 1',
    sessionDate: '2024-01-10',
    attendanceStatus: 'PRESENT',
    isJustified: false,
    description: '',
    paymentStatus: 'PAID',
    amountPaid: 30,
    paymentDate: '2024-01-10',
    ...overrides
  });

  beforeEach(() => {
    // Le service traduit ses libellés : on charge les vraies traductions FR pour que
    // les tests vérifient le rendu réel et non des clés techniques.
    TestBed.configureTestingModule({ imports: [TranslateModule.forRoot()] });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', frTranslations as any);
    translate.use('fr');
    service = TestBed.inject(PdfGeneratorService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getFillColorForAttendance', () => {
    const color = (session: SessionHistoryDTO): string =>
      (service as any).getFillColorForAttendance(session);

    it('returns the dedicated exempted color for an exempted + present session', () => {
      const session = baseSession({ isExempted: true, attendanceStatus: 'PRESENT' });
      expect(color(session)).toBe(EXEMPTED_COLOR);
    });

    it('prioritizes exempted color over the completed/present green', () => {
      const session = baseSession({ isExempted: true, paymentStatus: 'PAID', attendanceStatus: 'PRESENT' });
      expect(color(session)).toBe(EXEMPTED_COLOR);
      expect(color(session)).not.toBe('#32a852');
    });

    it('does NOT apply exempted color when the student is absent', () => {
      const session = baseSession({ isExempted: true, attendanceStatus: 'ABSENT', paymentStatus: 'PAID' });
      expect(color(session)).not.toBe(EXEMPTED_COLOR);
    });

    it('returns green (#32a852) for present + completed (unchanged)', () => {
      const session = baseSession({ attendanceStatus: 'PRESENT', paymentStatus: 'PAID' });
      expect(color(session)).toBe('#32a852');
    });

    it('returns a neutral colour for absent + completed, never an alert colour', () => {
      // Décision du propriétaire produit : une séance absente mais réglée n'est pas une dette. Le
      // rouge tomate d'origine (#ff6347) la faisait lire comme un impayé.
      const session = baseSession({ attendanceStatus: 'ABSENT', paymentStatus: 'PAID' });
      expect(color(session)).toBe(ABSENT_PAID_COLOR);
      // Distincte du gris des présences non renseignées et des séances écartées.
      expect(color(session)).not.toBe('#f5f5f5');
    });

    it('returns #ffd700 for present + in progress', () => {
      const session = baseSession({ attendanceStatus: 'PRESENT', paymentStatus: 'PARTIAL' });
      expect(color(session)).toBe('#ffd700');
    });

    it('returns #ff4500 for absent + in progress', () => {
      const session = baseSession({ attendanceStatus: 'ABSENT', paymentStatus: 'PARTIAL' });
      expect(color(session)).toBe('#ff4500');
    });

    it('returns #e60000 for present + unpaid', () => {
      const session = baseSession({ attendanceStatus: 'PRESENT', paymentStatus: 'UNPAID' });
      expect(color(session)).toBe('#e60000');
    });

    it('returns #f5f5f5 when attendance is not filled in', () => {
      const session = baseSession({ attendanceStatus: '', paymentStatus: '' });
      expect(color(session)).toBe('#f5f5f5');
    });
  });

  describe('PDF legend', () => {
    let openedDefinition: any;

    beforeEach(() => {
      // Interception de la définition de document passée à pdfMake.
      spyOn(service as any, 'convertImageToBase64').and.returnValue(Promise.resolve(''));
      spyOn(URL, 'createObjectURL').and.returnValue('blob:fake');
      spyOn(window, 'open');
    });

    const collectTexts = (node: any, acc: string[]): void => {
      if (node == null) { return; }
      if (Array.isArray(node)) {
        node.forEach(n => collectTexts(n, acc));
        return;
      }
      if (typeof node === 'object') {
        if (typeof node.text === 'string') { acc.push(node.text); }
        else if (Array.isArray(node.text)) { collectTexts(node.text, acc); }
        if (node.columns) { collectTexts(node.columns, acc); }
        if (node.table && node.table.body) { collectTexts(node.table.body, acc); }
        if (node.stack) { collectTexts(node.stack, acc); }
      }
    };

    const collectFillColors = (node: any, acc: string[]): void => {
      if (node == null) { return; }
      if (Array.isArray(node)) {
        node.forEach(n => collectFillColors(n, acc));
        return;
      }
      if (typeof node === 'object') {
        if (typeof node.fillColor === 'string') { acc.push(node.fillColor); }
        if (node.columns) { collectFillColors(node.columns, acc); }
        if (node.table && node.table.body) { collectFillColors(node.table.body, acc); }
        if (node.stack) { collectFillColors(node.stack, acc); }
      }
    };

    const fullHistory: StudentFullHistoryDTO = {
      studentId: 1,
      studentName: 'Jean Dupont',
      catchUp: false,
      groups: [
        {
          groupName: 'Groupe Math',
          series: [
            {
              seriesId: 10,
              seriesName: 'Série A',
              paymentStatus: 'PAID',
              totalAmountPaid: 240,
              totalCost: 240,
              isExempted: true,
              totalRefunded: 30,
              sessions: [
                {
                  catchUpSession: true,
                  sessionId: 100,
                  sessionName: 'Rattrapage 1',
                  sessionDate: '2024-01-15',
                  attendanceStatus: 'PRESENT',
                  isJustified: false,
                  description: '',
                  paymentStatus: 'PAID',
                  amountPaid: 30,
                  paymentDate: '2024-01-15',
                  isExempted: true
                }
              ]
            }
          ]
        } as any
      ]
    } as any;

    /** Nœud de document réduit à ce que ces tests inspectent. */
    interface InspectedNode { pageBreak?: unknown }

    /** Sauts de page rencontrés, dans l'ordre du document. */
    const collectPageBreaks = (node: unknown, acc: string[]): void => {
      if (node === null || node === undefined) { return; }
      if (Array.isArray(node)) {
        node.forEach(child => collectPageBreaks(child, acc));
        return;
      }
      if (typeof node === 'object') {
        const pageBreak = (node as InspectedNode).pageBreak;
        if (typeof pageBreak === 'string') { acc.push(pageBreak); }
      }
    };

    /**
     * Intercepte la définition passée à pdfMake et renvoie ses sauts de page.
     *
     * <p>Factorisé pour que chaque test n'exprime que son scénario : le double de `createPdf`
     * n'a aucune valeur documentaire répété trois fois.</p>
     */
    async function pageBreaksOf(history: StudentFullHistoryDTO): Promise<string[]> {
      let captured: { content?: unknown } = {};
      spyOn(pdfMakeModule(), 'createPdf').and.callFake(((definition: { content?: unknown }) => {
        captured = definition;
        return { getBlob: (callback: (blob: Blob) => void) => callback(new Blob()) };
      }) as unknown as typeof pdfMake.createPdf);

      await service.generateFullHistoryPdf(history, 'logo.png');

      const breaks: string[] = [];
      collectPageBreaks(captured.content, breaks);
      return breaks;
    }

    /** Copie de l'historique avec les groupes nommés dans l'ordre donné. */
    function historyWithGroups(...names: string[]): StudentFullHistoryDTO {
      return {
        ...fullHistory,
        groups: names.map(groupName => ({ ...fullHistory.groups[0], groupName }))
      };
    }

    it('starts every group but the first on a new page', async () => {
      // En flux continu, le titre du groupe se retrouvait seul en bas de page, ses séries
      // commençant à la page suivante : il fallait tourner la page pour savoir à quel groupe
      // appartenait un tableau. Le premier groupe, lui, suit l'en-tête de l'étudiant.
      expect(await pageBreaksOf(historyWithGroups('Groupe Math', 'Groupe Physique')))
        .toEqual(['before']);
    });

    it('does not force a page break when the student has a single group', async () => {
      expect(await pageBreaksOf(fullHistory)).toEqual([]);
    });

    it('leaves the caller\'s group order untouched', async () => {
      // `sort` trie en place : trier directement `fullHistory.groups` réordonnait les données
      // de l'écran appelant à chaque impression.
      const unsorted = historyWithGroups('Zèbre', 'Alpha');

      await pageBreaksOf(unsorted);

      expect(unsorted.groups.map(group => group.groupName)).toEqual(['Zèbre', 'Alpha']);
    });

    it('includes the "Présent et exempté" legend entry and its dedicated color', async () => {
      let captured: any;
      spyOn(pdfMakeModule(), 'createPdf').and.callFake((def: any) => {
        captured = def;
        return { getBlob: (cb: (b: any) => void) => cb(new Blob()) } as any;
      });

      await service.generateFullHistoryPdf(fullHistory, 'logo.png');

      const texts: string[] = [];
      collectTexts(captured.content, texts);
      expect(texts).toContain('Présent et exempté');

      const fills: string[] = [];
      collectFillColors(captured.content, fills);
      expect(fills).toContain(EXEMPTED_COLOR);
    });

    it('includes a catch-up indicator entry in the legend', async () => {
      let captured: any;
      spyOn(pdfMakeModule(), 'createPdf').and.callFake((def: any) => {
        captured = def;
        return { getBlob: (cb: (b: any) => void) => cb(new Blob()) } as any;
      });

      await service.generateFullHistoryPdf(fullHistory, 'logo.png');

      const texts: string[] = [];
      collectTexts(captured.content, texts);
      const hasCatchUpLegend = texts.some(t => t.toLowerCase().includes('rattrapage'));
      expect(hasCatchUpLegend).toBeTrue();
    });

    it('prints the justification of an absence as « Oui » / « Non », never as a raw key', async () => {
      let captured: any;
      spyOn(pdfMakeModule(), 'createPdf').and.callFake((def: any) => {
        captured = def;
        return { getBlob: (cb: (b: any) => void) => cb(new Blob()) } as any;
      });
      const absent = (sessionId: number, isJustified: boolean) => ({
        ...fullHistory.groups[0].series[0].sessions[0], catchUpSession: false, sessionId,
        sessionName: 'Séance ' + sessionId, attendanceStatus: 'ABSENT', isJustified, isExempted: false
      });
      const history = {
        ...fullHistory,
        groups: [{ ...fullHistory.groups[0], series: [{ ...fullHistory.groups[0].series[0], isExempted: false,
          sessions: [absent(101, false), absent(102, true)] }] }]
      } as any;

      await service.generateFullHistoryPdf(history, 'logo.png');

      const texts: string[] = [];
      collectTexts(captured.content, texts);
      expect(texts).toContain('Non');
      expect(texts).toContain('Oui');
      expect(texts.filter(t => t.startsWith('studentHistory.') || t.startsWith('common.')))
        .withContext('clés imprimées brutes').toEqual([]);
    });

    it('lets a long title or description wrap between words, never a date or an amount', async () => {
      // « Descriptio / n » : en 11 pt, les huit colonnes ne tenaient pas sur une page A4 et les
      // mots se coupaient. Le titre et la description prennent la place restante et vont à la
      // ligne ; dates et montants restent sur une ligne.
      let captured: any;
      spyOn(pdfMakeModule(), 'createPdf').and.callFake((def: any) => {
        captured = def;
        return { getBlob: (cb: (b: any) => void) => cb(new Blob()) } as any;
      });

      await service.generateFullHistoryPdf(fullHistory, 'logo.png');

      const findTable = (node: any): any => {
        if (Array.isArray(node)) { return node.map(findTable).find(Boolean); }
        if (node && typeof node === 'object') {
          if (node.style === 'historyTable') { return node; }
          return findTable(node.stack ?? node.columns);
        }
        return undefined;
      };
      const table = findTable(captured.content);
      expect(table.table.widths).toEqual(['*', 'auto', 'auto', 'auto', '*', 'auto', 'auto', 'auto']);
      expect(captured.styles.historyTable.fontSize).toBeLessThan(10);
      expect(captured.styles.tableHeader.fontSize).toBeLessThan(10);
      const row = table.table.body[1];
      expect(row[0].alignment).toBe('left');
      expect(row[4].alignment).toBe('left');
      expect([row[1].noWrap, row[5].noWrap, row[7].noWrap]).toEqual([true, true, true]);
      expect(table.layout.paddingTop()).toBeGreaterThan(0);
      expect(table.layout.hLineWidth()).toBe(0);
    });

    it('prefixes a catch-up session title with the translated catch-up label', async () => {
      let captured: any;
      spyOn(pdfMakeModule(), 'createPdf').and.callFake((def: any) => {
        captured = def;
        return { getBlob: (cb: (b: any) => void) => cb(new Blob()) } as any;
      });

      await service.generateFullHistoryPdf(fullHistory, 'logo.png');

      const texts: string[] = [];
      collectTexts(captured.content, texts);
      const hasPrefixedTitle = texts.some(t => t.includes('Séance de rattrapage : Rattrapage 1'));
      expect(hasPrefixedTitle).toBeTrue();
    });

    it('renders the refunded amount when totalRefunded > 0', async () => {
      let captured: any;
      spyOn(pdfMakeModule(), 'createPdf').and.callFake((def: any) => {
        captured = def;
        return { getBlob: (cb: (b: any) => void) => cb(new Blob()) } as any;
      });

      const historyWithNormalSeries: StudentFullHistoryDTO = {
        studentId: 2,
        studentName: 'Marie Martin',
        catchUp: false,
        groups: [
          {
            groupName: 'Groupe Physique',
            series: [
              {
                seriesId: 20,
                seriesName: 'Série B',
                paymentStatus: 'PAID',
                totalAmountPaid: 240,
                totalCost: 240,
                isExempted: false,
                totalRefunded: 60,
                sessions: [ baseSession({ sessionId: 200, sessionName: 'Séance B1' }) ]
              }
            ]
          } as any
        ]
      } as any;

      await service.generateFullHistoryPdf(historyWithNormalSeries, 'logo.png');

      const texts: string[] = [];
      collectTexts(captured.content, texts);
      const hasRefund = texts.some(t => t.includes('Montant remboursé') && t.includes('60'));
      expect(hasRefund).toBeTrue();
    });

    describe('refunds, net paid and status', () => {
      /** Intercepte la définition passée à pdfMake ; la lecture se fait après génération. */
      function captureDefinition(): () => TDocumentDefinitions {
        let captured: TDocumentDefinitions = { content: [] };
        spyOn(pdfMake, 'createPdf').and.callFake(((definition: TDocumentDefinitions) => {
          captured = definition;
          return { getBlob: (callback: (blob: Blob) => void) => callback(new Blob()) };
        }) as never);
        return () => captured;
      }

      /** Document produit pour une série, avec ou sans liste de remboursements. */
      async function textsFor(series: Partial<SeriesHistoryDTO>, refunds: StudentRefund[] = []): Promise<string[]> {
        const definition = captureDefinition();
        const history: StudentFullHistoryDTO = {
          studentId: 4, studentName: 'Camille Amrani', catchUp: false,
          groups: [{
            groupId: 1,
            groupName: 'Maths 4 AM A',
            catchUp: false,
            series: [{
              seriesId: 40, seriesName: 'Octobre', paymentStatus: 'PARTIAL', isExempted: false,
              totalAmountPaid: 0, totalCost: 2400,
              sessions: [baseSession({ sessionId: 400, sessionName: 'Séance 1' })],
              ...series
            }]
          }]
        };

        await service.generateFullHistoryPdf(history, 'logo.png', refunds);

        const texts: string[] = [];
        collectTexts(definition().content, texts);
        return texts;
      }

      const refund = (overrides: Partial<StudentRefund> = {}): StudentRefund => ({
        refundId: 7, refundNumber: 'REMB-2026-0007', refundDate: '2026-10-05T10:00:00',
        amount: 400, reason: 'Trop-perçu', seriesId: 40, seriesName: 'Octobre', ...overrides
      });

      it('prints the paid amount as net of refunds when money was returned', async () => {
        const texts = await textsFor({ paymentStatus: 'PARTIAL', totalAmountPaid: 2000, totalRefunded: 400 });

        expect(texts).toContain('Total versé, net des remboursements : 2 000,00 DA / 2 400,00 DA dus');
      });

      it('keeps the plain paid label when nothing was refunded', async () => {
        const texts = await textsFor({ paymentStatus: 'FULL', totalAmountPaid: 2400, totalRefunded: 0 });

        expect(texts).toContain('Total versé : 2 400,00 DA / 2 400,00 DA dus');
      });

      it('prints « Non payé » for a series with nothing paid', async () => {
        const texts = await textsFor({ paymentStatus: 'UNPAID', totalAmountPaid: 0, totalRefunded: 0 });

        expect(texts).toContain('Paiement : Non payé');
      });

      it('lists each refund of the series with its number, date, amount and reason', async () => {
        const texts = await textsFor(
          { paymentStatus: 'PARTIAL', totalAmountPaid: 2000, totalRefunded: 400 },
          [refund(), refund({ refundId: 8, refundNumber: 'REMB-2026-0009', seriesId: 99, amount: 50 })]);

        expect(texts).toContain('Pièce REMB-2026-0007 du 05/10/2026 : 400,00 DA — Trop-perçu');
        // Le remboursement d'une autre série n'est pas imprimé sous celle-ci.
        expect(texts.some(text => text.includes('REMB-2026-0009'))).toBeFalse();
      });

      it('names a missing reason instead of printing a blank', async () => {
        const texts = await textsFor(
          { paymentStatus: 'PARTIAL', totalAmountPaid: 2000, totalRefunded: 400 },
          [refund({ reason: '  ' })]);

        expect(texts).toContain('Pièce REMB-2026-0007 du 05/10/2026 : 400,00 DA — Motif non renseigné');
      });

      it('uses the neutral colour for absent + paid in the legend too', async () => {
        const definition = captureDefinition();

        await service.generateFullHistoryPdf(fullHistory, 'logo.png');

        const colors: string[] = [];
        collectFillColors(definition().content, colors);
        expect(colors).toContain(ABSENT_PAID_COLOR);
        expect(colors).not.toContain('#ff6347');
      });
    });

    it('marks an exempted series title with "(exempté)"', async () => {
      let captured: any;
      spyOn(pdfMakeModule(), 'createPdf').and.callFake((def: any) => {
        captured = def;
        return { getBlob: (cb: (b: any) => void) => cb(new Blob()) } as any;
      });

      const historyExemptedNormalSeries: StudentFullHistoryDTO = {
        studentId: 3,
        studentName: 'Ali Ben',
        catchUp: false,
        groups: [
          {
            groupName: 'Groupe Chimie',
            series: [
              {
                seriesId: 30,
                seriesName: 'Série C',
                paymentStatus: 'PAID',
                totalAmountPaid: 0,
                totalCost: 0,
                isExempted: true,
                totalRefunded: 0,
                sessions: [ baseSession({ sessionId: 300, sessionName: 'Séance C1', isExempted: true }) ]
              }
            ]
          } as any
        ]
      } as any;

      await service.generateFullHistoryPdf(historyExemptedNormalSeries, 'logo.png');

      const texts: string[] = [];
      collectTexts(captured.content, texts);
      const hasExemptedTitle = texts.some(t => t.includes('Série C') && t.includes('exempté'));
      expect(hasExemptedTitle).toBeTrue();
    });
  });
});

// Helper pour accéder au module pdfMake importé par le service (pour espionner createPdf).
function pdfMakeModule(): any {
  return pdfMake as any;
}
