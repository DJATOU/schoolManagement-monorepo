import { Component, OnInit, AfterViewInit, ViewChild, ViewEncapsulation, HostListener } from '@angular/core';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { FullCalendarComponent, FullCalendarModule } from '@fullcalendar/angular';
import dayGridPlugin from '@fullcalendar/daygrid';
import timeGridPlugin from '@fullcalendar/timegrid';
import interactionPlugin from '@fullcalendar/interaction';
import listPlugin from '@fullcalendar/list';
import { SessionService } from '../../../services/SessionService';
import { SessionModalComponent } from '../session-modal/session-modal.component';
import { MatButtonModule } from '@angular/material/button';
import { CalendarOptions, EventClickArg, EventInput } from '@fullcalendar/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatOptionModule } from '@angular/material/core';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatChipsModule } from '@angular/material/chips';
import { CommonModule } from '@angular/common';
import { MatMenuModule } from '@angular/material/menu';
import { TranslateService } from '@ngx-translate/core';
import { GroupService } from '../../../services/group.service';
import { LevelService } from '../../../services/level.service';
import { SubjectService } from '../../../services/subject.service';
import { Group } from '../../../models/group/group';
import { Level } from '../../../models/level/level';
import { Subject as SubjectModel } from '../../../models/subject/subject';

@Component({
  selector: 'app-calendar',
  templateUrl: './calendar.component.html',
  styleUrls: ['./calendar.component.scss'],
  encapsulation: ViewEncapsulation.None,
  imports: [
    FullCalendarModule,
    MatDialogModule,
    MatButtonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatSelectModule,
    MatOptionModule,
    MatIconModule,
    MatTooltipModule,
    MatChipsModule,
    CommonModule,
    MatMenuModule,
  ],
  standalone: true
})
export class CalendarComponent implements OnInit, AfterViewInit {
  @ViewChild('fullcalendar') calendarComponent: FullCalendarComponent | undefined;
  calendarOptions: CalendarOptions | undefined;

  // Filtres
  selectedLevel = new FormControl(0);
  selectedSubject = new FormControl(0);
  selectedGroup = new FormControl(0);

  levels: Level[] = [];
  subjects: SubjectModel[] = [];
  private allGroups: Group[] = [];
  filteredGroups: Group[] = [];

  // Panneau de filtres repliable (fermé par défaut pour libérer la hauteur)
  filtersOpen = false;

  private readonly allGroupsOption: Group = {
    id: 0, name: 'Tous les groupes', groupTypeId: 0, levelId: 0,
    subjectId: 0, sessionNumberPerSerie: 0, priceId: 0, teacherId: 0
  };

  private eventsSubject = new Subject<{ groupId: number | null, startStr: string, endStr: string, successCallback: (events: EventInput[]) => void, failureCallback: (error: Error) => void }>();

  constructor(
    private sessionService: SessionService,
    public dialog: MatDialog,
    private translate: TranslateService,
    private groupService: GroupService,
    private levelService: LevelService,
    private subjectService: SubjectService
  ) {}

  ngOnInit() {
    this.eventsSubject.pipe(
      debounceTime(300)
    ).subscribe(({ groupId, startStr, endStr, successCallback, failureCallback }) => {
      this.loadEvents(groupId, startStr, endStr, successCallback, failureCallback);
    });

    this.calendarOptions = {
      plugins: [dayGridPlugin, timeGridPlugin, interactionPlugin, listPlugin],
      initialView: 'dayGridMonth',
      locale: this.translate.currentLang,
      // Le calendrier remplit toute la hauteur dispo et étire ses lignes :
      // pas d'ascenseur interne, adaptatif sur tout écran.
      height: '100%',
      expandRows: true,
      dayMaxEvents: true,
      headerToolbar: {
        left: 'prev,next today',
        center: 'title',
        right: 'dayGridMonth,timeGridWeek,timeGridDay,listMonth'
      },
      buttonText: {
        today: this.translate.instant('calendar.buttons.today'),
        month: this.translate.instant('calendar.buttons.month'),
        week: this.translate.instant('calendar.buttons.week'),
        day: this.translate.instant('calendar.buttons.day'),
        listMonth: this.translate.instant('calendar.buttons.list')
      },
      events: (fetchInfo, successCallback, failureCallback) => {
        const groupId = this.selectedGroup.value === 0 ? null : this.selectedGroup.value;
        this.eventsSubject.next({ groupId, startStr: fetchInfo.startStr, endStr: fetchInfo.endStr, successCallback, failureCallback });
      },
      eventClick: this.handleEventClick.bind(this),
      eventTimeFormat: {
        hour: '2-digit',
        minute: '2-digit',
        hour12: false
      }
    };

    this.translate.onLangChange.subscribe(({ lang }) => {
      if (this.calendarOptions) {
        this.calendarOptions = {
          ...this.calendarOptions,
          locale: lang,
          buttonText: {
            today: this.translate.instant('calendar.buttons.today'),
            month: this.translate.instant('calendar.buttons.month'),
            week: this.translate.instant('calendar.buttons.week'),
            day: this.translate.instant('calendar.buttons.day'),
            listMonth: this.translate.instant('calendar.buttons.list')
          }
        };
        this.refreshEvents();
      }
    });

    this.loadFilterData();

    // Niveau / Matière filtrent la liste des groupes (cascade).
    this.selectedLevel.valueChanges.subscribe(() => this.applyGroupFilter());
    this.selectedSubject.valueChanges.subscribe(() => this.applyGroupFilter());

    // Le groupe sélectionné pilote le chargement des événements.
    this.selectedGroup.valueChanges.subscribe(() => this.refreshEvents());
  }

  ngAfterViewInit(): void {
    // Recalcule la taille une fois le layout flex stabilisé.
    setTimeout(() => this.calendarComponent?.getApi()?.updateSize(), 0);
  }

  @HostListener('window:resize')
  onWindowResize(): void {
    this.calendarComponent?.getApi()?.updateSize();
  }

  /**
   * Charge les niveaux, matières et groupes pour alimenter les filtres.
   */
  private loadFilterData(): void {
    this.levelService.getLevels().subscribe(levels => this.levels = levels);
    this.subjectService.getSubjects().subscribe(subjects => this.subjects = subjects);
    this.groupService.getGroups().subscribe(groups => {
      this.allGroups = groups;
      this.applyGroupFilter();
    });
  }

  /**
   * Recalcule la liste des groupes affichés selon le niveau et la matière,
   * en conservant toujours l'option "Tous les groupes" en tête.
   * Réinitialise le groupe sélectionné s'il ne fait plus partie de la liste.
   */
  private applyGroupFilter(): void {
    const levelId = this.selectedLevel.value ?? 0;
    const subjectId = this.selectedSubject.value ?? 0;

    const matching = this.allGroups.filter(g =>
      (levelId === 0 || g.levelId === levelId) &&
      (subjectId === 0 || g.subjectId === subjectId)
    );

    this.filteredGroups = [this.allGroupsOption, ...matching];

    // Si le groupe courant n'est plus dans la liste, repasser sur "Tous les groupes".
    const currentGroupId = this.selectedGroup.value ?? 0;
    if (currentGroupId !== 0 && !matching.some(g => g.id === currentGroupId)) {
      this.selectedGroup.setValue(0); // déclenche refreshEvents via valueChanges
    }
  }

  /**
   * Nom du groupe sélectionné (pour le trigger et le tooltip).
   */
  getSelectedGroupName(): string {
    const current = this.filteredGroups.find(g => g.id === this.selectedGroup.value);
    return current?.name ?? 'Tous les groupes';
  }

  /**
   * Nom du niveau sélectionné (libellé court par défaut).
   */
  getSelectedLevelName(): string {
    if ((this.selectedLevel.value ?? 0) === 0) return 'Niveau';
    return this.levels.find(l => l.id === this.selectedLevel.value)?.name ?? 'Niveau';
  }

  /**
   * Nom de la matière sélectionnée (libellé court par défaut).
   */
  getSelectedSubjectName(): string {
    if ((this.selectedSubject.value ?? 0) === 0) return 'Matière';
    return this.subjects.find(s => s.id === this.selectedSubject.value)?.name ?? 'Matière';
  }

  /**
   * Nombre de filtres actifs.
   */
  activeFiltersCount(): number {
    let count = 0;
    if ((this.selectedLevel.value ?? 0) !== 0) count++;
    if ((this.selectedSubject.value ?? 0) !== 0) count++;
    if ((this.selectedGroup.value ?? 0) !== 0) count++;
    return count;
  }

  /**
   * Ouvre / ferme le panneau de filtres et recalcule la taille du calendrier
   * (la hauteur disponible change quand le panneau s'ouvre/se ferme).
   */
  toggleFilters(): void {
    this.filtersOpen = !this.filtersOpen;
    setTimeout(() => this.calendarComponent?.getApi()?.updateSize(), 280);
  }

  /**
   * Réinitialise tous les filtres.
   */
  resetFilters(): void {
    this.selectedLevel.setValue(0);
    this.selectedSubject.setValue(0);
    this.selectedGroup.setValue(0);
  }

  private refreshEvents() {
    const calendarApi = this.calendarComponent?.getApi();
    if (calendarApi) {
      calendarApi.refetchEvents();
    }
  }

  private loadEvents(groupId: number | null, startStr: string, endStr: string, successCallback: (events: EventInput[]) => void, failureCallback: (error: Error) => void) {
    const startDate = new Date(startStr);
    const endDate = new Date(endStr);

    if (groupId !== null) {
        this.sessionService.getSessionsInDateRange(groupId, startDate, endDate).subscribe(sessions => {
            const events = sessions.map(session => {
                console.log('Generating Event for Session:', session); // Vérifiez ici les données de session

                return {
                    id: session.id.toString(), // Assurez-vous que l'ID est bien unique
                    title: session.title,
                    start: new Date(session.sessionTimeStart),
                    end: new Date(session.sessionTimeEnd),
                    extendedProps: {
                        id: session.id, // Cet ID doit être unique et correct
                        groupName: session.groupName,
                        roomName: session.roomName,
                        teacherName: session.teacherName,
                        feedbackLink: session.feedbackLink,
                        sessionType: session.sessionType,
                        groupId: session.groupId,
                        isFinished: session.isFinished,
                        sessionSeriesId: session.sessionSeriesId
                    },
                    classNames: session.isFinished ? ['is-finished'] : []
                };
            });

            console.log('Generated Events:', events); // Vérifiez que les événements ont bien des IDs uniques
            successCallback(events);
        }, error => {
            failureCallback(error);
        });
    } else {
        failureCallback(new Error('Group ID is null'));
    }
}


handleEventClick(clickInfo: EventClickArg) {
  // Log complet de l'événement cliqué pour déboguer
  console.log('Full Event Data:', clickInfo.event); 
  
  // Récupérer l'ID de la session depuis extendedProps et le convertir en nombre
  const sessionId = parseInt(clickInfo.event.extendedProps['id'], 10);
  
  // Log pour s'assurer que l'ID extrait est celui attendu
  console.log('Extracted Session ID:', sessionId);

  // Vérifier si l'ID est NaN ou invalide (<= 0)
  if (isNaN(sessionId) || sessionId <= 0) {
    console.error('Session ID is NaN or invalid:', sessionId);
    return;
  }

  // Si l'ID est valide, récupérer les données de session depuis le service
  this.sessionService.getSessionById(sessionId).subscribe({
    next: (session) => {
      // Log des données de la session récupérées pour vérification
      console.log('Session Data retrieved from service:', session);

      // Continuez avec la logique de traitement ici, par exemple l'ouverture d'une modale
      const sessionData = {
        ...session,
        students: [], // Ajouter ici la logique pour les étudiants si nécessaire
      };

      // Ouverture de la modale avec les données de session
      const dialogRef = this.dialog.open(SessionModalComponent, {
        data: sessionData,
        width: '600px',
        maxHeight: '90vh'
      });

      // Gérer les actions après la fermeture de la modale
      dialogRef.afterClosed().subscribe(result => {
        if (result && result.isFinished) {
          clickInfo.event.setProp('classNames', ['is-finished']);
          clickInfo.event.setExtendedProp('isFinished', true);
        }
      });
    },
    error: (error) => {
      // Log en cas d'erreur lors de la récupération des données de session
      console.error('Error fetching session data:', error);
    }
  });
}




  


  
}