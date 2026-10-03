import { Component, Inject, OnInit } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialog } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { MatTooltipModule } from '@angular/material/tooltip';
import { SessionService } from '../../../services/SessionService';
import { AttendanceService } from '../../../services/attendance.service';
import { MatDialogModule } from '@angular/material/dialog';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatCardModule } from '@angular/material/card';
import { MatTabsModule } from '@angular/material/tabs';
import { MatButtonModule } from '@angular/material/button';
import { FormsModule } from '@angular/forms';
import { Attendance } from '../../../models/Attendance/attendance';
import { Session } from '../../../models/session/session';
import { Student } from '../../student/domain/student';
import { CommonModule } from '@angular/common';
import { MatIcon } from '@angular/material/icon';
import { AddStudentDialogComponent } from '../add-student-dialog/add-student-dialog.component';
import { StudentService } from '../../student/services/student.service';
import { EditSessionDialogComponent } from '../edit/edit-session-dialog/edit-session-dialogue.component';
import { GroupService } from '../../../services/group.service';
import { SessionAttendancePdfService, SessionAttendanceStudentRow } from '../../../services/session-attendance-pdf.service';
import { ConfirmationDialogComponent } from '../../shared/confirmation-dialog/confirmation-dialog.component';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable, combineLatest, firstValueFrom, forkJoin } from 'rxjs';
import { map } from 'rxjs/operators';
import { SchoolYearContextService } from '../../../services/school-year-context.service';
import { AuthService } from '../../../services/auth.service';
import { NotConcernedStudent, RollCall } from '../../../models/session/roll-call';
import { AttendanceSubmissionError, RejectedAbsence } from '../../../models/Attendance/rejected-absence';
import { calendarDayOf, formatCalendarDay } from '../../../utils/calendar-day';
import { CorrectionReason, CorrectionReasonType } from '../../../models/correction/correction';
import { SessionUnvalidation } from '../../../models/session/session-unvalidation';
import { CorrectionStep } from '../../../services/encashment.service';
import {
  CorrectionDialogComponent,
  CorrectionDialogData,
  CorrectionDialogResult
} from '../../shared/correction-dialog/correction-dialog.component';

@Component({
  selector: 'app-session-modal',
  templateUrl: './session-modal.component.html',
  styleUrls: ['./session-modal.component.scss'],
  standalone: true,
  imports: [
    CommonModule,
    MatDialogModule,
    MatButtonModule,
    MatCheckboxModule,
    FormsModule,
    MatFormFieldModule,
    MatCardModule,
    MatTabsModule,
    MatIcon,
    MatTooltipModule,
    TranslateModule
  ]
})
export class SessionModalComponent implements OnInit {
  isFinished = false;

  /**
   * Vue en lecture seule (Read_Only_History) lorsque l'année scolaire
   * sélectionnée n'est pas l'année courante (Requirement 9.4). Désactive
   * l'édition, la validation et la modification de présence.
   */
  readonly readOnly$: Observable<boolean>;

  /**
   * Désactive les commandes d'écriture (édition, validation, présence) si la
   * vue est en lecture seule (année passée) OU si l'utilisateur n'est pas ADMIN.
   */
  readonly writeDisabled$: Observable<boolean>;

  constructor(
    public dialogRef: MatDialogRef<SessionModalComponent>,
    @Inject(MAT_DIALOG_DATA) public sessionData: Session & { students: Student[] },
    private sessionService: SessionService,
    private attendanceService: AttendanceService,
    private studentService : StudentService,
    private groupService :  GroupService,
    private sessionAttendancePdfService: SessionAttendancePdfService,
    private translate: TranslateService,
    private snackBar: MatSnackBar,
    private dialog: MatDialog, // Injection de MatDialog ici
    private schoolYearContext: SchoolYearContextService,
    private authService: AuthService,
    private router: Router
  ) {
    this.readOnly$ = this.schoolYearContext.readOnly$;
    this.writeDisabled$ = combineLatest([
      this.schoolYearContext.readOnly$,
      this.authService.currentUser$,
    ]).pipe(map(([readOnly]) => readOnly || !this.authService.hasRole('ADMIN')));
  }

  /** Ouvre une route de l'application dans un nouvel onglet du navigateur. */
  private openInNewTab(commands: unknown[]): void {
    const url = this.router.serializeUrl(this.router.createUrlTree(commands));
    window.open(url, '_blank');
  }

  /** Ouvre la fiche du groupe de la séance dans un nouvel onglet. */
  openGroup(): void {
    if (this.sessionData?.groupId) {
      this.openInNewTab(['/group', this.sessionData.groupId]);
    }
  }

  /** Ouvre la fiche de l'enseignant de la séance dans un nouvel onglet. */
  openTeacher(): void {
    if (this.sessionData?.teacherId) {
      this.openInNewTab(['/teacher', this.sessionData.teacherId]);
    }
  }

  /** Ouvre la fiche d'un étudiant dans un nouvel onglet. */
  openStudent(studentId?: number): void {
    if (studentId) {
      this.openInNewTab(['/student', studentId]);
    }
  }

  /**
   * Feuille d'appel servie par le serveur : les étudiants dont la fenêtre d'inscription contient
   * le jour de la séance. Nulle tant qu'elle n'est pas chargée.
   *
   * Une feuille vide n'est plus complétée par le reste du groupe : cela notait absents des
   * étudiants arrivés après la séance ou déjà partis. Elle s'explique par les non-concernés.
   */
  rollCall: RollCall | null = null;

  /** La feuille n'a pas pu être chargée : une liste vide ne doit pas passer pour une feuille vide. */
  rollCallError = false;

  /**
   * Absences refusées par la dernière validation : élèves que la séance ne concerne pas (arrivés
   * après, partis avant, ou sans inscription au groupe). Vide tant que rien n'est refusé.
   */
  rejectedAbsences: RejectedAbsence[] = [];

  private async loadStudentsData(): Promise<void> {
    // On ne charge les étudiants que si la liste est vide
    if (this.sessionData.students && this.sessionData.students.length > 0) {
      return;
    }
    try {
      const rollCall = await firstValueFrom(this.sessionService.getRollCall(this.sessionData.id));
      this.rollCall = rollCall;
      this.sessionData.students = rollCall.students.map(student => ({
        id: student.id,
        firstName: student.firstName,
        lastName: student.lastName,
        gender: student.gender ?? '',
        enrolmentDeparture: student.departure,
        isPresent: true,
        description: '',
        isCatchUp: false
      } as Student & { id: number; isPresent: boolean }));
    } catch (error) {
      this.rollCallError = true;
      this.sessionData.students = this.sessionData.students ?? [];
      console.error('Error fetching roll call:', error);
    }
  }

  /** Jour `yyyy-MM-dd` affiché `dd/MM/yyyy`. */
  day(value: string | null | undefined): string {
    return formatCalendarDay(value);
  }

  /** Fenêtres d'un étudiant non concerné, en clair : « à partir du 14/01/2030 », « du … au … ». */
  describeWindows(student: NotConcernedStudent): string {
    return student.windows.map(window => {
      if (!window.arrival) {
        return this.translate.instant('SESSION_MODAL.WINDOW_UNDATED');
      }
      return window.departure
        ? this.translate.instant('SESSION_MODAL.WINDOW_FROM_TO',
            { from: this.day(window.arrival), to: this.day(window.departure) })
        : this.translate.instant('SESSION_MODAL.WINDOW_FROM', { from: this.day(window.arrival) });
    }).join(' ; ');
  }
  


async ngOnInit(): Promise<void> {
  try {
      // Log avant le chargement des étudiants
      console.log('Before loading students, session data:', this.sessionData);
      
      await this.loadStudentsData();

      // Log après le chargement des étudiants mais avant le chargement des présences
      console.log('After loading students, before loading attendance, session data:', this.sessionData);

      this.loadAttendanceData();

      // Log après le chargement des présences
      console.log('After loading attendance, session data:', this.sessionData);
  } catch (error) {
      console.error('Error during initialization:', error);
  }

  // Log avant de vérifier si la session est terminée
  console.log('Before checking if session is finished, session data:', this.sessionData);

  this.isFinished = !!this.sessionData.isFinished;

  // Log après avoir vérifié si la session est terminée
  console.log('After checking if session is finished, session data:', this.sessionData);

  if (this.sessionData.roomId === null || this.sessionData.teacherId === null) {
      console.error('room_id or teacher_id is null in the initial session data.');
  }

  // Log final pour voir la session data complète à la fin de ngOnInit
  console.log('Final session data after ngOnInit:', this.sessionData);

  // Vérification de sessionSeriesId
  if (!this.sessionData.sessionSeriesId) {
      console.error('Session Series ID is undefined! Please ensure it is correctly set.');
  } else {
      console.log('Session Series ID:', this.sessionData.sessionSeriesId);
  }
}



 
private loadAttendanceData(): void {
    this.attendanceService.getAttendanceBySessionId(this.sessionData.id).subscribe({
        next: (attendances: Attendance[]) => {
            attendances.forEach((attendance) => {
                const existingStudent = this.sessionData.students.find((s: Student) => s.id === attendance.studentId);

                if (existingStudent) {
                    // Mettre à jour les informations de l'étudiant existant
                    existingStudent.isPresent = attendance.isPresent;
                    existingStudent.isJustified = attendance.isJustified ?? false;
                    existingStudent.isCatchUp = attendance.isCatchUp ?? existingStudent.isCatchUp;
                    existingStudent.description = attendance.description ?? '';
                    // État de facturation décidé par le serveur : il pilote le badge
                    // « à préciser », qui rend visible une séance consommée que personne ne
                    // facture encore.
                    existingStudent.catchUpBillingState = attendance.catchUpBillingState;
                } else {
                    // Ajouter uniquement les étudiants qui ne sont pas encore dans la liste
                    this.studentService.getStudentById(attendance.studentId).subscribe((student: Student) => {
                        this.sessionData.students.push({
                            ...student,
                            isPresent: attendance.isPresent,
                            isJustified: attendance.isJustified ?? false,
                            isCatchUp: attendance.isCatchUp ?? false,
                            description: attendance.description ?? '',
                            catchUpBillingState: attendance.catchUpBillingState
                        });
                    });
                }
            });
        },
        error: (error) => {
            console.error('Error fetching attendance:', error);
        }
    });
}

  

onValidateSession(): void {
  console.log('Validating session with Series ID:', this.sessionData.sessionSeriesId);

  if (!this.sessionData.sessionSeriesId) {
      console.error('Session Series ID is undefined! Please ensure it is correctly set.');
      return;
  }

  const attendanceUpdates: Attendance[] = this.sessionData.students.map((student: Student) => ({
      id: 0,
      studentId: student.id!,
      sessionId: this.sessionData.id,
      groupId: this.sessionData.groupId,
      originalGroupId: this.sessionData.groupId,
      sessionSeriesId: this.sessionData.sessionSeriesId, // Make sure this is not undefined
      isPresent: student.isPresent !== undefined ? student.isPresent : true,
      isJustified: student.isJustified !== undefined ? student.isJustified : false,
      justificationReason: student.description,
      description: student.description ?? '',
      isCatchUp: student.isCatchUp ?? false,
      paymentStatus: 'PENDING',
      dateCreation: new Date(),
      dateUpdate: new Date(),
      createdBy: 'system',
      updatedBy: 'system',
      active: true
  }));

   // Log pour vérifier les valeurs de isCatchUp
   attendanceUpdates.forEach(attendance => {
    console.log(`Student ID: ${attendance.studentId}, isCatchUp: ${attendance.isCatchUp}`);
  });

  console.log('Attendance Data:', attendanceUpdates);

  this.attendanceService.submitAttendance(attendanceUpdates).subscribe({
      next: (response) => {
          console.log('Attendance submitted successfully', response);

          this.rejectedAbsences = [];
          this.isFinished = true;
          this.loadAttendanceData();

          // Popup de succès. Le PDF n'est généré (nouvel onglet) qu'APRÈS
          // fermeture de la popup, pour que le message reste visible.
          this.dialog.open(ConfirmationDialogComponent, {
            data: {
              title: this.translate.instant('SESSION_MODAL.VALIDATE_SUCCESS_TITLE'),
              message: this.translate.instant('SESSION_MODAL.VALIDATE_SUCCESS'),
              confirmText: this.translate.instant('SESSION_MODAL.PRINT_PDF'),
              cancelText: this.translate.instant('SESSION_MODAL.CLOSE'),
              confirmColor: 'primary'
            }
          }).afterClosed().subscribe((printConfirmed: boolean) => {
            // Génération de la feuille de présence PDF (rendu « rempli »).
            if (printConfirmed) {
              this.printAttendanceSheet(true);
            }
            this.markSessionAsFinished();
          });
      },
      error: (error: unknown) => {
          console.error('Failed to submit attendance', error);
          if (error instanceof AttendanceSubmissionError && error.outsideWindow) {
            // Refus entier : chaque ligne en cause est nommée, à retirer en une action (7.5).
            this.rejectedAbsences = error.rejected;
            return;
          }
          this.showErrorMessage(error instanceof AttendanceSubmissionError
            ? error.message : this.translate.instant('SESSION_MODAL.VALIDATE_ERROR'));
      }
  });
}

  /**
   * Retire de la feuille les absences refusées, en une action, avant de revalider (exigence 7.5).
   *
   * <p>Seules les lignes nommées par le serveur partent : un élève non concerné par la séance n'a
   * pas à y être noté absent. La validation reste à refaire, la feuille sous les yeux.</p>
   */
  removeRejectedLines(): void {
    const rejected = new Set(this.rejectedAbsences.map(line => line.studentId));
    this.sessionData.students = this.sessionData.students
      .filter(student => !rejected.has(student.id as number)) as typeof this.sessionData.students;
    this.rejectedAbsences = [];
  }

  private showErrorMessage(message: string): void {
    this.snackBar.open(message, 'OK', {
      duration: 4000,
      panelClass: ['snack-bar-error']
    });
  }

  
  

  private markSessionAsFinished(): void {
    this.sessionService.markSessionAsFinished(this.sessionData.id).subscribe({
      next: (response) => {
        console.log('Session marked as finished', response);
        this.isFinished = true;
        this.dialogRef.close({ isFinished: true });
      },
      error: (error) => {
        console.error('Failed to mark session as finished', error);
        alert(error.message);
      }
    });
  }

  toggleAllStudents(isChecked: boolean): void {
    if (this.isFinished) return; // Prevent changing if session is finished
    this.sessionData.students.forEach((student) => student.isPresent = isChecked);
  }

  /**
   * Dévalide une séance validée par erreur (spec admin-corrections, D.3 ; exigence 10.1).
   *
   * <p>Le dialogue commun aux corrections : Motif, Aperçu, confirmation. L'Aperçu nomme chaque
   * ligne retirée et ce qui change sur le dû de chaque élève, par exemple « Séance du 07/01/2030
   * (Math 1ère A) dévalidée : 2 lignes retirées », janvier d'Amine 2 000 → 0 DA dû à ce jour. Le
   * serveur fait tout en une opération : séance de nouveau à valider, lignes désactivées, Trace.</p>
   *
   * <p>La modale reste ouverte, la feuille rechargée depuis le serveur : on dévalide pour refaire
   * la feuille, et les lignes désactivées ne doivent pas y rester cochées.</p>
   */
  onUnvalidateSession(): void {
    this.sessionService.getUnvalidationReasons().subscribe({
      next: reasons => this.openUnvalidation(reasons),
      error: (error: unknown) => this.showErrorMessage(error instanceof Error && error.message
        ? error.message : this.translate.instant('SESSION_MODAL.UNVALIDATE_ERROR'))
    });
  }

  private openUnvalidation(reasons: CorrectionReasonType[]): void {
    const data: CorrectionDialogData<SessionUnvalidation> = {
      titleKey: 'SESSION_MODAL.UNVALIDATE_TITLE',
      subject: this.translate.instant('SESSION_MODAL.UNVALIDATE_SUBJECT', {
        day: formatCalendarDay(calendarDayOf(new Date(this.sessionData.sessionTimeStart))),
        group: this.sessionData.groupName ?? ''
      }),
      reasons,
      run: (step: CorrectionStep, reason: CorrectionReason, token?: string) =>
        this.sessionService.unvalidate(this.sessionData.id, step, reason, token)
    };
    this.dialog.open<CorrectionDialogComponent<SessionUnvalidation>, CorrectionDialogData<SessionUnvalidation>,
      CorrectionDialogResult<SessionUnvalidation>>(CorrectionDialogComponent, { data, width: '660px', maxWidth: '95vw' })
      .afterClosed().subscribe(outcome => {
        if (outcome?.kind === 'confirmed') {
          void this.afterUnvalidation(outcome.result);
        }
      });
  }

  /**
   * La séance est de nouveau à valider. L'état est aussi porté par les données reçues : l'écran qui a
   * ouvert la modale le relit à la fermeture, quelle qu'elle soit (bouton, Échap, clic au dehors).
   */
  private async afterUnvalidation(result: SessionUnvalidation): Promise<void> {
    this.isFinished = false;
    this.sessionData.isFinished = false;
    this.rejectedAbsences = [];
    this.rollCall = null;
    this.rollCallError = false;
    this.sessionData.students = [];
    this.snackBar.open(
      this.translate.instant('SESSION_MODAL.UNVALIDATE_SUCCESS', { count: result.removedLines }),
      this.translate.instant('common.close'),
      { duration: 4000, panelClass: ['snack-bar-success'] }
    );
    await this.loadStudentsData();
    this.loadAttendanceData();
  }


/**
 * Supprime la séance après confirmation.
 *
 * <p>La suppression passe par la désactivation côté backend : les présences et les
 * <strong>détails de paiement</strong> rattachés sont désactivés en même temps. Une
 * suppression définitive les laisserait actifs et faussterait les montants dus.</p>
 *
 * <p>Le dialogue se referme en renvoyant {@code 'deleted'} afin que l'appelant (calendrier)
 * rafraîchisse son affichage.</p>
 */
onDeleteSession(): void {
  this.dialog.open(ConfirmationDialogComponent, {
    data: {
      title: this.translate.instant('SESSION_MODAL.DELETE_TITLE'),
      message: this.translate.instant('SESSION_MODAL.DELETE_MESSAGE'),
      confirmText: this.translate.instant('SESSION_MODAL.DELETE_CONFIRM'),
      cancelText: this.translate.instant('common.cancel'),
      confirmColor: 'warn'
    }
  }).afterClosed().subscribe((confirmed: boolean) => {
    if (!confirmed) {
      return;
    }

    this.sessionService.deactivateSession(this.sessionData.id).subscribe({
      next: () => {
        this.snackBar.open(
          this.translate.instant('SESSION_MODAL.DELETE_SUCCESS'),
          this.translate.instant('common.close'),
          { duration: 3000, panelClass: ['snack-bar-success'] }
        );
        this.dialogRef.close('deleted');
      },
      error: (error) => {
        console.error('Failed to delete session:', error);
        this.snackBar.open(
          error?.error?.message || this.translate.instant('SESSION_MODAL.DELETE_ERROR'),
          this.translate.instant('common.close'),
          { duration: 5000, panelClass: ['snack-bar-error'] }
        );
      }
    });
  });
}

openAddStudentDialog(): void {
  const existingStudentIds = this.sessionData.students.map(student => student.id);

  // Le niveau alimente la liste des candidats ; les membres du groupe servent à savoir
  // si l'ajout est un rattrapage ou un simple oubli de la feuille de présence.
  forkJoin({
    levelId: this.groupService.getLevelIdByGroupId(this.sessionData.groupId),
    groupStudents: this.groupService.getStudentsByGroupId(this.sessionData.groupId)
  }).subscribe({
    next: ({ levelId, groupStudents }) => {
      if (levelId === undefined) {
        console.error('Level ID is undefined for group ID:', this.sessionData.groupId);
        return;
      }

      const groupMemberIds = (groupStudents || [])
        .map(student => student.id)
        .filter((id): id is number => id !== undefined);

      const dialogRef = this.dialog.open(AddStudentDialogComponent, {
        width: '520px',
        maxWidth: '95vw',
        maxHeight: '90vh',
        autoFocus: false,
        data: {
          groupId: this.sessionData.groupId,
          levelId: levelId,
          existingStudentIds: existingStudentIds,
          groupMemberIds: groupMemberIds
        }
      });

      dialogRef.afterClosed().subscribe((selectedStudent: Student | null) => {
        if (selectedStudent) {
          // Un rattrapage, c'est une séance suivie dans un groupe dont l'étudiant n'est
          // pas membre. S'il est inscrit à ce groupe, la présence est ordinaire : la
          // marquer en rattrapage ferait basculer toute la série en calcul rattrapage.
          //
          // Ce drapeau ne dit QUE cela : « suivie hors de son groupe ». Il ne décide plus de
          // la facturation. C'est le serveur qui classe la présence à l'enregistrement — vrai
          // rattrapage à préciser, ou séance facturée sur place — car lui seul connaît les
          // inscriptions de l'étudiant. Le client décidait auparavant, sans jamais demander
          // quelle séance était rattrapée : la séance finissait facturée au groupe d'accueil
          // sans que personne ne l'ait choisi.
          const isGroupMember = groupMemberIds.includes(selectedStudent.id as number);

          this.sessionData.students.push({
            ...selectedStudent,
            isPresent: true,
            description: '',
            isCatchUp: !isGroupMember
          });
        }
      });
    },
    error: (error) => {
      console.error('Failed to prepare add-student dialog:', error);
    }
  });
}


  onEditSession(): void {
    const editSessionData: Session = {
      id: this.sessionData.id,
      title: this.sessionData.title,
      sessionType: this.sessionData.sessionType,
      groupId: this.sessionData.groupId,
      roomId: this.sessionData.roomId ?? null,  // Assurez-vous que la valeur est bien assignée
      teacherId: this.sessionData.teacherId ?? null,  // Assurez-vous que la valeur est bien assignée
      groupName: this.sessionData.groupName ?? '',
      roomName: this.sessionData.roomName ?? '',
      teacherName: this.sessionData.teacherName ?? '',
      sessionTimeStart: this.sessionData.sessionTimeStart,
      sessionTimeEnd: this.sessionData.sessionTimeEnd,
      students: this.sessionData.students
    };
  
    console.log('Edit Session Data:', editSessionData);
  
    const dialogRef = this.dialog.open(EditSessionDialogComponent, {
       backdropClass: '',
       panelClass: 'custom-dialog-container',
      width: '700px',
      data: { session: editSessionData }
    });
  
    dialogRef.afterClosed().subscribe((updatedSession: Session | null) => {
      if (updatedSession) {
        Object.assign(this.sessionData, updatedSession);
      }
    });
  }

  onPresentChange(student: Student): void {
    if (student.isPresent) {
        student.isJustified = false;  // Désélectionne isJustified si isPresent est coché
    }
}

  onJustifiedChange(student: Student): void {
    if (student.isJustified) {
        student.isPresent = false;  // Désélectionne isPresent si isJustified est coché
    }
}

  /**
   * Déclenché par le bouton « Imprimer la présence ».
   * Fonctionne avant validation (feuille vierge à cocher à la main) et après
   * validation (présence/justification remplies automatiquement).
   */
  onPrintAttendanceSheet(): void {
    this.printAttendanceSheet(this.isFinished);
  }

  /**
   * Génère la feuille de présence PDF.
   * @param validated état de validation à refléter dans le rendu (rempli vs vierge)
   */
  private printAttendanceSheet(validated: boolean): void {
    const students: SessionAttendanceStudentRow[] = ((this.sessionData.students ?? []) as Student[]).map((student: Student) => ({
      fullName: `${student.firstName ?? ''} ${student.lastName ?? ''}`.trim(),
      isPresent: student.isPresent,
      isJustified: student.isJustified,
      isCatchUp: student.isCatchUp,
      note: student.description
    }));

    this.sessionAttendancePdfService.generateAttendanceSheet(
      {
        title: this.sessionData.title,
        sessionType: this.sessionData.sessionType,
        groupName: this.sessionData.groupName,
        roomName: this.sessionData.roomName,
        teacherName: this.sessionData.teacherName,
        sessionTimeStart: this.sessionData.sessionTimeStart,
        sessionTimeEnd: this.sessionData.sessionTimeEnd,
        isFinished: validated
      },
      students
    );
  }

}
