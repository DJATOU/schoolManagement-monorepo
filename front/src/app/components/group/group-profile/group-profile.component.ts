import { Component, OnInit } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute } from '@angular/router';
import { Group } from '../../../models/group/group';
import { Student } from '../../student/domain/student';
import { SessionSeries } from '../../../models/sessionSerie/sessionSerie';
import { GroupService } from '../../../services/group.service';
import { AddStudentsDialogComponent } from '../add-students-dialog/add-students-dialog.component';
import { MatCardModule } from '@angular/material/card';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { CommonModule } from '@angular/common';
import { MatIcon } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatList, MatListItem } from '@angular/material/list';
import { ProfileListItemComponent } from '../../shared/profile-list-item/profile-list-item.component';
import { StudentListComponent } from "../../student/student-list/student-list.component";
import { StudentService } from '../../student/services/student.service';
import { ConfirmationDialogComponent } from '../../shared/confirmation-dialog/confirmation-dialog.component';
import { MatSnackBar } from '@angular/material/snack-bar';
import { EditGroupDialogComponent } from '../edit-group-dialog/edit-group-dialog.component';
import { ProfilePdfService } from '../../../services/profile-pdf.service';

@Component({
  selector: 'app-group-profile',
  templateUrl: './group-profile.component.html',
  standalone: true,
  styleUrls: ['./group-profile.component.scss'],
  imports: [
    MatCardModule,
    MatExpansionModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatIcon,
    MatTooltipModule,
    MatList,
    MatListItem,
    CommonModule,
    ProfileListItemComponent,
    StudentListComponent
  ]
})
export class GroupProfileComponent implements OnInit {
  group: Group | null = null;
  students: Student[] = [];
  series: SessionSeries[] = [];
  loadingGroup = true;
  loadingStudents = true;
  loadingSeries = true;
  groupPhotoUrl: string = '';
  avatarColor: string = '#6366f1';

  // Colors for avatar backgrounds
  private avatarColors = [
    '#6366f1', '#8b5cf6', '#ec4899', '#ef4444', '#f97316',
    '#eab308', '#22c55e', '#14b8a6', '#06b6d4', '#3b82f6'
  ];

  constructor(
    private groupService: GroupService,
    private studentService: StudentService,
    private route: ActivatedRoute,
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
    private profilePdfService: ProfilePdfService,
  ) { }

  /** Génère la fiche PDF du groupe (infos + liste des étudiants). */
  printGroupPdf(): void {
    if (!this.group) return;
    const g = this.group;
    this.profilePdfService.generateProfilePdf({
      title: g.name,
      subtitle: g.groupTypeName ? `Groupe · ${g.groupTypeName}` : 'Groupe',
      sections: [
        {
          heading: 'Informations sur le groupe',
          rows: [
            { label: 'Type de groupe', value: g.groupTypeName || g.groupTypeId },
            { label: 'Niveau', value: g.levelName || g.levelId },
            { label: 'Matière', value: g.subjectName || g.subjectId },
            { label: 'Sessions par série', value: g.sessionNumberPerSerie },
            { label: 'Prix', value: g.priceAmount || g.pricing?.price },
            { label: 'Enseignant', value: g.teacherName }
          ]
        }
      ],
      tableTitle: `Étudiants (${this.students.length})`,
      tableColumns: ['#', 'Nom', 'Prénom', 'Remarques'],
      tableRows: this.students.map((s, i) => [
        i + 1,
        s.lastName,
        s.firstName,
        ''
      ])
    });
  }

  ngOnInit(): void {
    const groupId = this.getGroupIdFromRoute();
    if (groupId) {
      this.loadGroupData(groupId);
      this.loadStudents(groupId);
      this.loadSeries(groupId);
    }
  }

  /**
   * Get initials from group name (max 2 characters)
   */
  getInitials(): string {
    const name = this.group?.name || '';
    const words = name.trim().split(/\s+/);
    if (words.length >= 2) {
      return (words[0][0] + words[1][0]).toUpperCase();
    }
    return name.substring(0, 2).toUpperCase();
  }

  /**
   * Set avatar color based on group name
   */
  private setAvatarColor(): void {
    const name = this.group?.name || '';
    const hash = name.split('').reduce((acc, char) => acc + char.charCodeAt(0), 0);
    this.avatarColor = this.avatarColors[hash % this.avatarColors.length];
  }

  /**
   * Handle image load error - clear URL to show initials
   */
  onImageError(): void {
    this.groupPhotoUrl = '';
  }

  private getGroupIdFromRoute(): number | null {
    const id = this.route.snapshot.paramMap.get('id');
    return id ? +id : null;
  }

  private loadGroupData(groupId: number): void {
    this.groupService.getGroupDetailsById(groupId).subscribe({
      next: (group) => {
        console.log('Group details:', group);
        this.group = group;
        this.setAvatarColor();
        // Générer l'URL de la photo si elle existe
        if (this.group?.photo) {
          this.groupPhotoUrl = this.groupService.getGroupPhotoUrl(this.group.id!);
        }
        this.loadingGroup = false;
      },
      error: (error) => {
        console.error('Error loading group:', error);
        this.loadingGroup = false;
      }
    });
  }


  private loadStudents(groupId: number): void {
    this.groupService.getStudentsByGroupId(groupId).subscribe({
      next: (students: Student[]) => {
        console.log(students);  // Log the students array to check its structure
        this.students = students;
        this.loadingStudents = false;
      },
      error: (error) => {
        console.error('Error loading students:', error);
        this.loadingStudents = false;
      }
    });
  }


  private loadSeries(groupId: number): void {
    this.groupService.getSeriesByGroupId(groupId).subscribe({
      next: (series) => {
        // Convertir les dates LocalDateTime en objets Date
        this.series = series.map(serie => ({
          ...serie,
          dateCreation: this.convertLocalDateTimeToDate(serie.dateCreation)
        }));
        this.loadingSeries = false;
      },
      error: (error) => {
        console.error('Error loading series:', error);
        this.loadingSeries = false;
      }
    });
  }

  private convertLocalDateTimeToDate(dateValue: any): Date {
    if (!dateValue) return new Date();

    // Si c'est déjà une Date, la retourner
    if (dateValue instanceof Date) return dateValue;

    // Si c'est une string, la parser
    if (typeof dateValue === 'string') return new Date(dateValue);

    // Si c'est un tableau [year, month, day, hour, minute, second, nano]
    if (Array.isArray(dateValue)) {
      const [year, month, day, hour = 0, minute = 0, second = 0] = dateValue;
      return new Date(year, month - 1, day, hour, minute, second);
    }

    return new Date();
  }

  addStudentToGroup(): void {
    if (!this.group?.id) {
      return;
    }

    // Récupérer le niveau du groupe pour ne proposer que les étudiants de ce niveau
    this.groupService.getLevelIdByGroupId(this.group.id).subscribe({
      next: (levelId) => {
        if (levelId === undefined) {
          console.error('Cannot add student: group has no level');
          return;
        }

        // Exclure les étudiants déjà présents dans le groupe
        const existingStudentIds = this.students
          .map(student => student.id)
          .filter((id): id is number => id !== undefined && id !== null);

        const dialogRef = this.dialog.open(AddStudentsDialogComponent, {
          width: '500px',
          maxWidth: '95vw',
          data: { levelId, existingStudentIds }
        });

        dialogRef.afterClosed().subscribe((selectedIds: number[] | null) => {
          if (selectedIds && selectedIds.length > 0) {
            this.groupService.addStudentsToGroup(this.group!.id!, selectedIds).subscribe({
              next: () => {
                this.loadStudents(this.group!.id!); // Recharger la liste après ajout
                this.showSuccessMessage(`${selectedIds.length} étudiant(s) ajouté(s) au groupe.`);
              },
              error: (error) => {
                console.error('Error adding students to group:', error);
                this.showErrorMessage("Erreur lors de l'ajout des étudiants au groupe.");
              }
            });
          }
        });
      },
      error: (error) => {
        console.error('Failed to get level for group:', error);
      }
    });
  }


  onEditGroup(): void {
    const dialogRef = this.dialog.open(EditGroupDialogComponent, {
      width: '600px',
      data: { group: this.group },
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        // result contains { group: Group, file: File | null }
        this.groupService.updateGroup(result.group).subscribe({
          next: (updatedGroup) => {
            // If a new photo was selected, upload it
            if (result.file) {
              this.groupService.uploadGroupPhoto(updatedGroup.id!, result.file).subscribe({
                next: () => {
                  this.loadGroupData(updatedGroup.id!);
                  this.showSuccessMessage('Groupe modifié avec succès.');
                },
                error: (error) => {
                  console.error('Error uploading photo:', error);
                  this.showErrorMessage('Erreur lors du téléchargement de la photo.');
                }
              });
            } else {
              this.group = updatedGroup;
              this.showSuccessMessage('Groupe modifié avec succès.');
            }
          },
          error: (error) => {
            console.error('Erreur lors de la mise à jour du groupe :', error);
            this.showErrorMessage('Erreur lors de la mise à jour du groupe.');
          },
        });
      } else {
        console.log('Modification annulée.');
      }
    });
  }


  onPrint() {

  }

  onDisable() {

  }

  removeStudentFromGroup(student: Student): void {
    this.dialog.open(ConfirmationDialogComponent, {
      data: {
        title: "Suppression d'un étudiant",
        message: 'Voulez-vous vraiment supprimer cet étudiant du groupe ?',
        confirmText: 'Oui, supprimer',
        cancelText: 'Non, annuler',
        confirmColor: 'warn'
      }
    }).afterClosed().subscribe((result: boolean) => {
      if (result) {
        if (this.group && this.group.id !== undefined) {
          const groupId = this.group.id;
          console.log("rrrrrr", student.id);
          this.studentService.removeStudentFromGroup(groupId, student.id).subscribe({
            next: () => {
              this.snackBar.open('Étudiant retiré du groupe avec succès', 'Fermer', {
                duration: 3000,
                panelClass: ['success-snackbar']
              });
              this.loadStudents(groupId); // Recharger les étudiants après suppression
            },
            error: () => {
              this.snackBar.open('Erreur lors du retrait de l\'étudiant du groupe', 'Fermer', {
                duration: 3000,
                panelClass: ['error-snackbar']
              });
            }
          });
        } else {
          this.snackBar.open('Le groupe ou l\'ID du groupe est indéfini', 'Fermer', {
            duration: 3000,
            panelClass: ['error-snackbar']
          });
        }
      } else {
        console.log('Suppression annulée');
      }
    });
  }

  showSuccessMessage(message: string): void {
    this.snackBar.open(message, 'Fermer', {
      duration: 3000,
      panelClass: ['snack-bar-success']
    });
  }

  showErrorMessage(message: string): void {
    this.snackBar.open(message, 'Fermer', {
      duration: 3000,
      panelClass: ['snack-bar-error']
    });
  }

}
