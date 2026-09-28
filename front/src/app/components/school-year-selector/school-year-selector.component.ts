import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatOptionModule } from '@angular/material/core';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { Subject, takeUntil } from 'rxjs';
import { SchoolYear } from '../../models/schoolYear/school-year';
import { SchoolYearService } from '../../services/school-year.service';
import { SchoolYearContextService } from '../../services/school-year-context.service';

/**
 * Sélecteur global d'année scolaire (School_Year_Selector)
 *
 * Contrôle global affiché en haut de l'application (barre de navigation) qui
 * liste les années scolaires et permet de basculer l'année sélectionnée via le
 * service de contexte (Requirement 10.1, 10.3).
 *
 * Au chargement, charge toutes les années scolaires (SchoolYearService.getAll)
 * et s'abonne à SchoolYearContextService.selectedSchoolYear$ pour refléter la
 * sélection courante. Le changement de valeur appelle
 * SchoolYearContextService.setSelectedSchoolYear (Requirement 10.3).
 *
 * @author Frontend Team
 */
@Component({
  selector: 'app-school-year-selector',
  standalone: true,
  imports: [
    CommonModule,
    MatFormFieldModule,
    MatSelectModule,
    MatOptionModule,
    MatIconModule,
    MatTooltipModule,
    TranslateModule
  ],
  templateUrl: './school-year-selector.component.html',
  styleUrls: ['./school-year-selector.component.scss']
})
export class SchoolYearSelectorComponent implements OnInit, OnDestroy {

  /** Liste des années scolaires disponibles (triées par date de début décroissante). */
  schoolYears: SchoolYear[] = [];

  /** Identifiant de l'année scolaire actuellement sélectionnée (liaison au mat-select). */
  selectedSchoolYearId: number | null = null;

  /** Libellé de l'année scolaire sélectionnée, affiché dans la zone de déclenchement. */
  selectedSchoolYearLabel = '';

  /** Sujet de désabonnement pour éviter les fuites mémoire. */
  private readonly destroy$ = new Subject<void>();

  constructor(
    private schoolYearService: SchoolYearService,
    private contextService: SchoolYearContextService
  ) {}

  ngOnInit(): void {
    // Charge toutes les années scolaires pour alimenter la liste déroulante.
    this.schoolYearService.getAll()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (years) => {
          this.schoolYears = years ?? [];
        },
        error: () => {
          this.schoolYears = [];
        }
      });

    // Reflète la sélection courante issue du service de contexte.
    this.contextService.selectedSchoolYear$
      .pipe(takeUntil(this.destroy$))
      .subscribe((selected) => {
        this.selectedSchoolYearId = selected?.id ?? null;
        this.selectedSchoolYearLabel = selected?.label ?? '';
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /**
   * Gère le changement de sélection dans le mat-select : met à jour l'année
   * scolaire sélectionnée via le service de contexte (Requirement 10.3).
   *
   * @param schoolYearId Identifiant de l'année scolaire choisie.
   */
  onSchoolYearChange(schoolYearId: number): void {
    const chosen = this.schoolYears.find((year) => year.id === schoolYearId);
    if (chosen) {
      this.contextService.setSelectedSchoolYear(chosen);
    }
  }
}
