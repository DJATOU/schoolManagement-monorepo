import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators, FormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TutorService } from '../../../services/tutor.service';
import { Tutor } from '../../../models/tutor/tutor';
import { AdminOnlyDirective } from '../../../shared/admin-only.directive';

/**
 * Dialogue de rattachement d'un tuteur à un étudiant.
 *
 * <p>Deux chemins possibles :</p>
 * <ul>
 *   <li><strong>Tuteur existant</strong> : sélection dans la liste des tuteurs déjà
 *       enregistrés. Indispensable pour les frères et sœurs, qui partagent le même parent :
 *       sans cela, on créerait un doublon par enfant.</li>
 *   <li><strong>Nouveau tuteur</strong> : création puis rattachement immédiat.</li>
 * </ul>
 *
 * <p>Ferme en renvoyant le {@link Tutor} à rattacher (avec son identifiant), ou
 * {@code null} si l'utilisateur annule. Le rattachement effectif à l'étudiant est réalisé
 * par l'appelant (mise à jour de l'étudiant avec {@code tutorId}).</p>
 */
@Component({
  selector: 'app-link-tutor-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatTabsModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatSnackBarModule,
    TranslateModule,
    AdminOnlyDirective
  ],
  templateUrl: './link-tutor-dialog.component.html',
  styleUrls: ['./link-tutor-dialog.component.scss']
})
export class LinkTutorDialogComponent implements OnInit {

  /** Tuteurs déjà enregistrés (onglet « Tuteur existant »). */
  tutors: Tutor[] = [];
  filteredTutors: Tutor[] = [];
  searchTerm = '';
  loadingTutors = true;
  selectedTutor: Tutor | null = null;

  /** Formulaire de création (onglet « Nouveau tuteur »). */
  tutorForm: FormGroup;
  saving = false;

  constructor(
    private fb: FormBuilder,
    private tutorService: TutorService,
    private snackBar: MatSnackBar,
    private translate: TranslateService,
    public dialogRef: MatDialogRef<LinkTutorDialogComponent, Tutor | null>
  ) {
    this.tutorForm = this.fb.group({
      firstName: ['', Validators.required],
      lastName: ['', Validators.required],
      phoneNumber: ['', Validators.required],
      email: ['', [Validators.email]],
      relationship: ['']
    });
  }

  ngOnInit(): void {
    this.loadTutors();
  }

  private loadTutors(): void {
    this.tutorService.getTutors().subscribe({
      next: (tutors) => {
        // Tri alphabétique : la liste des tuteurs grandit avec les inscriptions.
        this.tutors = [...tutors].sort((a, b) => {
          const byLast = (a.lastName || '').localeCompare(b.lastName || '', 'fr', { sensitivity: 'base' });
          return byLast !== 0
            ? byLast
            : (a.firstName || '').localeCompare(b.firstName || '', 'fr', { sensitivity: 'base' });
        });
        this.applySearch();
        this.loadingTutors = false;
      },
      error: (err) => {
        console.error('Error loading tutors:', err);
        this.loadingTutors = false;
      }
    });
  }

  applySearch(): void {
    const term = this.searchTerm.trim().toLowerCase();
    this.filteredTutors = !term
      ? [...this.tutors]
      : this.tutors.filter(t =>
          `${t.firstName ?? ''} ${t.lastName ?? ''}`.toLowerCase().includes(term) ||
          `${t.lastName ?? ''} ${t.firstName ?? ''}`.toLowerCase().includes(term) ||
          (t.phoneNumber || '').toLowerCase().includes(term) ||
          (t.email || '').toLowerCase().includes(term));
  }

  clearSearch(): void {
    this.searchTerm = '';
    this.applySearch();
  }

  select(tutor: Tutor): void {
    this.selectedTutor = this.selectedTutor?.id === tutor.id ? null : tutor;
  }

  isSelected(tutor: Tutor): boolean {
    return this.selectedTutor?.id === tutor.id;
  }

  initials(tutor: Tutor): string {
    return ((tutor.firstName?.[0] || '') + (tutor.lastName?.[0] || '')).toUpperCase() || '?';
  }

  /** Rattache le tuteur existant sélectionné. */
  attachExisting(): void {
    if (this.selectedTutor) {
      this.dialogRef.close(this.selectedTutor);
    }
  }

  /** Crée le nouveau tuteur puis le renvoie à l'appelant pour rattachement. */
  createAndAttach(): void {
    if (this.tutorForm.invalid || this.saving) {
      this.tutorForm.markAllAsTouched();
      return;
    }
    this.saving = true;
    const tutor: Tutor = this.tutorForm.value;

    this.tutorService.createTutor(tutor).subscribe({
      next: (saved) => {
        this.saving = false;
        this.dialogRef.close(saved);
      },
      error: (err) => {
        this.saving = false;
        console.error('Error creating tutor:', err);
        this.snackBar.open(
          this.translate.instant('tutorForm.messages.error'),
          this.translate.instant('common.ok'),
          { duration: 3000, panelClass: ['snack-bar-error'] }
        );
      }
    });
  }

  onCancel(): void {
    this.dialogRef.close(null);
  }
}
