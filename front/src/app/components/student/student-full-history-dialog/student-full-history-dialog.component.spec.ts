import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import frTranslations from '../../../../assets/i18n/fr.json';

import { MatSnackBar } from '@angular/material/snack-bar';

import { StudentFullHistoryDialogComponent } from './student-full-history-dialog.component';
import { StudentService } from '../services/student.service';
import { PdfGeneratorService } from '../services/pdf-generator.service';
import { StudentFullHistoryDTO } from '../domain/StudentFullHistoryDTO';
import { SeriesHistoryDTO } from '../../../models/sessionSerie/SeriesHistoryDTO';
import { SessionHistoryDTO } from '../../../models/session/SessionHistoryDTO';
import { RefundService } from '../../../services/refund.service';
import { RefundReceiptPdfService } from '../../../services/refund-receipt-pdf.service';
import { AuthService } from '../../../services/auth.service';
import { RefundReceipt, StudentRefund } from '../../../models/refund/refund';

/** Espaces de groupement des montants (U+202F en français) ramenées à une espace ordinaire. */
const plain = (text: string | null | undefined): string => (text ?? '').replace(/\s+/g, ' ');

/**
 * Tests du StudentFullHistoryDialogComponent (tâche 18.1).
 * Couvre le rendu à l'écran des paiements, rattrapages, exemptions et remboursements.
 * Requirements: 14.1, 14.4
 */
describe('StudentFullHistoryDialogComponent', () => {
  let component: StudentFullHistoryDialogComponent;
  let fixture: ComponentFixture<StudentFullHistoryDialogComponent>;
  let studentService: jasmine.SpyObj<StudentService>;
  let pdfService: jasmine.SpyObj<PdfGeneratorService>;
  let dialogRef: jasmine.SpyObj<MatDialogRef<StudentFullHistoryDialogComponent>>;

  const session = (overrides: Partial<SessionHistoryDTO> = {}): SessionHistoryDTO => ({
    catchUpSession: false,
    sessionId: 1,
    sessionName: 'Séance 1',
    sessionDate: '2024-01-10',
    attendanceStatus: 'PRESENT',
    isJustified: false,
    description: '',
    paymentStatus: 'PAID',
    amountPaid: 30,
    paymentDate: '2024-01-10',
    ...overrides
  });

  const fullHistory: StudentFullHistoryDTO = {
    studentId: 1,
    studentName: 'Jean Dupont',
    catchUp: false,
    groups: [
      {
        groupId: 1,
        groupName: 'Groupe Math',
        catchUp: false,
        series: [
          {
            seriesId: 10,
            seriesName: 'Série A',
            paymentStatus: 'PAID',
            totalAmountPaid: 240,
            totalCost: 240,
            isExempted: true,
            totalRefunded: 30,
            sessions: [
              // Une séance ordinaire + une de rattrapage : la série n'est donc pas une
              // « série de rattrapage », son récapitulatif de paiement est affiché.
              session({ sessionId: 99, sessionName: 'Séance ordinaire' }),
              session({ sessionId: 100, sessionName: 'Rattrapage 1', catchUpSession: true, isExempted: true })
            ]
          }
        ]
      }
    ]
  };

  let refundService: jasmine.SpyObj<RefundService>;
  let refundReceiptPdf: jasmine.SpyObj<RefundReceiptPdfService>;
  let authService: jasmine.SpyObj<AuthService>;
  let snackBar: jasmine.SpyObj<MatSnackBar>;

  /**
   * @param role rôle de l'utilisateur connecté : le détail des remboursements est réservé à ADMIN.
   *             Par défaut VIEWER, pour que les tests qui ne parlent pas de remboursements
   *             n'appellent pas une route qu'ils ne stubbent pas.
   */
  async function configure(role: 'ADMIN' | 'VIEWER' = 'VIEWER'): Promise<void> {
    studentService = jasmine.createSpyObj('StudentService', ['getStudentFullHistory']);
    pdfService = jasmine.createSpyObj('PdfGeneratorService', ['generateFullHistoryPdf']);
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    refundService = jasmine.createSpyObj('RefundService', ['getStudentRefunds', 'issueReceipt']);
    refundReceiptPdf = jasmine.createSpyObj('RefundReceiptPdfService', ['generateAndPrint']);
    authService = jasmine.createSpyObj('AuthService', ['hasRole']);
    authService.hasRole.and.callFake(required => required === role);
    snackBar = jasmine.createSpyObj('MatSnackBar', ['open']);
    refundService.getStudentRefunds.and.returnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [StudentFullHistoryDialogComponent, NoopAnimationsModule, TranslateModule.forRoot()],
      providers: [
        { provide: StudentService, useValue: studentService },
        { provide: PdfGeneratorService, useValue: pdfService },
        { provide: RefundService, useValue: refundService },
        { provide: RefundReceiptPdfService, useValue: refundReceiptPdf },
        { provide: AuthService, useValue: authService },
        { provide: MatSnackBar, useValue: snackBar },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: { studentId: 1 } }
      ]
    }).compileComponents();

    // Le gabarit affiche des libellés traduits : on charge les vraies traductions FR
    // pour que les assertions portent sur le rendu réel.
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', frTranslations);
    translate.use('fr');
  }

  describe('successful load', () => {
    beforeEach(async () => {
      await configure();
      studentService.getStudentFullHistory.and.returnValue(of(fullHistory));
      fixture = TestBed.createComponent(StudentFullHistoryDialogComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('should create', () => {
      expect(component).toBeTruthy();
    });

    it('loads the full history on init', () => {
      expect(studentService.getStudentFullHistory).toHaveBeenCalledWith(1);
      expect(component.fullHistory).toEqual(fullHistory);
      expect(component.loading).toBeFalse();
    });

    it('renders the student name and the three amounts in the DOM', () => {
      const el: HTMLElement = fixture.nativeElement;
      expect(el.textContent).toContain('Jean Dupont');
      expect(el.textContent).toContain('Groupe Math');
      // Les trois montants sont distincts et étiquetés : « 240 / 240 » sur une seule ligne
      // n'indiquait pas lequel était le dû et lequel le versé.
      expect(el.textContent).toContain('Dû');
      expect(el.textContent).toContain('Versé');
      expect(el.textContent).toContain('Reste');
      // Format français, comme les PDF : « 240,00 », jamais « 240.00 ».
      expect(el.textContent).toContain('240,00');
      expect(el.textContent).not.toContain('240.00');
      expect(el.querySelectorAll('.fh-amount').length).toBe(3);
    });

    it('never shows the word « prorata » to the user', () => {
      // Jargon interne : il peut rester dans le code et les commentaires, jamais à l'écran.
      const el: HTMLElement = fixture.nativeElement;
      expect(el.textContent?.toLowerCase()).not.toContain('prorata');
    });

    it('shows the catch-up indicator for a catch-up session', () => {
      const el: HTMLElement = fixture.nativeElement;
      const tags = el.querySelectorAll('.fh-catchup-tag');
      // Au moins un tag « Rattrapage » (légende + séance).
      expect(tags.length).toBeGreaterThan(0);
      expect(el.textContent).toContain('Rattrapage');
    });

    it('shows the exemption badge and its legend entry', () => {
      const el: HTMLElement = fixture.nativeElement;
      // « Exempté » et non « Soldé » : la série n'a pas été réglée, elle n'était pas due.
      expect(el.querySelector('.fh-badge-EXEMPTED')).toBeTruthy();
      expect(el.textContent).toContain('Exempté');
      expect(el.textContent).toContain('Séance exemptée');
    });

    it('hides the « Justifiée » column when the series has no absence', () => {
      // Toutes les séances de la série sont PRESENT : la colonne serait vide sur chaque ligne.
      const series = fullHistory.groups[0].series[0];
      expect(component.showJustifiedColumn(series)).toBeFalse();
      expect(fixture.nativeElement.textContent).not.toContain('Justifiée');
    });

    it('renders the refunded total of the series, without a per-session refund column', () => {
      // Un remboursement porte sur le versement de la série, jamais sur une séance : il est
      // nommé sous la série, et le tableau n'a plus de colonne « Remboursé » toujours vide.
      const el: HTMLElement = fixture.nativeElement;
      expect(plain(el.querySelector('.fh-refund')?.textContent)).toContain('Remboursé : 30,00 DA');
      expect(el.querySelector('.fh-refund-cell')).toBeNull();
    });

    it('says that the paid amount is net of the refund', () => {
      // 30 DA rendus : sans la mention, la famille lirait un versé inférieur à ce qu'elle a remis.
      expect(plain(fixture.nativeElement.querySelector('.fh-amount-net')?.textContent))
        .toContain('Net de 30,00 DA remboursés');
    });

    it('does not load the refund details for a VIEWER', () => {
      // Pièces de caisse : route réservée à ADMIN. L'appeler pour un VIEWER produirait un 403.
      expect(refundService.getStudentRefunds).not.toHaveBeenCalled();
      expect(fixture.nativeElement.querySelector('.fh-refund-list')).toBeNull();
      expect(component.canSeeRefunds).toBeFalse();
    });

    it('delegates PDF generation to the PdfGeneratorService', () => {
      component.generatePdf();
      expect(pdfService.generateFullHistoryPdf)
        .toHaveBeenCalledWith(fullHistory, 'assets/succes_assistance.png', []);
    });
  });

  describe('helper logic', () => {
    beforeEach(async () => {
      await configure();
      studentService.getStudentFullHistory.and.returnValue(of(fullHistory));
      fixture = TestBed.createComponent(StudentFullHistoryDialogComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('filters out CANCELLED payments from active sessions', () => {
      const series: SeriesHistoryDTO = {
        seriesId: 1,
        seriesName: 'S',
        paymentStatus: 'x',
        totalAmountPaid: 0,
        totalCost: 0,
        sessions: [
          session({ sessionId: 1, paymentStatus: 'CANCELLED' }),
          session({ sessionId: 2, paymentStatus: 'PAID' })
        ]
      };
      const active = component.getActiveSessions(series);
      expect(active.length).toBe(1);
      expect(active[0].sessionId).toBe(2);
    });

    it('detects a catch-up-only series', () => {
      const series: SeriesHistoryDTO = {
        seriesId: 1, seriesName: 'S', paymentStatus: 'x', totalAmountPaid: 0, totalCost: 0,
        sessions: [session({ catchUpSession: true }), session({ sessionId: 2, catchUpSession: true })]
      };
      expect(component.isCatchUpSeries(series)).toBeTrue();
    });

    it('returns exempted row class for present + exempted', () => {
      expect(component.getSessionRowClass(session({ isExempted: true, attendanceStatus: 'PRESENT' }))).toBe('row-exempted');
    });

    it('returns the paid row class for a settled session', () => {
      expect(component.getSessionRowClass(
        session({ attendanceStatus: 'PRESENT', paymentStatus: 'PAID', amountRemaining: 0 })
      )).toBe('row-paid');
    });

    it('derives the badge from the server status and the exemption, with exemption first', () => {
      const base = { seriesId: 1, seriesName: 'S', totalAmountPaid: 0, totalCost: 0, sessions: [] };
      expect(component.seriesBadge({ ...base, paymentStatus: 'FULL' })).toBe('FULL');
      expect(component.seriesBadge({ ...base, paymentStatus: 'PARTIAL' })).toBe('PARTIAL');
      // Rien de versé : « Non payé », et non « Partiel », qui se lisait comme un règlement entamé.
      expect(component.seriesBadge({ ...base, paymentStatus: 'UNPAID' })).toBe('UNPAID');
      expect(component.seriesBadgeIcon({ ...base, paymentStatus: 'UNPAID' })).toBe('error_outline');
      expect(component.seriesBadgeIcon({ ...base, paymentStatus: 'FULL' })).toBe('check_circle');
      expect(component.seriesBadgeIcon({ ...base, paymentStatus: 'PARTIAL' })).toBe('schedule');
      expect(component.seriesBadgeIcon({ ...base, paymentStatus: 'FULL', isExempted: true }))
        .toBe('volunteer_activism');
      // Une série exemptée a un coût nul, donc un versé nul, que le serveur rapporte « FULL ».
      // Afficher « Soldé » attribuerait à la famille un règlement qu'elle n'a jamais fait.
      expect(component.seriesBadge({ ...base, paymentStatus: 'FULL', isExempted: true }))
        .toBe('EXEMPTED');
    });

    it('never shows a negative remaining amount', () => {
      // Une série historiquement sur-encaissée : l'écart est porté par `totalOverpaid`, affiché
      // à part. Un « reste » négatif se lirait comme une dette inversée.
      const overpaid: SeriesHistoryDTO = {
        seriesId: 1, seriesName: 'S', paymentStatus: 'FULL',
        totalAmountPaid: 300, totalCost: 240, sessions: []
      };
      expect(component.seriesRemaining(overpaid)).toBe(0);
    });

    it('treats a session as due when the server sends no remaining amount', () => {
      // Repli sur le statut : sans lui, une réponse d'une version antérieure du serveur
      // afficherait toute séance impayée en vert.
      expect(component.sessionState(
        session({ paymentStatus: 'UNPAID', amountRemaining: undefined })
      )).toBe('DUE');
      expect(component.sessionState(
        session({ paymentStatus: 'PAID', amountRemaining: undefined })
      )).toBe('PAID');
    });

    it('flags a partially covered session as still due', () => {
      // Le statut seul ne distingue pas une séance à moitié couverte d'une séance soldée.
      expect(component.sessionState(
        session({ paymentStatus: 'PARTIAL', amountDue: 30, amountRemaining: 15 })
      )).toBe('DUE');
      expect(component.sessionAmount(
        session({ paymentStatus: 'PARTIAL', amountDue: 30, amountRemaining: 15 })
      )).toBe(15);
    });

    it('returns justification text only for absences', () => {
      expect(component.getJustificationText(session({ attendanceStatus: 'ABSENT', isJustified: true }))).toBe('Oui');
      expect(component.getJustificationText(session({ attendanceStatus: 'ABSENT', isJustified: false }))).toBe('Non');
      expect(component.getJustificationText(session({ attendanceStatus: 'PRESENT' }))).toBe('');
    });
  });

  /**
   * Lisibilité du prorata (exigences 11.3 à 11.6).
   *
   * <p>Une séance écartée du prorata et une séance facturable impayée sont deux choses
   * différentes : seule la seconde est une dette. Les confondre fait croire à un retard de
   * paiement inexistant.</p>
   */
  describe('prorata readability', () => {
    const withExcluded: StudentFullHistoryDTO = {
      studentId: 2,
      studentName: 'Amina Belkacem',
      catchUp: false,
      groups: [
        {
          groupId: 2,
          groupName: 'Groupe Physique',
          catchUp: false,
          series: [
            {
              seriesId: 20,
              seriesName: 'Série B',
              paymentStatus: 'FULL',
              totalAmountPaid: 30,
              totalCost: 30,
              billableSessions: 1,
              sessions: [
                // Séance tenue avant l'inscription et non suivie : affichée, non facturée.
                session({
                  sessionId: 201,
                  sessionName: 'Séance avant inscription',
                  billable: false,
                  attendanceStatus: 'UNKNOWN',
                  paymentStatus: 'UNPAID',
                  amountPaid: 0
                }),
                // Séance facturable et réglée.
                session({ sessionId: 202, sessionName: 'Séance facturée', billable: true })
              ]
            }
          ]
        }
      ]
    };

    beforeEach(async () => {
      await configure();
      studentService.getStudentFullHistory.and.returnValue(of(withExcluded));
      fixture = TestBed.createComponent(StudentFullHistoryDialogComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('marks an excluded session as not billed instead of unpaid', () => {
      const excluded = withExcluded.groups[0].series[0].sessions[0];
      expect(component.isExcluded(excluded)).toBeTrue();
      expect(component.sessionAmountLabel(excluded)).toBe('Non facturée');
      // Jamais la teinte d'une séance due : c'est tout l'enjeu de l'exigence 11.4.
      expect(component.getSessionRowClass(excluded)).toBe('row-not-billed');
    });

    it('keeps a billable unpaid session distinct from an excluded one', () => {
      const unpaid = session({
        billable: true, attendanceStatus: 'PRESENT', paymentStatus: 'UNPAID',
        amountDue: 30, amountRemaining: 30, amountPaid: 0
      });
      expect(component.getSessionRowClass(unpaid)).toBe('row-due');
      expect(component.sessionAmountLabel(unpaid)).toBe('à régler');
      // Le montant annoncé est ce qu'il reste à régler, non le zéro déjà versé.
      expect(component.sessionAmount(unpaid)).toBe(30);
    });

    it('renders a textual badge and the exclusion reason, not colour alone', () => {
      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('.fh-excluded-tag')).toBeTruthy();
      expect(el.textContent).toContain('Non facturée');
      expect(el.textContent).toContain('antérieure à l\'inscription');
    });

    it('counts billable and excluded sessions separately', () => {
      const series = withExcluded.groups[0].series[0];
      expect(component.billableSessionsCount(series)).toBe(1);
      expect(component.excludedSessionsCount(series)).toBe(1);
    });

    it('spells the cost out in plain language instead of saying « prorata »', async () => {
      // C'est la substitution demandée : « 2 séances × 6 000 DA = 12 000 DA » remplace un mot
      // que la famille ne peut pas interpréter.
      const priced: StudentFullHistoryDTO = {
        ...withExcluded,
        groups: [{
          ...withExcluded.groups[0],
          series: [{
            ...withExcluded.groups[0].series[0],
            billableSessions: 2,
            totalCost: 12000,
            unitPriceNet: 6000
          }]
        }]
      };
      studentService.getStudentFullHistory.and.returnValue(of(priced));
      const priceFixture = TestBed.createComponent(StudentFullHistoryDialogComponent);
      priceFixture.detectChanges();

      const text = plain(priceFixture.nativeElement.textContent);
      expect(text).toContain('2 séance(s) × 6 000,00 DA = 12 000,00 DA');
      expect(text.toLowerCase()).not.toContain('prorata');
    });

    it('strikes the catalogue price through when a discount applies', () => {
      // Sans le tarif barré, un prix réduit de moitié paraîtrait arbitraire et
      // l'administrateur ne pourrait pas l'expliquer à la famille.
      const series = withExcluded.groups[0].series[0];
      expect(component.hasDiscount({ ...series, unitPriceNet: 3000, unitPriceGross: 6000 }))
        .toBeTrue();
      expect(component.hasDiscount({ ...series, unitPriceNet: 6000, unitPriceGross: 6000 }))
        .toBeFalse();
      // Prix absents : rien à barrer, et surtout aucune comparaison hasardeuse.
      expect(component.hasDiscount(series)).toBeFalse();
    });

    it('shows the inline exclusion reason on the row itself', () => {
      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('.fh-inline-reason')).toBeTruthy();
      // Le motif est dans la ligne : hors d'elle, il faudrait relier une note de bas de carte
      // à une ligne précise pour comprendre pourquoi la séance n'est pas due.
      expect(el.textContent).toContain('Avant inscription');
      expect(el.textContent).toContain('Ce n\'est pas une dette.');
    });
  });

  /**
   * Remboursements nommés sous leur série et réimpression du reçu (décision du propriétaire
   * produit : le versé est net, et la pièce qui l'explique doit être retrouvable).
   */
  describe('refunds for an ADMIN', () => {
    const october: SeriesHistoryDTO = {
      seriesId: 10, seriesName: 'Octobre', paymentStatus: 'PARTIAL',
      totalAmountPaid: 2000, totalCost: 2400, totalRefunded: 400,
      sessions: [session({ sessionId: 1, amountRemaining: 0 })]
    };
    const november: SeriesHistoryDTO = {
      seriesId: 11, seriesName: 'Novembre', paymentStatus: 'UNPAID',
      totalAmountPaid: 0, totalCost: 2400, totalRefunded: 0,
      sessions: [session({ sessionId: 2, paymentStatus: 'UNPAID', amountPaid: 0, amountDue: 300, amountRemaining: 300 })]
    };
    const history: StudentFullHistoryDTO = {
      studentId: 1, studentName: 'Camille Amrani', catchUp: false,
      groups: [{ groupId: 1, groupName: 'Maths 4 AM A', catchUp: false, series: [october, november] }]
    };
    const refund = (overrides: Partial<StudentRefund> = {}): StudentRefund => ({
      refundId: 7, refundNumber: 'REMB-2026-0007', refundDate: '2026-10-05T10:00:00',
      amount: 400, reason: 'Trop-perçu', seriesId: 10, seriesName: 'Octobre',
      groupId: 1, groupName: 'Maths 4 AM A', ...overrides
    });
    const receipt = { refundId: 7, refundNumber: 'REMB-2026-0007', issuanceRank: 2 } as RefundReceipt;

    async function open(refunds: StudentRefund[]): Promise<void> {
      await configure('ADMIN');
      studentService.getStudentFullHistory.and.returnValue(of(history));
      refundService.getStudentRefunds.and.returnValue(of(refunds));
      fixture = TestBed.createComponent(StudentFullHistoryDialogComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    }

    const seriesCards = (): HTMLElement[] =>
      Array.from(fixture.nativeElement.querySelectorAll('.fh-series')) as HTMLElement[];

    it('lists each refund under its own series: number, date, amount, reason', async () => {
      await open([refund()]);

      expect(refundService.getStudentRefunds).toHaveBeenCalledWith(1);
      const [octoberCard, novemberCard] = seriesCards();
      const item = plain(octoberCard.querySelector('.fh-refund-item')?.textContent);
      expect(item).toContain('REMB-2026-0007');
      expect(item).toContain('05/10/2026');
      expect(item).toContain('400,00 DA');
      expect(item).toContain('Trop-perçu');
      // Aucun remboursement sur novembre : rien n'y est listé, et son versé n'est pas dit « net ».
      expect(novemberCard.querySelector('.fh-refund-item')).toBeNull();
      expect(novemberCard.querySelector('.fh-refunds')).toBeNull();
      expect(novemberCard.querySelector('.fh-amount-net')).toBeNull();
    });

    it('names a missing reason instead of leaving a blank', async () => {
      await open([refund({ reason: null })]);

      expect(plain(seriesCards()[0].querySelector('.fh-refund-reason')?.textContent))
        .toContain('Motif non renseigné');
    });

    it('shows « Non payé » on a series with nothing paid', async () => {
      await open([]);

      const badge = seriesCards()[1].querySelector('.fh-badge-UNPAID');
      expect(badge).toBeTruthy();
      expect(badge?.textContent).toContain('Non payé');
      expect(seriesCards()[0].querySelector('.fh-badge-PARTIAL')).toBeTruthy();
    });

    it('says so when the refund details cannot be loaded, and keeps the total', async () => {
      await configure('ADMIN');
      studentService.getStudentFullHistory.and.returnValue(of(history));
      refundService.getStudentRefunds.and.returnValue(throwError(() => new Error('boom')));
      fixture = TestBed.createComponent(StudentFullHistoryDialogComponent);
      fixture.detectChanges();

      const card = seriesCards()[0];
      expect(plain(card.querySelector('.fh-refund')?.textContent)).toContain('400,00 DA');
      expect(card.querySelector('.fh-refund-unavailable')?.textContent)
        .toContain('Le détail des remboursements n\'a pas pu être chargé.');
    });

    it('reprints the receipt: the server issues it, the PDF service prints it', async () => {
      await open([refund()]);
      refundService.issueReceipt.and.returnValue(of(receipt));
      refundReceiptPdf.generateAndPrint.and.returnValue(Promise.resolve());

      (seriesCards()[0].querySelector('.fh-refund-reprint') as HTMLButtonElement).click();

      expect(refundService.issueReceipt).toHaveBeenCalledWith(7);
      expect(refundReceiptPdf.generateAndPrint).toHaveBeenCalledWith(receipt);
      expect(component.reprintingRefundId).toBeNull();
    });

    it('ignores a second click while a reprint is in progress', async () => {
      await open([refund()]);
      // Réponse en attente : la réimpression reste en cours.
      refundService.issueReceipt.and.returnValue(new Subject<RefundReceipt>());

      component.reprintRefund(refund());
      component.reprintRefund(refund());

      expect(refundService.issueReceipt).toHaveBeenCalledTimes(1);
      expect(component.reprintingRefundId).toBe(7);
    });

    it('reports a refused reprint with the server message, and allows a new attempt', async () => {
      await open([refund()]);
      refundService.issueReceipt.and.returnValue(throwError(() => new Error('Remboursement introuvable ou inactif')));

      component.reprintRefund(refund());

      expect(snackBar.open).toHaveBeenCalledWith('Remboursement introuvable ou inactif', 'Fermer', jasmine.any(Object));
      expect(refundReceiptPdf.generateAndPrint).not.toHaveBeenCalled();
      expect(component.reprintingRefundId).toBeNull();
    });

    it('reports a failed printing', async () => {
      await open([refund()]);
      refundService.issueReceipt.and.returnValue(of(receipt));
      refundReceiptPdf.generateAndPrint.and.returnValue(Promise.reject(new Error('iframe')));
      spyOn(console, 'error');

      component.reprintRefund(refund());
      await fixture.whenStable();

      expect(snackBar.open).toHaveBeenCalledWith(
        'Le reçu de remboursement n\'a pas pu être imprimé. Réessayez.', 'Fermer', jasmine.any(Object));
    });

    it('passes the loaded refunds to the PDF', async () => {
      const refunds = [refund()];
      await open(refunds);

      component.generatePdf();

      expect(pdfService.generateFullHistoryPdf)
        .toHaveBeenCalledWith(history, 'assets/succes_assistance.png', refunds);
    });
  });

  describe('error handling', () => {
    beforeEach(async () => {
      await configure();
      studentService.getStudentFullHistory.and.returnValue(throwError(() => new Error('Étudiant non trouvé')));
      fixture = TestBed.createComponent(StudentFullHistoryDialogComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
    });

    it('surfaces the error message and stops loading', () => {
      expect(component.loading).toBeFalse();
      expect(component.errorMessage).toBe('Étudiant non trouvé');
      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('.fh-error')).toBeTruthy();
    });

    it('does not call the PDF service when there is no history', () => {
      component.generatePdf();
      expect(pdfService.generateFullHistoryPdf).not.toHaveBeenCalled();
    });
  });
});
