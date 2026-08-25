import { Component, OnInit } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, Router } from '@angular/router';
import { Group } from '../../../models/group/group';
import { Student } from '../../student/domain/student';
import { SessionSeries } from '../../../models/sessionSerie/sessionSerie';
import { SeriesService } from '../../../services/series.service';
import { RenameSeriesDialogComponent } from '../../serie/rename-series-dialog/rename-series-dialog.component';
import { GroupService } from '../../../services/group.service';
import { AddStudentsDialogComponent } from '../add-students-dialog/add-students-dialog.component';
import { MatCardModule } from '@angular/material/card';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { FormsModule } from '@angular/forms';
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
import { PdfExtraPage, PdfInfoRow, ProfilePdfService } from '../../../services/profile-pdf.service';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { BehaviorSubject, Observable, combineLatest } from 'rxjs';
import { map } from 'rxjs/operators';
import { SchoolYearContextService } from '../../../services/school-year-context.service';
import { AuthService } from '../../../services/auth.service';
import { SecureImageDirective } from '../../../shared/secure-image.directive';
import { HasRoleDirective } from '../../../shared/has-role.directive';
import { RevenueService } from '../../../services/revenue.service';
import { GroupRevenue, SeriesRevenue } from '../../../models/revenue/group-revenue';
import { resolveLocale } from '../../../shared/locale';

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
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    ProfileListItemComponent,
    StudentListComponent,
    TranslateModule
  ,
    SecureImageDirective,
    HasRoleDirective
  ]
})
export class GroupProfileComponent implements OnInit {
  /**
   * Vue en lecture seule (Read_Only_History) lorsque l'année scolaire
   * sélectionnée n'est pas l'année courante (Requirement 9.4), ou lorsque le groupe affiché
   * appartient lui-même à une autre année que l'année courante.
   *
   * <p>Le second cas manquait : on atteint la fiche d'un groupe d'une année passée depuis la
   * fiche d'un étudiant sans changer le sélecteur. Les commandes d'écriture restaient donc
   * actives sur un groupe figé, et l'échec n'apparaissait qu'au retour du serveur.</p>
   */
  readonly readOnly$: Observable<boolean>;

  /** Vrai lorsque le groupe affiché appartient à une année autre que l'année courante. */
  readonly groupIsPastYear$ = new BehaviorSubject<boolean>(false);

  /**
   * Désactive les commandes d'écriture (modifier / supprimer) si la vue est en
   * lecture seule (année passée) OU si l'utilisateur n'est pas ADMIN (VIEWER).
   */
  readonly writeDisabled$: Observable<boolean>;

  group: Group | null = null;
  students: Student[] = [];

  /** Étudiants retenus par le filtre de recherche (avant découpage en pages). */
  filteredStudents: Student[] = [];

  /** Étudiants réellement affichés : page courante du résultat filtré. */
  pagedStudents: Student[] = [];

  /** Terme de recherche du filtre étudiants. */
  studentFilter = '';

  /** Pagination de la liste des étudiants. */
  pageSize = 5;
  pageIndex = 0;
  readonly pageSizeOptions = [5, 10, 25, 50];

  /** Seuil au-delà duquel le champ de filtre est proposé. */
  private readonly filterThreshold = 8;

  series: SessionSeries[] = [];

  /**
   * Séries réellement affichées : page courante.
   *
   * <p>Pagination côté client, comme pour les étudiants : les séries d'un groupe sont déjà
   * toutes chargées par un seul appel, il n'y a donc rien à gagner à paginer côté serveur.</p>
   */
  pagedSeries: SessionSeries[] = [];
  seriesPageIndex = 0;
  seriesPageSize = 5;

  /** Séries d'encaissement réellement affichées : page courante. */
  pagedRevenueSeries: SeriesRevenue[] = [];
  revenuePageIndex = 0;
  revenuePageSize = 5;
  loadingGroup = true;
  loadingStudents = true;
  loadingSeries = true;

  /** Encaissements du groupe (ADMIN uniquement). */
  revenue: GroupRevenue | null = null;
  loadingRevenue = true;
  revenueError = false;
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
    private router: Router,
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
    private profilePdfService: ProfilePdfService,
    private translate: TranslateService,
    private schoolYearContext: SchoolYearContextService,
    private authService: AuthService,
    private revenueService: RevenueService,
    private seriesService: SeriesService,
  ) {
    this.readOnly$ = combineLatest([
      this.schoolYearContext.readOnly$,
      this.groupIsPastYear$,
    ]).pipe(map(([yearReadOnly, groupIsPastYear]) => yearReadOnly || groupIsPastYear));
    this.writeDisabled$ = combineLatest([
      this.readOnly$,
      this.authService.currentUser$,
    ]).pipe(map(([readOnly]) => readOnly || !this.authService.hasRole('ADMIN')));
  }

  /**
   * Génère la fiche PDF du groupe : informations et étudiants, puis les encaissements et les
   * séries sur des pages séparées.
   *
   * <p>Les encaissements ne sont inclus que pour un ADMIN : ce sont des données financières,
   * masquées à l'écran par la même règle et refusées par le serveur à un VIEWER. Le volet est
   * simplement absent du document, il n'est pas grisé.</p>
   */
  printGroupPdf(): void {
    if (!this.group) return;
    const g = this.group;

    const extraPages: PdfExtraPage[] = [];
    if (this.authService.hasRole('ADMIN')) {
      extraPages.push(this.revenuePdfPage());
    }
    extraPages.push(this.seriesPdfPage());

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
            { label: 'Année scolaire', value: g.schoolYearLabel },
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
      ]),
      extraPages
    });
  }

  /**
   * Page « Encaissements » du PDF.
   *
   * <p>Reprend les montants du relevé serveur sans les recalculer. « Encaissé » et « Attendu »
   * répondent à deux questions distinctes et restent séparés ; le trop-perçu et le reste à
   * recouvrer sont agrégés par étudiant, donc ne se compensent pas.</p>
   */
  private revenuePdfPage(): PdfExtraPage {
    const r = this.revenue;
    if (!r) {
      return {
        heading: 'Encaissements',
        subtitle: this.group?.name,
        emptyMessage: this.revenueError
          ? 'Les encaissements n\'ont pas pu être chargés.'
          : 'Aucun encaissement enregistré pour ce groupe.'
      };
    }

    const rows: PdfInfoRow[] = [
      { label: 'Encaissé (net)', value: this.amount(r.collected) },
      { label: 'Attendu', value: this.amount(r.expected) },
      { label: 'Reste à encaisser', value: this.amount(r.remaining) }
    ];
    if (r.refunded > 0) {
      rows.push({ label: 'Remboursé', value: this.amount(r.refunded) });
    }
    if (r.overpaid > 0) {
      rows.push({ label: 'Trop-perçu', value: this.amount(r.overpaid) });
    }
    if (r.unassignedToSeries > 0) {
      rows.push({ label: 'Non rattaché à une série', value: this.amount(r.unassignedToSeries) });
    }

    return {
      heading: 'Encaissements',
      subtitle: this.group?.name,
      sections: [{ heading: 'Synthèse', rows }],
      tables: [
        {
          title: 'Par série (mois de cours)',
          columns: ['#', 'Série', 'Encaissé', 'Attendu', 'Reste', 'Trop-perçu'],
          rows: r.series.map((s, i) => [
            i + 1,
            s.seriesName,
            this.amount(s.collected),
            this.amount(s.expected),
            this.amount(s.remaining),
            s.overpaid > 0 ? this.amount(s.overpaid) : ''
          ]),
          note: 'Reste et trop-perçu sont calculés étudiant par étudiant : un versement excédentaire ne compense pas le retard d\'un autre.'
        },
        {
          title: 'Par mois d\'encaissement',
          columns: ['#', 'Mois', 'Encaissé'],
          rows: r.months.map((m, i) => [i + 1, this.monthLabel(m), this.amount(m.collected)]),
          note: 'Ce qui est entré en caisse chaque mois, quelle que soit la série soldée : les totaux diffèrent donc de la ventilation par série.'
        }
      ]
    };
  }

  /** Page « Séries » du PDF : avancement de chaque série du groupe. */
  private seriesPdfPage(): PdfExtraPage {
    const dateFormat = new Intl.DateTimeFormat(resolveLocale(this.translate.currentLang));
    const formatDate = (value?: string) => (value ? dateFormat.format(new Date(value)) : '');

    return {
      heading: 'Séries',
      subtitle: this.group?.name,
      emptyMessage: 'Aucune série pour ce groupe.',
      tables: [
        {
          title: `Séries (${this.series.length})`,
          columns: ['#', 'Série', 'Début', 'Fin', 'Séances créées', 'Prévues', 'Validées'],
          rows: this.series.map((s, i) => [
            i + 1,
            s.name,
            formatDate(s.serieTimeStart),
            formatDate(s.serieTimeEnd),
            s.numberOfSessionsCreated,
            s.totalSessions,
            s.sessionsCompleted
          ])
        }
      ]
    };
  }

  /** Montant formaté avec le suffixe monétaire utilisé partout dans l'application. */
  private amount(value: number): string {
    return `${value.toLocaleString(resolveLocale(this.translate.currentLang), {
      maximumFractionDigits: 2
    }).replace(/[\u202F\u00A0]/g, ' ')} DA`;
  }

  ngOnInit(): void {
    const groupId = this.getGroupIdFromRoute();
    if (groupId) {
      this.loadGroupData(groupId);
      this.loadStudents(groupId);
      this.loadSeries(groupId);
      this.loadRevenue(groupId);
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

  /**
   * Recalcule l'URL de la photo du groupe. Nécessaire pour qu'un changement de photo soit
   * visible sans recharger la page : {@code onImageError} vide l'URL en cas d'échec, elle
   * doit donc être reconstruite. Le paramètre d'horodatage contourne le cache du navigateur.
   */
  private refreshPhotoUrl(): void {
    this.groupPhotoUrl = this.group?.photo && this.group.id
      ? `${this.groupService.getGroupPhotoUrl(this.group.id)}?t=${Date.now()}`
      : '';
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
        this.refreshPhotoUrl();
        // Un groupe rattaché à une autre année que l'année courante est figé, quelle que
        // soit l'année choisie dans le sélecteur.
        const currentYearId = this.schoolYearContext.getCurrentSchoolYear()?.id;
        this.groupIsPastYear$.next(currentYearId != null
          && group.schoolYearId != null
          && group.schoolYearId !== currentYearId);
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
        // Tri alphabétique (nom, prénom) : l'ordre renvoyé par l'API est arbitraire.
        this.students = [...students].sort((a, b) => {
          const byLast = (a.lastName || '').localeCompare(b.lastName || '', 'fr', { sensitivity: 'base' });
          return byLast !== 0
            ? byLast
            : (a.firstName || '').localeCompare(b.firstName || '', 'fr', { sensitivity: 'base' });
        });
        this.applyStudentFilter();
        this.loadingStudents = false;
      },
      error: (error) => {
        console.error('Error loading students:', error);
        this.loadingStudents = false;
      }
    });
  }


  /**
   * Charge les encaissements du groupe. Réservé à ADMIN : l'endpoint répond 403 à un
   * VIEWER, et le panneau n'est de toute façon pas rendu pour lui. On n'appelle donc
   * l'API que si le rôle le permet, pour éviter un 403 inutile dans la console.
   */
  private loadRevenue(groupId: number): void {
    if (!this.authService.hasRole('ADMIN')) {
      this.loadingRevenue = false;
      return;
    }

    this.revenueService.getGroupRevenue(groupId).subscribe({
      next: revenue => {
        this.revenue = revenue;
        this.revenuePageIndex = 0;
        this.updatePagedRevenueSeries();
        this.loadingRevenue = false;
      },
      error: error => {
        console.error('Erreur lors du chargement des encaissements :', error);
        this.revenueError = true;
        this.loadingRevenue = false;
      }
    });
  }

  /** Libellé d'un mois d'encaissement (« septembre 2026 »). */
  monthLabel(month: { year: number; month: number }): string {
    const date = new Date(month.year, month.month - 1, 1);
    return date.toLocaleDateString(resolveLocale(this.translate.currentLang),
      { month: 'long', year: 'numeric' });
  }

  /** Part encaissée d'une série, en pourcentage de l'attendu (0 si rien n'est attendu). */
  collectionRate(collected: number, expected: number): number {
    return expected > 0 ? Math.min(100, Math.round((collected / expected) * 100)) : 0;
  }

  private loadSeries(groupId: number): void {
    this.groupService.getSeriesByGroupId(groupId).subscribe({
      next: (series) => {
        // Convertir les dates LocalDateTime en objets Date
        this.series = series.map(serie => ({
          ...serie,
          dateCreation: this.convertLocalDateTimeToDate(serie.dateCreation)
        }));
        this.seriesPageIndex = 0;
        this.updatePagedSeries();
        this.loadingSeries = false;
      },
      error: (error) => {
        console.error('Error loading series:', error);
        this.loadingSeries = false;
      }
    });
  }

  /**
   * Applique le filtre de recherche sur la liste des étudiants du groupe, puis recalcule
   * la page affichée. Le filtre remet la pagination sur la première page : rester sur une
   * page devenue vide après filtrage donnerait l'impression d'une liste sans résultat.
   */
  applyStudentFilter(): void {
    const term = this.studentFilter.trim().toLowerCase();
    this.filteredStudents = !term
      ? [...this.students]
      : this.students.filter(s =>
          `${s.firstName ?? ''} ${s.lastName ?? ''}`.toLowerCase().includes(term) ||
          `${s.lastName ?? ''} ${s.firstName ?? ''}`.toLowerCase().includes(term));
    this.pageIndex = 0;
    this.updatePagedStudents();
  }

  /** Réinitialise le filtre étudiants. */
  clearStudentFilter(): void {
    this.studentFilter = '';
    this.applyStudentFilter();
  }

  /** Réagit au changement de page ou de taille de page. */
  onStudentPage(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.updatePagedStudents();
  }

  /** Découpe la liste filtrée selon la page courante. */
  private updatePagedStudents(): void {
    const start = this.pageIndex * this.pageSize;
    this.pagedStudents = this.filteredStudents.slice(start, start + this.pageSize);
  }

  /** Le champ de filtre n'est proposé qu'au-delà d'un certain nombre d'étudiants. */
  get showStudentFilter(): boolean {
    return this.students.length > this.filterThreshold;
  }

  /** Réagit au changement de page ou de taille de page sur la liste des séries. */
  onSeriesPage(event: PageEvent): void {
    this.seriesPageIndex = event.pageIndex;
    this.seriesPageSize = event.pageSize;
    this.updatePagedSeries();
  }

  /**
   * Découpe la liste des séries selon la page courante.
   *
   * <p>Les éléments de {@link pagedSeries} sont les objets de {@link series} eux-mêmes, non des
   * copies : renommer une série met donc à jour l'affichage sans recalculer la page.</p>
   */
  private updatePagedSeries(): void {
    const start = this.seriesPageIndex * this.seriesPageSize;
    this.pagedSeries = this.series.slice(start, start + this.seriesPageSize);
  }

  /** Réagit au changement de page ou de taille de page sur les encaissements par série. */
  onRevenuePage(event: PageEvent): void {
    this.revenuePageIndex = event.pageIndex;
    this.revenuePageSize = event.pageSize;
    this.updatePagedRevenueSeries();
  }

  /** Découpe les encaissements par série selon la page courante. */
  private updatePagedRevenueSeries(): void {
    const start = this.revenuePageIndex * this.revenuePageSize;
    this.pagedRevenueSeries = (this.revenue?.series ?? []).slice(start, start + this.revenuePageSize);
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
      width: '720px',
      maxWidth: '95vw',
      autoFocus: false,
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
                next: (filename: string) => {
                  // Affichage immédiat : on applique le nom de fichier renvoyé sans
                  // attendre le rechargement de la fiche.
                  this.group = { ...updatedGroup, photo: filename };
                  this.refreshPhotoUrl();
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
              this.refreshPhotoUrl();
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

  /**
   * Désactive le groupe après confirmation (suppression logique : l'historique est conservé).
   * Retour à la liste des groupes en cas de succès, la fiche n'ayant plus lieu d'être affichée.
   */
  onDisable(): void {
    if (!this.group?.id) {
      return;
    }
    const groupId = this.group.id;

    this.dialog.open(ConfirmationDialogComponent, {
      data: {
        title: this.translate.instant('CONFIRMATION_DIALOG.DISABLE_GROUP.TITLE'),
        message: this.translate.instant('CONFIRMATION_DIALOG.DISABLE_GROUP.MESSAGE'),
        confirmText: this.translate.instant('CONFIRMATION_DIALOG.DISABLE_GROUP.CONFIRM'),
        cancelText: this.translate.instant('CONFIRMATION_DIALOG.DISABLE_GROUP.CANCEL'),
        confirmColor: 'warn'
      }
    }).afterClosed().subscribe((confirmed: boolean) => {
      if (!confirmed) {
        return;
      }
      this.groupService.disableGroup(groupId).subscribe({
        next: () => {
          this.showSuccessMessage(this.translate.instant('CONFIRMATION_DIALOG.DISABLE_GROUP.SUCCESS'));
          this.router.navigate(['/group']);
        },
        error: (error) => {
          console.error('Error disabling group:', error);
          this.showErrorMessage(this.translate.instant('CONFIRMATION_DIALOG.DISABLE_GROUP.ERROR'));
        }
      });
    });
  }

  /**
   * Renomme une série après saisie du nouveau nom.
   *
   * <p>Le nom est remplacé en place dans la liste affichée plutôt que par un rechargement
   * complet du groupe : seule cette étiquette change.</p>
   */
  renameSeries(serie: SessionSeries): void {
    if (serie.id === undefined) {
      return;
    }
    const seriesId = serie.id;

    this.dialog.open(RenameSeriesDialogComponent, {
      width: '420px',
      maxWidth: '95vw',
      data: { currentName: serie.name ?? '' }
    }).afterClosed().subscribe((newName: string | undefined) => {
      if (!newName) {
        return;
      }
      this.seriesService.renameSeries(seriesId, newName).subscribe({
        next: (updated) => {
          serie.name = updated?.name ?? newName;
          this.showSuccessMessage(this.translate.instant('series.rename.success'));
        },
        error: (error) => {
          console.error('Error renaming series:', error);
          this.showErrorMessage(this.translate.instant('series.rename.error'));
        }
      });
    });
  }

  /** Ouvre le détail d'une série (liste de ses sessions). */
  openSeries(serie: SessionSeries): void {
    if (!this.group?.id || serie.id === undefined) {
      return;
    }
    this.router.navigate(['/group', this.group.id, 'series', serie.id]);
  }

  removeStudentFromGroup(student: Student): void {
    this.dialog.open(ConfirmationDialogComponent, {
      data: {
        title: this.translate.instant('CONFIRMATION_DIALOG.REMOVE_STUDENT_FROM_GROUP.TITLE'),
        message: this.translate.instant('CONFIRMATION_DIALOG.REMOVE_STUDENT_FROM_GROUP.MESSAGE'),
        confirmText: this.translate.instant('CONFIRMATION_DIALOG.REMOVE_STUDENT_FROM_GROUP.CONFIRM'),
        cancelText: this.translate.instant('CONFIRMATION_DIALOG.REMOVE_STUDENT_FROM_GROUP.CANCEL'),
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
