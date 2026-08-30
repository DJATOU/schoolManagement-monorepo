import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, Router } from '@angular/router';
import { TeacherService } from '../../../services/teacher.service';
import { SummaryDialogComponent } from '../../summary-dialog/summary-dialog.component';
import { MatTabsModule } from '@angular/material/tabs';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatIconModule } from '@angular/material/icon';
import { MatOptionModule } from '@angular/material/core';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { ReactiveFormsModule } from '@angular/forms';
import { CommonModule, DatePipe } from '@angular/common';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatCardModule } from '@angular/material/card';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { COMMUNICATION_OPTIONS, DEFAULT_NATIONALITY, GENDER_LABEL_KEYS, MARITAL_STATUS_LABEL_KEYS, NATIONALITIES } from '../../../utils/form-options';
import { SubjectService } from '../../../services/subject.service';
import { Subject } from '../../../models/subject/subject';
import { AdminOnlyDirective } from '../../../shared/admin-only.directive';

@Component({
  selector: 'app-teacher-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatIconModule,
    MatOptionModule,
    MatSelectModule,
    MatButtonModule,
    MatTabsModule,
    MatSnackBarModule,
    CommonModule,
    MatCardModule,
    TranslateModule,
    AdminOnlyDirective
  ],
  templateUrl: './teacher-form.component.html',
  styleUrls: ['./teacher-form.component.scss'],
  providers: [TeacherService, DatePipe]
})
export class TeacherFormComponent implements OnInit {
  selectedFile: File | null = null;
  teacherForm: FormGroup;
  teacherId: number | null = null;
  isEditMode = false;

  readonly communicationOptions = COMMUNICATION_OPTIONS;
  readonly nationalities = NATIONALITIES;
  subjects: Subject[] = [];

  constructor(
    private fb: FormBuilder,
    private teacherService: TeacherService,
    private subjectService: SubjectService,
    public dialog: MatDialog,
    private snackBar: MatSnackBar,
    private route: ActivatedRoute,
    private router: Router,
    private translate: TranslateService,
    private datePipe: DatePipe
  ) {
    this.teacherForm = this.fb.group({
      basicInformation: this.fb.group({
        firstName: ['', Validators.required],
        lastName: ['', Validators.required],
        gender: ['', Validators.required],
        photo: ['']
      }),
      contactInformation: this.fb.group({
        email: ['', [Validators.required, Validators.email]],
        phoneNumber: ['', Validators.required],
        nationality: [DEFAULT_NATIONALITY],
        communicationPreference: [''],
        dateOfBirth: ['', Validators.required],
        placeOfBirth: [''],
        address: [''],
        city: ['']
      }),
      professionalDetails: this.fb.group({
        specialization: [''],
        yearsOfExperience: ['', [Validators.required, Validators.pattern("^[0-9]*$")]],
        maritalStatus: ['']
      })
    });
  }

  ngOnInit(): void {
    this.loadSubjects();

    // Vérifier si c'est une édition
    this.route.params.subscribe(params => {
      const id = params['id'];
      if (id) {
        this.teacherId = +id;
        this.isEditMode = true;
        this.loadTeacher(this.teacherId);
      }
    });
  }

  private loadSubjects(): void {
    this.subjectService.getSubjects().subscribe({
      next: (data) => (this.subjects = data),
      error: (error) => console.error('Error loading subjects:', error)
    });
  }

  loadTeacher(id: number): void {
    this.teacherService.getTeacher(id).subscribe({
      next: (teacher) => {
        this.teacherForm.patchValue({
          basicInformation: {
            firstName: teacher.firstName,
            lastName: teacher.lastName,
            gender: teacher.gender,
            photo: teacher.photo
          },
          contactInformation: {
            email: teacher.email,
            phoneNumber: teacher.phoneNumber,
            nationality: teacher.nationality || DEFAULT_NATIONALITY,
            communicationPreference: teacher.communicationPreference,
            dateOfBirth: teacher.dateOfBirth,
            placeOfBirth: teacher.placeOfBirth,
            address: teacher.address,
            city: '' // Ajouter si disponible dans le model
          },
          professionalDetails: {
            specialization: teacher.specialization,
            yearsOfExperience: teacher.yearsOfExperience,
            maritalStatus: '' // Ajouter si disponible
          }
        });
      },
      error: (error) => {
        console.error('Error loading teacher:', error);
        this.showErrorMessage('TEACHER_FORM.MESSAGES.LOAD_ERROR');
      }
    });
  }

  onFileSelected(event: Event): void {
    const target = event.target as HTMLInputElement;
    if (target && target.files && target.files.length > 0) {
      this.selectedFile = target.files[0];
    }
  }


  /**
   * Construit le récapitulatif attendu par {@link SummaryDialogComponent} : une liste
   * d'entrées « Section - champ », avec les codes remplacés par leurs libellés traduits
   * (sexe, moyen de communication, statut matrimonial) et la date de naissance formatée.
   */
  private buildSummary(): { label: string; value: any }[] {
    const value = (path: string) => this.teacherForm.get(path)?.value;

    return [
      { label: 'basicInformation - firstName', value: value('basicInformation.firstName') },
      { label: 'basicInformation - lastName', value: value('basicInformation.lastName') },
      { label: 'basicInformation - gender', value: this.optionLabel(value('basicInformation.gender'), GENDER_LABEL_KEYS) },
      { label: 'basicInformation - photo', value: this.selectedFile?.name },
      { label: 'contactInformation - email', value: value('contactInformation.email') },
      { label: 'contactInformation - phoneNumber', value: value('contactInformation.phoneNumber') },
      { label: 'contactInformation - nationality', value: value('contactInformation.nationality') },
      {
        label: 'contactInformation - communicationPreference',
        value: this.communicationLabel(value('contactInformation.communicationPreference'))
      },
      {
        label: 'contactInformation - dateOfBirth',
        value: this.datePipe.transform(value('contactInformation.dateOfBirth'), 'd MMMM y') ?? ''
      },
      { label: 'contactInformation - placeOfBirth', value: value('contactInformation.placeOfBirth') },
      { label: 'contactInformation - address', value: value('contactInformation.address') },
      { label: 'contactInformation - city', value: value('contactInformation.city') },
      { label: 'professionalDetails - specialization', value: value('professionalDetails.specialization') },
      { label: 'professionalDetails - yearsOfExperience', value: value('professionalDetails.yearsOfExperience') },
      {
        label: 'professionalDetails - maritalStatus',
        value: this.optionLabel(value('professionalDetails.maritalStatus'), MARITAL_STATUS_LABEL_KEYS)
      }
    ];
  }

  /** Libellé traduit d'un code de liste déroulante (vide si le code est absent). */
  private optionLabel(code: string, labelKeys: Record<string, string>): string {
    return code ? this.translate.instant(labelKeys[code] ?? code) : '';
  }

  /** Libellé traduit du moyen de communication (la valeur stockée est un code). */
  private communicationLabel(code: string): string {
    const option = this.communicationOptions.find(o => o.value === code);
    return option ? this.translate.instant(option.labelKey) : '';
  }

  onSubmit(): void {
    if (this.isEditMode) {
      // MODE ÉDITION
      if (this.teacherForm.valid) {
        // Le formulaire ne comporte que ces trois sections ; l'ancien code étalait aussi
        // un groupe « otherInformation » inexistant, ce qui ne produisait rien.
        const teacherData = {
          id: this.teacherId,
          ...this.teacherForm.get('basicInformation')?.value,
          ...this.teacherForm.get('contactInformation')?.value,
          ...this.teacherForm.get('professionalDetails')?.value
        };

        // Une modification écrase des données existantes : elle mérite la même
        // relecture avant écriture que la création.
        const dialogRef = this.dialog.open(SummaryDialogComponent, {
          width: '520px',
          maxWidth: '95vw',
          data: this.buildSummary()
        });

        dialogRef.afterClosed().subscribe(confirmed => {
          if (!confirmed) {
            return;
          }
          this.teacherService.updateTeacher(this.teacherId!, teacherData).subscribe({
            next: () => {
              // Upload photo si sélectionnée
              if (this.selectedFile) {
                this.teacherService.uploadTeacherPhoto(this.teacherId!, this.selectedFile).subscribe({
                  next: () => {
                    this.showSuccessMessage('TEACHER_FORM.MESSAGES.UPDATED_WITH_PHOTO');
                    this.router.navigate(['/teacher', this.teacherId]);
                  },
                  error: (error) => {
                    console.error('Error uploading photo:', error);
                    this.showErrorMessage('TEACHER_FORM.MESSAGES.PHOTO_UPLOAD_FAILED');
                    this.router.navigate(['/teacher', this.teacherId]);
                  }
                });
              } else {
                this.showSuccessMessage('TEACHER_FORM.MESSAGES.UPDATED');
                this.router.navigate(['/teacher', this.teacherId]);
              }
            },
            error: (error) => {
              console.error('Error updating teacher:', error);
              this.showErrorMessage('TEACHER_FORM.MESSAGES.UPDATE_ERROR');
            }
          });
        });
      } else {
        this.teacherForm.markAllAsTouched();
        this.showErrorMessage('TEACHER_FORM.MESSAGES.INVALID');
      }
    } else {
      // MODE CRÉATION
      if (this.teacherForm.valid) {
        const dialogRef = this.dialog.open(SummaryDialogComponent, {
          width: '520px',
          maxWidth: '95vw',
          data: this.buildSummary()
        });

        dialogRef.afterClosed().subscribe(result => {
          if (result) {
            const formDataToSubmit = new FormData();
            if (this.selectedFile) {
              formDataToSubmit.append('file', this.selectedFile, this.selectedFile.name);
            }
            Object.keys(this.teacherForm.value).forEach(groupKey => {
              const group = this.teacherForm.get(groupKey) as FormGroup;
              Object.keys(group.controls).forEach(key => {
                const value = group.get(key)?.value;
                formDataToSubmit.append(key, value);
              });
            });

            this.teacherService.createTeacher(formDataToSubmit).subscribe({
              next: () => {
                this.onClearForm();
                this.showSuccessMessage('TEACHER_FORM.MESSAGES.CREATED');
              },
              error: (error) => {
                console.error('Error creating teacher:', error);
                this.showErrorMessage('TEACHER_FORM.MESSAGES.CREATE_ERROR');
              }
            });
          }
        });
      } else {
        // Sans ce marquage, aucun champ ne signalait ce qui manquait : le bouton
        // « Soumettre » paraissait mort et la popup ne s'ouvrait jamais.
        this.teacherForm.markAllAsTouched();
        this.showErrorMessage('TEACHER_FORM.MESSAGES.INVALID');
      }
    }
  }


  onClearForm(): void {
    this.teacherForm.reset();
    this.selectedFile = null;
  }

  showSuccessMessage(messageKey: string): void {
    this.snackBar.open(this.translate.instant(messageKey), this.translate.instant('common.ok'), {
      duration: 3000,
      panelClass: ['snack-bar-success']
    });
  }

  showErrorMessage(messageKey: string): void {
    this.snackBar.open(this.translate.instant(messageKey), this.translate.instant('common.ok'), {
      duration: 3000,
      panelClass: ['snack-bar-error']
    });
  }
}
