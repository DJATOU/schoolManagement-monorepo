import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import pdfMake from 'pdfmake/build/pdfmake';
import { Content, TDocumentDefinitions } from 'pdfmake/interfaces';
import frTranslations from '../../../../assets/i18n/fr.json';
import enTranslations from '../../../../assets/i18n/en.json';
import arTranslations from '../../../../assets/i18n/ar.json';

import { AttendanceHistoryDialogComponent } from './attendance-history-dialog.component';
import { createDialogRefSpy, DialogRefSpy, matDialogProviders, setupComponentTestBed } from '../../../../testing/setup';

/**
 * Historique d'assiduité d'un étudiant.
 *
 * <p>`data.studentId` est déréférencé dès `ngOnInit`, qui déclenche deux chargements. Les
 * requêtes sont laissées en attente : ce qui est vérifié ici est que le dialogue se construit
 * et s'ouvre sur l'étudiant demandé, pas le contenu de l'historique.</p>
 */
describe('AttendanceHistoryDialogComponent', () => {
  let component: AttendanceHistoryDialogComponent;
  let fixture: ComponentFixture<AttendanceHistoryDialogComponent>;
  let dialogRef: DialogRefSpy;

  beforeEach(async () => {
    dialogRef = createDialogRefSpy();
    await setupComponentTestBed(AttendanceHistoryDialogComponent, {
      providers: matDialogProviders({ studentId: 42 }, dialogRef)
    });
    fixture = TestBed.createComponent(AttendanceHistoryDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('porte l\'étudiant sur lequel il a été ouvert', () => {
    expect(component.data.studentId).toBe(42);
  });

  /**
   * PDF de l'historique : en français pour le français et l'arabe (pdfmake ne dessine pas l'arabe),
   * en anglais pour l'anglais. Le repli « Session inconnue » de l'écran n'y entre pas tel quel.
   */
  describe('PDF', () => {
    let translate: TranslateService;

    beforeEach(() => {
      translate = TestBed.inject(TranslateService);
      translate.setTranslation('fr', frTranslations);
      translate.setTranslation('en', enTranslations);
      translate.setTranslation('ar', arTranslations);
    });

    async function printedIn(lang: string): Promise<string> {
      translate.use(lang);
      component.studentName = 'Amina Belkacem';
      component.sessionSeries = [{ id: 7, name: 'Maths 1B - 02-2030-001' } as never];
      component.selectedSeries = 7;
      component.attendanceHistory.data = [
        { id: 1, isPresent: true, sessionTitle: 'Séance 1', sessionName: 'Séance 1', sessionDate: '2030-02-03T09:00:00' },
        { id: 2, isPresent: false, isJustified: true, isCatchUp: true, sessionTitle: null,
          sessionName: translate.instant('ATTENDANCE_HISTORY.UNKNOWN_SESSION'), sessionDate: '2030-02-10T09:00:00' }
      ];
      spyOn(component as never, 'convertImageToBase64').and.returnValue(Promise.resolve('') as never);
      let captured: TDocumentDefinitions = { content: [] };
      spyOn(pdfMake, 'createPdf').and.callFake(((definition: TDocumentDefinitions) => {
        captured = definition;
        return { getBlob: () => undefined };
      }) as never);
      await component.generatePdf();
      const footer = (captured.footer as (current: number, total: number) => Content)(1, 2);
      return JSON.stringify(captured.content) + JSON.stringify(footer);
    }

    it('français : libellés d\'aujourd\'hui', async () => {
      const text = await printedIn('fr');
      expect(text).toContain('Historique des Présences');
      expect(text).toContain('Étudiant : Amina Belkacem');
      expect(text).toContain('Date de la Session');
      expect(text).toContain('Session inconnue (Rattrapage)');
      expect(text).toContain('03/02/2030');
      expect(text).toContain('Signature de l\'Étudiant : ________________________');
      expect(text).toContain('Page 1 sur 2');
    });

    it('arabe : document en français, chiffres latins, aucun caractère arabe', async () => {
      const text = await printedIn('ar');
      expect(text).toContain('Historique des Présences');
      expect(text).toContain('Session inconnue (Rattrapage)');
      expect(text).toContain('"Oui"');
      expect(text).toContain('03/02/2030');
      expect(text).not.toMatch(/[\u0600-\u06FF]/);
    });

    it('anglais : document en anglais', async () => {
      const text = await printedIn('en');
      expect(text).toContain('Attendance history');
      expect(text).toContain('Page 1 of 2');
      expect(text).not.toContain('Historique des Présences');
    });
  });

  it('n\'affiche aucun nom avant la réponse du serveur', () => {
    // Le nom est chargé de façon asynchrone : afficher « undefined undefined » en attendant
    // serait pire que de n'afficher rien.
    expect(component.studentName).toBe('');
  });
});
