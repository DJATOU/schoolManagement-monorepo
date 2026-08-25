import { Component, Inject, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { Teacher } from '../../../models/teacher/teacher';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatTabsModule } from '@angular/material/tabs';
import { TeacherService } from '../../../services/teacher.service';
import { COMMUNICATION_OPTIONS, DEFAULT_NATIONALITY, NATIONALITIES } from '../../../utils/form-options';
import { TranslateModule } from '@ngx-translate/core';
import { SubjectService } from '../../../services/subject.service';
import { Subject } from '../../../models/subject/subject';
import { AdminOnlyDirective } from '../../../shared/admin-only.directive';
import { SecureImageDirective } from '../../../shared/secure-image.directive';

@Component({
  selector: 'app-edit-teacher-dialog',
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
  templateUrl: './edit-teacher-dialog.component.html',
  styleUrls: ['./edit-teacher-dialog.component.scss']
})
export class EditTeacherDialogComponent implements OnInit {
  editTeacherForm!: FormGroup;
  selectedFile: File | null = null;
  photoPreview: string | null = null;
  shouldClearPhoto: boolean = false;

  /** Vrai si la photo référencée est introuvable : on affiche alors un visuel de repli. */
  photoError = false;

  readonly communicationOptions = COMMUNICATION_OPTIONS;
  readonly nationalities = NATIONALITIES;
  subjects: Subject[] = [];

  constructor(
    public dialogRef: MatDialogRef<EditTeacherDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { teacher: Teacher },
    private fb: FormBuilder,
    private teacherService: TeacherService,
    private subjectService: SubjectService
  ) { }

  ngOnInit(): void {
    this.subjectService.getSubjects().subscribe({
      next: (data) => (this.subjects = data),
      error: (error) => console.error('Error loading subjects:', error)
    });

    // Convertir dateOfBirth en objet Date si c'est une chaîne
    let dateOfBirth: Date | null = null;
    if (this.data.teacher.dateOfBirth) {
      dateOfBirth = new Date(this.data.teacher.dateOfBirth);
    }

    this.editTeacherForm = this.fb.group({
      firstName: [this.data.teacher.firstName, Validators.required],
      lastName: [this.data.teacher.lastName, Validators.required],
      email: [this.data.teacher.email, [Validators.email]],
      phoneNumber: [this.data.teacher.phoneNumber, Validators.required],
      dateOfBirth: [dateOfBirth],
      placeOfBirth: [this.data.teacher.placeOfBirth],
      gender: [this.data.teacher.gender],
      specialization: [this.data.teacher.specialization],
      yearsOfExperience: [this.data.teacher.yearsOfExperience, [Validators.min(0)]],
      nationality: [this.data.teacher.nationality || DEFAULT_NATIONALITY],
      communicationPreference: [this.data.teacher.communicationPreference],
      // Adresse et ville : portées par le backend et saisies à l'inscription, elles
      // étaient absentes de ce dialogue et donc impossibles à corriger.
      address: [this.data.teacher.address],
      city: [this.data.teacher.city]
    });

    // Afficher photo actuelle si elle existe
    if (this.data.teacher.photo) {
      this.photoPreview = this.teacherService.getTeacherPhotoUrl(this.data.teacher.id!);
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
      this.photoError = false;
      this.shouldClearPhoto = false;
    }
  }

  clearPhoto(): void {
    this.selectedFile = null;
    this.photoPreview = null;
    this.photoError = false;
    this.shouldClearPhoto = true;
  }

  /**
   * La photo référencée est introuvable (fichier absent côté serveur) : on bascule sur un
   * visuel de repli plutôt que d'afficher une image cassée.
   */
  onPhotoError(): void {
    this.photoError = true;
  }

  onCancel(): void {
    this.dialogRef.close();
  }

  onSave(): void {
    if (this.editTeacherForm.invalid) {
      this.editTeacherForm.markAllAsTouched();
      return;
    }

    const formValues = this.editTeacherForm.value;

    // Convertir dateOfBirth en format ISO string si c'est un objet Date
    if (formValues.dateOfBirth instanceof Date) {
      formValues.dateOfBirth = formValues.dateOfBirth.toISOString();
    }

    const updatedTeacher: Teacher = {
      ...this.data.teacher,
      ...formValues,
      groups: [] // Ne pas envoyer les groups au backend lors de la mise à jour
    };

    console.log('Updated teacher:', updatedTeacher);
    this.dialogRef.close({ teacher: updatedTeacher, file: this.selectedFile, clearPhoto: this.shouldClearPhoto });
  }
}
