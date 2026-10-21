import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import pdfMake from 'pdfmake/build/pdfmake';
import { TDocumentDefinitions } from 'pdfmake/interfaces';

import { setupServiceTestBed } from '../../testing/setup';
import { payout, slip } from '../../testing/payroll-fixtures';
import { definitionTexts } from '../../testing/print-language';
import { PrintLanguage, withPrintLanguage } from '../shared/pdf-i18n';
import { AttendanceHistoryDialogComponent } from '../components/attendance/attendance-history-dialog/attendance-history-dialog.component';
import { PaymentHistoryDialogComponent } from '../components/payment/payment-history/payment-history-dialog/payment-history-dialog.component';
import { PdfGeneratorService } from '../components/student/services/pdf-generator.service';
import { CorrectionJournalPdfService } from './correction-journal-pdf.service';
import { PaymentReceiptData, PaymentReceiptPdfService } from './payment-receipt-pdf.service';
import { PayoutSlipPdfService } from './payout-slip-pdf.service';
import { PdfOutputService } from './pdf-output.service';
import { ProfilePdfService } from './profile-pdf.service';
import { RefundReceiptPdfService } from './refund-receipt-pdf.service';
import { SeriesSessionsPdfService } from './series-sessions-pdf.service';
import { SessionAttendancePdfService } from './session-attendance-pdf.service';
import { RefundReceipt } from '../models/refund/refund';
import { SessionSeries } from '../models/sessionSerie/sessionSerie';
import frTranslations from '../../assets/i18n/fr.json';
import enTranslations from '../../assets/i18n/en.json';
import arTranslations from '../../assets/i18n/ar.json';

/**
 * Chemin de sortie de chaque document selon la langue choisie à l'impression :
 *
 * <ul>
 *   <li>français et anglais : pdfmake reçoit la définition que son générateur compose, comme avant ;</li>
 *   <li>arabe : pdfmake n'est jamais appelé, la page HTML de droite à gauche part à l'impression.</li>
 * </ul>
 */
describe('documents : chemin d\'impression selon la langue', () => {
  let translate: TranslateService;
  let createPdf: jasmine.Spy;
  let pdfmakeDocs: TDocumentDefinitions[];
  let printedHtml: { html: string; title: string }[];
  const originalPrintHtml = PdfOutputService.printHtmlDocument;
  const originalPrintBlob = PdfOutputService.printBlob;

  beforeEach(() => {
    setupServiceTestBed();
    translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', frTranslations);
    translate.setTranslation('en', enTranslations);
    translate.setTranslation('ar', arTranslations);
    translate.use('fr');
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date(2030, 1, 3, 9, 15));
    pdfmakeDocs = [];
    createPdf = spyOn(pdfMake, 'createPdf').and.callFake(((doc: TDocumentDefinitions) => {
      pdfmakeDocs.push(doc);
      return { getBlob: () => undefined, download: () => undefined };
    }) as never);
    printedHtml = [];
    PdfOutputService.printHtmlDocument = (html: string, title: string) => {
      printedHtml.push({ html, title });
      return Promise.resolve();
    };
    PdfOutputService.printBlob = () => undefined;
    spyOn(TestBed.inject(PdfOutputService), 'imageDataUrl').and.resolveTo('');
  });

  afterEach(() => {
    PdfOutputService.printHtmlDocument = originalPrintHtml;
    PdfOutputService.printBlob = originalPrintBlob;
    jasmine.clock().uninstall();
  });

  /** Pied de page d'une définition, calculé comme pdfmake le fera (page 1 sur 2). */
  const footerText = (doc: TDocumentDefinitions): string =>
    typeof doc.footer === 'function' ? definitionTexts((doc.footer as (c: number, t: number) => unknown)(1, 2)).join(' ') : '';

  /** Un document comparable : ses textes et son pied de page (les fonctions ne se comparent pas). */
  const snapshot = (doc: TDocumentDefinitions): string => JSON.stringify(doc) + '|' + footerText(doc);

  interface Entry {
    name: string;
    /** Imprime le document par son point d'entrée public. */
    print: () => Promise<unknown> | void;
    /** La définition que son générateur compose, dans la langue courante. */
    build: () => TDocumentDefinitions;
    /** Un libellé du document, par langue. */
    label: Record<PrintLanguage, string>;
  }

  const receiptData = (): PaymentReceiptData => ({
    reference: 'RECU-2030-0042', issuedAt: new Date(2030, 1, 3, 9, 15), studentName: 'Amine Belkacem',
    groupName: 'Maths 4 AM A', seriesName: 'Octobre', amountPaid: 12500, paymentMethodLabel: 'Espèces',
    isCatchUp: false, amountAllocated: 12500, carryOvers: [], adminUsername: 'caissier1'
  });
  const refund = (): RefundReceipt => ({
    refundId: 7, refundNumber: 'REMB-2030-0007', refundDate: new Date(2030, 9, 5), amount: 1200, reason: 'Trop-perçu',
    studentFirstName: 'Amine', studentLastName: 'Belkacem', paymentDate: new Date(2030, 9, 1), amountPaid: 12500,
    groupName: 'Maths 4 AM A', seriesName: 'Octobre', recordedBy: 'admin', issuanceRank: 2,
    issuedAt: '2030-10-06T11:20:00', fileName: 'remboursement-REMB-2030-0007.pdf'
  });
  const journal = () => ({ studentId: 42, studentName: 'Amine Belkacem', from: '2030-01-01', to: '2030-01-31', entries: [] });
  const history = () => ({
    studentId: 42, studentName: 'Amine Belkacem', catchUp: false,
    groups: [{ groupId: 3, groupName: 'Maths 4 AM A', catchUp: false, series: [] }]
  });

  function entries(): Entry[] {
    const profile = TestBed.inject(ProfilePdfService);
    const receipt = TestBed.inject(PaymentReceiptPdfService);
    const refundPdf = TestBed.inject(RefundReceiptPdfService);
    const slipPdf = TestBed.inject(PayoutSlipPdfService);
    const journalPdf = TestBed.inject(CorrectionJournalPdfService);
    const sheet = TestBed.inject(SessionAttendancePdfService);
    const series = TestBed.inject(SeriesSessionsPdfService);
    const generator = TestBed.inject(PdfGeneratorService);
    for (const service of [receipt, refundPdf]) {
      spyOn(service as unknown as { loadLogo: () => Promise<string> }, 'loadLogo').and.resolveTo('');
    }
    spyOn(generator as unknown as { convertImageToBase64: () => Promise<string> }, 'convertImageToBase64').and.resolveTo('');
    const attendance = TestBed.runInInjectionContext(() => new AttendanceHistoryDialogComponent(
      null as never, null as never, null as never, null as never, null as never, translate, null as never, { studentId: 42 }));
    attendance.studentName = 'Amine Belkacem';
    attendance.sessionSeries = [{ id: 12, name: 'Octobre' } as SessionSeries];
    attendance.selectedSeries = 12;
    const payments = TestBed.runInInjectionContext(() => new PaymentHistoryDialogComponent(
      null as never, null as never, { studentId: 42 }, translate));
    payments.studentName = 'Amine Belkacem';
    for (const dialog of [attendance, payments]) {
      spyOn(dialog as unknown as { convertImageToBase64: () => Promise<string> }, 'convertImageToBase64').and.resolveTo('');
    }
    const options = { title: 'Amine Belkacem', sections: [] };
    const sheetInfo = { title: 'Séance 3', isFinished: true };
    return [
      { name: 'fiche', print: () => profile.generateProfilePdf(options), build: () => profile.buildDocument(options),
        label: { fr: frTranslations.PROFILE_PDF.SIGNATURE, en: enTranslations.PROFILE_PDF.SIGNATURE, ar: arTranslations.PROFILE_PDF.SIGNATURE } },
      { name: 'reçu', print: () => receipt.generateAndPrint(receiptData()), build: () => receipt.buildDocument(receiptData(), ''),
        label: { fr: frTranslations.payment.receipt.title, en: enTranslations.payment.receipt.title, ar: arTranslations.payment.receipt.title } },
      { name: 'reçu de remboursement', print: () => refundPdf.generateAndPrint(refund()), build: () => refundPdf.buildDocument(refund(), ''),
        label: { fr: frTranslations.refund.receipt.title, en: enTranslations.refund.receipt.title, ar: arTranslations.refund.receipt.title } },
      { name: 'reçu de remboursement (téléchargement)', print: () => refundPdf.download(refund()), build: () => refundPdf.buildDocument(refund(), ''),
        label: { fr: frTranslations.refund.receipt.title, en: enTranslations.refund.receipt.title, ar: arTranslations.refund.receipt.title } },
      { name: 'feuille de présence', print: () => sheet.generateAttendanceSheet(sheetInfo, []), build: () => sheet.buildDocument(sheetInfo, []),
        label: { fr: frTranslations.SESSION_ATTENDANCE_PDF.SECTION_TITLE, en: enTranslations.SESSION_ATTENDANCE_PDF.SECTION_TITLE,
          ar: arTranslations.SESSION_ATTENDANCE_PDF.SECTION_TITLE } },
      { name: 'séances d\'une série', print: () => series.generate({ name: 'Octobre' }, []), build: () => series.buildDocument({ name: 'Octobre' }, []),
        label: { fr: frTranslations.SERIES_SESSIONS_PDF.SECTION_TITLE, en: enTranslations.SERIES_SESSIONS_PDF.SECTION_TITLE,
          ar: arTranslations.SERIES_SESSIONS_PDF.SECTION_TITLE } },
      { name: 'historique complet', print: () => generator.generateFullHistoryPdf(history(), 'logo.png', []),
        build: () => generator.buildDocument(history(), '', []),
        label: { fr: frTranslations.studentHistory.pdf.title, en: enTranslations.studentHistory.pdf.title, ar: arTranslations.studentHistory.pdf.title } },
      { name: 'historique des présences', print: () => attendance.generatePdf(), build: () => attendance.buildPdfDocument(''),
        label: { fr: frTranslations.ATTENDANCE_HISTORY.PDF.TITLE, en: enTranslations.ATTENDANCE_HISTORY.PDF.TITLE, ar: arTranslations.ATTENDANCE_HISTORY.PDF.TITLE } },
      { name: 'historique des paiements', print: () => payments.generatePdf(), build: () => payments.buildPdfDocument(''),
        label: { fr: frTranslations.payment.history.pdf.title, en: enTranslations.payment.history.pdf.title, ar: arTranslations.payment.history.pdf.title } },
      { name: 'bordereau de paie', print: () => slipPdf.print(slip()), build: () => slipPdf.buildDocument(slip({ payout: payout() }), ''),
        label: { fr: frTranslations.teacherPayroll.slip.title.INITIAL, en: enTranslations.teacherPayroll.slip.title.INITIAL,
          ar: arTranslations.teacherPayroll.slip.title.INITIAL } },
      { name: 'journal des corrections', print: () => journalPdf.print(journal(), new Date(2030, 1, 1, 14, 30)),
        build: () => journalPdf.buildDocument(journal(), '', new Date(2030, 1, 1, 14, 30)),
        label: { fr: frTranslations.journal.title, en: enTranslations.journal.title, ar: arTranslations.journal.title } }
    ];
  }

  /** Imprime chaque document, dans `lang` ou sans choix, et attend la fin de sa composition. */
  async function printAll(lang?: PrintLanguage): Promise<Entry[]> {
    const all = entries();
    for (const entry of all) {
      await (lang ? withPrintLanguage(lang, entry.print) : entry.print());
    }
    return all;
  }

  /** Définitions reçues par pdfmake, directement ou par PdfOutputService.print. */
  function pdfmakeDefinitions(): TDocumentDefinitions[] {
    return pdfmakeDocs;
  }

  it('sans choix (règle historique) : pdfmake reçoit exactement la définition du générateur', async () => {
    const all = await printAll();
    expect(printedHtml).toEqual([]);
    const docs = pdfmakeDefinitions();
    expect(docs.length).toBe(all.length);
    all.forEach((entry, i) => {
      expect(snapshot(docs[i])).withContext(entry.name).toBe(snapshot(entry.build()));
      expect(JSON.stringify(docs[i]) + footerText(docs[i])).withContext(entry.name).toContain(entry.label.fr);
    });
  });

  it('« Français » choisi sous une interface en français : même définition qu\'avant', async () => {
    const all = await printAll('fr');
    expect(printedHtml).toEqual([]);
    all.forEach((entry, i) => expect(snapshot(pdfmakeDefinitions()[i])).withContext(entry.name).toBe(snapshot(entry.build())));
  });

  it('« English » choisi sous une interface en français : pdfmake, document et pied de page en anglais', async () => {
    const all = await printAll('en');
    expect(printedHtml).toEqual([]);
    expect(createPdf).toHaveBeenCalled();
    all.forEach((entry, i) => {
      const doc = pdfmakeDefinitions()[i];
      // Pied de page appelé plus tard par pdfmake, hors du choix : il reste en anglais.
      const printed = JSON.stringify(doc) + footerText(doc);
      expect(printed).withContext(entry.name).toContain(entry.label.en);
      expect(printed).withContext(entry.name).not.toMatch(/[\u0600-\u06FF]/);
      expect(snapshot(doc)).withContext(entry.name).toBe(withPrintLanguage('en', () => snapshot(entry.build())));
    });
    expect(translate.currentLang).toBe('fr');
  });

  it('« Français » choisi sous une interface en arabe : pdfmake en français, comme avant', async () => {
    translate.use('ar');
    const all = await printAll('fr');
    expect(printedHtml).toEqual([]);
    all.forEach((entry, i) => {
      const doc = pdfmakeDefinitions()[i];
      expect(JSON.stringify(doc) + footerText(doc)).withContext(entry.name).not.toMatch(/[\u0600-\u06FF]/);
      expect(snapshot(doc)).withContext(entry.name).toBe(snapshot(entry.build()));
    });
  });

  it('« العربية » : jamais pdfmake, la page HTML de droite à gauche part à l\'impression', async () => {
    const all = await printAll('ar');
    expect(createPdf).not.toHaveBeenCalled();
    expect(printedHtml.length).toBe(all.length);
    printedHtml.forEach(({ html, title }, i) => {
      const name = all[i].name;
      expect(html).withContext(name).toContain('<html lang="ar" dir="rtl">');
      expect(html).withContext(name).toContain(all[i].label.ar);
      expect(title).withContext(name).not.toBe('');
      expect(title).withContext(name).not.toMatch(/\.pdf$/);
    });
    expect(translate.currentLang).toBe('fr');
  });

  it('« العربية » : montants en « دج », numéros isolés, numéros de page', async () => {
    await printAll('ar');
    const receipt = printedHtml[1].html;
    expect(receipt).toContain('<bdi dir="ltr">12.500,00</bdi> دج');
    expect(receipt).toContain('<bdi dir="ltr">RECU-2030-0042</bdi>');
    expect(printedHtml[0].html).toContain('@bottom-center{content:"الصفحة " "\\002066" counter(page) " / " counter(pages) "\\002069"');
    expect(printedHtml[9].html).toContain('<bdi dir="ltr">43.200,00</bdi> دج');
  });
});
