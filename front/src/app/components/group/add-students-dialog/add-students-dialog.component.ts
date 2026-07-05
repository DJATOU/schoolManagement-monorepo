import { Component, Inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { StudentService } from '../../student/services/student.service';
import { Student } from '../../student/domain/student';

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
    MatProgressSpinnerModule
  ],
  templateUrl: './add-students-dialog.component.html',
  styleUrls: ['./add-students-dialog.component.scss']
})
export class AddStudentsDialogComponent implements OnInit {
  students: Student[] = [];
  filtered: Student[] = [];
  selectedIds = new Set<number>();
  searchTerm = '';
  loading = true;

  constructor(
    public dialogRef: MatDialogRef<AddStudentsDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { levelId: number; existingStudentIds: number[] },
    private studentService: StudentService
  ) {}

  ngOnInit(): void {
    this.loadStudents();
  }

  private loadStudents(): void {
    this.studentService.getStudentsByLevel(this.data.levelId).subscribe({
      next: (students) => {
        const existing = this.data.existingStudentIds || [];
        this.students = students.filter(s => s.id != null && !existing.includes(s.id));
        this.filtered = [...this.students];
        this.loading = false;
      },
      error: (err) => {
        console.error('Failed to load students:', err);
        this.loading = false;
      }
    });
  }

  applySearch(): void {
    const term = this.searchTerm.trim().toLowerCase();
    if (!term) {
      this.filtered = [...this.students];
      return;
    }
    this.filtered = this.students.filter(s =>
      `${s.firstName} ${s.lastName}`.toLowerCase().includes(term) ||
      (s.email || '').toLowerCase().includes(term)
    );
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
