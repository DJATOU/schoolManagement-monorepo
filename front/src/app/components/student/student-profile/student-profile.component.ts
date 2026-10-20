import { SharedModule } from '../../../shared/shared/shared.module';
import { GroupCardComponent } from '../../group/group-card/group-card.component';
import { PaymentDialogComponent } from '../../payment/payment-dialog/payment-dialog.component';
import { GroupDialogComponent, GroupDialogData, GroupSelection } from '../../group/group-dialog/group-dialog.component';
import { EditStudentDialogComponent } from '../edit-student-dialog/edit-student-dialog.component';
import { StudentService } from '../services/student.service';
import { GroupService } from '../../../services/group.service';
import { LevelService } from '../../../services/level.service';
import { GroupTypeService } from '../../../services/GroupTypeService';
import { Component, OnInit, ViewChild } from '@angular/core';
import { Student } from '../domain/student';
import { Group } from '../../../models/group/group';
import { GroupType } from '../../../models/GroupType/groupType';
import { Level } from '../../../models/level/level';
import { FormBuilder, FormGroup } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';
import { ApiError, ApiResponse } from '../../../models/response';
import { ConfirmationDialogComponent } from '../../shared/confirmation-dialog/confirmation-dialog.component';
import { PaymentHistoryDialogComponent } from '../../payment/payment-history/payment-history-dialog/payment-history-dialog.component';
import { AttendanceHistoryDialogComponent } from '../../attendance/attendance-history-dialog/attendance-history-dialog.component';
import { environment } from '../../../../environments/environment';
import { PdfGeneratorService } from '../services/pdf-generator.service';
import { ProfilePdfService } from '../../../services/profile-pdf.service';
import { StudentFullHistoryDialogComponent } from '../student-full-history-dialog/student-full-history-dialog.component';
import { TranslateService } from '@ngx-translate/core';
import { TutorService } from '../../../services/tutor.service';
import { Tutor } from '../../../models/tutor/tutor';
import { TranslateModule } from '@ngx-translate/core';
import { Parcours } from '../../../models/parcours/parcours';
import { Observable, combineLatest } from 'rxjs';
import { map } from 'rxjs/operators';
import { SchoolYearContextService } from '../../../services/school-year-context.service';
import { DiscountService } from '../../../services/discount.service';
import { Discount } from '../../../models/discount/student-discount';
import { AdminOnlyDirective } from '../../../shared/admin-only.directive';
import { AuthService } from '../../../services/auth.service';
import { LinkTutorDialogComponent } from '../../tutor/link-tutor-dialog/link-tutor-dialog.component';
import { SecureImageDirective } from '../../../shared/secure-image.directive';
import { formatDayFirst } from '../../../shared/iso-day';
import { GroupChangeNoticeComponent } from '../../shared/group-change-notice/group-change-notice.component';
import { HasRoleDirective } from '../../../shared/has-role.directive';
import { StudentEncashmentsComponent } from '../student-encashments/student-encashments.component';
import { StudentEnrolmentsComponent } from '../student-enrolments/student-enrolments.component';
import { StudentJournalComponent } from '../student-journal/student-journal.component';
import { enrolmentRefusalMessage } from '../../../utils/enrolment-refusal';
import { genderLabelKey, nationalityLabelKey } from '../../../utils/form-options';
import { pdfInstant } from '../../../shared/pdf-i18n';

/** Code d'erreur → clé de traduction du message, traduit au moment de l'affichage. */
const errorMessages = {
  PAYMENT_EXCEEDS_SESSIONS: 'STUDENT_PROFILE.ERRORS.PAYMENT_EXCEEDS_SESSIONS',
  STUDENT_NOT_FOUND: 'STUDENT_PROFILE.ERRORS.STUDENT_NOT_FOUND',
  GROUP_NOT_FOUND: 'STUDENT_PROFILE.ERRORS.GROUP_NOT_FOUND',
  GENERIC_ERROR: 'STUDENT_PROFILE.ERRORS.GENERIC_ERROR',
  GROUP_ALREADY_ASSOCIATED: 'STUDENT_PROFILE.ERRORS.GROUP_ALREADY_ASSOCIATED',
  INSUFFICIENT_SESSIONS: 'STUDENT_PROFILE.ERRORS.INSUFFICIENT_SESSIONS',
  INVALID_GROUP_LEVEL: 'STUDENT_PROFILE.ERRORS.INVALID_GROUP_LEVEL',
};

@Component({
  selector: 'app-student-profile',
  standalone: true,
  imports: [
    SharedModule,
    GroupCardComponent,
    TranslateModule,
    AdminOnlyDirective
  ,
    SecureImageDirective,
    GroupChangeNoticeComponent,
    HasRoleDirective,
    StudentEncashmentsComponent,
    StudentEnrolmentsComponent,
    StudentJournalComponent
  ],
  templateUrl: './student-profile.component.html',
  styleUrls: ['./student-profile.component.scss'],
  providers: [StudentService, GroupService, LevelService, GroupTypeService]
})
export class StudentProfileComponent implements OnInit {
  student: Student | null = null;

  /**
   * Réductions accordées à l'étudiant, toutes portées confondues (groupe, série, séance).
   * Affichées sur la fiche : le tarif réellement facturé en dépend.
   */
  discounts: Discount[] = [];

  allGroups: Group[] = [];
  allGroupTypes: GroupType[] = [];
  levels: Level[] = [];
  studentGroups: Group[] = [];
  studentLevelId: number = -1;
  groupForm: FormGroup;
  loading = true;
  studentPhotoUrl: string = '';
  hasImageError: boolean = false;
  avatarColor: string = '#6366f1';
  tutor: Tutor | null = null;
  parcours: Parcours | null = null;

  /** Historique des versements, absent pour un rôle autre qu'ADMIN. */
  @ViewChild(StudentEncashmentsComponent) encashmentsPanel?: StudentEncashmentsComponent;

  /** Inscriptions de l'élève, arrivées et départs : rechargées après une inscription. */
  @ViewChild(StudentEnrolmentsComponent) enrolmentsPanel?: StudentEnrolmentsComponent;

  /**
   * Vue en lecture seule (Read_Only_History) lorsque l'année scolaire
   * sélectionnée n'est pas l'année courante (Requirement 9.4). Désactive les
   * contrôles de paiement et de gestion des groupes.
   */
  readonly readOnly$: Observable<boolean>;

  /**
   * Désactive les commandes d'écriture (paiement, gestion des groupes) si la vue
   * est en lecture seule (année passée) OU si l'utilisateur n'est pas ADMIN.
   */
  readonly writeDisabled$: Observable<boolean>;

  // Colors for avatar backgrounds
  private avatarColors = [
    '#6366f1', '#8b5cf6', '#ec4899', '#ef4444', '#f97316',
    '#eab308', '#22c55e', '#14b8a6', '#06b6d4', '#3b82f6'
  ];

  constructor(
    private route: ActivatedRoute,
    private studentService: StudentService,
    private groupService: GroupService,
    private groupTypeService: GroupTypeService,
    private levelService: LevelService,
    private fb: FormBuilder,
    private snackBar: MatSnackBar,
    private dialog: MatDialog,
    private pdfGeneratorService: PdfGeneratorService, // Injection du service
    private profilePdfService: ProfilePdfService,
    private translate: TranslateService,
    private tutorService: TutorService,
    private schoolYearContext: SchoolYearContextService,
    private authService: AuthService,
    private discountService: DiscountService,
    private router: Router
  ) {
    this.readOnly$ = this.schoolYearContext.readOnly$;
    this.writeDisabled$ = combineLatest([
      this.schoolYearContext.readOnly$,
      this.authService.currentUser$,
    ]).pipe(map(([readOnly]) => readOnly || !this.authService.hasRole('ADMIN')));
    this.groupForm = this.fb.group({
      groupIds: [[]],
      // Date d'arrivée choisie dans le dialogue ; sans elle, le serveur retient le jour même.
      arrival: [null]
    });
  }

  /** Génère la fiche profil PDF de l'étudiant (infos, hors historique). */
  printProfilePdf(): void {
    if (!this.student) return;
    const s = this.student;
    const t = (key: string, params?: object) => pdfInstant(this.translate, key, params);
    const genderKey = genderLabelKey(s.gender);

    const sections = [
      {
        heading: t('STUDENT_PROFILE.PERSONAL_INFO'),
        rows: [
          { label: t('COMMON.GENDER'), value: genderKey ? t(genderKey) : s.gender },
          { label: t('COMMON.EMAIL'), value: s.email },
          { label: t('STUDENT_PROFILE.PHONE'), value: s.phoneNumber },
          { label: t('COMMON.BIRTHDATE'), value: formatDayFirst(s.dateOfBirth) || null },
          { label: t('COMMON.BIRTHPLACE'), value: s.placeOfBirth }
        ]
      },
      {
        heading: t('STUDENT_PROFILE.ACADEMIC_INFO'),
        rows: [
          { label: t('COMMON.LEVEL'), value: s.levelName },
          { label: t('COMMON.SCHOOL'), value: s.establishment },
          { label: t('STUDENT_PROFILE.AVERAGE'), value: s.averageScore }
        ]
      }
    ];

    // Section tuteur (affichée uniquement si un tuteur est rattaché).
    if (this.tutor) {
      sections.push({
        heading: t('tutor.title'),
        rows: [
          { label: t('tutor.name'), value: `${this.tutor.firstName} ${this.tutor.lastName}` },
          { label: t('tutor.relationship'), value: this.tutor.relationship ?? null },
          { label: t('tutor.phone'), value: this.tutor.phoneNumber ?? null },
          { label: t('tutor.email'), value: this.tutor.email ?? null }
        ]
      });
    }

    this.profilePdfService.generateProfilePdf({
      title: `${s.firstName} ${s.lastName}`,
      subtitle: s.levelName
        ? t('PROFILE_PDF.STUDENT_WITH_LEVEL', { level: s.levelName })
        : t('PROFILE_PDF.STUDENT'),
      sections,
      tableTitle: t('STUDENT_PROFILE.GROUPS_COUNT', { count: this.studentGroups.length }),
      tableColumns: ['#', t('STUDENT_PROFILE.PDF.GROUP_COLUMN'), t('COMMON.LEVEL'), t('STUDENT_PROFILE.PDF.SUBJECT_COLUMN')],
      tableRows: this.studentGroups.map((g, i) => [
        i + 1,
        g.name,
        g.levelName ?? '—',
        g.subjectName ?? '—'
      ])
    });
  }

  ngOnInit(): void {
    const studentId = this.getStudentIdFromRoute();
    if (studentId) {
      this.loadStudentData(studentId);
    } else {
      this.showError(this.translate.instant(errorMessages.STUDENT_NOT_FOUND));
    }
    this.loadSelectOptions();
    this.loadAllGroups(); // Ajouté pour charger les groupes
    this.loadAllGroupTypes(); // Si nécessaire pour charger les types de groupes
  }

  private getStudentIdFromRoute(): number | null {
    const id = this.route.snapshot.paramMap.get('id');
    return id ? +id : null;
  }

  private loadStudentData(studentId: number): void {
    this.studentService.getStudentById(studentId).subscribe({
      next: student => {
        this.student = student;
        console.log('Student data:', this.student);

        this.refreshPhotoUrl();
        this.setAvatarColor();

        this.loading = false;
        this.loadStudentLevel();
        this.loadStudentGroups();
        this.loadTutor();
        this.loadParcours();
        this.loadDiscounts();
      },
      error: () => {
        this.loading = false;
        this.showError(this.translate.instant(errorMessages.STUDENT_NOT_FOUND));
      }
    });
  }

  /**
   * Ouvre le dialogue de rattachement d'un tuteur (existant ou nouveau), puis enregistre
   * le lien sur l'étudiant.
   */
  openLinkTutorDialog(): void {
    this.dialog.open(LinkTutorDialogComponent, {
      width: '560px',
      maxWidth: '95vw',
      autoFocus: false
    }).afterClosed().subscribe((tutor: Tutor | null) => {
      if (tutor?.id) {
        this.saveTutorLink(tutor.id, this.translate.instant('tutor.attached'));
      }
    });
  }

  /** Demande confirmation avant de détacher le tuteur de l'étudiant. */
  confirmDetachTutor(): void {
    this.dialog.open(ConfirmationDialogComponent, {
      data: {
        title: this.translate.instant('tutor.detachTitle'),
        message: this.translate.instant('tutor.detachMessage'),
        confirmText: this.translate.instant('tutor.detachConfirm'),
        cancelText: this.translate.instant('common.cancel'),
        confirmColor: 'warn'
      }
    }).afterClosed().subscribe((confirmed: boolean) => {
      if (confirmed) {
        this.saveTutorLink(null, this.translate.instant('tutor.detached'));
      }
    });
  }

  /**
   * Enregistre le rattachement (ou le détachement) du tuteur sur l'étudiant.
   *
   * <p>Le lien est porté par l'étudiant ({@code tutorId}) : on met donc à jour l'étudiant.
   * Détacher ne supprime pas la fiche du tuteur, qui reste réutilisable pour un autre
   * étudiant (frères et sœurs).</p>
   */
  private saveTutorLink(tutorId: number | null, successMessage: string): void {
    if (!this.student?.id) {
      return;
    }

    // Point d'entrée dédié : la mise à jour générale de l'étudiant ignore les valeurs
    // nulles côté backend, un détachement passé par ce chemin serait silencieusement perdu.
    this.studentService.setTutor(this.student.id, tutorId).subscribe({
      next: () => {
        this.student = { ...this.student!, tutorId: tutorId ?? undefined };
        this.loadTutor();
        this.showSuccessMessage(successMessage);
      },
      error: (error) => {
        console.error('Error linking tutor:', error);
        this.showErrorMessage(this.translate.instant('tutor.attachError'));
      }
    });
  }

  /** Charge le tuteur rattaché à l'étudiant, s'il existe. */
  private loadTutor(): void {
    if (this.student?.tutorId) {
      this.tutorService.getTutorById(this.student.tutorId).subscribe({
        next: (tutor) => (this.tutor = tutor),
        error: (error) => {
          console.error('Error loading tutor:', error);
          this.tutor = null;
        }
      });
    } else {
      this.tutor = null;
    }
  }

  private loadStudentLevel(): void {
    if (this.student?.levelId) {
      console.log('Attempting to fetch level with ID:', this.student.levelId); // Log pour vérifier l'ID du niveau
      this.levelService.getLevelById(this.student.levelId).subscribe({
        next: level => {
          console.log('Level fetched successfully:', level); // Log pour vérifier la réponse du backend
          this.student!.levelName = level.name;
          this.studentLevelId = level.id ?? 0;
          console.log('Level name set:', this.student?.levelName);
          this.updateUI();
        },
        error: error => {
          console.error('Error fetching level:', error); // Log pour vérifier les erreurs
          this.showError(this.translate.instant(errorMessages.GENERIC_ERROR));
          this.updateUI();
        }
      });
    } else {
      console.warn('No level ID provided for student:', this.student);
      this.updateUI();
    }
  }

  private updateUI(): void {
    // Mettez à jour l'interface ici après avoir récupéré les données
    this.loading = false;
  }

  /**
   * Get initials from first and last name (max 2 characters)
   */
  getInitials(): string {
    const firstName = this.student?.firstName || '';
    const lastName = this.student?.lastName || '';
    const firstInitial = firstName.charAt(0).toUpperCase();
    const lastInitial = lastName.charAt(0).toUpperCase();
    return firstInitial + lastInitial || 'XX';
  }

  /**
   * Set avatar color based on student name
   */
  private setAvatarColor(): void {
    const name = `${this.student?.firstName || ''}${this.student?.lastName || ''}`;
    const hash = name.split('').reduce((acc, char) => acc + char.charCodeAt(0), 0);
    this.avatarColor = this.avatarColors[hash % this.avatarColors.length];
  }

  /**
   * Handle image load error - fallback to initials
   */
  /**
   * Recalcule l'URL de la photo et réarme l'indicateur d'erreur.
   *
   * <p>Deux points importants pour que le changement de photo soit visible sans recharger
   * la page : (1) {@code hasImageError} doit être remis à faux, sinon un échec précédent
   * continue d'afficher les initiales même après un envoi réussi ; (2) l'URL porte un
   * paramètre d'horodatage pour contourner le cache du navigateur.</p>
   */
  private refreshPhotoUrl(): void {
    this.hasImageError = false;
    this.studentPhotoUrl = this.student?.photo
      ? `${environment.apiUrl}${environment.imagesPath}${this.student.photo}?t=${Date.now()}`
      : '';
  }

  onImageError(): void {
    this.hasImageError = true;
  }

  /**
   * Charge le parcours de l'étudiant : pour chaque année scolaire fréquentée,
   * les niveaux et les groupes suivis. Les entrées sont déjà triées par date de
   * début d'année décroissante côté backend (exigences 11.1, 11.2, 11.3).
   */
  private loadParcours(): void {
    if (this.student?.id !== undefined) {
      this.studentService.getParcours(this.student.id).subscribe({
        next: parcours => {
          this.parcours = parcours;
        },
        error: () => {
          this.parcours = null;
        }
      });
    }
  }

  /**
   * Charge les groupes de l'étudiant pour l'année scolaire sélectionnée.
   *
   * La fiche listait tous les groupes, toutes années confondues : un groupe d'une année
   * révolue s'affichait alors qu'il est absent de la liste des groupes, filtrée sur l'année
   * sélectionnée. L'historique des années précédentes reste consultable dans la section
   * « Parcours », ou en basculant le sélecteur d'année.
   */
  /** Taux de réduction en pourcentage pour l'affichage (0.65 → 65). */
  discountPercent(discount: Discount): number {
    return Math.round(discount.rate * 100);
  }

  /** Réduction la plus élevée de l'étudiant, en pourcentage, pour le résumé du panneau. */
  get highestDiscountPercent(): number {
    return this.discounts.reduce((max, d) => Math.max(max, this.discountPercent(d)), 0);
  }

  /**
   * Charge les réductions de l'étudiant.
   *
   * Une erreur n'affiche pas de message bloquant : l'absence de cette information secondaire
   * ne doit pas parasiter la consultation de la fiche.
   */
  private loadDiscounts(): void {
    if (this.student?.id === undefined) {
      return;
    }
    this.discountService.getDiscountsForStudent(this.student.id).subscribe({
      next: discounts => {
        this.discounts = discounts ?? [];
      },
      error: () => {
        this.discounts = [];
      }
    });
  }

  private loadStudentGroups(): void {
    if (this.student?.id !== undefined) {
      const selectedYearId = this.schoolYearContext.getSelectedSchoolYear()?.id;
      this.studentService.getGroupsForStudent(this.student.id, selectedYearId).subscribe({
        next: groups => {
          this.studentGroups = groups;
        },
        error: () => {
          this.showError(this.translate.instant(errorMessages.GENERIC_ERROR));
        }
      });
    }
  }



  private loadAllGroups(): void {
    this.groupService.getGroups().subscribe({
      next: groups => {
        this.allGroups = groups;
      },
      error: () => {
        this.showError(this.translate.instant(errorMessages.GENERIC_ERROR));
      }
    });
  }

  private loadAllGroupTypes(): void {
    this.groupTypeService.getAllGroupTypes().subscribe({
      next: groupTypes => {
        this.allGroupTypes = groupTypes;
      },
      error: () => {
        this.showError(this.translate.instant(errorMessages.GENERIC_ERROR));
      }
    });
  }

  loadSelectOptions(): void {
    this.levelService.getLevels().subscribe(data => this.levels = data);
  }

  onSubmitGroups(): void {
    if (this.groupForm.valid) {
      const groupIds: number[] = this.groupForm.value.groupIds;
      const arrival: string | null = this.groupForm.value.arrival ?? null;
      if (this.student?.id !== undefined) {
        this.studentService.addGroupsToStudent(this.student.id, groupIds, arrival).subscribe({
          next: (response: ApiResponse) => {
            this.snackBar.open(response.message, this.translate.instant('STUDENT_PROFILE.MESSAGES.CLOSE'), {
              duration: 3000,
              panelClass: ['success-snackbar']
            });

            this.updateStudentGroups(groupIds);
            this.enrolmentsPanel?.reload();

            this.groupForm.reset({ groupIds: [], arrival: null });
          },
          error: (error: ApiError) => {
            this.handleGroupSubmissionError(error);
          }
        });
      } else {
        this.showError(this.translate.instant(errorMessages.STUDENT_NOT_FOUND));
      }
    }
  }

  private updateStudentGroups(groupIds: number[]): void {
    const newGroups = this.allGroups.filter(group => group.id !== undefined && groupIds.includes(group.id!));
    this.studentGroups = [...this.studentGroups, ...newGroups];
  }

  /**
   * Le refus tel que le serveur le rédige : année et bornes, départ recouvert, niveau, groupes
   * déjà suivis. Le message générique d'avant laissait deviner la cause.
   */
  private handleGroupSubmissionError(error: ApiError): void {
    this.showError(enrolmentRefusalMessage(error, this.translate));
  }

  /** Année affichée : la liste des inscriptions la suit, comme celle des groupes. */
  get selectedSchoolYearId(): number | null {
    return this.schoolYearContext.getSelectedSchoolYear()?.id ?? null;
  }

  get studentFullName(): string {
    return `${this.student?.firstName ?? ''} ${this.student?.lastName ?? ''}`.trim();
  }

  /** Date de naissance JJ/MM/AAAA, comme à la saisie : découpée, sans conversion de fuseau. */
  get birthDateLabel(): string {
    return formatDayFirst(this.student?.dateOfBirth);
  }

  /** Libellé traduit du sexe (« M » / « male » → Masculin) ; valeur brute si le code est inconnu. */
  genderLabel(code: string | null | undefined): string {
    const key = genderLabelKey(code);
    return key ? this.translate.instant(key) : code ?? '';
  }

  /** Libellé traduit d'une nationalité enregistrée ; valeur brute si elle est hors liste. */
  nationalityLabel(value: string | null | undefined): string {
    const key = nationalityLabelKey(value);
    return key ? this.translate.instant(key) : value ?? '';
  }

  /**
   * Une arrivée, un départ ou une réouverture corrigés : les groupes suivis, et les montants des
   * versements, ont pu changer.
   */
  onEnrolmentCorrected(): void {
    this.loadStudentGroups();
    this.encashmentsPanel?.reload();
  }

  private showError(message: string): void {
    this.snackBar.open(message, this.translate.instant('STUDENT_PROFILE.MESSAGES.CLOSE'), {
      duration: 5000,
      panelClass: ['error-snackbar']
    });
  }

  openPaymentDialog(): void {
    if (!this.student?.id) {
      this.showError(this.translate.instant('STUDENT_PROFILE.MESSAGES.INVALID_STUDENT'));
      return;
    }

    // Charger la liste "fixe + rattrapage" avant d'ouvrir la dialog
    this.groupService.getGroupsForPayment(this.student.id).subscribe({
      next: (allGroups) => {
        if (allGroups.length === 0) {
          this.showError(this.translate.instant('STUDENT_PROFILE.MESSAGES.NO_PAYMENT_GROUP'));
          return;
        }

        // Ouvrir la dialog en passant cette liste élargie :
        const dialogRef = this.dialog.open(PaymentDialogComponent, {
          // 400px rendait le formulaire très haut : deux champs par ligne tiennent à 560px
          // et la fenêtre reste dans l'écran sans faire défiler la page.
          width: '560px',
          maxWidth: '95vw',
          maxHeight: '90vh',
          autoFocus: 'first-tabbable',
          data: {
            studentId: this.student!.id,
            groups: allGroups,  // => contiendra fixes + rattrapage
            // Nom imprimé sur le reçu remis à l'étudiant.
            studentName: `${this.student!.firstName ?? ''} ${this.student!.lastName ?? ''}`.trim()
          }
        });

        // Le dialogue se ferme sur la réponse d'encaissement : le versement est enregistré,
        // l'historique des versements doit le montrer sans recharger la page.
        dialogRef.afterClosed().subscribe(result => {
          if (result) {
            this.encashmentsPanel?.reload();
          }
        });
      },
      error: (err) => {
        console.error('Error fetching groups for payment', err);
        this.showError(this.translate.instant(errorMessages.GENERIC_ERROR));
      }
    });
  }


  openGroupDialog(): void {
    console.log('All groups:', this.allGroups);

    // Filtrer tous les groupes correspondant au niveau de l'étudiant
    const groupsForLevel = this.allGroups.filter(group => group.levelId === this.studentLevelId);

    if (groupsForLevel.length === 0) {
      // Aucun groupe disponible pour le niveau de l'étudiant
      this.showErrorMessage(this.translate.instant('STUDENT_PROFILE.MESSAGES.NO_GROUP_FOR_LEVEL'));
      return;
    }

    // Filtrer pour exclure les groupes déjà ajoutés à l'étudiant
    const possibleGroups = groupsForLevel.filter(group =>
      !this.studentGroups.some(studentGroup => studentGroup.id === group.id)
    );

    if (possibleGroups.length === 0) {
      // Tous les groupes de ce niveau ont déjà été ajoutés à l'étudiant
      this.showErrorMessage(this.translate.instant('STUDENT_PROFILE.MESSAGES.ALL_GROUPS_ADDED'));
      return;
    }

    console.log('Possible groups for level:', possibleGroups);

    // Ouvrir un dialogue pour sélectionner les groupes et la date d'arrivée
    const year = this.schoolYearContext.getSelectedSchoolYear();
    const dialogRef = this.dialog.open<GroupDialogComponent, GroupDialogData, GroupSelection>(GroupDialogComponent, {
      width: '400px',
      data: {
        allGroups: possibleGroups,  // Passer les groupes filtrés qui ne sont pas déjà ajoutés
        selectedGroups: this.groupForm.value.groupIds,  // Groupes déjà sélectionnés dans le formulaire
        yearStart: year?.startDate ?? null,
        yearEnd: year?.endDate ?? null
      }
    });

    // Mettre à jour le formulaire avec les groupes sélectionnés
    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.groupForm.patchValue({ groupIds: result.groupIds, arrival: result.arrival });
        this.onSubmitGroups();
      }
    });
  }

  onEdit(): void {
    const dialogRef = this.dialog.open(EditStudentDialogComponent, {
      width: '720px',
      maxWidth: '95vw',
      autoFocus: false,
      data: { student: this.student },
    });

    const previousTutorId = this.student?.tutorId ?? null;

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        // result contains { student: Student, file: File | null }
        this.studentService.updateStudent(result.student).subscribe({
          next: (updatedStudent) => {
            // Le tuteur est traité à part : la mise à jour générale ignore les valeurs
            // nulles, un tuteur retiré depuis le dialogue ne serait donc pas enregistré.
            const newTutorId = result.student.tutorId ?? null;
            if (newTutorId !== previousTutorId) {
              this.studentService.setTutor(updatedStudent.id!, newTutorId).subscribe({
                next: () => this.loadTutor(),
                error: (error: any) => {
                  console.error('Error linking tutor:', error);
                  this.showErrorMessage(this.translate.instant('tutor.attachError'));
                }
              });
            }
            // If a new photo was selected, upload it
            if (result.file) {
              this.studentService.uploadStudentPhoto(updatedStudent.id!, result.file).subscribe({
                next: (filename: string) => {
                  // Mise à jour immédiate de l'affichage : le nom du fichier renvoyé par
                  // l'envoi est appliqué sans attendre, puis la fiche est rechargée.
                  this.student = { ...updatedStudent, photo: filename };
                  this.refreshPhotoUrl();
                  this.loadStudentData(updatedStudent.id!);
                  this.showSuccessMessage(this.translate.instant('STUDENT_PROFILE.MESSAGES.UPDATED'));
                },
                error: (error: any) => {
                  console.error('Error uploading photo:', error);
                  this.showErrorMessage(this.translate.instant('STUDENT_PROFILE.MESSAGES.PHOTO_UPLOAD_ERROR'));
                }
              });
            } else {
              this.student = updatedStudent;
              this.refreshPhotoUrl();
              this.loadStudentLevel();
              this.showSuccessMessage(this.translate.instant('STUDENT_PROFILE.MESSAGES.UPDATED'));
            }
          },
          error: (error) => {
            console.error('Error updating student:', error);
            this.showErrorMessage(this.translate.instant('STUDENT_PROFILE.MESSAGES.UPDATE_ERROR'));
          },
        });
      } else {
        console.log('Modification annulée.');
      }
    });
  }

  onDisable(): void {
    // Confirmation dialog to disable student
    this.dialog.open(ConfirmationDialogComponent, {
      data: {
        title: this.translate.instant('CONFIRMATION_DIALOG.DISABLE_STUDENT.TITLE'),
        message: this.translate.instant('CONFIRMATION_DIALOG.DISABLE_STUDENT.MESSAGE'),
        confirmText: this.translate.instant('CONFIRMATION_DIALOG.DISABLE_STUDENT.CONFIRM'),
        cancelText: this.translate.instant('CONFIRMATION_DIALOG.DISABLE_STUDENT.CANCEL'),
        confirmColor: 'warn'
      }
    }).afterClosed().subscribe((result: boolean) => {
      if (result) {
        // Désactivation par statut (INACTIVE) : cohérent avec le filtre de la recherche
        // (exclu par défaut, visible via « inclure inactifs »). Historique conservé.
        this.studentService.deactivateStudent(this.student!.id || -1).subscribe({
          next: () => {
            this.showSuccessMessage(this.translate.instant('CONFIRMATION_DIALOG.DISABLE_STUDENT.SUCCESS'));
            // Retour à la page de résultats de recherche après désactivation.
            this.router.navigate(['/student']);
          },
          error: (error) => {
            console.error('Error disabling student:', error);
            this.showErrorMessage(this.translate.instant('CONFIRMATION_DIALOG.DISABLE_STUDENT.ERROR'));
          }
        });
      }
      else {
        console.log('Operation canceled.');
      }
    });
  }

  openPaymentHistoryDialog(): void {
    this.dialog.open(PaymentHistoryDialogComponent, {
      width: '600px',
      data: { studentId: this.student?.id } // Passer l'ID de l'étudiant pour filtrer les données
    });
  }

  openAttendanceHistoryDialog(): void {
    this.dialog.open(AttendanceHistoryDialogComponent, {
      width: '600px',
      data: { studentId: this.student?.id } // Passer l'ID de l'étudiant pour filtrer les données
    });
  }

  /**
   * Ouvre le dialogue d'historique complet à l'écran (paiements, rattrapages,
   * réductions/exemptions et remboursements). L'impression PDF reste accessible
   * depuis le dialogue.
   */
  openFullHistoryDialog(): void {
    if (!this.student?.id) {
      this.showErrorMessage(this.translate.instant('STUDENT_PROFILE.MESSAGES.NOT_FOUND'));
      return;
    }
    this.dialog.open(StudentFullHistoryDialogComponent, {
      width: '900px',
      maxWidth: '95vw',
      data: { studentId: this.student.id }
    });
  }

  generateFullHistoryPdf(): void {
    if (this.student?.id) {
      this.studentService.getStudentFullHistory(this.student.id).subscribe({
        next: (fullHistory) => {
          console.log('Full History:', fullHistory);
          this.pdfGeneratorService.generateFullHistoryPdf(fullHistory, 'assets/succes_assistance.png');
        },
        error: (error) => {
          console.error('Error fetching full history:', error);
          this.showErrorMessage(this.translate.instant('STUDENT_PROFILE.MESSAGES.FULL_HISTORY_ERROR'));
        }
      });
    } else {
      this.showErrorMessage(this.translate.instant('STUDENT_PROFILE.MESSAGES.NOT_FOUND'));
    }
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
}