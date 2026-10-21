import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';

import { setupServiceTestBed } from '../../../testing/setup';
import { PrintFixture, buildPrintFixtures } from '../../../testing/print-fixtures';
import { marginBoxTexts, missingContentTexts, missingFooterTexts } from '../../../testing/print-language';
import { PdfOutputService } from '../../services/pdf-output.service';
import { withPrintLanguage } from '../pdf-i18n';
import { stripBidiControls } from './bidi-isolate';
import frTranslations from '../../../assets/i18n/fr.json';
import enTranslations from '../../../assets/i18n/en.json';
import arTranslations from '../../../assets/i18n/ar.json';

/**
 * Chaque document réel de l'application, composé par son générateur à partir de données
 * réalistes, se convertit en page HTML arabe sans exception et sans perdre un seul texte : c'est la
 * preuve que le convertisseur couvre tous les nœuds employés. Un nœud ajouté demain à une définition
 * et non pris en charge fait échouer ce test, pas une impression.
 */
describe('documents réels en arabe', () => {
  const PNG = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=';
  let output: PdfOutputService;

  beforeEach(() => {
    setupServiceTestBed();
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', frTranslations);
    translate.setTranslation('en', enTranslations);
    translate.setTranslation('ar', arTranslations);
    // Interface en français : le choix « العربية » vaut pour le document seul.
    translate.use('fr');
    output = TestBed.inject(PdfOutputService);
  });

  /** Documents composés en arabe, avec leur page HTML. */
  function arabicDocuments(): { fixture: PrintFixture; html: string; missing: string[]; footer: string[] }[] {
    return withPrintLanguage('ar', () => buildPrintFixtures(PNG).map(fixture => {
      const html = output.htmlFor(fixture.doc, fixture.title);
      return { fixture, html, missing: missingContentTexts(fixture.doc, html), footer: missingFooterTexts(fixture.doc, html) };
    }));
  }

  it('les quinze documents se convertissent, sans texte perdu ni pied de page perdu', () => {
    const documents = arabicDocuments();
    expect(documents.map(d => d.fixture.name)).toEqual([
      'student-profile', 'teacher-profile', 'group-profile', 'datatable-export', 'payment-receipt',
      'payment-receipt-cancelled', 'refund-receipt', 'payout-slip', 'payout-slip-cancelled', 'correction-journal',
      'attendance-sheet', 'series-sessions', 'full-history', 'attendance-history', 'payment-history'
    ]);
    for (const { fixture, html, missing, footer } of documents) {
      expect(missing).withContext(`${fixture.name} : texte perdu`).toEqual([]);
      expect(footer).withContext(`${fixture.name} : pied de page perdu`).toEqual([]);
      expect(html).withContext(fixture.name).toContain('<html lang="ar" dir="rtl">');
    }
  });

  it('libellés arabes, sans gabarit non résolu ni clé brute', () => {
    for (const { fixture, html } of arabicDocuments()) {
      const body = new DOMParser().parseFromString(html, 'text/html').body.textContent ?? '';
      expect(body).withContext(fixture.name).toMatch(/[\u0600-\u06FF]/);
      expect(body).withContext(fixture.name).not.toContain('{{');
      expect(body).withContext(fixture.name).not.toMatch(/\b[A-Z_]+\.[A-Z_]+\b/);
    }
  });

  it('reçu : titre arabe, montants en « دج », nombres et numéro isolés, format A5', () => {
    const receipt = arabicDocuments().find(d => d.fixture.name === 'payment-receipt')!.html;
    expect(receipt).toContain(arTranslations.payment.receipt.title);
    expect(receipt).toContain('<bdi dir="ltr">12.500,00</bdi> دج');
    expect(receipt).toContain('<bdi dir="ltr">RECU-2030-0042</bdi>');
    expect(receipt).toContain('<bdi dir="ltr">Maths 4 AM A</bdi>');
    expect(receipt).toContain('<bdi dir="ltr">Amine Belkacem</bdi>');
    // Date au format de l'écran en arabe (ar-DZ, chiffres latins), marques de direction retirées.
    const day = stripBidiControls(new Intl.DateTimeFormat('ar-DZ', { dateStyle: 'short' }).format(new Date(2030, 1, 3)));
    expect(receipt).toContain(`<bdi dir="ltr">${day}</bdi>`);
    expect(receipt).toContain('size:A5 portrait');
    expect(receipt).not.toContain(' DA');
  });

  it('reçu annulé et bordereau annulé : tampon arabe sur chaque page', () => {
    for (const name of ['payment-receipt-cancelled', 'payout-slip-cancelled']) {
      const html = arabicDocuments().find(d => d.fixture.name === name)!.html;
      expect(html).withContext(name).toContain('class="pdf-wm"');
      expect(html).withContext(name).toContain('ملغى');
    }
  });

  it('numéros de page arabes : « الصفحة X / Y » dans les boîtes de marge', () => {
    const documents = arabicDocuments();
    const boxes = (name: string): Record<string, string> => marginBoxTexts(documents.find(d => d.fixture.name === name)!.html);
    expect(boxes('student-profile')['bottom-center']).toBe('الصفحة 900001 / 900002');
    expect(boxes('attendance-sheet')['bottom-left']).toBe('الصفحة 900001 / 900002');
    expect(boxes('attendance-sheet')['bottom-right']).toContain('أُنشئت الورقة في');
    expect(boxes('correction-journal')['bottom-left']).toBe('الصفحة 900001 / 900002');
    expect(boxes('payment-history')['bottom-center']).toBe('الصفحة 900001 من 900002');
    expect(boxes('attendance-history')['bottom-center']).toBe('الصفحة 900001 من 900002');
    expect(boxes('payment-receipt')['bottom-center']).toBe(arTranslations.payment.receipt.footerNote);
  });

  it('Journal en paysage, en-tête de tableau répétable', () => {
    const journal = arabicDocuments().find(d => d.fixture.name === 'correction-journal')!.html;
    expect(journal).toContain('size:A4 landscape');
    const page = new DOMParser().parseFromString(journal, 'text/html');
    expect(page.querySelectorAll('thead tr').length).toBe(1);
    expect(page.querySelectorAll('tbody tr').length).toBe(30);
  });
});
