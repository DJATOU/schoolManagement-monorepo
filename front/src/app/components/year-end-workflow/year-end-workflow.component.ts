import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatOptionModule } from '@angular/material/core';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';

import { YearEndService } from '../../services/year-end.service';
import { LevelService } from '../../services/level.service';
import { GroupService } from '../../services/group.service';
import { Level } from '../../models/level/level';
import { Group } from '../../models/group/group';
import {
  PromotionDecision,
  StudentDecisionPreview,
  YearEndPreview,
  YearEndRequest,
  YearEndResult
} from '../../models/yearEnd/year-end';

/**
 * Assistant de fin d'année (Year_End_Workflow)
 *
 * Affiche un aperçu (GET /api/year-end/preview) : libellé proposé pour l'année
 * suivante et une ligne par étudiant actif avec la décision par défaut PROMOTION
 * (Requirement 5.7). Les étudiants au niveau le plus élevé sont signalés pour revue
 * (Requirements 8.1, 8.2).
 *
 * L'administrateur peut modifier la décision de chaque étudiant (Promotion /
 * Redoublement / Départ, Requirement 5.3) et éventuellement le libellé/les dates de
 * la nouvelle année (Requirement 5.1). L'action « exécuter » appelle
 * POST /api/year-end/run puis affiche le résultat (nouvelle année, liste de revue,
 * nombre d'étudiants traités).
 *
 * Composant autonome (standalone), textes via ngx-translate (clés uniquement).
 *
 * @author Frontend Team
 */
@Component({
  selector: 'app-year-end-workflow',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatOptionModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    TranslateModule
  ],
  templateUrl: './year-end-workflow.component.html',
  styleUrls: ['./year-end-workflow.component.scss']
})
export class YearEndWorkflowComponent implements OnInit {

  /** Expose l'énumération au template. */
  readonly PromotionDecision = PromotionDecision;

  /** Options de décision proposées à l'administrateur. */
  readonly decisionOptions: PromotionDecision[] = [
    PromotionDecision.PROMOTION,
    PromotionDecision.REDOUBLEMENT,
    PromotionDecision.DEPARTURE
  ];

  /** Colonnes affichées dans le tableau des décisions. */
  readonly displayedColumns: string[] = ['student', 'review', 'decision'];

  /** Aperçu chargé depuis le backend. */
  preview: YearEndPreview | null = null;

  /** Lignes de décision éditables (copie complète de l'aperçu — utilisée pour l'exécution). */
  rows: StudentDecisionPreview[] = [];

  /** Options de filtre (niveaux et groupes de l'année courante). */
  levels: Level[] = [];
  groups: Group[] = [];

  /** État des filtres d'affichage (n'affectent pas les décisions envoyées). */
  filterLevelId: number | null = null;
  filterGroupId: number | null = null;
  filterStudentTerm = '';

  /** Panneau de filtres ouvert/fermé (barre repliable, style calendrier). */
  filtersOpen = false;

  /** Nombre de filtres actifs (pour le badge du bouton « Filtres »). */
  get activeFiltersCount(): number {
    let n = 0;
    if (this.filterLevelId != null) n++;
    if (this.filterGroupId != null) n++;
    if (this.filterStudentTerm.trim()) n++;
    return n;
  }

  toggleFilters(): void {
    this.filtersOpen = !this.filtersOpen;
  }

  /** Libellé de la nouvelle année (optionnel : dérivé côté backend si vide). */
  newLabel = '';

  /** Date de début de la nouvelle année (optionnelle, format yyyy-MM-dd). */
  startDate = '';

  /** Date de fin de la nouvelle année (optionnelle, format yyyy-MM-dd). */
  endDate = '';

  /** Indicateurs d'état. */
  loadingPreview = false;
  running = false;

  /** Résultat de l'exécution du workflow. */
  result: YearEndResult | null = null;

  /** Message d'erreur éventuel (clé ou message brut). */
  errorMessage: string | null = null;

  constructor(
    private yearEndService: YearEndService,
    private levelService: LevelService,
    private groupService: GroupService
  ) {}

  ngOnInit(): void {
    this.loadPreview();
    this.loadFilterOptions();
  }

  /** Charge les niveaux et groupes (année courante) pour alimenter les filtres. */
  private loadFilterOptions(): void {
    this.levelService.getLevels().subscribe({
      next: (levels) => (this.levels = levels || []),
      error: () => (this.levels = [])
    });
    this.groupService.getGroups().subscribe({
      next: (groups) => (this.groups = groups || []),
      error: () => (this.groups = [])
    });
  }

  /**
   * Lignes filtrées pour l'AFFICHAGE uniquement (niveau, groupe, nom d'étudiant).
   * Les décisions envoyées à l'exécution portent sur toutes les lignes (`rows`).
   */
  get filteredRows(): StudentDecisionPreview[] {
    return this.rows.filter((row) => {
      const s = row.student as { levelId?: number; groupIds?: number[] };

      if (this.filterLevelId != null && s.levelId !== this.filterLevelId) {
        return false;
      }
      if (this.filterGroupId != null) {
        const groupIds = Array.isArray(s.groupIds) ? s.groupIds : [];
        if (!groupIds.includes(this.filterGroupId)) {
          return false;
        }
      }
      if (this.filterStudentTerm.trim()) {
        const term = this.filterStudentTerm.trim().toLowerCase();
        if (!this.studentName(row).toLowerCase().includes(term)) {
          return false;
        }
      }
      return true;
    });
  }

  /** Réinitialise les filtres d'affichage. */
  clearFilters(): void {
    this.filterLevelId = null;
    this.filterGroupId = null;
    this.filterStudentTerm = '';
  }

  /**
   * Charge l'aperçu du workflow (Requirement 5.7, 8.1, 8.2) et pré-remplit le
   * libellé proposé pour l'année suivante.
   */
  loadPreview(): void {
    this.loadingPreview = true;
    this.errorMessage = null;
    this.result = null;

    this.yearEndService.preview().subscribe({
      next: (preview) => {
        this.preview = preview;
        this.rows = (preview?.decisions ?? []).map((d) => ({ ...d }));
        this.newLabel = preview?.proposedNextLabel ?? '';
        this.loadingPreview = false;
      },
      error: (err: Error) => {
        this.errorMessage = err.message;
        this.loadingPreview = false;
      }
    });
  }

  /**
   * Construit la requête et exécute le workflow de fin d'année.
   * Toutes les décisions (par défaut PROMOTION, Requirement 5.7) sont incluses.
   */
  run(): void {
    this.running = true;
    this.errorMessage = null;

    const request: YearEndRequest = {
      newLabel: this.newLabel?.trim() || undefined,
      startDate: this.startDate || undefined,
      endDate: this.endDate || undefined,
      decisions: this.rows
        .filter((row) => row.student?.id != null)
        .map((row) => ({
          studentId: row.student.id as number,
          decision: row.decision
        }))
    };

    this.yearEndService.run(request).subscribe({
      next: (result) => {
        this.result = result;
        this.running = false;
      },
      error: (err: Error) => {
        this.errorMessage = err.message;
        this.running = false;
      }
    });
  }

  /**
   * Nom affichable d'un étudiant de l'aperçu.
   */
  studentName(row: StudentDecisionPreview): string {
    const first = row.student?.firstName ?? '';
    const last = row.student?.lastName ?? '';
    return `${first} ${last}`.trim();
  }
}
