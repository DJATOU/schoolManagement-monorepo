import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { TDocumentDefinitions } from 'pdfmake/interfaces';
import frTranslations from '../../assets/i18n/fr.json';
import { payout, slip } from '../../testing/payroll-fixtures';
import { setupServiceTestBed } from '../../testing/setup';
import { PayoutSlipPdfService } from './payout-slip-pdf.service';
import { PdfOutputService } from './pdf-output.service';

/** Tous les textes du document, en-tête, contenu et pied de page compris. */
function texts(doc: TDocumentDefinitions): string[] {
  const found: string[] = [];
  const walk = (node: unknown): void => {
    if (typeof node === 'string') {
      found.push(node);
    } else if (Array.isArray(node)) {
      node.forEach(walk);
    } else if (node && typeof node === 'object') {
      Object.values(node as Record<string, unknown>).forEach(walk);
    }
  };
  walk(doc.content);
  const footer = doc.footer as unknown;
  if (typeof footer === 'function') {
    walk((footer as (current: number, total: number) => unknown)(1, 1));
  }
  return found;
}

describe('PayoutSlipPdfService', () => {
  let service: PayoutSlipPdfService;
  let output: jasmine.SpyObj<PdfOutputService>;

  beforeEach(() => {
    output = jasmine.createSpyObj<PdfOutputService>('PdfOutputService', ['print', 'imageDataUrl']);
    output.imageDataUrl.and.resolveTo('');
    setupServiceTestBed({ providers: [{ provide: PdfOutputService, useValue: output }] });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', frTranslations);
    translate.use('fr');
    service = TestBed.inject(PayoutSlipPdfService);
  });

  it('bordereau de paie : numéro, parties, calcul en clair, deux parts, auteur, deux signatures', () => {
    const all = texts(service.buildDocument(slip(), '')).join('\n');

    expect(all).toContain('Bordereau de paie');
    expect(all).toContain('N° PAIE-2030-0001');
    expect(all).toContain('Nadia Aït Ahmed');
    expect(all).toContain('Maths 4 AM A');
    expect(all).toContain('Octobre');
    expect(all).toContain('Standard (60 %)');
    expect(all).toContain('74 000,00 DA');
    expect(all).toContain('72 000,00 DA × 60 % = 43 200,00 DA');
    expect(all).toContain('Somme remise à l\'enseignant');
    expect(all).toContain('43 200,00 DA');
    expect(all).toContain('Part de l\'école : 28 800,00 DA');
    expect(all).toContain('Enregistrée par directrice');
    expect(all).toContain('Signature de l\'enseignant');
    expect(all).toContain('Signature de l\'administration');
    expect(all).not.toContain('DUPLICATA');
    expect(all).not.toContain('{{');
  });

  it('original sans tampon ; réimpression : DUPLICATA et son rang', () => {
    const original = service.buildDocument(slip(), '');
    expect(original.watermark).toBeUndefined();

    const all = texts(service.buildDocument(slip({ issuanceRank: 3 }), '')).join('\n');
    expect(all).toMatch(/DUPLICATA n° 3 — /);
  });

  it('paie annulée : tampon ANNULÉE, date, auteur, motif et remplaçante', () => {
    const cancelled = slip({
      payout: payout({
        status: 'CANCELLED', cancelledAt: '2030-02-03T09:00:00', cancelledBy: 'directrice',
        cancelReasonType: 'DATA_ENTRY_ERROR', cancelReasonText: 'Mauvais taux choisi', replacedByNumber: 'PAIE-2030-0002'
      })
    });
    const doc = service.buildDocument(cancelled, '');
    const all = texts(doc).join('\n');

    expect((doc.watermark as { text: string }).text).toBe('ANNULÉE');
    expect(all).toContain('Annulée le ');
    expect(all).toContain('par directrice');
    expect(all).toContain('Mauvais taux choisi');
    expect(all).toContain('Remplacée par la paie PAIE-2030-0002');
  });

  it('retenue : intitulée comme telle, somme due par l\'enseignant en positif', () => {
    const deduction = slip({
      payout: payout({
        payoutNumber: 'PAIE-2030-0004', kind: 'REGULARIZATION', initialPayoutNumber: 'PAIE-2030-0001',
        collectedGross: 74000, refunded: 3000, collectedNet: 71000, baseDelta: -1000, teacherAmount: -600,
        schoolAmount: -400
      })
    });
    const all = texts(service.buildDocument(deduction, '')).join('\n');

    expect(all).toContain('Bordereau de retenue');
    expect(all).toContain('Somme due par l\'enseignant');
    expect(all).toContain('600,00 DA');
    expect(all).not.toContain('-600,00 DA');
    expect(all).toContain('Paie initiale');
    expect(all).toContain('PAIE-2030-0001');
    expect(all).toContain('Encaissé depuis la paie précédente');
    expect(all).toContain('-1 000,00 DA');
  });

  it('complément : intitulé « Bordereau de complément »', () => {
    const complement = slip({
      payout: payout({ kind: 'REGULARIZATION', baseDelta: 3000, teacherAmount: 1800, schoolAmount: 1200 })
    });
    expect(texts(service.buildDocument(complement, '')).join('\n')).toContain('Bordereau de complément');
  });

  it('impression : logo chargé, document produit sous le nom de fichier du serveur', async () => {
    output.imageDataUrl.and.resolveTo('data:image/png;base64,AAAA');

    await service.print(slip());

    expect(output.print).toHaveBeenCalledTimes(1);
    const [doc, fileName] = output.print.calls.mostRecent().args;
    expect(fileName).toBe('paie-2030-0001_nadia_ait_ahmed.pdf');
    expect(JSON.stringify(doc.content)).toContain('data:image/png;base64,AAAA');
  });
});
