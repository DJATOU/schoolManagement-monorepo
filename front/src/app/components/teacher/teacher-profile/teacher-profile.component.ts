import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TeacherService } from '../../../services/teacher.service';
import { Teacher } from '../../../models/teacher/teacher';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ConfirmationDialogComponent } from '../../shared/confirmation-dialog/confirmation-dialog.component';
import { EditTeacherDialogComponent } from '../edit-teacher-dialog/edit-teacher-dialog.component';
import { MatSnackBar } from '@angular/material/snack-bar';
import { environment } from '../../../../environments/environment';
import { GroupService } from '../../../services/group.service';
import { Group } from '../../../models/group/group';
import { GroupCardComponent } from '../../group/group-card/group-card.component';
import { MatExpansionModule } from '@angular/material/expansion';
import { ProfilePdfService } from '../../../services/profile-pdf.service';
@Component({
  selector: 'app-teacher-profile',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatDialogModule,
    MatProgressSpinnerModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatSelectModule,
    MatTooltipModule,
    MatExpansionModule,
    GroupCardComponent
  ],
  templateUrl: './teacher-profile.component.html',
  styleUrls: ['./teacher-profile.component.scss']
})
export class TeacherProfileComponent implements OnInit {
  teacher: Teacher | undefined;
  loading = true;
  groupForm: FormGroup;
  teacherPhotoUrl: string = '';
  hasImageError: boolean = false;
  avatarColor: string = '#6366f1';
  teacherGroups: Group[] = [];
  levels: any[] = [];
  groupTypes: any[] = [];

  // Colors for avatar backgrounds
  private readonly avatarColors = [
    '#6366f1', '#8b5cf6', '#ec4899', '#ef4444', '#f97316',
    '#eab308', '#22c55e', '#14b8a6', '#06b6d4', '#3b82f6'
  ];

  constructor(
    private route: ActivatedRoute,
    private teacherService: TeacherService,
    private fb: FormBuilder,
    private router: Router,
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
    private groupService: GroupService,
    private profilePdfService: ProfilePdfService
  ) {
    this.groupForm = this.fb.group({
      groupIds: [[]]
    });
  }

  ngOnInit(): void {
    const teacherId = this.route.snapshot.paramMap.get('id');
    if (teacherId) {
      this.getTeacherDetails(+teacherId);
      this.loadTeacherGroups(+teacherId);
    }
  }

  private loadTeacherGroups(teacherId: number): void {
    this.groupService.getGroupsByTeacherId(teacherId).subscribe({
      next: (groups) => this.teacherGroups = groups || [],
      error: (err) => console.error('Error loading teacher groups:', err)
    });
  }

  /** Génère la fiche PDF de l'enseignant (infos + groupes). */
  printProfilePdf(): void {
    if (!this.teacher) return;
    const t = this.teacher;
    this.profilePdfService.generateProfilePdf({
      title: `${t.firstName} ${t.lastName}`,
      subtitle: t.specialization ? `Enseignant · ${t.specialization}` : 'Enseignant',
      sections: [
        {
          heading: 'Informations personnelles',
          rows: [
            { label: 'Sexe', value: t.gender },
            { label: 'Email', value: t.email },
            { label: 'Téléphone', value: t.phoneNumber },
            { label: 'Date de naissance', value: t.dateOfBirth ? new Date(t.dateOfBirth).toLocaleDateString('fr-FR') : null },
            { label: 'Lieu de naissance', value: t.placeOfBirth },
            { label: 'Adresse', value: t.address }
          ]
        },
        {
          heading: 'Informations professionnelles',
          rows: [
            { label: 'Spécialisation', value: t.specialization },
            { label: "Années d'expérience", value: t.yearsOfExperience }
          ]
        }
      ],
      listTitle: `Groupes enseignés (${this.teacherGroups.length})`,
      listItems: this.teacherGroups.map(g =>
        `${g.name}${g.levelName ? ' — ' + g.levelName : ''}${g.subjectName ? ' (' + g.subjectName + ')' : ''}`
      )
    });
  }

  getTeacherDetails(id: number): void {
    this.teacherService.getTeacher(id).subscribe((teacher) => {
      this.teacher = teacher;
      this.loading = false;
      // Générer l'URL complète de la photo en utilisant les variables d'environnement
      if (this.teacher?.photo) {
        this.teacherPhotoUrl = `${environment.apiUrl}${environment.imagesPath}${this.teacher.photo}`;
      }
      this.setAvatarColor();
    });
  }

  /**
   * Get initials from first and last name (max 2 characters)
   */
  getInitials(): string {
    const firstName = this.teacher?.firstName || '';
    const lastName = this.teacher?.lastName || '';
    const firstInitial = firstName.charAt(0).toUpperCase();
    const lastInitial = lastName.charAt(0).toUpperCase();
    return firstInitial + lastInitial || 'XX';
  }

  /**
   * Set avatar color based on teacher name
   */
  private setAvatarColor(): void {
    const name = `${this.teacher?.firstName || ''}${this.teacher?.lastName || ''}`;
    const hash = name.split('').reduce((acc, char) => acc + char.charCodeAt(0), 0);
    this.avatarColor = this.avatarColors[hash % this.avatarColors.length];
  }

  /**
   * Handle image load error - fallback to initials
   */
  onImageError(): void {
    this.hasImageError = true;
  }

  onEdit(): void {
    const dialogRef = this.dialog.open(EditTeacherDialogComponent, {
      width: '600px',
      data: { teacher: this.teacher }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        // result contains { teacher: Teacher, file: File | null }
        this.teacherService.updateTeacher(result.teacher.id!, result.teacher).subscribe({
          next: (updatedTeacher) => {
            // If a new photo was selected, upload it
            if (result.file) {
              this.teacherService.uploadTeacherPhoto(updatedTeacher.id!, result.file).subscribe({
                next: () => {
                  this.getTeacherDetails(updatedTeacher.id!);
                  this.showSuccessMessage('Enseignant modifié avec succès.');
                },
                error: (error) => {
                  console.error('Error uploading photo:', error);
                  this.showErrorMessage('Erreur lors du téléchargement de la photo.');
                }
              });
            } else {
              this.teacher = updatedTeacher;
              this.showSuccessMessage('Enseignant modifié avec succès.');
            }
          },
          error: (error) => {
            console.error('Error updating teacher:', error);
            this.showErrorMessage('Erreur lors de la modification de l\'enseignant.');
          }
        });
      }
    });
  }

  onDisable(): void {
    // Confirmation dialog to disable student
    this.dialog.open(ConfirmationDialogComponent, {
      data: {
        title: "Suppression d'un enseignant",
        message: 'Voulez-vous vraiment supprimer cet enseignant?',
        confirmText: 'Yes, delete',
        cancelText: 'No, cancel',
        confirmColor: 'warn'
      }
    }).afterClosed().subscribe((result: boolean) => {
      if (result) {
        this.teacherService.disableTeacher(this.teacher!.id || -1).subscribe({
          next: (response) => {
            console.log('Teacher disabled successfully:', response);
            this.showSuccessMessage('Teacher disabled successfully.'); // Affichez le message de succès
          },
          error: (error) => {
            console.error('Error disabling teacher:', error);
            this.showErrorMessage('Error disabling teacher.'); // Affichez le message d'erreur
          }
        });
      }
      else {
        console.log('Operation canceled.');
      }
    });
  }

  showSuccessMessage(message: string): void {
    this.snackBar.open(message, 'OK', {
      duration: 3000,
      panelClass: ['snack-bar-success']
    });
  }

  showErrorMessage(message: string): void {
    this.snackBar.open(message, 'OK', {
      duration: 3000,
      panelClass: ['snack-bar-error']
    });
  }

  onSubmitGroups(): void {
    // Handle group form submission
  }
}
