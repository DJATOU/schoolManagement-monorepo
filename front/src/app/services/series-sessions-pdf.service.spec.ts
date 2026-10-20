import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import pdfMake from 'pdfmake/build/pdfmake';
import { Content, TDocumentDefinitions } from 'pdfmake/interfaces';
import { SeriesSessionsPdfService } from './series-sessions-pdf.service';
import { setupServiceTestBed } from '../../testing/setup';
import frTranslations from '../../assets/i18n/fr.json';
import enTranslations from '../../assets/i18n/en.json';
import arTranslations from '../../assets/i18n/ar.json';

/**
 * Liste des séances d'une série : en français pour le français et pour l'arabe (pdfmake ne sait
 * pas dessiner l'arabe), en anglais pour l'anglais.
 */
describe('SeriesSessionsPdfService — langue du document', () => {
  let service: SeriesSessionsPdfService;
  let translate: TranslateService;
  let captured: TDocumentDefinitions;

  beforeEach(() => {
    setupServiceTestBed();
    service = TestBed.inject(SeriesSessionsPdfService);
    translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', frTranslations);
    translate.setTranslation('en', enTranslations);
    translate.setTranslation('ar', arTranslations);
    captured = { content: [] };
    spyOn(pdfMake, 'createPdf').and.callFake(((definition: TDocumentDefinitions) => {
      captured = definition;
      return { getBlob: () => undefined };
    }) as never);
  });

  /** Tout le texte du document, pied de page compris, à plat. */
  function print(): string {
    service.generate(
      { name: 'Maths 1B - 02-2030-001', groupName: 'Maths 1B', totalSessions: 8, numberOfSessionsCreated: 2,
        sessionsCompleted: 1, serieTimeStart: '2030-02-03T09:00:00' },
      [
        { title: 'Séance 1', sessionTimeStart: '2030-02-03T09:00:00', isFinished: true },
        { title: 'Séance 2', sessionTimeStart: '2030-02-10T09:00:00', isFinished: false }
      ]
    );
    const footer = (captured.footer as (current: number, total: number) => Content)(1, 2);
    return JSON.stringify(captured.content) + JSON.stringify(footer);
  }

  it('français : libellés d\'aujourd\'hui', () => {
    translate.use('fr');
    const text = print();
    expect(text).toContain('Groupe · Maths 1B');
    expect(text).toContain('Sessions prévues');
    expect(text).toContain('Sessions créées');
    expect(text).toContain('Validée');
    expect(text).toContain('Programmée');
    expect(text).toContain('Enseignant : ________________________');
    expect(text).toContain('Page 1 / 2');
    expect(text).toContain('Généré le ');
    expect(text).toContain('03/02/2030 09:00');
  });

  it('arabe : document en français, chiffres latins, sans aucun caractère arabe', () => {
    translate.use('ar');
    const text = print();
    expect(text).toContain('Sessions prévues');
    expect(text).toContain('Programmée');
    expect(text).toContain('03/02/2030 09:00');
    expect(text).not.toMatch(/[\u0600-\u06FF]/);
  });

  it('anglais : libellés anglais', () => {
    translate.use('en');
    const text = print();
    expect(text).toContain('Group · Maths 1B');
    expect(text).toContain('Planned sessions');
    expect(text).toContain('Scheduled');
    expect(text).not.toContain('Sessions prévues');
  });
});
