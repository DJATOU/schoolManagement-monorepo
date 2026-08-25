import { TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import frTranslations from '../../../../assets/i18n/fr.json';
import pdfMake from 'pdfmake/build/pdfmake';

import { PdfGeneratorService } from './pdf-generator.service';
import { SessionHistoryDTO } from '../../../models/session/SessionHistoryDTO';
import { StudentFullHistoryDTO } from '../domain/StudentFullHistoryDTO';

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

    it('returns #ff6347 for absent + completed', () => {
      const session = baseSession({ attendanceStatus: 'ABSENT', paymentStatus: 'PAID' });
      expect(color(session)).toBe('#ff6347');
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
