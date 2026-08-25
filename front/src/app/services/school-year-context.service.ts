import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, combineLatest } from 'rxjs';
import { distinctUntilChanged, map } from 'rxjs/operators';
import { SchoolYear } from '../models/schoolYear/school-year';
import { SchoolYearService } from './school-year.service';

/**
 * Service de contexte d'année scolaire (Selected_School_Year)
 *
 * Détient l'année scolaire sélectionnée dans un BehaviorSubject, l'initialise à
 * l'année courante au chargement (Requirement 10.2), la met à jour lors d'une
 * sélection (Requirement 10.3) et la préserve à travers la navigation au sein
 * d'une même session applicative (Requirement 10.6).
 *
 * `providedIn: 'root'` : singleton pour toute l'application, ce qui garantit
 * naturellement la préservation de la sélection lors de la navigation.
 *
 * @see SchoolYearService - appels HTTP vers /api/school-years
 * @author Frontend Team
 */
@Injectable({
  providedIn: 'root'
})
export class SchoolYearContextService {

  /**
   * Année scolaire sélectionnée. `null` tant qu'aucune année n'a été résolue
   * (ex: aucune année courante définie côté backend).
   */
  private readonly selectedSchoolYearSubject = new BehaviorSubject<SchoolYear | null>(null);

  /**
   * Année scolaire courante (Current_School_Year), conservée pour comparer avec
   * la sélection et déterminer l'état lecture seule (Read_Only_History).
   */
  private readonly currentSchoolYearSubject = new BehaviorSubject<SchoolYear | null>(null);

  /** Flux observable de l'année scolaire sélectionnée. */
  readonly selectedSchoolYear$: Observable<SchoolYear | null> =
    this.selectedSchoolYearSubject.asObservable();

  /**
   * Flux observable indiquant si la vue doit être en lecture seule
   * (Read_Only_History, Requirement 9.4).
   *
   * `true` lorsqu'une année est sélectionnée ET qu'elle n'est pas l'année
   * courante. `false` tant que la sélection ou l'année courante n'est pas
   * encore résolue (par prudence : on n'interdit pas l'édition par défaut).
   */
  readonly readOnly$: Observable<boolean> = combineLatest([
    this.selectedSchoolYearSubject,
    this.currentSchoolYearSubject
  ]).pipe(
    map(([selected, current]) => this.computeReadOnly(selected, current)),
    distinctUntilChanged()
  );

  constructor(private schoolYearService: SchoolYearService) {
    this.initialize();
  }

  /**
   * Détermine l'état lecture seule à partir de la sélection et de l'année
   * courante : lecture seule ssi une année est sélectionnée et diffère de
   * l'année courante.
   */
  private computeReadOnly(selected: SchoolYear | null, current: SchoolYear | null): boolean {
    if (!selected || !current) {
      return false;
    }
    if (selected.id != null && current.id != null) {
      return selected.id !== current.id;
    }
    return selected.label !== current.label;
  }

  /**
   * Initialise le contexte en récupérant l'année scolaire courante.
   *
   * Si aucune année n'est encore sélectionnée, la sélection est positionnée sur
   * l'année courante (Requirement 10.2). L'absence d'année courante (erreur
   * backend, ex: 404) est gérée silencieusement : la sélection reste `null`.
   */
  private initialize(): void {
    this.schoolYearService.getCurrent().subscribe({
      next: (current) => {
        this.currentSchoolYearSubject.next(current);
        // Ne pas écraser une sélection déjà effectuée par l'utilisateur.
        if (this.selectedSchoolYearSubject.value === null) {
          this.selectedSchoolYearSubject.next(current);
        }
      },
      error: () => {
        // Aucune année courante définie : on laisse la sélection à null.
        this.currentSchoolYearSubject.next(null);
      }
    });
  }

  /**
   * Récupère la valeur courante (snapshot) de l'année scolaire sélectionnée.
   *
   * @returns L'année scolaire sélectionnée ou `null`.
   */
  getSelectedSchoolYear(): SchoolYear | null {
    return this.selectedSchoolYearSubject.value;
  }

  /**
   * Met à jour l'année scolaire sélectionnée (Requirement 10.3).
   *
   * @param schoolYear L'année scolaire choisie.
   */
  setSelectedSchoolYear(schoolYear: SchoolYear): void {
    this.selectedSchoolYearSubject.next(schoolYear);
  }

  /**
   * Récupère l'année scolaire courante (Current_School_Year), si connue.
   *
   * @returns L'année courante ou `null`.
   */
  getCurrentSchoolYear(): SchoolYear | null {
    return this.currentSchoolYearSubject.value;
  }

  /**
   * Indique si l'année sélectionnée est l'année courante.
   *
   * Utile pour le rendu en lecture seule des années passées
   * (Read_Only_History, cf. tâche 20.5). Retourne `false` si l'année courante
   * ou la sélection n'est pas encore résolue.
   *
   * @returns `true` si la sélection correspond à l'année courante.
   */
  isCurrentYearSelected(): boolean {
    const selected = this.selectedSchoolYearSubject.value;
    const current = this.currentSchoolYearSubject.value;
    if (!selected || !current) {
      return false;
    }
    // Comparaison par id lorsque disponible, sinon par label.
    if (selected.id != null && current.id != null) {
      return selected.id === current.id;
    }
    return selected.label === current.label;
  }

  /**
   * Indique (snapshot) si la vue courante doit être en lecture seule
   * (Read_Only_History, Requirement 9.4) : une année passée est sélectionnée.
   *
   * @returns `true` si la sélection existe et n'est pas l'année courante.
   */
  isReadOnly(): boolean {
    return this.computeReadOnly(
      this.selectedSchoolYearSubject.value,
      this.currentSchoolYearSubject.value
    );
  }
}
