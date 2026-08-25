import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { finalize } from 'rxjs';
import { RevenueGroupBy, RevenueReport, RevenueRow } from '../../../models/revenue/revenue-report';
import { GroupService } from '../../../services/group.service';
import { LevelService } from '../../../services/level.service';
import { RevenueService } from '../../../services/revenue.service';
import { SeriesService } from '../../../services/series.service';
import { resolveLocale } from '../../../shared/locale';

/**
 * Page « Recettes » : combien l'école a encaissé, ventilé sur l'axe choisi.
 *
 * <p>Complète le panneau de la fiche groupe, qui répond à « ce groupe est-il à jour »
 * (encaissé face à l'attendu, calcul lourd par étudiant). Ici on répond à « quel groupe
 * rapporte le plus », « combien est entré en septembre » : agrégation transversale
 * entièrement faite par la base, donc tenable quel que soit le nombre de séries.</p>
 *
 * <p>Réservé à ADMIN : la route est gardée et les endpoints exigent le rôle côté serveur.</p>
 */
@Component({
  selector: 'app-revenue-report',
  standalone: true,
  templateUrl: './revenue-report.component.html',
  styleUrls: ['./revenue-report.component.scss'],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatTableModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    TranslateModule
  ]
})
export class RevenueReportComponent implements OnInit {

  /** Axes proposés, du plus synthétique au plus détaillé. */
  readonly axes: RevenueGroupBy[] = ['GROUP', 'SERIES', 'MONTH', 'SESSION'];

  readonly displayedColumns = ['label', 'share', 'collected'];
  readonly currencySuffix = 'DA';

  filterForm: FormGroup;
  report: RevenueReport | null = null;
  isLoading = false;

  levels: any[] = [];
  groups: any[] = [];
  series: any[] = [];
  private allSeries: any[] = [];

  private autoSearchTimer: ReturnType<typeof setTimeout> | undefined;

  constructor(
    private fb: FormBuilder,
    private revenueService: RevenueService,
    private groupService: GroupService,
    private levelService: LevelService,
    private seriesService: SeriesService,
    private snackBar: MatSnackBar,
    private translate: TranslateService
  ) {
    this.filterForm = this.fb.group({
      groupBy: ['GROUP'],
      levelId: [null],
      groupId: [null],
      seriesId: [null],
      dateFrom: [null],
      dateTo: [null]
    });
  }

  ngOnInit(): void {
    this.loadFilterOptions();
    this.wireFilters();
    this.load();
  }

  // =========================================================================
  // Filtres
  // =========================================================================

  private loadFilterOptions(): void {
    this.levelService.getLevels().subscribe({
      next: levels => this.levels = levels || [],
      error: () => this.notifyError('filterOptionsError')
    });
    this.groupService.getGroups().subscribe({
      next: groups => this.groups = groups || [],
      error: () => this.notifyError('filterOptionsError')
    });
    this.seriesService.getAllSessionSeries().subscribe({
      next: series => {
        this.allSeries = series || [];
        if (!this.filterForm.value.groupId) {
          this.series = this.allSeries;
        }
      },
      error: () => this.notifyError('filterOptionsError')
    });
  }

  /**
   * Même cascade que la gestion des paiements : niveau → groupe → série. Les listes
   * restreintes sont chargées côté serveur, l'entité série brute n'exposant pas de
   * {@code groupId} exploitable.
   */
  private wireFilters(): void {
    this.filterForm.get('levelId')!.valueChanges.subscribe(() => {
      const groupId = this.filterForm.value.groupId;
      if (groupId && !this.filteredGroups.some(group => group.id === groupId)) {
        this.filterForm.get('groupId')!.setValue(null, { emitEvent: false });
        this.series = this.allSeries;
        this.filterForm.get('seriesId')!.setValue(null, { emitEvent: false });
      }
      this.autoLoad();
    });

    this.filterForm.get('groupId')!.valueChanges.subscribe(groupId => {
      this.loadGroupSeries(groupId);
      this.autoLoad();
    });

    ['groupBy', 'seriesId', 'dateFrom', 'dateTo'].forEach(control => {
      this.filterForm.get(control)!.valueChanges.subscribe(() => this.autoLoad());
    });
  }

  get filteredGroups(): any[] {
    const levelId = this.filterForm.value.levelId;
    return levelId ? this.groups.filter(group => group.levelId === levelId) : this.groups;
  }

  private loadGroupSeries(groupId: number | null): void {
    this.filterForm.get('seriesId')!.setValue(null, { emitEvent: false });

    if (!groupId) {
      this.series = this.allSeries;
      return;
    }

    this.seriesService.getSessionSeriesByGroupId(groupId).subscribe({
      next: series => this.series = series || [],
      error: () => {
        this.series = [];
        this.notifyError('filterOptionsError');
      }
    });
  }

  private autoLoad(): void {
    clearTimeout(this.autoSearchTimer);
    this.autoSearchTimer = setTimeout(() => this.load(), 350);
  }

  resetFilters(): void {
    this.filterForm.reset({ groupBy: 'GROUP' }, { emitEvent: false });
    this.series = this.allSeries;
    this.load();
  }

  // =========================================================================
  // Chargement
  // =========================================================================

  load(): void {
    this.isLoading = true;
    const lang = this.translate.currentLang || 'fr';

    this.revenueService.getReport(this.filterForm.value, lang)
      .pipe(finalize(() => this.isLoading = false))
      .subscribe({
        next: report => this.report = report,
        error: () => {
          this.report = null;
          this.notifyError('loadError');
        }
      });
  }

  // =========================================================================
  // Affichage
  // =========================================================================

  axisLabel(axis: RevenueGroupBy): string {
    return this.translate.instant(`revenue.report.axes.${axis}`);
  }

  /** Le montant remboursé n'est déduit qu'au total : on le dit plutôt que de le cacher. */
  get hasRefunds(): boolean {
    return !!this.report && this.report.totalRefunded > 0;
  }

  /** Lien vers la fiche du groupe, seul endroit où l'attendu est calculé. */
  groupLink(row: RevenueRow): string | null {
    return this.report?.groupBy === 'GROUP' && row.key ? `/group/${row.key}` : null;
  }

  exportToCSV(): void {
    if (!this.report || this.report.rows.length === 0) {
      return;
    }

    const headers = [
      this.axisLabel(this.report.groupBy),
      this.translate.instant('revenue.report.table.share'),
      this.translate.instant('revenue.report.table.collected')
    ];

    const lines = this.report.rows.map(row => [
      row.subLabel ? `${row.label} (${row.subLabel})` : row.label,
      `${row.share} %`,
      row.collected
    ].map(value => this.csvCell(value)).join(';'));

    const totals = [
      this.translate.instant('revenue.report.table.total'),
      '',
      this.report.totalNet
    ].map(value => this.csvCell(value)).join(';');

    // BOM UTF-8 : sans lui Excel casse les accents.
    const csv = '\uFEFF' + [headers.map(h => this.csvCell(h)).join(';'), ...lines, totals].join('\r\n');
    this.download(csv);
  }

  /** Échappement CSV, avec neutralisation de l'injection de formule. */
  private csvCell(value: unknown): string {
    if (value === null || value === undefined) {
      return '';
    }
    let text = String(value);
    if (/^[=+\-@]/.test(text)) {
      text = `'${text}`;
    }
    return `"${text.replace(/"/g, '""')}"`;
  }

  private download(csv: string): void {
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `recettes-${new Date().toISOString().slice(0, 10)}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  }

  /** Formate un montant selon la langue courante. */
  formatAmount(amount: number): string {
    return new Intl.NumberFormat(resolveLocale(this.translate.currentLang), {
      minimumFractionDigits: 0,
      maximumFractionDigits: 2
    }).format(amount ?? 0);
  }

  private notifyError(key: string): void {
    this.snackBar.open(
      this.translate.instant(`revenue.report.messages.${key}`),
      this.translate.instant('common.close'),
      { duration: 5000, panelClass: ['error-snackbar'] });
  }
}
