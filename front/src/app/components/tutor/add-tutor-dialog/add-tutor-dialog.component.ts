import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TutorService } from '../../../services/tutor.service';
import { Tutor } from '../../../models/tutor/tutor';

@Component({
  selector: 'app-add-tutor-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatSnackBarModule,
    TranslateModule
  ],
  templateUrl: './add-tutor-dialog.component.html',
  styleUrls: ['./add-tutor-dialog.component.scss']
})
export class AddTutorDialogComponent {
  tutorForm: FormGroup;
  saving = false;

  constructor(
    private fb: FormBuilder,
    private tutorService: TutorService,
    private snackBar: MatSnackBar,
    private translate: TranslateService,
    public dialogRef: MatDialogRef<AddTutorDialogComponent>
  ) {
    this.tutorForm = this.fb.group({
      firstName: ['', Validators.required],
      lastName: ['', Validators.required],
      phoneNumber: ['', Validators.required],
      email: ['', [Validators.email]],
      relationship: ['']
    });
  }

  onCancel(): void {
    this.dialogRef.close(null);
  }

  onSave(): void {
    if (this.tutorForm.invalid) {
      this.tutorForm.markAllAsTouched();
      return;
    }

    this.saving = true;
    const tutor: Tutor = this.tutorForm.value;

    // Crée le tuteur en base ; le tuteur sauvegardé (avec id) est renvoyé
    // au formulaire étudiant qui l'attachera lors de la création de l'étudiant.
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
}
