import { Component, Inject, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatDialog, MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { Student } from '../domain/student';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { LevelService } from '../../../services/level.service';
import { StudentService } from '../services/student.service';
import { Level } from '../../../models/level/level';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule, provideNativeDateAdapter } from '@angular/material/core';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatTabsModule } from '@angular/material/tabs';
import { TranslateModule } from '@ngx-translate/core';
import { TutorService } from '../../../services/tutor.service';
import { Tutor } from '../../../models/tutor/tutor';
import { LinkTutorDialogComponent } from '../../tutor/link-tutor-dialog/link-tutor-dialog.component';
import { COMMUNICATION_OPTIONS, NATIONALITIES } from '../../../utils/form-options';
import { AdminOnlyDirective } from '../../../shared/admin-only.directive';
import { SecureImageDirective } from '../../../shared/secure-image.directive';

/**
 * Dialogue de modification d'un étudiant.
 *
 * <p>Expose <strong>tous</strong> les champs modifiables portés par le backend
 * ({@code StudentDTO}) : identité, contact, naissance, adresse, scolarité et tuteur. Les
 * champs nationalité, préférence de communication, adresse et ville étaient saisis à
 * l'inscription mais absents de ce dialogue : une erreur de saisie y était donc définitive.</p>
 *
 * <p>La mise en page est organisée en sections sur deux colonnes : l'ancienne liste d'une
 * seule colonne obligeait à défiler pour découvrir les derniers champs (lieu de naissance,
 * moyenne), qui passaient pour absents.</p>
 */
@Component({
  selector: 'app-edit-student-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatSelectModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatIconModule,
    MatTooltipModule,
    MatTabsModule,
    TranslateModule,
    AdminOnlyDirective
  ,
    SecureImageDirective
  ],
  // Fournit le DateAdapter au niveau du composant : nécessaire pour le
  // datepicker « Date de naissance » dans ce dialog (Angular 17 standalone).
  providers: [provideNativeDateAdapter()],
  templateUrl: './edit-student-dialog.component.html',
  styleUrls: ['./edit-student-dialog.component.scss']
})
export class EditStudentDialogComponent implements OnInit {
  editStudentForm!: FormGroup;
  levels: Level[] = [];
  tutors: Tutor[] = [];
  selectedFile: File | null = null;
  photoPreview: string | null = null;
  shouldClearPhoto: boolean = false;

  /** Vrai si la photo référencée est introuvable : on affiche alors un visuel de repli. */
  photoError = false;

  readonly communicationOptions = COMMUNICATION_OPTIONS;
  readonly nationalities = NATIONALITIES;

  constructor(
    public dialogRef: MatDialogRef<EditStudentDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { student: Student },
    private fb: FormBuilder,
    private levelService: LevelService,
    private studentService: StudentService,
    private tutorService: TutorService,
    private dialog: MatDialog
  ) { }

  ngOnInit(): void {
    // Convertir dateOfBirth en objet Date si c'est une chaîne de caractères
    let dateOfBirth: Date | null = null;
    if (this.data.student.dateOfBirth) {
      dateOfBirth = new Date(this.data.student.dateOfBirth);
    }

    this.editStudentForm = this.fb.group({
      // Identité
      firstName: [this.data.student.firstName, Validators.required],
      lastName: [this.data.student.lastName, Validators.required],
      gender: [this.data.student.gender],
      nationality: [this.data.student.nationality],
      // Contact
      email: [this.data.student.email, [Validators.email]],
      phoneNumber: [this.data.student.phoneNumber],
      communicationPreference: [this.data.student.communicationPreference],
      address: [this.data.student.address],
      city: [this.data.student.city],
      // Naissance
      dateOfBirth: [dateOfBirth],
      placeOfBirth: [this.data.student.placeOfBirth],
      // Scolarité
      levelId: [this.data.student.levelId],
      establishment: [this.data.student.establishment],
      averageScore: [this.data.student.averageScore,
        [Validators.min(0), Validators.max(100)]],
      description: [this.data.student.description],
      // Tuteur
      tutorId: [this.data.student.tutorId ?? null]
    });

    this.loadLevels();
    this.loadTutors();

    // Afficher photo actuelle si elle existe
    if (this.data.student.photo) {
      this.photoPreview = this.studentService.getStudentPhotoUrl(this.data.student.id!);
    }
  }

  onFileSelected(event: Event): void {
    const target = event.target as HTMLInputElement;
    if (target?.files?.length) {
      this.selectedFile = target.files[0];

      // Preview de la nouvelle photo
      const reader = new FileReader();
      reader.onload = (e) => {
        this.photoPreview = e.target?.result as string;
      };
      reader.readAsDataURL(this.selectedFile);
      this.photoError = false; // La nouvelle image est locale : plus d'erreur de chargement
      this.shouldClearPhoto = false; // Si on selectionne une nouvelle photo, on ne supprime plus
    }
  }

  clearPhoto(): void {
    this.selectedFile = null;
    this.photoPreview = null;
    this.photoError = false;
    this.shouldClearPhoto = true; // Flag pour indiquer qu'on veut supprimer la photo
  }

  /**
   * La photo référencée par l'étudiant est introuvable (fichier supprimé côté serveur) :
   * on bascule sur un visuel de repli plutôt que d'afficher une image cassée.
   */
  onPhotoError(): void {
    this.photoError = true;
  }

  /**
   * Vrai si le niveau saisi diffère du niveau d'origine et que l'étudiant est inscrit dans
   * des groupes : ceux-ci restent rattachés à l'ancien niveau et méritent une vérification.
   */
  get levelChanged(): boolean {
    const current = this.editStudentForm?.get('levelId')?.value;
    return current != null
      && current !== this.data.student.levelId
      && (this.data.student.groupIds?.length ?? 0) > 0;
  }

  loadLevels(): void {
    this.levelService.getLevels().subscribe({
      next: (levels) => {
        this.levels = levels;
      },
      error: (error) => {
        console.error('Error loading levels:', error);
      },
    });
  }

  /** Charge les tuteurs existants pour permettre le rattachement depuis ce dialogue. */
  loadTutors(): void {
    this.tutorService.getTutors().subscribe({
      next: (tutors) => {
        this.tutors = [...tutors].sort((a, b) => {
          const byLast = (a.lastName || '').localeCompare(b.lastName || '', 'fr', { sensitivity: 'base' });
          return byLast !== 0
            ? byLast
            : (a.firstName || '').localeCompare(b.firstName || '', 'fr', { sensitivity: 'base' });
        });
      },
      error: (error) => console.error('Error loading tutors:', error)
    });
  }

  /** Libellé d'un tuteur dans la liste déroulante. */
  tutorLabel(tutor: Tutor): string {
    const name = `${tutor.lastName ?? ''} ${tutor.firstName ?? ''}`.trim();
    return tutor.relationship ? `${name} (${tutor.relationship})` : name;
  }

  /**
   * Ouvre le dialogue de création d'un tuteur puis le sélectionne dans la liste, afin de
   * ne pas avoir à quitter la modification pour créer un tuteur manquant.
   */
  createTutor(): void {
    this.dialog.open(LinkTutorDialogComponent, {
      width: '560px',
      maxWidth: '95vw',
      autoFocus: false
    }).afterClosed().subscribe((tutor: Tutor | null) => {
      if (tutor?.id) {
        this.tutors = [...this.tutors, tutor];
        this.editStudentForm.patchValue({ tutorId: tutor.id });
      }
    });
  }

  onCancel(): void {
    this.dialogRef.close();
  }

  onSave(): void {
    if (this.editStudentForm.invalid) {
      this.editStudentForm.markAllAsTouched();
      return;
    }

    const formValues: Partial<Student> = this.editStudentForm.value;

    // Convertir dateOfBirth en format ISO string si c'est un objet Date
    if (formValues.dateOfBirth instanceof Date) {
      formValues.dateOfBirth = formValues.dateOfBirth.toISOString() as any;
    }

    const updatedStudent: Student = { ...this.data.student };

    (Object.keys(formValues) as Array<keyof Student>).forEach((key) => {
      const value = formValues[key];
      // On applique aussi les valeurs vidées (null) : c'est ainsi qu'on détache un tuteur
      // ou qu'on efface un champ facultatif renseigné par erreur.
      if (value !== undefined) {
        (updatedStudent as any)[key] = value === null ? undefined : value;
      }
    });

    this.dialogRef.close({ student: updatedStudent, file: this.selectedFile, clearPhoto: this.shouldClearPhoto });
  }
}
