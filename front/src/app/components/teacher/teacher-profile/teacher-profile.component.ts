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
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AdminOnlyDirective } from '../../../shared/admin-only.directive';
import { SecureImageDirective } from '../../../shared/secure-image.directive';
import { formatDayFirst } from '../../../shared/iso-day';
import { HasRoleDirective } from '../../../shared/has-role.directive';
import { TeacherPayoutsComponent } from '../teacher-payouts/teacher-payouts.component';
import { genderLabelKey } from '../../../utils/form-options';
import { PrintLanguage, pdfInstant, runInPrintLanguage } from '../../../shared/pdf-i18n';
import { PrintLanguageMenuComponent } from '../../shared/print-language-menu/print-language-menu.component';
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
    GroupCardComponent,
    AdminOnlyDirective
  ,
    SecureImageDirective,
    HasRoleDirective,
    TeacherPayoutsComponent,
    TranslateModule,
    PrintLanguageMenuComponent
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
    private profilePdfService: ProfilePdfService,
    private translate: TranslateService
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

  /** Date de naissance JJ/MM/AAAA, comme à la saisie : découpée, sans conversion de fuseau. */
  get birthDateLabel(): string {
    return formatDayFirst(this.teacher?.dateOfBirth);
  }

  /** Libellé traduit du sexe (« M » / « male » → Masculin) ; valeur brute si le code est inconnu. */
  genderLabel(code: string | null | undefined): string {
    const key = genderLabelKey(code);
    return key ? this.translate.instant(key) : code ?? '';
  }

  /**
   * Génère la fiche PDF de l'enseignant (infos + groupes), dans la langue choisie au moment
   * d'imprimer ; sans choix, selon la règle des documents (voir pdfLanguage).
   */
  printProfilePdf(lang?: PrintLanguage): void {
    if (!this.teacher) return;
    runInPrintLanguage(this.translate, lang, () => this.composeProfilePdf(this.teacher!))
      .catch((err: unknown) => console.error('Impression de la fiche impossible :', err));
  }

  private composeProfilePdf(t: Teacher): void {
    const tr = (key: string, params?: object) => pdfInstant(this.translate, key, params);
    const genderKey = genderLabelKey(t.gender);
    this.profilePdfService.generateProfilePdf({
      title: `${t.firstName} ${t.lastName}`,
      subtitle: t.specialization
        ? tr('PROFILE_PDF.TEACHER_WITH_SPECIALIZATION', { specialization: t.specialization })
        : tr('PROFILE_PDF.TEACHER'),
      sections: [
        {
          heading: tr('TEACHER_PROFILE.PERSONAL_INFO'),
          rows: [
            { label: tr('COMMON.GENDER'), value: genderKey ? tr(genderKey) : t.gender },
            { label: tr('COMMON.EMAIL'), value: t.email },
            { label: tr('TEACHER_PROFILE.PHONE'), value: t.phoneNumber },
            { label: tr('COMMON.BIRTHDATE'), value: formatDayFirst(t.dateOfBirth) || null },
            { label: tr('COMMON.BIRTHPLACE'), value: t.placeOfBirth },
            { label: tr('COMMON.ADDRESS'), value: t.address }
          ]
        },
        {
          heading: tr('TEACHER_PROFILE.PROFESSIONAL_INFO'),
          rows: [
            { label: tr('TEACHER_PROFILE.SPECIALIZATION'), value: t.specialization },
            { label: tr('TEACHER_PROFILE.EXPERIENCE'), value: t.yearsOfExperience }
          ]
        }
      ],
      listTitle: tr('TEACHER_PROFILE.GROUPS_COUNT', { count: this.teacherGroups.length }),
      listItems: this.teacherGroups.map(g =>
        `${g.name}${g.levelName ? ' — ' + g.levelName : ''}${g.subjectName ? ' (' + g.subjectName + ')' : ''}`
      )
    });
  }

  getTeacherDetails(id: number): void {
    this.teacherService.getTeacher(id).subscribe((teacher) => {
      this.teacher = teacher;
      this.loading = false;
      this.refreshPhotoUrl();
      this.setAvatarColor();
    });
  }

  /**
   * Recalcule l'URL de la photo et réarme l'indicateur d'erreur, afin qu'une nouvelle
   * photo s'affiche sans recharger la page (un échec précédent laissait sinon les
   * initiales affichées). Le paramètre d'horodatage contourne le cache du navigateur.
   */
  private refreshPhotoUrl(): void {
    this.hasImageError = false;
    this.teacherPhotoUrl = this.teacher?.photo
      ? `${environment.apiUrl}${environment.imagesPath}${this.teacher.photo}?t=${Date.now()}`
      : '';
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
      width: '720px',
      maxWidth: '95vw',
      autoFocus: false,
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
                next: (filename: string) => {
                  // Affichage immédiat : on applique le nom de fichier renvoyé sans
                  // attendre le rechargement de la fiche.
                  this.teacher = { ...updatedTeacher, photo: filename };
                  this.refreshPhotoUrl();
                  this.getTeacherDetails(updatedTeacher.id!);
                  this.showSuccessMessage(this.translate.instant('TEACHER_PROFILE.MESSAGES.UPDATED'));
                },
                error: (error) => {
                  console.error('Error uploading photo:', error);
                  this.showErrorMessage(this.translate.instant('TEACHER_PROFILE.MESSAGES.PHOTO_UPLOAD_ERROR'));
                }
              });
            } else {
              this.teacher = updatedTeacher;
              this.refreshPhotoUrl();
              this.showSuccessMessage(this.translate.instant('TEACHER_PROFILE.MESSAGES.UPDATED'));
            }
          },
          error: (error) => {
            console.error('Error updating teacher:', error);
            this.showErrorMessage(this.translate.instant('TEACHER_PROFILE.MESSAGES.UPDATE_ERROR'));
          }
        });
      }
    });
  }

  /**
   * Désactive l'enseignant après confirmation (suppression logique : l'historique est conservé).
   */
  onDisable(): void {
    this.dialog.open(ConfirmationDialogComponent, {
      data: {
        title: this.translate.instant('CONFIRMATION_DIALOG.DISABLE_TEACHER.TITLE'),
        message: this.translate.instant('CONFIRMATION_DIALOG.DISABLE_TEACHER.MESSAGE'),
        confirmText: this.translate.instant('CONFIRMATION_DIALOG.DISABLE_TEACHER.CONFIRM'),
        cancelText: this.translate.instant('CONFIRMATION_DIALOG.DISABLE_TEACHER.CANCEL'),
        confirmColor: 'warn'
      }
    }).afterClosed().subscribe((result: boolean) => {
      if (result) {
        this.teacherService.disableTeacher(this.teacher!.id || -1).subscribe({
          next: () => {
            this.showSuccessMessage(this.translate.instant('CONFIRMATION_DIALOG.DISABLE_TEACHER.SUCCESS'));
            // Retour à la liste : la fiche d'un enseignant désactivé n'a plus lieu d'être affichée.
            this.router.navigate(['/teacher']);
          },
          error: (error) => {
            console.error('Error disabling teacher:', error);
            this.showErrorMessage(this.translate.instant('CONFIRMATION_DIALOG.DISABLE_TEACHER.ERROR'));
          }
        });
      }
      else {
        console.log('Operation canceled.');
      }
    });
  }

  showSuccessMessage(message: string): void {
    this.snackBar.open(message, this.translate.instant('common.ok'), {
      duration: 3000,
      panelClass: ['snack-bar-success']
    });
  }

  showErrorMessage(message: string): void {
    this.snackBar.open(message, this.translate.instant('common.ok'), {
      duration: 3000,
      panelClass: ['snack-bar-error']
    });
  }

  onSubmitGroups(): void {
    // Handle group form submission
  }
}
