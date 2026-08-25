import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { ImportKind, ImportResult, ImportService } from '../../services/import.service';
import { AdminOnlyDirective } from '../../shared/admin-only.directive';

/** Définition d'une carte d'import (métadonnées d'affichage). */
interface ImportCard {
  kind: ImportKind;
  icon: string;
  titleKey: string;
  columnsKey: string;
  noteKey?: string;
}

/**
 * Page d'import CSV (ressources académiques + élèves, enseignants, groupes).
 *
 * Chaque ressource a une carte : sélection d'un fichier CSV puis envoi au
 * backend, avec affichage du résumé (importés + erreurs par ligne). Les cartes
 * sont ordonnées selon les dépendances (référentiels d'abord, groupes en
 * dernier). Textes via ngx-translate.
 */
@Component({
  selector: 'app-import',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    TranslateModule,
    AdminOnlyDirective
  ],
  templateUrl: './import.component.html',
  styleUrls: ['./import.component.scss']
})
export class ImportComponent {

  /** Cartes d'import, ordonnées par dépendance (référentiels → profs → élèves → groupes). */
  readonly cards: ImportCard[] = [
    { kind: 'levels', icon: 'stairs', titleKey: 'IMPORT.LEVELS.TITLE', columnsKey: 'IMPORT.LEVELS.COLUMNS' },
    { kind: 'subjects', icon: 'menu_book', titleKey: 'IMPORT.SUBJECTS.TITLE', columnsKey: 'IMPORT.SUBJECTS.COLUMNS' },
    { kind: 'rooms', icon: 'meeting_room', titleKey: 'IMPORT.ROOMS.TITLE', columnsKey: 'IMPORT.ROOMS.COLUMNS' },
    { kind: 'group-types', icon: 'category', titleKey: 'IMPORT.GROUP_TYPES.TITLE', columnsKey: 'IMPORT.GROUP_TYPES.COLUMNS' },
    { kind: 'pricing', icon: 'price_change', titleKey: 'IMPORT.PRICING.TITLE', columnsKey: 'IMPORT.PRICING.COLUMNS' },
    { kind: 'teachers', icon: 'workspace_premium', titleKey: 'IMPORT.TEACHERS.TITLE', columnsKey: 'IMPORT.TEACHERS.COLUMNS' },
    { kind: 'students', icon: 'school', titleKey: 'IMPORT.STUDENTS.TITLE', columnsKey: 'IMPORT.STUDENTS.COLUMNS' },
    { kind: 'groups', icon: 'groups_3', titleKey: 'IMPORT.GROUPS.TITLE', columnsKey: 'IMPORT.GROUPS.COLUMNS', noteKey: 'IMPORT.GROUPS.NOTE' }
  ];

  /** État par type d'import (fichier sélectionné, chargement, résultat, erreur). */
  selectedFiles: Partial<Record<ImportKind, File | null>> = {};
  loading: Partial<Record<ImportKind, boolean>> = {};
  results: Partial<Record<ImportKind, ImportResult | null>> = {};
  errorMessages: Partial<Record<ImportKind, string | null>> = {};

  constructor(private importService: ImportService) {}

  onFileSelected(kind: ImportKind, event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFiles[kind] = input.files && input.files.length > 0 ? input.files[0] : null;
    this.results[kind] = null;
    this.errorMessages[kind] = null;
  }

  runImport(kind: ImportKind): void {
    const file = this.selectedFiles[kind];
    if (!file) {
      return;
    }
    this.loading[kind] = true;
    this.results[kind] = null;
    this.errorMessages[kind] = null;

    this.callFor(kind, file).subscribe({
      next: (result) => {
        this.results[kind] = result;
        this.loading[kind] = false;
      },
      error: (err: Error) => {
        this.errorMessages[kind] = err.message;
        this.loading[kind] = false;
      }
    });
  }

  private callFor(kind: ImportKind, file: File) {
    switch (kind) {
      case 'students': return this.importService.importStudents(file);
      case 'teachers': return this.importService.importTeachers(file);
      case 'groups': return this.importService.importGroups(file);
      case 'levels': return this.importService.importLevels(file);
      case 'subjects': return this.importService.importSubjects(file);
      case 'rooms': return this.importService.importRooms(file);
      case 'group-types': return this.importService.importGroupTypes(file);
      case 'pricing': return this.importService.importPricing(file);
    }
  }
}
