import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import pdfMake from 'pdfmake/build/pdfmake';
import { TDocumentDefinitions } from 'pdfmake/interfaces';

import { PaymentHistoryDialogComponent } from './payment-history-dialog.component';
import { SeriesHistoryDTO } from '../../../../models/sessionSerie/SeriesHistoryDTO';
import { createDialogRefSpy, DialogRefSpy, matDialogProviders, setupComponentTestBed } from '../../../../../testing/setup';

/**
 * Historique de paiement d'un étudiant, série par série.
 *
 * <p>`data.studentId` est déréférencé au chargement. Les requêtes sont laissées en attente :
 * ce qui est vérifié est que le dialogue s'ouvre sur le bon étudiant et n'affiche aucune
 * série tant qu'aucun groupe n'est choisi — l'écran est un sélecteur avant d'être un tableau.</p>
 */
describe('PaymentHistoryDialogComponent', () => {
  let component: PaymentHistoryDialogComponent;
  let fixture: ComponentFixture<PaymentHistoryDialogComponent>;
  let dialogRef: DialogRefSpy;

  beforeEach(async () => {
    dialogRef = createDialogRefSpy();
    await setupComponentTestBed(PaymentHistoryDialogComponent, {
      providers: matDialogProviders({ studentId: 42 }, dialogRef)
    });
    fixture = TestBed.createComponent(PaymentHistoryDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('porte l\'étudiant sur lequel il a été ouvert', () => {
    expect(component.data.studentId).toBe(42);
  });

  it('n\'affiche aucune série tant qu\'aucun groupe n\'est sélectionné', () => {
    expect(component.selectedSeries).toBeFalsy();
    expect(component.sessionSeries).toEqual([]);
  });

  /**
   * Une séance à venir n'est pas une séance écartée.
   *
   * <p>Les deux cas partageaient le même libellé : une séance postérieure à l'inscription,
   * facturée et déjà réglée, mais dont la feuille de présence n'est pas encore saisie,
   * s'annonçait « non présent — séance antérieure à l'inscription, non facturée », en
   * contradiction avec le coût de la série affiché au-dessus. La présence n'étant saisie
   * qu'après la séance, son absence ne dit rien de la facturation.</p>
   */
  describe('statusLabel', () => {
    const row = (over: Partial<Parameters<typeof component.statusLabel>[0]> = {}) => ({
      sessionName: 'passif voice 1',
      paymentDate: null,
      amountPaid: 0,
      status: 'unpaid' as const,
      catchUp: false,
      attendanceRecorded: true,
      excluded: false,
      pendingDecision: false,
      ...over
    });

    // Le harnais ne charge pas les fichiers de traduction : `instant()` renvoie la clé
    // demandée. C'est la clé qui est donc vérifiée, et c'est bien elle qui porte le bug.
    it('n\'annonce pas le motif d\'exclusion pour une séance réglée sans présence saisie', () => {
      const label = component.statusLabel(row({ attendanceRecorded: false, status: 'paid', amountPaid: 2000 }));

      expect(label).not.toBe('payment.history.excluded.reason');
      expect(label).toBe('payment.history.attendancePending');
    });

    it('réserve le motif d\'exclusion aux séances réellement écartées', () => {
      expect(component.statusLabel(row({ excluded: true }))).toBe('payment.history.excluded.reason');
    });

    it('rend le statut du serveur pour une séance dont la présence est saisie', () => {
      expect(component.statusLabel(row({ status: 'paid' }))).toBe('payment.history.status.paid');
    });

    /**
     * Un rattrapage à préciser n'est ni une dette ni une gratuité : son libellé doit être
     * distinct des deux autres, sinon la ligne annonce une décision que personne n'a prise.
     */
    it('distingue un rattrapage à préciser d\'une séance écartée et d\'un impayé', () => {
      const pending = component.statusLabel(row({ pendingDecision: true, status: 'unpaid' }));

      expect(pending).toBe('payment.history.pendingDecision.reason');
      expect(pending).not.toBe('payment.history.excluded.reason');
      expect(pending).not.toBe('payment.history.status.unpaid');
    });

    it('donne la priorité à « à préciser » sur « écartée » quand les deux sont vrais', () => {
      // Le serveur ne devrait pas produire cette combinaison, mais l'ordre des tests ne doit pas
      // dépendre de cette confiance : la décision non prise primera toujours.
      expect(component.statusLabel(row({ pendingDecision: true, excluded: true })))
        .toBe('payment.history.pendingDecision.reason');
    });
  });

  /**
   * Résumé de la série : le statut vient du serveur, jugé sur le versé net des remboursements.
   */
  describe('résumé de la série', () => {
    const series = (over: Partial<SeriesHistoryDTO> = {}): SeriesHistoryDTO => ({
      seriesId: 5, seriesName: 'Octobre', paymentStatus: 'PARTIAL',
      totalAmountPaid: 2000, totalCost: 2400, totalRefunded: 400, sessions: [], ...over
    });

    function select(value: SeriesHistoryDTO): void {
      // L'historique est laissé en attente par le harnais : on simule sa fin de chargement.
      component.loading = false;
      component.sessionSeries = [value];
      component.selectedSeries = value.seriesId;
      component.loadPaymentHistory();
      fixture.detectChanges();
    }

    it('reprend le statut du serveur : FULL, UNPAID, PARTIAL', () => {
      select(series({ paymentStatus: 'FULL' }));
      expect(component.seriesStatus).toBe('paid');
      select(series({ paymentStatus: 'UNPAID', totalAmountPaid: 0 }));
      expect(component.seriesStatus).toBe('unpaid');
      select(series({ paymentStatus: 'PARTIAL' }));
      expect(component.seriesStatus).toBe('partiallyPaid');
    });

    it('ne déduit plus « non payé » d\'un versé brut : une série remboursée reste celle du serveur', () => {
      // Versé net 2 000 : le serveur dit « partiel », le dialogue ne le contredit pas.
      select(series({ paymentStatus: 'PARTIAL', totalAmountPaid: 2000 }));
      expect(component.seriesStatus).toBe('partiallyPaid');
      expect(component.seriesRemaining).toBe(400);
      expect(component.seriesRefunded).toBe(400);
    });

    it('affiche les montants au format français', () => {
      TestBed.inject(TranslateService).use('fr');
      select(series());

      const text = (fixture.nativeElement.textContent as string).replace(/\s+/g, ' ');
      expect(text).toContain('2 400,00 DA');
      expect(text).not.toContain('2,400.00');
    });

    it('imprime le remboursé dans le PDF, à côté d\'un versé désormais net', async () => {
      select(series());
      spyOn(component as never, 'convertImageToBase64').and.returnValue(Promise.resolve('') as never);
      let captured: TDocumentDefinitions = { content: [] };
      spyOn(pdfMake, 'createPdf').and.callFake(((definition: TDocumentDefinitions) => {
        captured = definition;
        return { getBlob: () => undefined };
      }) as never);

      await component.generatePdf();

      const texts = JSON.stringify(captured.content);
      expect(texts).toContain('payment.history.labels.refunded : 400 DA');
    });

    it('n\'imprime pas de ligne « remboursé » sans remboursement', async () => {
      select(series({ totalRefunded: 0 }));
      spyOn(component as never, 'convertImageToBase64').and.returnValue(Promise.resolve('') as never);
      let captured: TDocumentDefinitions = { content: [] };
      spyOn(pdfMake, 'createPdf').and.callFake(((definition: TDocumentDefinitions) => {
        captured = definition;
        return { getBlob: () => undefined };
      }) as never);

      await component.generatePdf();

      expect(JSON.stringify(captured.content)).not.toContain('payment.history.labels.refunded');
    });
  });
});
