import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateService } from '@ngx-translate/core';
import { of, Subject, throwError } from 'rxjs';

import { StudentJournalComponent } from './student-journal.component';
import { setupComponentTestBed } from '../../../../testing/setup';
import { CorrectionJournal, JournalEntry } from '../../../models/correction/journal';
import { CorrectionJournalPdfService } from '../../../services/correction-journal-pdf.service';
import { CorrectionJournalService } from '../../../services/correction-journal.service';

/**
 * Journal des corrections sur la fiche élève, et son impression (D.5 ; exigence 12).
 *
 * <p>Amine Belkacem : une présence corrigée en janvier, une justification. Le serveur rédige ; l'écran
 * affiche tel quel, dans l'ordre reçu.</p>
 */
describe('StudentJournalComponent', () => {
  let fixture: ComponentFixture<StudentJournalComponent>;
  let component: StudentJournalComponent;
  let journals: jasmine.SpyObj<CorrectionJournalService>;
  let pdf: jasmine.SpyObj<CorrectionJournalPdfService>;

  const presence: JournalEntry = {
    performedAt: '2030-01-14T09:05:00',
    category: 'ATTENDANCE',
    description: 'Séance du 07/01/2030 (Math 1ère A) : Amine Belkacem absent → présent',
    amountEffect: 'Janvier (Math 1ère A) : dû à ce jour 0,00 → 2 000,00 DA ; Février (Math 1ère A) : reste 0,00 → 0,00 DA',
    reasonType: 'OTHER',
    reasonText: 'Feuille d\'un autre groupe',
    performedBy: 'directrice'
  };
  const justification: JournalEntry = {
    performedAt: '2030-01-10T16:30:00',
    category: 'JUSTIFICATION',
    description: 'Séance du 07/01/2030 (Math 1ère A) : absence non justifiée → justifiée',
    amountEffect: null,
    reasonType: null,
    reasonText: 'Certificat médical',
    performedBy: 'secretariat'
  };
  const journal = (overrides: Partial<CorrectionJournal> = {}): CorrectionJournal => ({
    studentId: 42, studentName: 'Amine Belkacem', from: null, to: null, entries: [presence, justification], ...overrides
  });

  beforeEach(async () => {
    journals = jasmine.createSpyObj<CorrectionJournalService>('CorrectionJournalService', ['getJournal']);
    journals.getJournal.and.returnValue(of(journal()));
    pdf = jasmine.createSpyObj<CorrectionJournalPdfService>('CorrectionJournalPdfService', ['print']);
    pdf.print.and.resolveTo();
    await setupComponentTestBed(StudentJournalComponent, {
      providers: [
        { provide: CorrectionJournalService, useValue: journals },
        { provide: CorrectionJournalPdfService, useValue: pdf }
      ]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', {
      journal: {
        title: 'Journal des corrections', empty: 'Aucune correction sur cette période.',
        periodInvalid: 'La date de fin précède la date de début.', noEffect: 'Sans effet sur le dû',
        by: 'par {{user}}', printError: 'Impression impossible', loadError: 'Journal illisible',
        category: { ATTENDANCE: 'Présence', JUSTIFICATION: 'Justification' }
      },
      correction: { reason: { OTHER: 'Autre' } },
      common: { close: 'Fermer' }
    });
    translate.use('fr');
    fixture = TestBed.createComponent(StudentJournalComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('studentId', 42);
    fixture.detectChanges();
  });

  const element = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const items = (): HTMLElement[] => Array.from(element().querySelectorAll('.jr-item'));
  const button = (selector: string): HTMLButtonElement => element().querySelector(selector) as HTMLButtonElement;

  /** Le panneau s'ouvre, comme d'un clic sur son en-tête. */
  function open(): void {
    component.onOpened();
    fixture.detectChanges();
  }

  function period(from: string, to: string): void {
    component.form.setValue({ from, to });
    fixture.detectChanges();
  }

  it('ne lit rien tant que le panneau est fermé', () => {
    expect(journals.getJournal).not.toHaveBeenCalled();
  });

  it('à l\'ouverture : toute période, chaque entrée telle que le serveur l\'a rédigée, dans son ordre', () => {
    open();

    expect(journals.getJournal).toHaveBeenCalledOnceWith(42, null, null);
    expect(element().querySelector('mat-panel-title')?.textContent).toContain('Journal des corrections (2)');
    const [first, second] = items();
    expect(first.querySelector('.jr-category')?.textContent?.trim()).toBe('Présence');
    expect(first.querySelector('.jr-when')?.textContent?.trim()).toBe('14/01/2030 09:05');
    expect(first.querySelector('.jr-by')?.textContent?.trim()).toBe('par directrice');
    expect(first.querySelector('.jr-description')?.textContent?.trim()).toBe(presence.description);
    expect(Array.from(first.querySelectorAll('.jr-effect')).map(line => line.textContent?.trim())).toEqual([
      'paymentsJanvier (Math 1ère A) : dû à ce jour 0,00 → 2 000,00 DA',
      'paymentsFévrier (Math 1ère A) : reste 0,00 → 0,00 DA'
    ]);
    expect(first.querySelector('.jr-reason')?.textContent).toContain('Autre');
    expect(first.querySelector('.jr-reason')?.textContent).toContain('— ');
    expect(first.querySelector('.jr-reason')?.textContent).toContain('Feuille d\'un autre groupe');
    expect(second.querySelector('.jr-category')?.textContent?.trim()).toBe('Justification');
    expect(second.querySelector('.jr-effect--none')?.textContent).toContain('Sans effet sur le dû');
    expect(second.querySelector('.jr-reason strong')).toBeNull();
    expect(second.querySelector('.jr-reason')?.textContent).not.toContain('—');
    expect(second.querySelector('.jr-reason')?.textContent).toContain('Certificat médical');
  });

  it('chaque ouverture relit le Journal : une correction faite ailleurs sur la fiche y apparaît', () => {
    open();
    open();

    expect(journals.getJournal).toHaveBeenCalledTimes(2);
  });

  it('une entrée sans effet ni Motif : ni ligne d\'effet ni ligne de Motif', () => {
    journals.getJournal.and.returnValue(of(journal({ entries: [{ ...presence, category: 'CATCH_UP', amountEffect: null,
      reasonType: null, reasonText: null }] })));
    open();

    expect(items()[0].querySelector('.jr-effect')).toBeNull();
    expect(items()[0].querySelector('.jr-reason')).toBeNull();
  });

  it('période bornée : relue sur ses bornes ; « Toute période » les efface', () => {
    open();
    period('2030-01-01', '2030-01-31');
    button('.jr-apply').click();
    fixture.detectChanges();

    expect(journals.getJournal).toHaveBeenCalledWith(42, '2030-01-01', '2030-01-31');
    button('.jr-reset').click();
    expect(component.form.value).toEqual({ from: '', to: '' });
    expect(journals.getJournal.calls.mostRecent().args).toEqual([42, null, null]);
  });

  it('période à l\'envers : dite sur place, aucune lecture, rien à imprimer', () => {
    open();
    journals.getJournal.calls.reset();
    period('2030-01-31', '2030-01-10');

    expect(element().querySelector('.jr-invalid')?.textContent).toContain('La date de fin précède la date de début.');
    expect(button('.jr-apply').disabled).toBeTrue();
    expect(button('.jr-print').disabled).toBeTrue();
    component.reload();
    component.print();
    expect(journals.getJournal).not.toHaveBeenCalled();
    expect(pdf.print).not.toHaveBeenCalled();
    expect(component.printing).toBeFalse();
  });

  it('une seule borne n\'est jamais « à l\'envers »', () => {
    period('2030-01-31', '');
    expect(component.periodInvalid).toBeFalse();
    period('', '2030-01-10');
    expect(component.periodInvalid).toBeFalse();
  });

  it('Journal vide : le dit', () => {
    journals.getJournal.and.returnValue(of(journal({ entries: [] })));
    open();

    expect(element().querySelector('.pf-empty')?.textContent).toContain('Aucune correction sur cette période.');
    expect(items().length).toBe(0);
  });

  it('lecture refusée : le motif du serveur, ou le message de l\'écran', () => {
    journals.getJournal.and.returnValue(throwError(() => new Error('Étudiant introuvable : 42')));
    open();
    expect(element().querySelector('.jr-error')?.textContent).toContain('Étudiant introuvable : 42');
    expect(component.journal).toBeNull();

    journals.getJournal.and.returnValue(throwError(() => new Error('')));
    open();
    expect(element().querySelector('.jr-error')?.textContent).toContain('Journal illisible');
  });

  it('imprimer : relit le Journal sur la période saisie, puis imprime celui-là', async () => {
    open();
    const january = journal({ from: '2030-01-01', to: '2030-01-31', entries: [presence] });
    journals.getJournal.and.returnValue(of(january));
    period('2030-01-01', '2030-01-31');

    button('.jr-print').click();
    await new Promise(resolve => setTimeout(resolve));

    expect(journals.getJournal.calls.mostRecent().args).toEqual([42, '2030-01-01', '2030-01-31']);
    expect(pdf.print).toHaveBeenCalledOnceWith(january);
    expect(component.journal).toBe(january);
    expect(component.printing).toBeFalse();
  });

  it('impression en cours : un second clic n\'imprime pas deux fois', () => {
    open();
    let finish!: () => void;
    pdf.print.and.returnValue(new Promise<void>(resolve => finish = resolve));

    component.print();
    component.print();

    expect(pdf.print).toHaveBeenCalledTimes(1);
    expect(component.printing).toBeTrue();
    finish();
  });

  it('impression impossible : le dit ; lecture refusée : n\'imprime rien', async () => {
    const snackBar = spyOn(TestBed.inject(MatSnackBar), 'open');
    pdf.print.and.rejectWith(new Error('pdfmake'));
    open();

    component.print();
    // L'échec de l'impression se lit sur sa promesse : quelques microtâches plus tard.
    await new Promise(resolve => setTimeout(resolve));
    expect(snackBar).toHaveBeenCalledWith('Impression impossible', 'Fermer', jasmine.any(Object));
    expect(component.printing).toBeFalse();

    pdf.print.calls.reset();
    journals.getJournal.and.returnValue(throwError(() => new Error('Serveur injoignable')));
    component.print();
    expect(pdf.print).not.toHaveBeenCalled();
    expect(component.printing).toBeFalse();
  });

  it('seule la dernière lecture demandée s\'affiche', () => {
    const slow = new Subject<CorrectionJournal>();
    journals.getJournal.and.returnValues(slow, of(journal({ entries: [justification] })));
    open();
    component.reload();
    slow.next(journal());
    fixture.detectChanges();

    expect(items().length).toBe(1);
  });

  it('autre élève, panneau ouvert : relu pour lui ; panneau fermé : rien', () => {
    fixture.componentRef.setInput('studentId', 7);
    fixture.detectChanges();
    expect(journals.getJournal).not.toHaveBeenCalled();

    open();
    fixture.componentRef.setInput('studentId', 8);
    fixture.detectChanges();
    expect(journals.getJournal.calls.mostRecent().args).toEqual([8, null, null]);
  });

  it('sans élève : rien à lire', () => {
    fixture.componentRef.setInput('studentId', null);
    open();

    expect(journals.getJournal).not.toHaveBeenCalled();
    expect(component.journal).toBeNull();
  });
});
