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
import { CommonModule, DatePipe } from '@angular/common';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { NgxMaterialTimepickerModule } from 'ngx-material-timepicker';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { AdminOnlyDirective } from '../../../shared/admin-only.directive';
import { resolveLocale } from '../../../shared/locale';
import {
  RecurringConflict, RecurringSessionRequest, RecurringSessionResult, WeekDay
} from '../../../models/session/recurring-session';

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
    MatButtonModule,
    NgxMaterialTimepickerModule,
    MatCheckboxModule,
    TranslateModule,
    AdminOnlyDirective
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

  // Format d'heure des timepickers : 24h en français, 12h (AM/PM) en anglais.
  timeFormat: 12 | 24 = 24;

  /** Jours proposés pour la répétition, dans l'ordre de la semaine scolaire. */
  readonly weekDays: { value: WeekDay; labelKey: string }[] = [
    { value: 'MONDAY', labelKey: 'sessionForm.recurrence.weekDays.MONDAY' },
    { value: 'TUESDAY', labelKey: 'sessionForm.recurrence.weekDays.TUESDAY' },
    { value: 'WEDNESDAY', labelKey: 'sessionForm.recurrence.weekDays.WEDNESDAY' },
    { value: 'THURSDAY', labelKey: 'sessionForm.recurrence.weekDays.THURSDAY' },
    { value: 'FRIDAY', labelKey: 'sessionForm.recurrence.weekDays.FRIDAY' },
    { value: 'SATURDAY', labelKey: 'sessionForm.recurrence.weekDays.SATURDAY' },
    { value: 'SUNDAY', labelKey: 'sessionForm.recurrence.weekDays.SUNDAY' }
  ];

  /** Jours cochés pour la répétition. */
  selectedDays: WeekDay[] = [];

  /** Dernière simulation renvoyée par le serveur. */
  preview: RecurringSessionResult | null = null;
  previewLoading = false;

  /** Formateur de dates du récapitulatif, aligné sur la langue active. */
  private datePipe: DatePipe = new DatePipe(resolveLocale('fr'));

  // Types de session disponibles (valeur stockée + clé de traduction).
  sessionTypes: { value: string; labelKey: string }[] = [
    { value: 'COURS', labelKey: 'sessionForm.sessionTypes.COURS' },
    { value: 'EXERCICES', labelKey: 'sessionForm.sessionTypes.EXERCICES' },
    { value: 'EXAMEN', labelKey: 'sessionForm.sessionTypes.EXAMEN' },
    { value: 'REVISION', labelKey: 'sessionForm.sessionTypes.REVISION' },
    { value: 'AUTRE', labelKey: 'sessionForm.sessionTypes.AUTRE' }
  ];

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
        sessionType: ['', Validators.required]
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
      }),
      // Répétition : la date et les heures de l'onglet Planification servent de modèle,
      // seuls les jours et la date de fin sont propres à la récurrence.
      recurrence: this.fb.group({
        enabled: [false],
        until: [null],
        skipConflicts: [true]
      })
    });

    this.loadSelectOptions();
    this.setupTimingAutoFill();
    this.setupTeacherAutoFill();

    // Format d'heure selon la langue : 24h en français, 12h (AM/PM) en anglais.
    this.updateTimeFormat(this.translate.currentLang ?? this.translate.getDefaultLang());
    this.datePipe = new DatePipe(resolveLocale(this.translate.currentLang));
    this.translate.onLangChange.subscribe(({ lang }) => {
      this.updateTimeFormat(lang);
      this.datePipe = new DatePipe(resolveLocale(lang));
    });
  }

  /**
   * Choisit le format d'heure des timepickers selon la langue active :
   * 12h (AM/PM) uniquement en anglais, 24h partout ailleurs (français par
   * défaut, pas de notion AM/PM). Robuste si la langue n'est pas encore
   * résolue (currentLang undefined) : on reste en 24h.
   */
  private updateTimeFormat(lang: string | undefined): void {
    this.timeFormat = (lang ?? '').toLowerCase().startsWith('en') ? 12 : 24;
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

            // Récapitulatif avant écriture en base. Attention : prepareSubmissionData()
            // renvoie un objet *plat* ; l'ancienne version lisait
            // submissionData.sessionDetails / .sessionTiming (inexistants), si bien que le
            // résumé n'affichait que le groupe, la salle et l'enseignant.
            const dialogRef = this.dialog.open(SummaryDialogComponent, {
                width: '520px',
                maxWidth: '95vw',
                data: this.buildSummary(submissionData)
            });

            dialogRef.afterClosed().subscribe(result => {
                if (!result) {
                    return;
                }
                if (this.recurrenceEnabled) {
                    // Répétition : une seule requête, le serveur crée toutes les occurrences
                    // et les rattache aux séries du groupe.
                    this.submitRecurrence(submissionData);
                } else {
                    this.processSeriesCreationOrSubmission(submissionData);
                }
            });
        } else {
            this.sessionForm.markAllAsTouched();
            this.showErrorMessage('sessionForm.messages.invalid');
        }
    } catch (error: unknown) {
        this.handleError(error);
    }
}

/**
 * Construit le récapitulatif attendu par {@link SummaryDialogComponent} : une liste
 * d'entrées « Section - champ ». Les identifiants sont remplacés par les libellés et les
 * dates/heures sont formatées, pour que la relecture avant enregistrement soit utile.
 */
private buildSummary(data: any): { label: string; value: any }[] {
    const dateTime = (value: string) =>
        this.datePipe.transform(value, 'EEEE d MMMM y, HH:mm') ?? '';

    const summary: { label: string; value: any }[] = [
        { label: 'sessionDetails - title', value: data.title },
        { label: 'sessionDetails - sessionType', value: this.sessionTypeLabel(data.sessionType) },
        { label: 'sessionDetails - description', value: data.description },
        { label: 'sessionTiming - start', value: dateTime(data.sessionTimeStart) },
        { label: 'sessionTiming - end', value: dateTime(data.sessionTimeEnd) },
        { label: 'sessionTiming - duration', value: this.formatDuration(data) },
        { label: 'identifiers - group', value: this.getGroupNameById(data.groupId) },
        { label: 'identifiers - room', value: this.getRoomNameById(data.roomId) },
        { label: 'identifiers - teacher', value: this.getTeacherNameById(data.teacherId) }
    ];

    // La répétition change radicalement la portée de l'enregistrement : elle doit
    // apparaître dans le récapitulatif, avec le nombre de séances annoncé.
    if (this.recurrenceEnabled) {
        summary.push(
            {
                label: 'recurrence - days',
                value: this.selectedDays
                    .map(day => this.translate.instant(`sessionForm.recurrence.weekDays.${day}`))
                    .join(', ')
            },
            {
                label: 'recurrence - until',
                value: this.datePipe.transform(
                    this.sessionForm.get('recurrence.until')?.value, 'EEEE d MMMM y') ?? ''
            },
            {
                label: 'recurrence - count',
                value: this.preview
                    ? this.translate.instant('sessionForm.recurrence.previewCount',
                        { count: this.preview.created })
                    : this.translate.instant('sessionForm.recurrence.previewUnknown')
            }
        );
    }

    return summary;
}

/** Libellé traduit du type de séance (la valeur stockée est un code). */
private sessionTypeLabel(value: string): string {
    const type = this.sessionTypes.find(t => t.value === value);
    return type ? this.translate.instant(type.labelKey) : value;
}

/** Durée de la séance en heures et minutes (vide si les bornes sont incohérentes). */
private formatDuration(data: any): string {
    const minutes = Math.round(
        (new Date(data.sessionTimeEnd).getTime() - new Date(data.sessionTimeStart).getTime()) / 60000);
    if (!Number.isFinite(minutes) || minutes <= 0) {
        return '';
    }
    const hours = Math.floor(minutes / 60);
    const rest = minutes % 60;
    return hours > 0 ? `${hours} h${rest ? ' ' + rest : ''}` : `${rest} min`;
}

// =========================================================================
// Répétition
// =========================================================================

/** La répétition est-elle activée ? */
get recurrenceEnabled(): boolean {
    return !!this.sessionForm?.get('recurrence.enabled')?.value;
}

isDaySelected(day: WeekDay): boolean {
    return this.selectedDays.includes(day);
}

toggleDay(day: WeekDay): void {
    this.selectedDays = this.isDaySelected(day)
        ? this.selectedDays.filter(selected => selected !== day)
        : [...this.selectedDays, day];
    // La simulation précédente ne vaut plus rien dès que les jours changent.
    this.preview = null;
}

/** Libellé lisible d'un conflit renvoyé par le serveur (code + ressource en cause). */
conflictReason(conflict: RecurringConflict): string {
    const key = conflict.reason === 'ROOM_BUSY'
        ? 'sessionForm.recurrence.conflictRoom'
        : 'sessionForm.recurrence.conflictTeacher';
    return this.translate.instant(key, { name: conflict.detail });
}

/**
 * Demande au serveur ce que produirait la répétition, sans rien enregistrer.
 *
 * <p>La simulation vit côté serveur : lui seul connaît les créneaux déjà réservés. Créer
 * une centaine de séances sans annoncer le résultat serait irréversible en pratique.</p>
 */
onPreviewRecurrence(): void {
    const request = this.buildRecurrenceRequest();
    if (!request) {
        return;
    }

    this.previewLoading = true;
    this.sessionService.previewRecurringSessions(request).subscribe({
        next: result => {
            this.preview = result;
            this.previewLoading = false;
        },
        error: error => {
            this.preview = null;
            this.previewLoading = false;
            this.showBackendError(error, 'sessionForm.recurrence.previewError');
        }
    });
}

/** Envoie la répétition : une requête, une transaction serveur. */
private submitRecurrence(submissionData: any): void {
    const request = this.buildRecurrenceRequest(submissionData);
    if (!request) {
        return;
    }

    this.sessionService.createRecurringSessions(request).subscribe({
        next: result => {
            this.sessionForm.reset();
            this.selectedDays = [];
            this.preview = null;
            const message = result.skipped > 0
                ? this.translate.instant('sessionForm.recurrence.createdWithConflicts',
                    { count: result.created, skipped: result.skipped })
                : this.translate.instant('sessionForm.recurrence.created', { count: result.created });
            this.snackBar.open(message, this.translate.instant('common.close'), { duration: 6000 });
        },
        error: error => this.showBackendError(error, 'sessionForm.recurrence.createError')
    });
}

/**
 * Assemble la demande de récurrence à partir du formulaire.
 *
 * @returns la demande, ou {@code null} si la saisie est incomplète (un message est alors
 *          affiché)
 */
private buildRecurrenceRequest(submissionData?: any): RecurringSessionRequest | null {
    const data = submissionData ?? this.prepareSubmissionData();
    const until = this.sessionForm.get('recurrence.until')?.value;

    if (this.selectedDays.length === 0 || !until || !data.sessionTimeStart || !data.sessionTimeEnd) {
        this.showErrorMessage('sessionForm.recurrence.incomplete');
        return null;
    }

    const start = new Date(data.sessionTimeStart);
    const end = new Date(data.sessionTimeEnd);

    return {
        groupId: data.groupId,
        teacherId: data.teacherId ?? null,
        roomId: data.roomId ?? null,
        title: data.title,
        sessionType: data.sessionType,
        // La date de la séance saisie sert de première date possible de la répétition.
        startDate: this.isoDate(start),
        endDate: this.isoDate(new Date(until)),
        daysOfWeek: this.selectedDays,
        startTime: this.isoTime(start),
        endTime: this.isoTime(end),
        skipConflicts: !!this.sessionForm.get('recurrence.skipConflicts')?.value,
        numberTitles: true
    };
}

/** Date au format yyyy-MM-dd, sans décalage de fuseau. */
private isoDate(date: Date): string {
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${date.getFullYear()}-${month}-${day}`;
}

/** Heure au format HH:mm. */
private isoTime(date: Date): string {
    return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}

/** Affiche le message du serveur s'il existe, sinon un libellé traduit. */
private showBackendError(error: unknown, fallbackKey: string): void {
    const serverMessage = (error as { error?: { message?: string } })?.error?.message;
    this.snackBar.open(
        serverMessage || this.translate.instant(fallbackKey),
        this.translate.instant('common.close'),
        { duration: 6000, panelClass: ['snack-bar-error'] });
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

      this.seriesService.getSessionSeriesByGroupId(submissionData.groupId).subscribe(series => {
          console.log('Existing series for group:', series);

          this.findOrCreateSeries(submissionData, series, totalSessionsPerSeries);
      });
  });
}

private findOrCreateSeries(submissionData: any, series: any[], totalSessionsPerSeries: number): void {
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
              this.createAndAssignNewSeries(submissionData, totalSessionsPerSeries);
          }
      });
  });

  if (!series.length) {
      // Si aucune série n'existe, en créer une nouvelle
      this.createAndAssignNewSeries(submissionData, totalSessionsPerSeries);
  }
}

private createAndAssignNewSeries(submissionData: any, totalSessionsPerSeries: number): void {
    // La série est datée par la séance qui la déclenche : c'est cette date qui détermine
    // le mois du nom généré par le serveur.
    const newSeriesData = this.constructSeriesData(
        submissionData.groupId, totalSessionsPerSeries, submissionData.sessionTimeStart);

    this.seriesService.createSeries(newSeriesData).subscribe(newSeries => {
        console.log(`New series created: ${newSeries.name} with ID: ${newSeries.id}`);
        submissionData.sessionSeriesId  = newSeries.id;
        this.submitSession(submissionData);
    });
}

/**
 * Prépare la création d'une série.
 *
 * <p>La série démarre à la date de la <strong>première séance</strong> qu'elle va contenir,
 * et non à la date de création : une séance planifiée en septembre saisie en août doit
 * appartenir à la série de septembre.</p>
 *
 * <p>Le nom n'est volontairement pas composé ici. Le serveur le calcule à partir de cette
 * date de début et du numéro de séquence du groupe. L'ancienne version le fabriquait
 * localement avec {@code toLocaleString('default', { month: 'long' })} : le nom dépendait
 * donc de la langue du navigateur (« August » ou « août » selon le poste), ne pouvait plus
 * être traduit une fois enregistré, et divergeait du format du backend.</p>
 */
private constructSeriesData(groupId: number, totalSessionsPerSeries: number, firstSessionStart: string): SessionSeries {
  const start = new Date(firstSessionStart);
  const end = new Date(start);
  end.setMonth(end.getMonth() + 1);

  return {
      groupId: groupId,
      totalSessions: totalSessionsPerSeries,
      sessionsCompleted: 0,
      numberOfSessionsCreated: 0, // Initialisation à 0
      // name omis : nommage assuré par le serveur (SeriesNamingService).
      serieTimeStart: start.toISOString(),
      serieTimeEnd: end.toISOString(),
  } as SessionSeries;
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
    // Sans ce retour visible, un échec ne se voyait que dans la console : l'utilisateur
    // cliquait sur « Enregistrer » et rien ne se passait.
    this.showErrorMessage('sessionForm.messages.saveError');
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
