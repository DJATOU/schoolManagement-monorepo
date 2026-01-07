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
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatStepperModule } from '@angular/material/stepper';
import { MatTabsModule } from '@angular/material/tabs';
import { RouterModule } from '@angular/router';
import { Level } from '../../../models/level/level';
import { LevelService } from '../../../services/level.service';
import { StudentService } from '../services/student.service';
import { SummaryDialogComponent } from '../../summary-dialog/summary-dialog.component';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

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
    CommonModule,
    MatDialogModule,
    MatCard,
    MatCardContent,
    MatCardHeader,
    MatCardTitle,
    MatSnackBarModule,
    TranslateModule
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
        dateOfBirth: ['', Validators.required],
        placeOfBirth: ['']
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
      error: () => this.showErrorMessage('messages.levelsError')
    });
  }

  onFileSelected(event: Event): void {
    const target = event.target as HTMLInputElement;
    if (target?.files?.length) {
      this.selectedFile = target.files[0];
    }
  }

  onSubmit(): void {
    if (this.studentForm.invalid) {
      this.showErrorMessage('messages.invalidForm');
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
      const value = data[key];
      if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
        result = result.concat(this.flattenFormData(value, newKey));
      } else {
        result.push({ label: newKey, value: value });
      }
    });
    return result;
  }

  private submitForm(formData: FormData): void {
    this.studentService.createStudent(formData).subscribe({
      next: (response) => {
        console.log('Student created:', response);
        this.onClearForm();
        this.showSuccessMessage('messages.studentCreated');
      },
      error: (error) => {
        console.error('Error creating student:', error);
        this.showErrorMessage('messages.studentCreateError');
      }
    });
  }

  onClearForm(): void {
    this.studentForm.reset();
    this.selectedFile = null;
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
