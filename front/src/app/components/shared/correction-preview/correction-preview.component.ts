import { CommonModule } from '@angular/common';
import { Component, Input, TemplateRef } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import {
  AmountSnapshot,
  CorrectionEffect,
  CorrectionPreview,
  SeriesAmountChange
} from '../../../models/correction/correction';

/** Une ligne du tableau : un montant d'une Série, avant et après. */
interface AmountRow {
  labelKey: string;
  before: number;
  after: number;
}

/**
 * Aperçu d'une correction : pour chaque Série dont un montant change, coût, dû à ce jour, versé,
 * reste à payer et statut, avant et après ; puis les autres effets (spec admin-corrections,
 * exigences 4.1, 4.2 et 4.4).
 *
 * <p>Un seul composant pour toutes les corrections : chacune présente son effet de la même façon,
 * l'administratrice n'apprend pas une lecture par écran. Purement présentationnel — il n'appelle
 * rien et ne décide rien.</p>
 *
 * <p>Un montant qui change est marqué par une flèche et par sa nouvelle valeur en gras, pas par la
 * seule couleur : le passage « à jour → en retard » est écrit en toutes lettres.</p>
 */
@Component({
  selector: 'app-correction-preview',
  standalone: true,
  imports: [CommonModule, MatIconModule, TranslateModule],
  templateUrl: './correction-preview.component.html',
  styleUrls: ['./correction-preview.component.scss']
})
export class CorrectionPreviewComponent {
  @Input({ required: true }) preview!: CorrectionPreview;

  /**
   * Gabarit rendu à côté de chaque effet, l'effet en contexte : l'hôte y place ce qu'il propose
   * (noter présent ou absent une séance désignée par l'effet). L'Aperçu n'en sait rien d'autre.
   */
  @Input() effectAction: TemplateRef<{ $implicit: CorrectionEffect }> | null = null;

  readonly currencySuffix = 'DA';

  /** Montants affichés d'une Série, dans l'ordre de l'exigence 4.1. */
  rows(change: SeriesAmountChange): AmountRow[] {
    return [
      this.row('correction.preview.cost', change, s => s.cost),
      this.row('correction.preview.dueSoFar', change, s => s.dueSoFar),
      this.row('correction.preview.paid', change, s => s.paid),
      this.row('correction.preview.remaining', change, s => s.remaining)
    ];
  }

  changed(row: AmountRow): boolean {
    return row.before !== row.after;
  }

  statusKey(late: boolean): string {
    return late ? 'correction.preview.late' : 'correction.preview.upToDate';
  }

  /** Titre d'une Série : « Janvier (Math 1ère A) — Amine Belkacem ». */
  title(change: SeriesAmountChange): string {
    const series = change.groupName ? `${change.seriesName} (${change.groupName})` : change.seriesName;
    return `${series} — ${change.studentName}`;
  }

  trackBySeries(_index: number, change: SeriesAmountChange): string {
    return `${change.studentId}-${change.seriesId}`;
  }

  private row(labelKey: string, change: SeriesAmountChange, pick: (s: AmountSnapshot) => number): AmountRow {
    return { labelKey, before: pick(change.before), after: pick(change.after) };
  }
}
