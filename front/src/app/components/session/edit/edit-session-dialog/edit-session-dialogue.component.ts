import { Component, Inject, OnInit } from '@angular/core';
import { MatDialog, MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { DatePipe } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { SummaryDialogComponent } from '../../../summary-dialog/summary-dialog.component';
import { FormBuilder, FormGroup } from '@angular/forms';
import { SessionService } from '../../../../services/SessionService';
import { TeacherService } from '../../../../services/teacher.service';
import { GroupService } from '../../../../services/group.service';
import { RoomService } from '../../../../services/room.service';
import { OverlayContainer } from '@angular/cdk/overlay';
import { CommonModule } from '@angular/common';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';
import { MatSelectModule } from '@angular/material/select';
import { ReactiveFormsModule } from '@angular/forms';
import { DateAdapter, MAT_DATE_FORMATS, MAT_DATE_LOCALE, NativeDateAdapter } from '@angular/material/core';
import { Session } from '../../../../models/session/session';
import { forkJoin } from 'rxjs';
import { Group } from '../../../../models/group/group';
import { Room } from '../../../../models/room/room';
import { Teacher } from '../../../../models/teacher/teacher';
import { AdminOnlyDirective } from '../../../../shared/admin-only.directive';
import { resolveLocale } from '../../../../shared/locale';

export const MY_DATE_FORMATS = {
  parse: {
    dateInput: 'DD/MM/YYYY',
  },
  display: {
    dateInput: 'DD/MM/YYYY',
    monthYearLabel: 'MMMM YYYY',
    dateA11yLabel: 'LL',
    monthYearA11yLabel: 'MMMM YYYY',
  },
};

@Component({
  selector: 'app-edit-session-dialog',
  templateUrl: './edit-session-dialog.component.html',
  styleUrls: ['./edit-session-dialog.component.scss'],
  standalone: true,
  imports: [
    CommonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatDatepickerModule,
    ReactiveFormsModule,
    MatInputModule,
    MatButtonModule,
    MatSelectModule,
    MatIconModule,
    MatTabsModule,
    MatSnackBarModule,
    TranslateModule,
    AdminOnlyDirective
  ],
  providers: [
    { provide: DateAdapter, useClass: NativeDateAdapter },
    { provide: MAT_DATE_LOCALE, useValue: 'en-GB' },
    { provide: MAT_DATE_FORMATS, useValue: MY_DATE_FORMATS }
  ]
})
export class EditSessionDialogComponent implements OnInit {
  sessionForm!: FormGroup;
  groups: Group[] = [];
  rooms: Room[] = [];
  teachers: Teacher[] = [];
  hours: string[] = [];
  minutes: string[] = [];
  periods: string[] = ['AM', 'PM'];

  /**
   * Types de séance proposés, alignés sur le formulaire de création. Ce champ était un
   * texte libre en modification : n'importe quelle valeur pouvait être enregistrée.
   */
  sessionTypes: { value: string; labelKey: string }[] = [
    { value: 'COURS', labelKey: 'sessionForm.sessionTypes.COURS' },
    { value: 'EXERCICES', labelKey: 'sessionForm.sessionTypes.EXERCICES' },
    { value: 'EXAMEN', labelKey: 'sessionForm.sessionTypes.EXAMEN' },
    { value: 'REVISION', labelKey: 'sessionForm.sessionTypes.REVISION' },
    { value: 'AUTRE', labelKey: 'sessionForm.sessionTypes.AUTRE' }
  ];

  /** Formateur de dates du récapitulatif, aligné sur la langue active. */
  private datePipe: DatePipe;

  constructor(
    public dialogRef: MatDialogRef<EditSessionDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { session: Session },
    private fb: FormBuilder,
    private sessionService: SessionService,
    private groupService: GroupService,
    private roomService: RoomService,
    private teacherService: TeacherService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
    private translate: TranslateService,
    private overlayContainer: OverlayContainer  // OverlayContainer for managing overlays
  ) {
    // Initialize hour and minute options
    this.datePipe = new DatePipe(resolveLocale(this.translate.currentLang));
    this.translate.onLangChange.subscribe(({ lang }) => (this.datePipe = new DatePipe(resolveLocale(lang))));
    this.hours = Array.from({ length: 12 }, (_, i) => (i + 1).toString().padStart(2, '0')); // 01-12
    this.minutes = Array.from({ length: 60 }, (_, i) => i.toString().padStart(2, '0')); // 00-59
  }

  ngOnInit(): void {
    // Set a custom class on the overlay container to handle high z-index issues
    this.overlayContainer.getContainerElement().classList.add('custom-timepicker-overlay');

    const { groupId, roomId, teacherId } = this.data.session;
    const sessionTimeStart = new Date(this.data.session.sessionTimeStart);
    const sessionTimeEnd = new Date(this.data.session.sessionTimeEnd);

    this.sessionForm = this.fb.group({
      sessionDetails: this.fb.group({
        title: [this.data.session.title],
        description: [this.data.session.description],
        sessionType: [this.data.session.sessionType]
      }),
      sessionTiming: this.fb.group({
        sessionDateStart: [this.formatDateToLocal(sessionTimeStart)],
        startHour: [this.formatHour(sessionTimeStart.getHours())],
        startMinute: [this.formatMinute(sessionTimeStart.getMinutes())],
        startPeriod: [this.getPeriod(sessionTimeStart.getHours())],
        sessionDateEnd: [this.formatDateToLocal(sessionTimeEnd)],
        endHour: [this.formatHour(sessionTimeEnd.getHours())],
        endMinute: [this.formatMinute(sessionTimeEnd.getMinutes())],
        endPeriod: [this.getPeriod(sessionTimeEnd.getHours())]
      }),
      identifiers: this.fb.group({
        groupId: [groupId],
        roomId: [roomId],
        teacherId: [teacherId]
      })
    });

    if (groupId && roomId && teacherId) {
      forkJoin([
        this.groupService.getGroups(),
        this.roomService.getRooms(),
        this.teacherService.getTeachers()
      ]).subscribe(([groups, rooms, teachers]) => {
        this.groups = groups;
        this.rooms = rooms;
        this.teachers = teachers;
      }, error => {
        console.error('Error fetching related entities:', error);
      });
    } else {
      console.error('Missing IDs in session data:', { groupId, roomId, teacherId });
    }
  }

  formatHour(hour: number): string {
    return ((hour % 12) || 12).toString().padStart(2, '0');
  }

  formatMinute(minute: number): string {
    return minute.toString().padStart(2, '0');
  }

  getPeriod(hour: number): string {
    return hour >= 12 ? 'PM' : 'AM';
  }

  onSave(): void {
    if (this.sessionForm.valid) {
      const formValues = this.sessionForm.value;

      // Log the form values for debugging
      console.log('Form Values:', formValues);

      const startHour24 = this.convertTo24Hour(formValues.sessionTiming.startHour, formValues.sessionTiming.startPeriod);
      const endHour24 = this.convertTo24Hour(formValues.sessionTiming.endHour, formValues.sessionTiming.endPeriod);

      const sessionStartDateTime = new Date(formValues.sessionTiming.sessionDateStart);
      sessionStartDateTime.setHours(startHour24, +formValues.sessionTiming.startMinute);

      // Log the calculated session start date and time
      console.log('Session Start DateTime:', sessionStartDateTime);

      const sessionEndDateTime = new Date(formValues.sessionTiming.sessionDateEnd);
      sessionEndDateTime.setHours(endHour24, +formValues.sessionTiming.endMinute);

      // Log the calculated session end date and time
      console.log('Session End DateTime:', sessionEndDateTime);

      const updatedSession: Session = {
        ...this.data.session,
        title: formValues.sessionDetails.title,
        description: formValues.sessionDetails.description,
        sessionType: formValues.sessionDetails.sessionType,
        sessionTimeStart: sessionStartDateTime,
        sessionTimeEnd: sessionEndDateTime,
        groupId: formValues.identifiers.groupId,
        groupName: this.getGroupNameById(formValues.identifiers.groupId),
        roomName: this.getRoomNameById(formValues.identifiers.roomId),
        teacherName: this.getTeacherNameById(formValues.identifiers.teacherId),
        roomId: formValues.identifiers.roomId,
        teacherId: formValues.identifiers.teacherId,
        date_update: new Date(),
      };

      // Récapitulatif avant écriture en base : la modification d'une séance touche à la
      // planification et à l'affectation (groupe, salle, enseignant). On fait relire les
      // valeurs résolues (noms, dates formatées) plutôt que des identifiants bruts.
      this.dialog.open(SummaryDialogComponent, {
        width: '520px',
        maxWidth: '95vw',
        data: this.buildSummary(updatedSession)
      }).afterClosed().subscribe((confirmed: boolean) => {
        if (!confirmed) {
          return;
        }

        // On n'envoie que les champs réellement modifiables. Diffuser la séance complète
        // faisait partir des champs dérivés (`groupName`) et imbriqués (`group`,
        // `students`) que le serveur doit ignorer : le patch échouait alors en bloc.
        const patch = {
          title: updatedSession.title,
          description: updatedSession.description,
          sessionType: updatedSession.sessionType,
          sessionTimeStart: updatedSession.sessionTimeStart,
          sessionTimeEnd: updatedSession.sessionTimeEnd,
          groupId: updatedSession.groupId,
          roomId: updatedSession.roomId,
          teacherId: updatedSession.teacherId
        };

        this.sessionService.updateSession(updatedSession.id, patch).subscribe({
          next: () => {
            this.dialogRef.close(updatedSession);
          },
          error: (error) => {
            console.error('Error updating session:', error);
            this.snackBar.open(
              this.translate.instant('sessionForm.messages.updateError'),
              this.translate.instant('common.ok'),
              { duration: 4000, panelClass: ['snack-bar-error'] }
            );
          }
        });
      });
    } else {
      this.sessionForm.markAllAsTouched();
      this.snackBar.open(
        this.translate.instant('sessionForm.messages.invalid'),
        this.translate.instant('common.ok'),
        { duration: 3000, panelClass: ['snack-bar-error'] }
      );
    }
  }

  /**
   * Construit le récapitulatif attendu par {@link SummaryDialogComponent} : une liste
   * d'entrées « Section - champ ». Les identifiants sont remplacés par les libellés et les
   * dates/heures sont formatées, afin que la relecture soit réellement utile.
   */
  private buildSummary(session: Session): { label: string; value: any }[] {
    const dateTime = (value: Date) =>
      this.datePipe.transform(value, 'EEEE d MMMM y, HH:mm') ?? '';

    return [
      { label: 'sessionDetails - title', value: session.title },
      { label: 'sessionDetails - sessionType', value: session.sessionType },
      { label: 'sessionDetails - description', value: session.description },
      { label: 'sessionTiming - start', value: dateTime(session.sessionTimeStart as Date) },
      { label: 'sessionTiming - end', value: dateTime(session.sessionTimeEnd as Date) },
      { label: 'sessionTiming - duration', value: this.formatDuration(session) },
      { label: 'identifiers - group', value: session.groupName },
      { label: 'identifiers - room', value: session.roomName },
      { label: 'identifiers - teacher', value: session.teacherName }
    ];
  }

  /** Durée de la séance en heures et minutes (vide si les bornes sont incohérentes). */
  private formatDuration(session: Session): string {
    const start = new Date(session.sessionTimeStart as Date).getTime();
    const end = new Date(session.sessionTimeEnd as Date).getTime();
    const minutes = Math.round((end - start) / 60000);
    if (!Number.isFinite(minutes) || minutes <= 0) {
      return '';
    }
    const hours = Math.floor(minutes / 60);
    const rest = minutes % 60;
    return hours > 0 ? `${hours} h${rest ? ' ' + rest : ''}` : `${rest} min`;
  }

  private convertTo24Hour(hour: string, period: string): number {
    let hourNumber = +hour;
    if (period === 'PM' && hourNumber < 12) {
      hourNumber += 12;
    } else if (period === 'AM' && hourNumber === 12) {
      hourNumber = 0;
    }
    return hourNumber;
  }

  private formatDateToLocal(date: Date): string {
    const localDate = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
    return localDate.toISOString().split('T')[0];
  }

  private getGroupNameById(id: number): string {
    const group = this.groups.find(g => g.id === id);
    return group ? group.name : '';
  }

  private getRoomNameById(id: number): string {
    const room = this.rooms.find(r => r.id === id);
    return room ? room.name : '';
  }

  private getTeacherNameById(id: number): string {
    const teacher = this.teachers.find(t => t.id === id);
    return teacher ? `${teacher.firstName} ${teacher.lastName}` : '';
  }
  onCancel(): void {
    this.dialogRef.close();
  }
}
