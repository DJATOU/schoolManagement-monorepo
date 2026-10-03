import { CommonModule } from '@angular/common';
import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs';

import { CorrectionJournal, JournalEntry } from '../../../models/correction/journal';
import { CorrectionJournalPdfService } from '../../../services/correction-journal-pdf.service';
import { CorrectionJournalService } from '../../../services/correction-journal.service';
import { formatJournalInstant } from '../../../utils/journal-format';

/**
 * Journal des corrections d'un élève, sur une période choisie, et son impression (spec
 * admin-corrections, D.5 ; exigences 12.1 à 12.5).
 *
 * <p>Ce qui a été corrigé à son sujet, en clair, du plus récent au plus ancien : versements
 * annulés ou remplacés, dates d'inscription, présences, justifications, rattrapages. Chaque entrée
 * dit son effet sur le dû quand il y en a un, son Motif et son auteur.</p>
 *
 * <ul>
 *   <li>Lu à l'ouverture du panneau, et relu à chaque ouverture : une correction faite ailleurs
 *       sur la fiche y apparaît sans recharger la page.</li>
 *   <li><b>Imprimer</b> relit le Journal sur la période saisie, puis l'imprime : une période tapée
 *       mais pas encore affichée ne peut pas sortir sous l'en-tête de la précédente.</li>
 *   <li>Une période dont la fin précède le début est refusée sur place, sans appel.</li>
 * </ul>
 *
 * <p>Réservé à l'ADMIN, comme les versements, dont il nomme les reçus. Ouvert sur une année close.</p>
 */
@Component({
  selector: 'app-student-journal',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatExpansionModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    TranslateModule
  ],
  templateUrl: './student-journal.component.html',
  styleUrls: ['./student-journal.component.scss']
})
export class StudentJournalComponent implements OnChanges {
  /** Élève dont le Journal est lu. */
  @Input() studentId: number | null | undefined;

  readonly form: FormGroup;

  journal: CorrectionJournal | null = null;
  loading = false;
  printing = false;
  errorMessage = '';
  /** Le panneau a été ouvert : le Journal se relit quand l'élève change. */
  private opened = false;
  private request: Subscription | null = null;

  constructor(
    fb: FormBuilder,
    private journalService: CorrectionJournalService,
    private pdfService: CorrectionJournalPdfService,
    private translate: TranslateService,
    private snackBar: MatSnackBar
  ) {
    this.form = fb.group({ from: [''], to: [''] });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['studentId']) {
      this.journal = null;
      if (this.opened) {
        this.reload();
      }
    }
  }

  /** Le panneau s'ouvre : le Journal est lu, ou relu. */
  onOpened(): void {
    this.opened = true;
    this.reload();
  }

  /** La fin précède le début : refusé avant tout appel. */
  get periodInvalid(): boolean {
    const { from, to } = this.period();
    return from !== null && to !== null && to < from;
  }

  /** Lit le Journal sur la période saisie. */
  reload(): void {
    this.load(() => undefined);
  }

  /** Toute période : bornes effacées, Journal relu. */
  resetPeriod(): void {
    this.form.setValue({ from: '', to: '' });
    this.reload();
  }

  /** Relit le Journal sur la période saisie, puis l'imprime tel qu'affiché. */
  print(): void {
    if (this.printing) {
      return;
    }
    this.printing = true;
    const started = this.load(journal => {
      if (!journal) {
        this.printing = false;
        return;
      }
      this.pdfService.print(journal)
        .catch((err: unknown) => {
          console.error('Erreur lors de l\'impression du journal :', err);
          this.notify(this.translate.instant('journal.printError'));
        })
        .finally(() => this.printing = false);
    });
    if (!started) {
      this.printing = false;
    }
  }

  instant(entry: JournalEntry): string {
    return formatJournalInstant(entry.performedAt);
  }

  /** Une ligne par série touchée. */
  effects(entry: JournalEntry): string[] {
    return entry.amountEffect ? entry.amountEffect.split(' ; ') : [];
  }

  /**
   * Lit le Journal sur la période saisie, puis appelle `done` avec lui, ou `null` en cas d'échec
   * (déjà affiché). Une lecture en cours est abandonnée : seule la dernière demandée s'affiche.
   *
   * @return faux s'il n'y a rien à lire : élève absent, période à l'envers
   */
  private load(done: (journal: CorrectionJournal | null) => void): boolean {
    if (this.studentId === null || this.studentId === undefined || this.periodInvalid) {
      return false;
    }
    const { from, to } = this.period();
    this.request?.unsubscribe();
    this.loading = true;
    this.errorMessage = '';
    this.request = this.journalService.getJournal(this.studentId, from, to).subscribe({
      next: journal => {
        this.journal = journal;
        this.loading = false;
        done(journal);
      },
      error: (err: Error) => {
        this.journal = null;
        this.loading = false;
        this.errorMessage = err.message || this.translate.instant('journal.loadError');
        done(null);
      }
    });
    return true;
  }

  private period(): { from: string | null; to: string | null } {
    const value = this.form.value as { from?: string | null; to?: string | null };
    return { from: value.from || null, to: value.to || null };
  }

  private notify(message: string): void {
    this.snackBar.open(message, this.translate.instant('common.close'), { duration: 6000 });
  }
}
