import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatCard, MatCardContent, MatCardHeader, MatCardTitle } from '@angular/material/card';
import { DateAdapter, MAT_DATE_FORMATS, MAT_DATE_LOCALE, MatNativeDateModule, MatOption, NativeDateAdapter } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatStepperModule } from '@angular/material/stepper';
import { MatTabsModule } from '@angular/material/tabs';
import { RouterModule } from '@angular/router';
import { Level } from '../../../models/level/level';
import { LevelService } from '../../../services/level.service';
import { StudentService } from '../services/student.service';
import { SummaryDialogComponent } from '../../summary-dialog/summary-dialog.component';
import { AddTutorDialogComponent } from '../../tutor/add-tutor-dialog/add-tutor-dialog.component';
import { Tutor } from '../../../models/tutor/tutor';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { COMMUNICATION_OPTIONS, DEFAULT_NATIONALITY, NATIONALITIES } from '../../../utils/form-options';
import { AdminOnlyDirective } from '../../../shared/admin-only.directive';

@Component({
  selector: 'app-student',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,

    MatNativeDateModule,
    RouterModule,
    MatStepperModule,
    MatIconModule,
    MatTabsModule,
    MatOption,
    MatSelectModule,
    MatButtonModule,
    MatTooltipModule,
    CommonModule,
    MatDialogModule,
    MatCard,
    MatCardContent,
    MatCardHeader,
    MatCardTitle,
    MatSnackBarModule,
    TranslateModule,
    AdminOnlyDirective
  ],
  templateUrl: './student-form.component.html',
  styleUrls: ['./student-form.component.scss'],
  providers: [
    StudentService,
    { provide: DateAdapter, useClass: NativeDateAdapter },
    { provide: MAT_DATE_LOCALE, useValue: 'us-US' },
    {
      provide: MAT_DATE_FORMATS,
      useValue: {
        parse: {
          dateInput: 'LL'
        },
        display: {
          dateInput: 'LL',
          monthYearLabel: 'MMM YYYY',
          dateA11yLabel: 'LL',
          monthYearA11yLabel: 'MMMM YYYY'
        }
      }
    }
  ]
})
export class StudentFormComponent implements OnInit {
  selectedFile: File | null = null;
  levels: Level[] = [];
  studentForm!: FormGroup;
  selectedTutor: Tutor | null = null;

  readonly communicationOptions = COMMUNICATION_OPTIONS;
  readonly nationalities = NATIONALITIES;

  constructor(
    private fb: FormBuilder,
    private studentService: StudentService,
    private levelService: LevelService,
    public dialog: MatDialog,
    private snackBar: MatSnackBar,
    private translate: TranslateService
  ) { }

  ngOnInit(): void {
    this.initializeForm();
    this.loadLevels();
  }

  private initializeForm(): void {
    this.studentForm = this.fb.group({
      personalInformation: this.fb.group({
        firstName: ['', Validators.required],
        lastName: ['', Validators.required],
        gender: ['', Validators.required],
        photo: ['']
      }),
      contactInformation: this.fb.group({
        email: ['', [Validators.required, Validators.email]],
        phoneNumber: [''],
        nationality: [DEFAULT_NATIONALITY],
        communicationPreference: [''],
        dateOfBirth: ['', Validators.required],
        placeOfBirth: [''],
        address: [''],
        city: ['']
      }),
      academicInformation: this.fb.group({
        level: ['', Validators.required],
        establishment: [''],
        averageScore: ['', Validators.pattern('^[0-9]*$')],
        description: ['']
      })
    });
  }

  private loadLevels(): void {
    this.levelService.getLevels().subscribe({
      next: (data) => (this.levels = data),
      error: () => this.showErrorMessage('STUDENT_FORM.MESSAGES.LEVELS_ERROR')
    });
  }

  onFileSelected(event: Event): void {
    const target = event.target as HTMLInputElement;
    if (target?.files?.length) {
      this.selectedFile = target.files[0];
    }
  }

  /**
   * Ouvre la popup d'ajout de tuteur. Le tuteur est créé en base par la popup
   * et renvoyé ici ; on le mémorise pour l'attacher à l'étudiant à la soumission.
   */
  openTutorDialog(): void {
    const dialogRef = this.dialog.open(AddTutorDialogComponent, {
      width: '520px',
      maxWidth: '95vw'
    });

    dialogRef.afterClosed().subscribe((tutor: Tutor | null) => {
      if (tutor) {
        this.selectedTutor = tutor;
        this.showSuccessMessage('tutorForm.messages.attached');
      }
    });
  }

  /** Retire le tuteur sélectionné du formulaire (sans le supprimer en base). */
  clearTutor(): void {
    this.selectedTutor = null;
  }

  get tutorFullName(): string {
    if (!this.selectedTutor) return '';
    return `${this.selectedTutor.firstName} ${this.selectedTutor.lastName}`;
  }

  onSubmit(): void {
    if (this.studentForm.invalid) {
      this.showErrorMessage('STUDENT_FORM.MESSAGES.INVALID_FORM');
      return;
    }

    const formData = this.prepareFormData();

    const flattenedData = this.flattenFormData(this.studentForm.value);
    const dialogRef = this.dialog.open(SummaryDialogComponent, {
      data: flattenedData
    });

    dialogRef.afterClosed().subscribe((result) => {
      if (result) {
        this.submitForm(formData);
      } else {
        console.warn('Form submission was cancelled.');
      }
    });
  }

  private prepareFormData(): FormData {
    const formDataToSubmit = new FormData();

    if (this.selectedFile) {
      formDataToSubmit.append('file', this.selectedFile, this.selectedFile.name);
    }

    Object.keys(this.studentForm.value).forEach((groupKey) => {
      const group = this.studentForm.get(groupKey) as FormGroup;
      Object.keys(group.controls).forEach((key) => {
        const value = group.get(key)?.value;
        if (key === 'level') {
          formDataToSubmit.append('levelId', value);
        } else if (key === 'dateOfBirth' && value instanceof Date) {
          // Format date as yyyy-MM-dd for backend
          const formattedDate = this.formatDateForBackend(value);
          formDataToSubmit.append(key, formattedDate);
        } else {
          formDataToSubmit.append(key, value);
        }
      });
    });

    // Attacher le tuteur sélectionné (déjà créé en base via la popup)
    if (this.selectedTutor?.id != null) {
      formDataToSubmit.append('tutorId', String(this.selectedTutor.id));
    }

    return formDataToSubmit;
  }

  private formatDateForBackend(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  private flattenFormData(data: any, parentKey: string = ''): { label: string; value: any }[] {
    let result: { label: string; value: any }[] = [];
    Object.keys(data).forEach(key => {
      const newKey = parentKey ? `${parentKey} - ${key}` : key;
      let value = data[key];

      if (value instanceof Date) {
        // Afficher la date au format lisible plutôt que l'objet Date brut
        value = this.formatDateForBackend(value);
        result.push({ label: newKey, value });
      } else if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
        result = result.concat(this.flattenFormData(value, newKey));
      } else {
        // Résoudre le nom du niveau à partir de son ID pour l'affichage
        if (key === 'level' && value != null) {
          const level = this.levels.find(l => l.id === value);
          value = level ? level.name : value;
        }
        result.push({ label: newKey, value });
      }
    });
    return result;
  }

  private submitForm(formData: FormData): void {
    this.studentService.createStudent(formData).subscribe({
      next: (response) => {
        console.log('Student created:', response);
        this.onClearForm();
        this.showSuccessMessage('STUDENT_FORM.MESSAGES.CREATED');
      },
      error: (error) => {
        console.error('Error creating student:', error);
        this.showErrorMessage('STUDENT_FORM.MESSAGES.CREATE_ERROR');
      }
    });
  }

  onClearForm(): void {
    this.studentForm.reset();
    this.selectedFile = null;
    this.selectedTutor = null;
  }

  private showSuccessMessage(messageKey: string): void {
    this.snackBar.open(this.translate.instant(messageKey), this.translate.instant('common.ok'), {
      duration: 3000,
      panelClass: ['snack-bar-success']
    });
  }

  private showErrorMessage(messageKey: string): void {
    this.snackBar.open(this.translate.instant(messageKey), this.translate.instant('common.ok'), {
      duration: 3000,
      panelClass: ['snack-bar-error']
    });
  }
}
