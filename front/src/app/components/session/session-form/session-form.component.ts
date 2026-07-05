import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';
import { SessionService } from '../../../services/SessionService';
import { TeacherService } from '../../../services/teacher.service';
import { GroupService } from '../../../services/group.service';
import { RoomService } from '../../../services/room.service';
import { SeriesService } from '../../../services/series.service';
import { SummaryDialogComponent } from '../../summary-dialog/summary-dialog.component';
import { Teacher } from '../../../models/teacher/teacher';
import { Group } from '../../../models/group/group';
import { Room } from '../../../models/room/room';
import { SessionSeries } from '../../../models/sessionSerie/sessionSerie';
import { MatTabsModule } from '@angular/material/tabs';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatIconModule } from '@angular/material/icon';
import { MatOptionModule } from '@angular/material/core';
import { MatSelectModule } from '@angular/material/select';
import { ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatCardModule } from '@angular/material/card';
import { NgxMaterialTimepickerModule } from 'ngx-material-timepicker';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

@Component({
  selector: 'app-session-form',
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
    MatTabsModule,
    MatSnackBarModule,
    CommonModule,
    MatCardModule,
    NgxMaterialTimepickerModule,
    TranslateModule
  ],
  templateUrl: './session-form.component.html',
  styleUrls: ['./session-form.component.scss'],
  providers: [SessionService]
})
export class SessionFormComponent implements OnInit {
  sessionForm!: FormGroup;
  teachers: Teacher[] = [];
  groups: Group[] = [];
  rooms: Room[] = [];
  series: SessionSeries[] = [];

  // Format d'heure des timepickers : 24h en français, 12h (AM/PM) sinon.
  timeFormat: 12 | 24 = 24;

  constructor(
    private fb: FormBuilder,
    private sessionService: SessionService,
    private teacherService: TeacherService,
    private groupService: GroupService,
    private roomService: RoomService,
    private seriesService: SeriesService,
    public dialog: MatDialog,
    private snackBar: MatSnackBar,
    private translate: TranslateService
  ) {}

  ngOnInit(): void {
    this.sessionForm = this.fb.group({
      sessionDetails: this.fb.group({
        title: ['', Validators.required],
        description: [''],
        sessionType: ['', Validators.required],
        feedbackLink: ['']
      }),
      sessionTiming: this.fb.group({
        sessionDateStart: [null, Validators.required],
        sessionTimeStart: [null, Validators.required],
        sessionDateEnd: [null, Validators.required],
        sessionTimeEnd: [null, Validators.required],
      }),
      identifiers: this.fb.group({
        groupId: [null, Validators.required],
        roomId: [null, Validators.required],
        teacherId: [null, Validators.required]
      })
    });

    this.loadSelectOptions();
    this.setupTimingAutoFill();
    this.setupTeacherAutoFill();

    // Format d'heure selon la langue : 24h en français, 12h (AM/PM) en anglais.
    this.updateTimeFormat(this.translate.currentLang);
    this.translate.onLangChange.subscribe(({ lang }) => this.updateTimeFormat(lang));
  }

  /**
   * Choisit le format d'heure des timepickers selon la langue active.
   */
  private updateTimeFormat(lang: string | undefined): void {
    this.timeFormat = (lang ?? 'fr').startsWith('fr') ? 24 : 12;
  }

  /**
   * Auto-remplissage intelligent de la planification :
   * - quand la date de début change, la date de fin prend la même valeur
   *   (une séance se termine le même jour) ;
   * - quand l'heure de début change, l'heure de fin est proposée à +2h.
   * L'utilisateur peut toujours ajuster la date/heure de fin manuellement ensuite.
   */
  private setupTimingAutoFill(): void {
    const timing = this.sessionForm.get('sessionTiming');
    const dateStart = timing?.get('sessionDateStart');
    const dateEnd = timing?.get('sessionDateEnd');
    const timeStart = timing?.get('sessionTimeStart');
    const timeEnd = timing?.get('sessionTimeEnd');

    dateStart?.valueChanges.subscribe(value => {
      if (value && !dateEnd?.value) {
        dateEnd?.setValue(value);
      }
    });

    timeStart?.valueChanges.subscribe(value => {
      if (value && !timeEnd?.value) {
        timeEnd?.setValue(this.addHours(value, 2));
      }
    });
  }

  /**
   * Ajoute un nombre d'heures à une valeur d'heure, en gérant à la fois le
   * format 24h ("18:00") et le format 12h ("06:00 PM"). Retourne dans le même
   * format que l'entrée, avec débordement sur 24h.
   */
  private addHours(time: string, hoursToAdd: number): string {
    const match = time.match(/(\d{1,2}):(\d{2})/);
    if (!match) {
      return time;
    }
    const period = time.match(/AM|PM/i)?.[0]?.toUpperCase();

    let hours = parseInt(match[1], 10);
    const minutes = parseInt(match[2], 10);

    // Normaliser en 24h pour le calcul
    if (period === 'PM' && hours !== 12) {
      hours += 12;
    } else if (period === 'AM' && hours === 12) {
      hours = 0;
    }

    const total = ((hours + hoursToAdd) * 60 + minutes + 24 * 60) % (24 * 60);
    const h24 = Math.floor(total / 60);
    const m = total % 60;
    const mm = m.toString().padStart(2, '0');

    if (period) {
      // Reformater en 12h AM/PM
      const newPeriod = h24 >= 12 ? 'PM' : 'AM';
      let h12 = h24 % 12;
      if (h12 === 0) {
        h12 = 12;
      }
      return `${h12.toString().padStart(2, '0')}:${mm} ${newPeriod}`;
    }

    return `${h24.toString().padStart(2, '0')}:${mm}`;
  }

  /**
   * Pré-remplit automatiquement l'enseignant à partir du groupe choisi
   * (le groupe est déjà lié à un enseignant lors de sa création).
   * L'utilisateur peut toujours changer manuellement ensuite.
   */
  private setupTeacherAutoFill(): void {
    const identifiers = this.sessionForm.get('identifiers');
    const groupId = identifiers?.get('groupId');
    const teacherId = identifiers?.get('teacherId');

    groupId?.valueChanges.subscribe(id => {
      const group = this.groups.find(g => g.id === id);
      if (group?.teacherId) {
        teacherId?.setValue(group.teacherId);
      }
    });
  }

  loadSelectOptions(): void {
    this.teacherService.getTeachers().subscribe(data => this.teachers = data);
    this.groupService.getGroups().subscribe(data => this.groups = data);
    this.roomService.getRooms().subscribe(data => this.rooms = data);
  }

  private combineDateTime(date: string, time: string): string {
    const [hourPart, minutePart] = time.match(/\d+/g) || [];
    const period = time.match(/AM|PM/i)?.[0];

    if (!hourPart || !minutePart) {
      throw new Error('Invalid time input format');
    }

    let hours = parseInt(hourPart, 10);
    const minutes = parseInt(minutePart, 10);
    const dateTime = new Date(date);

    if (isNaN(dateTime.getTime())) {
      throw new Error('Invalid date format');
    }

    if (period) {
      // Compat ancien format AM/PM
      if (period.toUpperCase() === 'PM' && hours !== 12) {
        hours += 12;
      } else if (period.toUpperCase() === 'AM' && hours === 12) {
        hours = 0;
      }
    }
    // Format 24h : on utilise les heures telles quelles

    dateTime.setHours(hours, minutes, 0, 0);
    return dateTime.toISOString();
  }

  onSubmit(): void {
    try {
        if (this.sessionForm.valid) {
            const submissionData = this.prepareSubmissionData();

            // Préparation des données aplatées pour le dialogue
            const flattenedData = this.flattenFormData({
                sessionDetails: submissionData.sessionDetails,
                sessionTiming: submissionData.sessionTiming,
                identifiers: {
                    group: this.getGroupNameById(submissionData.groupId),
                    room: this.getRoomNameById(submissionData.roomId),
                    teacher: this.getTeacherNameById(submissionData.teacherId),
                }
            });

            console.log('Flattened Data for dialog:', flattenedData);

            const dialogRef = this.dialog.open(SummaryDialogComponent, { data: flattenedData });

            dialogRef.afterClosed().subscribe(result => {
                if (result) {
                    console.log('Dialog confirmed, proceeding with series creation or session submission.');
                    this.processSeriesCreationOrSubmission(submissionData);
                } else {
                    console.warn('Form submission was cancelled.');
                }
            });
        } else {
            console.warn('The form is not valid.');
            this.showErrorMessage('sessionForm.messages.invalid');
        }
    } catch (error: unknown) {
        this.handleError(error);
    }
}

private prepareSubmissionData(): any {
    const formData = this.sessionForm.value;
    const startDateTime = this.combineDateTime(formData.sessionTiming.sessionDateStart, formData.sessionTiming.sessionTimeStart);
    const endDateTime = this.combineDateTime(formData.sessionTiming.sessionDateEnd, formData.sessionTiming.sessionTimeEnd);

    const submissionData = {
        ...formData.sessionDetails,
        ...formData.sessionTiming,
        sessionTimeStart: startDateTime,
        sessionTimeEnd: endDateTime,
        groupId: formData.identifiers.groupId,
        roomId: formData.identifiers.roomId,
        teacherId: formData.identifiers.teacherId,
    };

    console.log('Prepared submission data:', submissionData);
    return submissionData;
}

private processSeriesCreationOrSubmission(submissionData: any): void {
  this.groupService.getGroupById(submissionData.groupId).subscribe(group => {
      const totalSessionsPerSeries = group.sessionNumberPerSerie;
      const groupName = group.name;

      console.log(`Group Name: ${groupName}, Total Sessions per Series: ${totalSessionsPerSeries}`);

      this.seriesService.getSessionSeriesByGroupId(submissionData.groupId).subscribe(series => {
          console.log('Existing series for group:', series);

          this.findOrCreateSeries(submissionData, series, totalSessionsPerSeries, groupName);
      });
  });
}

private findOrCreateSeries(submissionData: any, series: any[], totalSessionsPerSeries: number, groupName: string): void {
  let seriesFound = false;

  series.forEach(existingSeries => {
      this.sessionService.getSessionsBySeriesId(existingSeries.id).subscribe(sessions => {
          const sessionCount = sessions.length;
          console.log(`Checking series: ${existingSeries.name} (ID: ${existingSeries.id}) - Session Count: ${sessionCount}`);

          if (sessionCount < totalSessionsPerSeries) {
              console.log(`Found available series: ${existingSeries.name} with ID: ${existingSeries.id}`);
              submissionData.sessionSeriesId  = existingSeries.id;
              this.submitSession(submissionData);
              seriesFound = true;
          }

          if (!seriesFound && existingSeries === series[series.length - 1]) {
              // Si aucune série n'a été trouvée ou toutes les séries sont pleines
              console.log('No available series found or all series are full, creating a new series.');
              this.createAndAssignNewSeries(submissionData, groupName, totalSessionsPerSeries, series.length + 1);
          }
      });
  });

  if (!series.length) {
      // Si aucune série n'existe, en créer une nouvelle
      this.createAndAssignNewSeries(submissionData, groupName, totalSessionsPerSeries, 1);
  }
}

private createAndAssignNewSeries(submissionData: any, groupName: string, totalSessionsPerSeries: number, seriesCount: number): void {
    console.log(`Creating new series as all existing series are full or none exist. Series Count: ${seriesCount}`);

    const newSeriesData = this.constructSeriesData(submissionData.groupId, totalSessionsPerSeries, groupName, seriesCount);

    this.seriesService.createSeries(newSeriesData).subscribe(newSeries => {
        console.log(`New series created: ${newSeries.name} with ID: ${newSeries.id}`);
        submissionData.sessionSeriesId  = newSeries.id;
        this.submitSession(submissionData);
    });
}

private constructSeriesData(groupId: number, totalSessionsPerSeries: number, groupName: string, seriesCount: number): SessionSeries {
  const now = new Date();
  const month = now.toLocaleString('default', { month: 'long' });
  const year = now.getFullYear();
  const seriesName = `Série ${groupName} - ${month}-${year}-${seriesCount.toString().padStart(3, '0')}`;

  console.log(`Constructing new series data: ${seriesName}`);

  return {
      groupId: groupId,
      totalSessions: totalSessionsPerSeries,
      sessionsCompleted: 0,
      numberOfSessionsCreated: 0, // Initialisation à 0
      name: seriesName,
      serieTimeStart: now.toISOString(),
      serieTimeEnd: new Date(new Date().setMonth(now.getMonth() + 1)).toISOString(),
  };
}


private submitSession(submissionData: any): void {
    console.log('Submitting session with data:', submissionData);
    this.sessionService.createSession(submissionData).subscribe({
        next: response => {
            console.log('Session created successfully:', response);
            this.sessionForm.reset();
            this.showSuccessMessage('sessionForm.messages.created');
        },
        error: (error: unknown) => {
            this.handleError(error);
        }
    });
}

private handleError(error: unknown): void {
    if (error instanceof Error) {
        console.error('Error in form submission:', error.message);
    } else {
        console.error('Error in form submission:', error);
    }
}



  getGroupNameById(id: number): string {
    const group = this.groups.find(g => g.id === id);
    return group ? group.name : '';
  }

  getRoomNameById(id: number): string {
    const room = this.rooms.find(r => r.id === id);
    return room ? room.name : '';
  }

  getTeacherNameById(id: number): string {
    const teacher = this.teachers.find(t => t.id === id);
    return teacher ? `${teacher.firstName} ${teacher.lastName}` : '';
  }

  flattenFormData(data: any, parentKey: string = ''): { label: string, value: any }[] {
    let result: { label: string, value: any }[] = [];
    Object.keys(data).forEach(key => {
      const newKey = parentKey ? `${parentKey} - ${key}` : key;
      const value = data[key];
      if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
        result = result.concat(this.flattenFormData(value, newKey));
      } else if (Array.isArray(value)) {
        result.push({ label: newKey, value: value.join(', ') });
      } else {
        result.push({ label: newKey, value: value });
      }
    });
    return result;
  }

  onClearForm(): void {
    this.sessionForm.reset();
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
