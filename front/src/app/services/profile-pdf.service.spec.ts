import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import pdfMake from 'pdfmake/build/pdfmake';
import { Content, TDocumentDefinitions } from 'pdfmake/interfaces';
import { ProfilePdfService } from './profile-pdf.service';
import { setupServiceTestBed } from '../../testing/setup';
import frTranslations from '../../assets/i18n/fr.json';
import enTranslations from '../../assets/i18n/en.json';

/**
 * Habillage commun des fiches PDF (pied de page, signatures, date d'édition) : en français pour
 * le français et pour toute langue que Roboto ne sait pas dessiner (l'arabe), en anglais pour
 * l'anglais.
 */
describe('ProfilePdfService — langue du document', () => {
  let service: ProfilePdfService;
  let translate: TranslateService;
  let captured: TDocumentDefinitions;

  beforeEach(() => {
    setupServiceTestBed();
    service = TestBed.inject(ProfilePdfService);
    translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', frTranslations);
    translate.setTranslation('en', enTranslations);
    translate.setTranslation('ar', {});
    captured = { content: [] };
    spyOn(pdfMake, 'createPdf').and.callFake(((definition: TDocumentDefinitions) => {
      captured = definition;
      return { getBlob: () => undefined };
    }) as never);
  });

  function print(): { text: string; footer: string } {
    service.generateProfilePdf({ title: 'Amina Belkacem', sections: [] });
    const footer = (captured.footer as (current: number, total: number) => Content)(1, 2);
    return { text: JSON.stringify(captured.content), footer: JSON.stringify(footer) };
  }

  it('français : pied de page et signatures en français', () => {
    translate.use('fr');
    const { text, footer } = print();
    expect(footer).toContain('Page 1 / 2');
    expect(text).toContain('Signature : ________________________');
    expect(text).toContain('Administration : ________________________');
  });

  it('arabe : document en français, sans aucun caractère arabe', () => {
    translate.use('ar');
    const { text, footer } = print();
    expect(footer).toContain('Page 1 / 2');
    expect(text).toContain('Signature : ________________________');
    expect(text).toContain(new Date().toLocaleDateString('fr-FR'));
    expect(text + footer).not.toMatch(/[\u0600-\u06FF]/);
  });

  it('anglais : pied de page et signatures en anglais', () => {
    translate.use('en');
    const { text } = print();
    expect(text).toContain('Signature: ________________________');
    expect(text).toContain('Administration: ________________________');
  });
});
