import { AfterViewInit, Component, ElementRef, Inject, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatChipsModule } from '@angular/material/chips';
import { TranslateModule } from '@ngx-translate/core';
import { StudentService } from '../../student/services/student.service';
import { Student } from '../../student/domain/student';
import { AdminOnlyDirective } from '../../../shared/admin-only.directive';

/** Un groupe de la liste : une lettre initiale et les étudiants correspondants. */
interface LetterGroup {
  letter: string;
  students: Student[];
}

/**
 * Dialogue d'ajout d'étudiants à un groupe.
 *
 * <p>La liste peut compter plus d'une centaine d'étudiants : elle est donc <strong>triée
 * alphabétiquement</strong> (nom puis prénom) et découpée en sections par lettre initiale,
 * avec un index latéral cliquable pour sauter directement à une lettre. Le défilement
 * devient exploitable, et la recherche reste le chemin le plus rapide (champ auto-focalisé).</p>
 */
@Component({
  selector: 'app-add-students-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatCheckboxModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatChipsModule,
    TranslateModule,
    AdminOnlyDirective
  ],
  templateUrl: './add-students-dialog.component.html',
  styleUrls: ['./add-students-dialog.component.scss']
})
export class AddStudentsDialogComponent implements OnInit, AfterViewInit {
  students: Student[] = [];
  filtered: Student[] = [];
  groups: LetterGroup[] = [];
  selectedIds = new Set<number>();
  searchTerm = '';
  loading = true;

  /** Conteneur défilant de la liste (cible des sauts par lettre). */
  @ViewChild('listContainer') listContainer?: ElementRef<HTMLElement>;
  @ViewChild('searchInput') searchInput?: ElementRef<HTMLInputElement>;

  constructor(
    public dialogRef: MatDialogRef<AddStudentsDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { levelId: number; existingStudentIds: number[] },
    private studentService: StudentService
  ) {}

  ngOnInit(): void {
    this.loadStudents();
  }

  ngAfterViewInit(): void {
    // Le champ de recherche est le chemin le plus court : on y place le curseur d'emblée.
    setTimeout(() => this.searchInput?.nativeElement.focus());
  }

  private loadStudents(): void {
    this.studentService.getStudentsByLevel(this.data.levelId).subscribe({
      next: (students) => {
        const existing = this.data.existingStudentIds || [];
        // Tri alphabétique (nom, puis prénom) : sans lui, l'ordre de l'API est arbitraire
        // et parcourir la liste à la molette n'a aucun sens.
        this.students = students
          .filter(s => s.id != null && !existing.includes(s.id))
          .sort((a, b) => this.compareStudents(a, b));
        this.applySearch();
        this.loading = false;
      },
      error: (err) => {
        console.error('Failed to load students:', err);
        this.loading = false;
      }
    });
  }

  /** Comparateur alphabétique tolérant aux accents et à la casse. */
  private compareStudents(a: Student, b: Student): number {
    const byLast = (a.lastName || '').localeCompare(b.lastName || '', 'fr', { sensitivity: 'base' });
    if (byLast !== 0) {
      return byLast;
    }
    return (a.firstName || '').localeCompare(b.firstName || '', 'fr', { sensitivity: 'base' });
  }

  applySearch(): void {
    const term = this.searchTerm.trim().toLowerCase();
    this.filtered = !term
      ? [...this.students]
      : this.students.filter(s =>
          `${s.firstName} ${s.lastName}`.toLowerCase().includes(term) ||
          `${s.lastName} ${s.firstName}`.toLowerCase().includes(term) ||
          (s.email || '').toLowerCase().includes(term));
    this.groups = this.buildGroups(this.filtered);
  }

  clearSearch(): void {
    this.searchTerm = '';
    this.applySearch();
    this.searchInput?.nativeElement.focus();
  }

  /** Découpe la liste filtrée en sections par lettre initiale du nom. */
  private buildGroups(students: Student[]): LetterGroup[] {
    const groups: LetterGroup[] = [];
    students.forEach(student => {
      const letter = this.letterOf(student);
      const last = groups[groups.length - 1];
      if (last && last.letter === letter) {
        last.students.push(student);
      } else {
        groups.push({ letter, students: [student] });
      }
    });
    return groups;
  }

  /** Lettre initiale du nom, sans accent ni casse ('#' si indéterminable). */
  private letterOf(student: Student): string {
    const source = (student.lastName || student.firstName || '').trim();
    if (!source) {
      return '#';
    }
    const normalized = source.normalize('NFD').replace(/[\u0300-\u036f]/g, '');
    const first = normalized.charAt(0).toUpperCase();
    return /[A-Z]/.test(first) ? first : '#';
  }

  /** Lettres présentes dans la liste filtrée (index latéral). */
  get availableLetters(): string[] {
    return this.groups.map(g => g.letter);
  }

  /** Fait défiler la liste jusqu'à la section de la lettre demandée. */
  jumpTo(letter: string): void {
    const container = this.listContainer?.nativeElement;
    const target = container?.querySelector<HTMLElement>(`[data-letter="${letter}"]`);
    if (container && target) {
      container.scrollTo({ top: target.offsetTop - container.offsetTop, behavior: 'smooth' });
    }
  }

  toggle(id?: number): void {
    if (id == null) return;
    if (this.selectedIds.has(id)) {
      this.selectedIds.delete(id);
    } else {
      this.selectedIds.add(id);
    }
  }

  isSelected(id?: number): boolean {
    return id != null && this.selectedIds.has(id);
  }

  get allFilteredSelected(): boolean {
    return this.filtered.length > 0 && this.filtered.every(s => s.id != null && this.selectedIds.has(s.id));
  }

  toggleSelectAll(): void {
    if (this.allFilteredSelected) {
      this.filtered.forEach(s => s.id != null && this.selectedIds.delete(s.id));
    } else {
      this.filtered.forEach(s => s.id != null && this.selectedIds.add(s.id));
    }
  }

  /**
   * Étudiants sélectionnés, y compris ceux masqués par la recherche courante : la sélection
   * reste ainsi visible et révocable sans avoir à retrouver la ligne dans la liste.
   */
  get selectedStudents(): Student[] {
    return this.students.filter(s => s.id != null && this.selectedIds.has(s.id));
  }

  clearSelection(): void {
    this.selectedIds.clear();
  }

  fullName(student: Student): string {
    return `${student.firstName ?? ''} ${student.lastName ?? ''}`.trim();
  }

  getInitials(s: Student): string {
    return ((s.firstName?.[0] || '') + (s.lastName?.[0] || '')).toUpperCase() || '?';
  }

  onCancel(): void {
    this.dialogRef.close(null);
  }

  onConfirm(): void {
    if (this.selectedIds.size === 0) return;
    this.dialogRef.close(Array.from(this.selectedIds));
  }
}
