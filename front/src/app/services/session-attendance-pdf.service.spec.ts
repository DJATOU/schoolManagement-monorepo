import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import pdfMake from 'pdfmake/build/pdfmake';
import { Content, TDocumentDefinitions } from 'pdfmake/interfaces';
import { SessionAttendancePdfService, SessionAttendanceStudentRow } from './session-attendance-pdf.service';
import { setupServiceTestBed } from '../../testing/setup';
import frTranslations from '../../assets/i18n/fr.json';
import enTranslations from '../../assets/i18n/en.json';
import arTranslations from '../../assets/i18n/ar.json';

/**
 * Feuille de présence d'une séance : en français pour le français et pour l'arabe (pdfmake ne sait
 * pas dessiner l'arabe), en anglais pour l'anglais.
 */
describe('SessionAttendancePdfService — langue du document', () => {
  let service: SessionAttendancePdfService;
  let translate: TranslateService;
  let captured: TDocumentDefinitions;

  const students: SessionAttendanceStudentRow[] = [
    { fullName: 'Amina Belkacem', isPresent: true },
    { fullName: 'Yanis Haddad', isPresent: false, isJustified: true, isCatchUp: true }
  ];

  beforeEach(() => {
    setupServiceTestBed();
    service = TestBed.inject(SessionAttendancePdfService);
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
    service.generateAttendanceSheet({
      title: 'Maths 1B', groupName: 'Maths 1B', roomName: 'Salle 3', teacherName: 'K. Saidi',
      sessionTimeStart: '2030-02-03T09:00:00', sessionTimeEnd: '2030-02-03T10:30:00', isFinished: true
    }, students);
    const footer = (captured.footer as (current: number, total: number) => Content)(1, 2);
    return JSON.stringify(captured.content) + JSON.stringify(footer);
  }

  it('français : libellés d\'aujourd\'hui', () => {
    translate.use('fr');
    const text = print();
    expect(text).toContain('Feuille de présence');
    expect(text).toContain('Session validée');
    expect(text).toContain('Yanis Haddad  (Rattrapage)');
    expect(text).toContain('Absent / Non justifié');
    expect(text).toContain('Enseignant : ________________________');
    expect(text).toContain('Administration : ________________________');
    expect(text).toContain('Page 1 / 2');
    expect(text).toContain('Feuille générée le ');
    expect(text).toContain('03/02/2030 09:00');
  });

  it('arabe : document en français, chiffres latins, sans aucun caractère arabe', () => {
    translate.use('ar');
    const text = print();
    expect(text).toContain('Feuille de présence');
    expect(text).toContain('Présent');
    expect(text).toContain('03/02/2030 09:00');
    expect(text).not.toMatch(/[\u0600-\u06FF]/);
  });

  it('anglais : libellés anglais', () => {
    translate.use('en');
    const text = print();
    expect(text).toContain('Attendance sheet');
    expect(text).toContain('Session validated');
    expect(text).toContain('Yanis Haddad  (Catch-up)');
    expect(text).toContain('Teacher: ________________________');
    expect(text).not.toContain('Feuille de présence');
  });
});
