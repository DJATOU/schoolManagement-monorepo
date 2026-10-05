import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import pdfMake from 'pdfmake/build/pdfmake';
import { Content, TDocumentDefinitions } from 'pdfmake/interfaces';

import { CorrectionJournalPdfService } from './correction-journal-pdf.service';
import { setupServiceTestBed } from '../../testing/setup';
import { CorrectionJournal, JournalEntry } from '../models/correction/journal';
import { PdfOutputService } from './pdf-output.service';

/**
 * Journal imprimé (D.5 ; exigence 12.4) : ce qu'on remet à un parent qui conteste. On vérifie la
 * définition du document, sans passer par l'impression.
 */
describe('CorrectionJournalPdfService', () => {
  let service: CorrectionJournalPdfService;

  const entry = (overrides: Partial<JournalEntry> = {}): JournalEntry => ({
    performedAt: '2030-01-14T09:05:00',
    category: 'ATTENDANCE',
    description: 'Séance du 07/01/2030 (Math 1ère A) : Amine Belkacem absent → présent',
    amountEffect: 'Janvier (Math 1ère A) : dû à ce jour 0,00 → 2 000,00 DA ; Février (Math 1ère A) : reste 4 000,00 → 2 000,00 DA',
    reasonType: 'OTHER',
    reasonText: 'Feuille d\'un autre groupe',
    performedBy: 'directrice',
    ...overrides
  });

  const journal = (overrides: Partial<CorrectionJournal> = {}): CorrectionJournal => ({
    studentId: 42,
    studentName: 'Amine Belkacem',
    from: '2030-01-01',
    to: '2030-01-31',
    entries: [entry()],
    ...overrides
  });

  /** Tout le texte d'un nœud, à plat. */
  const allText = (node: unknown): string => {
    if (typeof node === 'string') {
      return node;
    }
    if (Array.isArray(node)) {
      return node.map(allText).join(' | ');
    }
    if (node && typeof node === 'object') {
      return Object.values(node as Record<string, unknown>).map(allText).join(' | ');
    }
    return '';
  };

  /** Lignes du tableau des entrées, en-tête comprise. */
  const rows = (doc: TDocumentDefinitions): unknown[][] =>
    ((doc.content as Content[])[2] as { table: { body: unknown[][] } }).table.body;

  beforeEach(() => {
    setupServiceTestBed();
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', {
      journal: {
        title: 'Journal des corrections',
        count: '{{count}} correction(s)',
        empty: 'Aucune correction sur cette période.',
        noEffect: 'Sans effet sur le dû',
        period: { between: 'Du {{from}} au {{to}}', from: 'Depuis le {{from}}', to: 'Jusqu\'au {{to}}', all: 'Toute période' },
        columns: { date: 'Date', correction: 'Correction', effect: 'Effet sur le dû', reason: 'Motif', by: 'Par' },
        category: { ATTENDANCE: 'Présence', JUSTIFICATION: 'Justification', CATCH_UP: 'Rattrapage' },
        generatedOn: 'Édité le {{date}}',
        page: 'Page {{current}} / {{total}}',
        fileName: 'journal'
      },
      correction: { reason: { OTHER: 'Autre', DATA_ENTRY_ERROR: 'Erreur de saisie' } }
    });
    translate.use('fr');
    service = TestBed.inject(CorrectionJournalPdfService);
  });

  it('A4 paysage : titre, élève, période et nombre d\'entrées en tête', () => {
    const doc = service.buildDocument(journal(), '', new Date(2030, 1, 3, 8, 4));

    expect(doc.pageSize).toBe('A4');
    expect(doc.pageOrientation).toBe('landscape');
    expect(doc.info?.title).toBe('Journal des corrections — Amine Belkacem');
    const header = allText((doc.content as Content[])[0]);
    expect(header).toContain('Journal des corrections');
    expect(header).toContain('Amine Belkacem');
    expect(header).toContain('Du 01/01/2030 au 31/01/2030');
    expect(header).toContain('1 correction(s)');
  });

  it('la période dite comme saisie : bornée, depuis, jusqu\'à, toute période', () => {
    expect(service.period(journal({ from: '2030-01-01', to: null }))).toBe('Depuis le 01/01/2030');
    expect(service.period(journal({ from: null, to: '2030-01-31' }))).toBe('Jusqu\'au 31/01/2030');
    expect(service.period(journal({ from: null, to: null }))).toBe('Toute période');
  });

  it('une ligne par entrée : date, catégorie et phrase, effet par série, Motif, auteur ; la flèche imprimable', () => {
    const body = rows(service.buildDocument(journal(), '', new Date()));

    expect(allText(body[0])).toBe('Date | tableHeader | Correction | tableHeader | Effet sur le dû | tableHeader | '
      + 'Motif | tableHeader | Par | tableHeader');
    const [date, correction, effect, reason, by] = body[1].map(allText);
    expect(date).toContain('14/01/2030 09:05');
    expect(correction).toContain('PRÉSENCE');
    expect(correction).toContain('Séance du 07/01/2030 (Math 1ère A) : Amine Belkacem absent -> présent');
    expect(allText(body)).not.toContain('→');
    const effects = ((body[1][2] as { stack: { text: string }[] }).stack).map(line => line.text);
    expect(effects).toEqual(['Janvier (Math 1ère A) : dû à ce jour 0,00 -> 2 000,00 DA',
      'Février (Math 1ère A) : reste 4 000,00 -> 2 000,00 DA']);
    expect(effect).toContain('Janvier');
    expect(reason).toContain('Autre');
    expect(reason).toContain('Feuille d\'un autre groupe');
    expect(by).toContain('directrice');
  });

  it('justification : « Sans effet sur le dû » ; effet non mesuré : un tiret ; ni Motif ni texte : un tiret', () => {
    const body = rows(service.buildDocument(journal({ entries: [
      entry({ category: 'JUSTIFICATION', amountEffect: null, reasonType: null, reasonText: 'Certificat médical' }),
      entry({ category: 'CATCH_UP', amountEffect: null, reasonType: null, reasonText: null }),
      entry({ reasonType: 'DATA_ENTRY_ERROR', reasonText: null })
    ] }), '', new Date()));

    expect(allText(body[1][2])).toContain('Sans effet sur le dû');
    expect(allText(body[1][3])).toContain('Certificat médical');
    expect(allText(body[1][3])).not.toContain('Autre');
    expect(allText(body[2][2])).toContain('—');
    expect(allText(body[2][2])).not.toContain('Sans effet');
    expect(allText(body[2][3])).toContain('—');
    expect(allText(body[3][3])).toContain('Erreur de saisie');
    expect(((body[3][3] as { stack: unknown[] }).stack).length).toBe(1);
  });

  it('les lignes alternent leur fond, pour suivre une ligne longue', () => {
    const body = rows(service.buildDocument(journal({ entries: [entry(), entry(), entry()] }), '', new Date()));

    const fills = body.slice(1).map(row => (row[0] as { fillColor: string }).fillColor);
    expect(fills).toEqual(['#f8fafc', '#ffffff', '#f8fafc']);
  });

  it('Journal vide : une ligne le dit, sur toute la largeur', () => {
    const doc = service.buildDocument(journal({ entries: [] }), '', new Date());

    const body = rows(doc);
    expect(body.length).toBe(2);
    expect((body[1][0] as { text: string; colSpan: number }).text).toBe('Aucune correction sur cette période.');
    expect((body[1][0] as { colSpan: number }).colSpan).toBe(5);
    expect(allText((doc.content as Content[])[0])).toContain('0 correction(s)');
  });

  it('logo chargé : en tête, à gauche ; absent : pas de colonne vide', () => {
    const withLogo = (service.buildDocument(journal(), 'data:image/png;base64,AAA', new Date()).content as Content[])[0];
    const withoutLogo = (service.buildDocument(journal(), '', new Date()).content as Content[])[0];

    const columns = (withLogo as { columns: { image?: string }[] }).columns;
    expect(columns.length).toBe(3);
    expect(columns[0].image).toBe('data:image/png;base64,AAA');
    expect((withoutLogo as { columns: unknown[] }).columns.length).toBe(2);
  });

  it('pied de page : date d\'édition et pagination', () => {
    const doc = service.buildDocument(journal(), '', new Date(2030, 1, 3, 8, 4));

    const footer = allText((doc.footer as (current: number, total: number) => Content)(2, 5));
    expect(footer).toContain('Édité le 03/02/2030 08:04');
    expect(footer).toContain('Page 2 / 5');
  });

  it('nom du fichier : élève et période', () => {
    expect(service.fileName(journal())).toBe('journal_Amine_Belkacem_2030-01-01_2030-01-31.pdf');
    expect(service.fileName(journal({ from: null, to: null }))).toBe('journal_Amine_Belkacem.pdf');
    expect(service.fileName(journal({ studentName: '  ', from: '2030-01-01', to: null }))).toBe('journal_42_2030-01-01.pdf');
  });

  it('imprimer : le logo de l\'école, le document composé, imprimé sous son nom', async () => {
    const output = TestBed.inject(PdfOutputService);
    const logo = spyOn(output, 'imageDataUrl').and.resolveTo('data:image/png;base64,LOGO');
    const printed = spyOn(output, 'print');

    await service.print(journal(), new Date(2030, 1, 3, 8, 4));

    expect(logo).toHaveBeenCalledWith('assets/succes_assistance.png');
    const [doc, fileName] = printed.calls.mostRecent().args;
    expect(((doc.content as Content[])[0] as { columns: { image?: string }[] }).columns[0].image)
      .toBe('data:image/png;base64,LOGO');
    expect(fileName).toBe('journal_Amine_Belkacem_2030-01-01_2030-01-31.pdf');
  });
});

describe('PdfOutputService', () => {
  beforeEach(() => setupServiceTestBed());

  it('produit le PDF puis l\'imprime sous son nom', () => {
    const blob = new Blob(['%PDF']);
    const definition: TDocumentDefinitions = { content: ['Journal'] };
    const created = spyOn(pdfMake, 'createPdf').and.returnValue(
      { getBlob: (callback: (result: Blob) => void) => callback(blob) } as never);
    const printed = spyOn(PdfOutputService, 'printBlob');

    TestBed.inject(PdfOutputService).print(definition, 'journal.pdf');

    expect(created).toHaveBeenCalledWith(definition);
    expect(printed).toHaveBeenCalledWith(blob, 'journal.pdf');
  });

  it('logo introuvable : chaîne vide, le document s\'imprime sans lui', async () => {
    await expectAsync(TestBed.inject(PdfOutputService).imageDataUrl('assets/introuvable.png')).toBeResolvedTo('');
  });
});
