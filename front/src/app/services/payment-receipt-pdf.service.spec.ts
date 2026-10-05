import { TestBed } from '@angular/core/testing';

import { PaymentReceiptData, PaymentReceiptPdfService } from './payment-receipt-pdf.service';
import { setupServiceTestBed } from '../../testing/setup';

/**
 * Le reçu réimprimé d'un versement annulé ne peut pas passer pour valide (spec admin-corrections,
 * exigence 2.6) : tampon sur toute la page, date de l'annulation, reçu de remplacement.
 *
 * <p>On vérifie la définition du document — ce que la famille aura entre les mains — sans passer
 * par l'impression.</p>
 */
describe('PaymentReceiptPdfService — reçu annulé', () => {
  let service: PaymentReceiptPdfService;

  const receipt = (overrides: Partial<PaymentReceiptData> = {}): PaymentReceiptData => ({
    reference: 'RECU-2030-0042',
    issuedAt: new Date('2030-02-03T09:15:00'),
    studentName: 'Amina Belkacem',
    groupName: 'Maths 1B',
    seriesName: 'Octobre',
    amountPaid: 3000,
    paymentMethodLabel: 'Espèces',
    isCatchUp: false,
    adminUsername: 'caissier1',
    ...overrides
  });

  beforeEach(() => {
    setupServiceTestBed();
    service = TestBed.inject(PaymentReceiptPdfService);
  });

  /** Tout le texte du document, à plat. */
  const allText = (node: unknown): string => {
    if (typeof node === 'string') {
      return node;
    }
    if (Array.isArray(node)) {
      return node.map(allText).join(' ');
    }
    if (node && typeof node === 'object') {
      return Object.values(node as Record<string, unknown>).map(allText).join(' ');
    }
    return '';
  };

  it('un reçu valide n\'a ni tampon ni mention d\'annulation', () => {
    const doc = service.buildDocument(receipt(), '');

    expect(doc.watermark).toBeUndefined();
    expect(allText(doc.content)).not.toContain('payment.receipt.cancelledOn');
  });

  it('un reçu annulé porte le tampon, la date de l\'annulation et le reçu qui le remplace', () => {
    const doc = service.buildDocument(receipt({
      cancellation: { cancelledAt: new Date('2030-02-10T14:30:00'), replacedBy: 'RECU-2030-0043' }
    }), '');

    expect(doc.watermark).toEqual(jasmine.objectContaining({ text: 'payment.receipt.cancelledStamp' }));
    const text = allText(doc.content);
    expect(text).toContain('payment.receipt.cancelledOn');
    expect(text).toContain('payment.receipt.replacedBy');
  });

  it('un reçu annulé sans remplacement n\'en nomme aucun', () => {
    const doc = service.buildDocument(receipt({
      cancellation: { cancelledAt: new Date('2030-02-10T14:30:00'), replacedBy: null }
    }), '');

    const text = allText(doc.content);
    expect(text).toContain('payment.receipt.cancelledOn');
    expect(text).not.toContain('payment.receipt.replacedBy');
  });
});
