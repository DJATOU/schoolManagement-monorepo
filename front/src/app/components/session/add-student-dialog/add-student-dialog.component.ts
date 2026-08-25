import { AfterViewInit, Component, ElementRef, Inject, OnInit, ViewChild } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { StudentService } from '../../student/services/student.service';
import { Student } from '../../student/domain/student';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { AdminOnlyDirective } from '../../../shared/admin-only.directive';

/** Une section de la liste : une lettre initiale et les étudiants correspondants. */
interface LetterGroup {
  letter: string;
  students: Student[];
}

/**
 * Dialogue d'ajout d'un étudiant à une feuille de présence.
 *
 * <p>Les candidats sont tous les étudiants du niveau du groupe, ce qui peut représenter
 * une centaine de lignes : la recherche (champ auto-focalisé) est le chemin principal, la
 * liste étant par ailleurs triée alphabétiquement et découpée par lettre initiale.</p>
 *
 * <p>Chaque ligne indique si l'étudiant est membre du groupe de la séance. La distinction
 * n'est pas cosmétique : ajouter un membre du groupe est une présence ordinaire, alors
 * qu'ajouter un étudiant extérieur est un rattrapage, ce qui change le calcul de paiement
 * de la série.</p>
 */
@Component({
  selector: 'app-add-student-dialog',
  templateUrl: './add-student-dialog.component.html',
  styleUrls: ['./add-student-dialog.component.scss'],
  standalone: true,
  imports: [
    CommonModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    FormsModule,
    AdminOnlyDirective,
    TranslateModule
  ]
})
export class AddStudentDialogComponent implements OnInit, AfterViewInit {
  students: Student[] = [];
  filtered: Student[] = [];
  groups: LetterGroup[] = [];
  selectedStudent: Student | null = null;
  searchTerm = '';
  loading = true;

  @ViewChild('searchInput') searchInput?: ElementRef<HTMLInputElement>;

  constructor(
    public dialogRef: MatDialogRef<AddStudentDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: {
      levelId: number,
      existingStudentIds: number[],
      /** Identifiants des étudiants inscrits au groupe de la séance (affectation active). */
      groupMemberIds?: number[]
    },
    private studentService: StudentService
  ) {}

  ngOnInit(): void {
    this.loadStudents();
  }

  ngAfterViewInit(): void {
    // La recherche est le chemin le plus court : on y place le curseur d'emblée.
    setTimeout(() => this.searchInput?.nativeElement.focus());
  }

  private loadStudents(): void {
    this.studentService.getStudentsByLevel(this.data.levelId).subscribe({
      next: (students) => {
        const existing = this.data.existingStudentIds || [];
        // Tri alphabétique (nom puis prénom) : l'ordre renvoyé par l'API est arbitraire.
        this.students = (students || [])
          .filter(student => student.id != null && !existing.includes(student.id))
          .sort((a, b) => this.compareStudents(a, b));
        this.applySearch();
        this.loading = false;
      },
      error: (error) => {
        console.error('Failed to load students:', error);
        this.loading = false;
      }
    });
  }

  /** Comparateur alphabétique tolérant aux accents et à la casse. */
  private compareStudents(a: Student, b: Student): number {
    const byLast = (a.lastName || '').localeCompare(b.lastName || '', 'fr', { sensitivity: 'base' });
    return byLast !== 0
      ? byLast
      : (a.firstName || '').localeCompare(b.firstName || '', 'fr', { sensitivity: 'base' });
  }

  applySearch(): void {
    const term = this.searchTerm.trim().toLowerCase();
    this.filtered = !term
      ? [...this.students]
      : this.students.filter(student =>
          `${student.firstName} ${student.lastName}`.toLowerCase().includes(term) ||
          `${student.lastName} ${student.firstName}`.toLowerCase().includes(term));
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

  /**
   * Indique si l'étudiant est inscrit au groupe de la séance.
   *
   * <p>Un membre du groupe absent de la feuille (inscription postérieure au début de la
   * séance) est une présence ordinaire ; les autres sont des rattrapages.</p>
   */
  isGroupMember(student: Student): boolean {
    return (this.data.groupMemberIds ?? []).includes(student.id as number);
  }

  select(student: Student): void {
    // Un second clic sur la même ligne annule la sélection.
    this.selectedStudent = this.isSelected(student) ? null : student;
  }

  isSelected(student: Student): boolean {
    return this.selectedStudent?.id === student.id;
  }

  clearSelection(): void {
    this.selectedStudent = null;
  }

  fullName(student: Student): string {
    return `${student.lastName ?? ''} ${student.firstName ?? ''}`.trim();
  }

  getInitials(student: Student): string {
    return ((student.firstName?.[0] || '') + (student.lastName?.[0] || '')).toUpperCase() || '?';
  }

  onConfirm(): void {
    if (this.selectedStudent) {
      this.dialogRef.close(this.selectedStudent);
    }
  }

  onCancel(): void {
    this.dialogRef.close(null);
  }
}
