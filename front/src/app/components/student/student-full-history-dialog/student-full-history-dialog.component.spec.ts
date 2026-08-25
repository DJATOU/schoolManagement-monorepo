import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import frTranslations from '../../../../assets/i18n/fr.json';

import { StudentFullHistoryDialogComponent } from './student-full-history-dialog.component';
import { StudentService } from '../services/student.service';
import { PdfGeneratorService } from '../services/pdf-generator.service';
import { StudentFullHistoryDTO } from '../domain/StudentFullHistoryDTO';
import { SeriesHistoryDTO } from '../../../models/sessionSerie/SeriesHistoryDTO';
import { SessionHistoryDTO } from '../../../models/session/SessionHistoryDTO';

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
              session({ sessionId: 100, sessionName: 'Rattrapage 1', catchUpSession: true, isExempted: true, refundedAmount: 30 })
            ]
          }
        ]
      }
    ]
  };

  async function configure(): Promise<void> {
    studentService = jasmine.createSpyObj('StudentService', ['getStudentFullHistory']);
    pdfService = jasmine.createSpyObj('PdfGeneratorService', ['generateFullHistoryPdf']);
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);

    await TestBed.configureTestingModule({
      imports: [StudentFullHistoryDialogComponent, NoopAnimationsModule, TranslateModule.forRoot()],
      providers: [
        { provide: StudentService, useValue: studentService },
        { provide: PdfGeneratorService, useValue: pdfService },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: { studentId: 1 } }
      ]
    }).compileComponents();

    // Le gabarit affiche des libellés traduits : on charge les vraies traductions FR
    // pour que les assertions portent sur le rendu réel.
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', frTranslations as any);
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
      expect(el.textContent).toContain('240.00');
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

    it('keeps the « Remboursé » column visible when money was returned', () => {
      // De l'argent rendu ne se masque jamais : la colonne n'est repliée que si elle est vide.
      const series = fullHistory.groups[0].series[0];
      expect(component.showRefundColumn(series)).toBeTrue();
      expect(fixture.nativeElement.querySelector('.fh-refund-cell')).toBeTruthy();
    });

    it('renders the refund amount for a series and a session', () => {
      const el: HTMLElement = fixture.nativeElement;
      expect(el.textContent).toContain('Remboursé');
      expect(el.querySelector('.fh-refund-cell')).toBeTruthy();
    });

    it('delegates PDF generation to the PdfGeneratorService', () => {
      component.generatePdf();
      expect(pdfService.generateFullHistoryPdf).toHaveBeenCalledWith(fullHistory, 'assets/succes_assistance.png');
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

      const text = priceFixture.nativeElement.textContent as string;
      expect(text).toContain('2 séance(s) × 6,000.00 DA = 12,000.00 DA');
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
