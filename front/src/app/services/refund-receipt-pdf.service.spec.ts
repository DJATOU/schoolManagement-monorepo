import { TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import pdfMake from 'pdfmake/build/pdfmake';
import { TDocumentDefinitions } from 'pdfmake/interfaces';
import frTranslations from '../../assets/i18n/fr.json';
import enTranslations from '../../assets/i18n/en.json';

import { RefundReceiptPdfService } from './refund-receipt-pdf.service';
import { RefundReceipt } from '../models/refund/refund';

/**
 * Tests du reçu de remboursement.
 *
 * <p>Le défaut corrigé : le numéro de pièce était transmis sous le paramètre `reference` alors que le
 * gabarit attend `{{number}}`. ngx-translate ne signale rien dans ce cas, il imprime le gabarit
 * brut : le reçu portait « Pièce n° {{number}} », sans numéro. D'où le test général « aucun gabarit
 * non résolu », qui couvre aussi les autres paramètres du document.</p>
 */
describe('RefundReceiptPdfService', () => {
  let service: RefundReceiptPdfService;
  let translate: TranslateService;

  const receipt = (overrides: Partial<RefundReceipt> = {}): RefundReceipt => ({
    refundId: 7,
    refundNumber: 'REMB-2026-0007',
    refundDate: new Date('2026-10-05T10:00:00'),
    amount: 400,
    reason: 'Trop-perçu sur la série d\'octobre',
    studentFirstName: 'Camille',
    studentLastName: 'Amrani',
    paymentDate: new Date('2026-10-01T09:00:00'),
    amountPaid: 2400,
    groupName: 'Maths 4 AM A',
    seriesName: 'Octobre',
    recordedBy: 'admin',
    issuanceRank: 1,
    issuedAt: '2026-10-05T10:00:00',
    fileName: 'remboursement-REMB-2026-0007.pdf',
    ...overrides
  });

  /** Tous les textes du document, pied de page compris. */
  const collectTexts = (node: unknown, acc: string[]): string[] => {
    if (node == null) { return acc; }
    if (Array.isArray(node)) {
      node.forEach(child => collectTexts(child, acc));
      return acc;
    }
    if (typeof node === 'object') {
      const record = node as Record<string, unknown>;
      if (typeof record['text'] === 'string') { acc.push(record['text']); }
      Object.entries(record)
        .filter(([key]) => key !== 'text' || typeof record['text'] !== 'string')
        .forEach(([, value]) => collectTexts(value, acc));
    }
    return acc;
  };

  /** Produit le reçu par le chemin public et renvoie ses textes. */
  async function textsOf(value: RefundReceipt): Promise<string[]> {
    let captured: TDocumentDefinitions = { content: [] };
    spyOn(pdfMake, 'createPdf').and.callFake(((definition: TDocumentDefinitions) => {
      captured = definition;
      return { download: () => undefined };
    }) as never);
    spyOn(service as unknown as { loadLogo: () => Promise<string> }, 'loadLogo')
      .and.returnValue(Promise.resolve(''));

    await service.download(value);

    const texts = collectTexts(captured.content, []);
    const footer = captured.footer as () => unknown;
    collectTexts(footer(), texts);
    return texts;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [TranslateModule.forRoot()] });
    translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', frTranslations);
    translate.setTranslation('en', enTranslations);
    translate.use('fr');
    service = TestBed.inject(RefundReceiptPdfService);
  });

  it('prints the refund number in the header', async () => {
    const texts = await textsOf(receipt());

    expect(texts).toContain('Pièce n° REMB-2026-0007');
  });

  it('prints the refund number in English too', async () => {
    translate.use('en');

    const texts = await textsOf(receipt());

    expect(texts).toContain('Document no. REMB-2026-0007');
  });

  it('leaves no unresolved template in the document, duplicate mention included', async () => {
    // Rang 2 : la mention « DUPLICATA » et ses deux paramètres entrent dans le document.
    const texts = await textsOf(receipt({ issuanceRank: 2 }));

    expect(texts.some(text => text.startsWith('DUPLICATA n° 2'))).toBeTrue();
    expect(texts.filter(text => text.includes('{{'))).toEqual([]);
  });
});
