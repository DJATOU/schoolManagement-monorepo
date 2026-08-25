import { CommonModule } from '@angular/common';
import { Component, OnInit, ViewChild } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { HttpClient, HttpParams } from '@angular/common/http';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { MatPaginator, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { API_BASE_URL } from '../../../api-base-url';
import { AdminOnlyDirective } from '../../../shared/admin-only.directive';
import { resolveLocale } from '../../../shared/locale';
import { GroupService } from '../../../services/group.service';
import { EditPaymentDetailDialogComponent } from './dialogs/edit-payment-detail-dialog.component';
import { PaymentDetailHistoryDialogComponent } from './dialogs/payment-detail-history-dialog.component';
import { ReasonDialogComponent, ReasonDialogData } from './dialogs/reason-dialog.component';
import { LevelService } from '../../../services/level.service';
import { SessionService } from '../../../services/SessionService';
import { SeriesService } from '../../../services/series.service';
import { finalize } from 'rxjs';

interface PaymentDetailView {
  id: number;
  studentFirstName: string;
  studentLastName: string;
  studentId: number;
  groupName: string;
  groupId: number;
  seriesName?: string;
  seriesId?: number;
  sessionName?: string;
  sessionId?: number;
  amountPaid: number;
  active: boolean;
  permanentlyDeleted?: boolean;
  dateCreation?: Date;
  paymentDate?: Date;
  paymentId?: number;
  paymentStatus?: string;
  isCatchUp?: boolean;
}

@Component({
  selector: 'app-payment-management',
  standalone: true,
  templateUrl: './payment-management.component.html',
  styleUrls: ['./payment-management.component.scss'],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatAutocompleteModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatChipsModule,
    MatDialogModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    TranslateModule,
    AdminOnlyDirective,
    EditPaymentDetailDialogComponent,
    PaymentDetailHistoryDialogComponent
  ]
})
export class PaymentManagementComponent implements OnInit {
  displayedColumns: string[] = ['id', 'student', 'group', 'series', 'session', 'amount', 'status',
    'paymentStatus', 'dateCreation', 'actions'];

  /** Suffixe monétaire, aligné sur le reste de l'application (« 6 000 DA »). */
  readonly currencySuffix = 'DA';
  dataSource = new MatTableDataSource<PaymentDetailView>([]);
  filterForm: FormGroup;

  groups: any[] = [];
  students: any[] = [];
  /** Séries affichées dans le filtre (celles du groupe, ou toutes). */
  series: any[] = [];
  /** Liste complète des séries, servant de repli quand aucun groupe n'est sélectionné. */
  private allSeries: any[] = [];
  levels: any[] = [];
  /** Séances de la série sélectionnée (vide tant qu'aucune série n'est choisie). */
  sessions: any[] = [];

  isLoading = false;
  totalElements = 0;
  pageIndex = 0;
  pageSize = 10;

  /** Champ de saisie de l'autocomplétion étudiant (hors formulaire de filtres). */
  studentSearch = new FormControl<string>('');
  studentOptions: any[] = [];

  /** Identifiants des membres actifs du groupe sélectionné. */
  private groupMemberIds: number[] = [];
  private autoSearchTimer: ReturnType<typeof setTimeout> | undefined;

  @ViewChild(MatPaginator) paginator!: MatPaginator;

  constructor(
    private fb: FormBuilder,
    private http: HttpClient,
    private dialog: MatDialog,
    private groupService: GroupService,
    private levelService: LevelService,
    private sessionService: SessionService,
    private seriesService: SeriesService,
    private snackBar: MatSnackBar,
    private translate: TranslateService
  ) {
    this.filterForm = this.fb.group({
      levelId: [null],
      groupId: [null],
      sessionSeriesId: [null],
      // Désactivé jusqu'à ce qu'une série soit choisie : une séance n'a de sens que dans
      // sa série. Un contrôle désactivé n'est pas envoyé en paramètre de recherche.
      sessionId: [{ value: null, disabled: true }],
      studentId: [null],
      active: [null],
      dateFrom: [null],
      dateTo: [null]
    });
  }

  ngOnInit(): void {
    this.loadPaymentDetails();
    this.loadFilterOptions();
    this.wireCascadingFilters();
    this.wireStudentAutocomplete();
  }

  // =========================================================================
  // Filtres en cascade
  // =========================================================================

  /**
   * Enchaîne les filtres du plus général au plus précis : niveau → groupe → série,
   * l'étudiant étant restreint par le niveau et par le groupe.
   *
   * <p>Chaque sélection réduit les listes suivantes, et une sélection devenue
   * incohérente est effacée plutôt que laissée en place à produire zéro résultat
   * (par exemple une série qui n'appartient pas au groupe choisi).</p>
   */
  private wireCascadingFilters(): void {
    this.filterForm.get('levelId')!.valueChanges.subscribe(() => {
      this.dropInconsistentGroup();
      this.dropInconsistentSeries();
      this.dropInconsistentStudent();
      this.autoSearch();
    });

    this.filterForm.get('groupId')!.valueChanges.subscribe(groupId => {
      this.loadGroupMembers(groupId);
      this.loadGroupSeries(groupId);
      this.autoSearch();
    });

    // Les séances n'existent que dans le contexte d'une série : on ne les charge donc
    // qu'à la sélection de celle-ci, plutôt que de lister toutes les séances de l'école.
    this.filterForm.get('sessionSeriesId')!.valueChanges.subscribe(seriesId => {
      this.loadSeriesSessions(seriesId);
      this.autoSearch();
    });

    ['sessionId', 'studentId', 'active', 'dateFrom', 'dateTo'].forEach(control => {
      this.filterForm.get(control)!.valueChanges.subscribe(() => this.autoSearch());
    });
  }

  /**
   * Séances de la série sélectionnée. Le filtre reste inactif tant qu'aucune série n'est
   * choisie : sans ce périmètre, la liste des séances serait ingérable et ambiguë
   * (plusieurs séries portent des séances de même titre).
   */
  private loadSeriesSessions(seriesId: number | null): void {
    this.filterForm.get('sessionId')!.setValue(null, { emitEvent: false });

    if (!seriesId) {
      this.sessions = [];
      this.filterForm.get('sessionId')!.disable({ emitEvent: false });
      return;
    }

    this.sessionService.getSessionsBySeriesId(seriesId).subscribe({
      next: sessions => {
        // Ordre chronologique : c'est l'ordre attendu d'une feuille de séances.
        this.sessions = (sessions || []).sort((a, b) =>
          new Date(a.sessionTimeStart ?? 0).getTime() - new Date(b.sessionTimeStart ?? 0).getTime());
        this.filterForm.get('sessionId')!.enable({ emitEvent: false });
      },
      error: error => {
        this.sessions = [];
        this.filterForm.get('sessionId')!.disable({ emitEvent: false });
        this.notifyError(error, 'filterOptionsError');
      }
    });
  }

  /** Libellé d'une séance : titre et date, le titre seul étant souvent ambigu. */
  sessionLabel(session: any): string {
    if (!session) {
      return '';
    }
    const date = session.sessionTimeStart
      ? new Date(session.sessionTimeStart).toLocaleDateString(resolveLocale(this.translate.currentLang))
      : '';
    return date ? `${session.title} · ${date}` : session.title;
  }

  /** Groupes du niveau sélectionné (tous les groupes si aucun niveau). */
  get filteredGroups(): any[] {
    const levelId = this.filterForm.get('levelId')!.value;
    return levelId ? this.groups.filter(group => group.levelId === levelId) : this.groups;
  }

  /**
   * Séries proposées : celles du groupe sélectionné, sinon toutes.
   *
   * <p>La liste est chargée côté serveur ({@code loadGroupSeries}) et non filtrée
   * localement : {@code GET /api/series} renvoie l'entité brute, qui n'expose pas de
   * champ {@code groupId} (le groupe n'y est qu'un objet imbriqué). Filtrer dessus ne
   * retournait aucune série.</p>
   */
  get filteredSeries(): any[] {
    return this.series;
  }

  /**
   * Étudiants proposés : les membres du groupe s'il est sélectionné, sinon les étudiants
   * du niveau, sinon tous. C'est ce qui évitait d'avoir « tous les étudiants d'un coup ».
   */
  get scopedStudents(): any[] {
    const groupId = this.filterForm.get('groupId')!.value;
    if (groupId && this.groupMemberIds.length > 0) {
      return this.students.filter(student => this.groupMemberIds.includes(student.id));
    }

    const levelId = this.filterForm.get('levelId')!.value;
    return levelId ? this.students.filter(student => student.levelId === levelId) : this.students;
  }

  private dropInconsistentGroup(): void {
    const groupId = this.filterForm.get('groupId')!.value;
    if (groupId && !this.filteredGroups.some(group => group.id === groupId)) {
      this.filterForm.get('groupId')!.setValue(null, { emitEvent: false });
      this.groupMemberIds = [];
    }
  }

  /**
   * Séries du groupe sélectionné, via l'endpoint dédié qui renvoie un DTO
   * ({@code GET /api/series/group/{id}}). Sans groupe, on revient à la liste complète.
   */
  private loadGroupSeries(groupId: number | null): void {
    if (!groupId) {
      this.series = this.allSeries;
      this.dropInconsistentSeries();
      return;
    }

    this.seriesService.getSessionSeriesByGroupId(groupId).subscribe({
      next: series => {
        this.series = series || [];
        this.dropInconsistentSeries();
      },
      error: error => {
        this.series = [];
        this.dropInconsistentSeries();
        this.notifyError(error, 'filterOptionsError');
      }
    });
  }

  /** Efface la série sélectionnée si elle n'appartient plus au périmètre courant. */
  private dropInconsistentSeries(): void {
    const seriesId = this.filterForm.get('sessionSeriesId')!.value;
    if (seriesId && !this.series.some(serie => serie.id === seriesId)) {
      // emitEvent volontaire : le changement doit vider la liste des séances associées.
      this.filterForm.get('sessionSeriesId')!.setValue(null);
    }
  }

  private dropInconsistentStudent(): void {
    const studentId = this.filterForm.get('studentId')!.value;
    if (studentId && !this.scopedStudents.some(student => student.id === studentId)) {
      this.filterForm.get('studentId')!.setValue(null, { emitEvent: false });
      this.studentSearch.setValue('', { emitEvent: false });
    }
  }

  /** Membres actifs du groupe, pour restreindre la liste des étudiants. */
  private loadGroupMembers(groupId: number | null): void {
    if (!groupId) {
      this.groupMemberIds = [];
      this.dropInconsistentStudent();
      return;
    }

    this.groupService.getStudentsByGroupId(groupId).subscribe({
      next: students => {
        this.groupMemberIds = (students || [])
          .map(student => student.id)
          .filter((id): id is number => id !== undefined);
        this.dropInconsistentStudent();
      },
      error: () => this.groupMemberIds = []
    });
  }

  // =========================================================================
  // Recherche d'étudiant par saisie
  // =========================================================================

  /**
   * Le sélecteur d'étudiant est une autocomplétion : sur plusieurs centaines
   * d'inscrits, dérouler une liste plate n'est pas exploitable.
   */
  private wireStudentAutocomplete(): void {
    this.studentSearch.valueChanges.subscribe(value => {
      const term = (typeof value === 'string' ? value : '').trim().toLowerCase();
      this.studentOptions = !term
        ? this.scopedStudents.slice(0, 50)
        : this.scopedStudents
            .filter(student => this.studentLabel(student).toLowerCase().includes(term))
            .slice(0, 50);
    });
  }

  /** Ouvre la liste avec les premiers étudiants du périmètre courant. */
  onStudentFocus(): void {
    this.studentOptions = this.scopedStudents.slice(0, 50);
  }

  studentLabel(student: any): string {
    return student ? `${student.lastName ?? ''} ${student.firstName ?? ''}`.trim() : '';
  }

  onStudentSelected(student: any): void {
    this.filterForm.get('studentId')!.setValue(student?.id ?? null);
    this.studentSearch.setValue(this.studentLabel(student), { emitEvent: false });
  }

  clearStudent(): void {
    this.studentSearch.setValue('', { emitEvent: false });
    this.studentOptions = this.scopedStudents.slice(0, 50);
    this.filterForm.get('studentId')!.setValue(null);
  }

  /**
   * Relance la recherche après une courte pause : la frappe ou un changement de
   * sélection ne déclenche qu'un seul appel.
   */
  private autoSearch(): void {
    clearTimeout(this.autoSearchTimer);
    this.autoSearchTimer = setTimeout(() => this.search(), 350);
  }

  // =========================================================================
  // Résumé des filtres actifs
  // =========================================================================

  /** Filtres actifs, affichés en pastilles retirables. */
  get activeFilterChips(): { key: string; label: string }[] {
    const chips: { key: string; label: string }[] = [];
    const value = this.filterForm.value;

    const push = (key: string, labelKey: string, display?: string) => {
      if (display) {
        chips.push({ key, label: `${this.translate.instant(labelKey)} : ${display}` });
      }
    };

    push('levelId', 'payment.admin.filters.level',
      this.levels.find(level => level.id === value.levelId)?.name);
    push('groupId', 'payment.admin.filters.group',
      this.groups.find(group => group.id === value.groupId)?.name);
    push('sessionSeriesId', 'payment.admin.filters.series',
      this.series.find(serie => serie.id === value.sessionSeriesId)?.name);
    push('sessionId', 'payment.admin.filters.session',
      this.sessionLabel(this.sessions.find(session => session.id === value.sessionId)));
    push('studentId', 'payment.admin.filters.student',
      this.studentLabel(this.students.find(student => student.id === value.studentId)));

    if (value.active !== null && value.active !== undefined) {
      push('active', 'payment.admin.filters.rowStatus', this.translate.instant(
        value.active ? 'payment.admin.rowStatus.active' : 'payment.admin.rowStatus.inactive'));
    }

    const locale = resolveLocale(this.translate.currentLang);
    if (value.dateFrom) {
      push('dateFrom', 'payment.admin.filters.dateFrom',
        new Date(value.dateFrom).toLocaleDateString(locale));
    }
    if (value.dateTo) {
      push('dateTo', 'payment.admin.filters.dateTo',
        new Date(value.dateTo).toLocaleDateString(locale));
    }

    return chips;
  }

  removeFilter(key: string): void {
    if (key === 'studentId') {
      this.clearStudent();
      return;
    }
    this.filterForm.get(key)!.setValue(null);
  }

  /** Relance la recherche depuis la première page (les filtres ont changé). */
  search(): void {
    this.pageIndex = 0;
    this.loadPaymentDetails();
  }

  loadPaymentDetails(): void {
    this.isLoading = true;
    let params = new HttpParams()
      .set('page', String(this.pageIndex))
      .set('size', String(this.pageSize))
      .set('sort', 'id')
      .set('direction', 'DESC');

    Object.entries(this.filterForm.value).forEach(([key, value]) => {
      const normalized = this.normalizeParamValue(value);
      if (normalized !== null) {
        params = params.set(key, normalized);
      }
    });

    this.http.get<any>(`${API_BASE_URL}/api/payment-details`, { params })
      .pipe(finalize(() => this.isLoading = false))
      .subscribe({
        next: response => {
          // Transform date arrays to JavaScript Date objects
          const content = (response.content || []).map((item: any) => ({
            ...item,
            dateCreation: this.convertToDate(item.dateCreation)
          }));
          this.dataSource.data = content;
          this.totalElements = response.totalElements || 0;
          // La pagination est faite par le serveur : brancher le paginator sur la
          // dataSource ajouterait une seconde pagination locale sur la page courante.
        },
        error: error => {
          this.dataSource.data = [];
          this.totalElements = 0;
          this.notifyError(error, 'loadError');
        }
      });
  }

  private normalizeParamValue(value: any): string | null {
    if (value === null || value === undefined || value === '') {
      return null;
    }

    // Handle Date objects (e.g., from date pickers) as yyyy-MM-dd
    if (value instanceof Date) {
      const yyyy = value.getFullYear();
      const mm = String(value.getMonth() + 1).padStart(2, '0');
      const dd = String(value.getDate()).padStart(2, '0');
      return `${yyyy}-${mm}-${dd}`;
    }

    // If it's an object from a select, try to use its id; otherwise skip
    if (typeof value === 'object') {
      const maybeId = (value as any)?.id;
      if (maybeId !== undefined && maybeId !== null) {
        return String(maybeId);
      }
      return null;
    }

    // Primitives: string/number/boolean
    return String(value);
  }

  private convertToDate(dateArray: any): Date | null {
    if (!dateArray) {
      return null;
    }

    // If it's already a Date or string, return it
    if (dateArray instanceof Date || typeof dateArray === 'string') {
      return new Date(dateArray);
    }

    // If it's an array [year, month, day, hour, minute, second, nano]
    if (Array.isArray(dateArray) && dateArray.length >= 3) {
      const [year, month, day, hour = 0, minute = 0, second = 0, nano = 0] = dateArray;
      // Month in JavaScript Date is 0-indexed, but Java LocalDateTime is 1-indexed
      return new Date(year, month - 1, day, hour, minute, second, Math.floor(nano / 1000000));
    }

    return null;
  }

  loadFilterOptions(): void {
    // Un échec de chargement laisse la liste déroulante vide sans bloquer la recherche :
    // on prévient l'utilisateur plutôt que d'échouer en silence.
    this.groupService.getGroups().subscribe({
      next: groups => this.groups = groups || [],
      error: error => this.notifyError(error, 'filterOptionsError')
    });
    this.http.get<any[]>(`${API_BASE_URL}/api/students`).subscribe({
      next: students => this.students = students || [],
      error: error => this.notifyError(error, 'filterOptionsError')
    });
    this.http.get<any[]>(`${API_BASE_URL}/api/series`).subscribe({
      next: series => {
        this.allSeries = series || [];
        // Ne pas écraser une liste déjà restreinte par un groupe choisi entre-temps.
        if (!this.filterForm.get('groupId')!.value) {
          this.series = this.allSeries;
        }
      },
      error: error => this.notifyError(error, 'filterOptionsError')
    });
    this.levelService.getLevels().subscribe({
      next: levels => this.levels = levels || [],
      error: error => this.notifyError(error, 'filterOptionsError')
    });
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadPaymentDetails();
  }

  openEditDialog(detail: PaymentDetailView): void {
    const dialogRef = this.dialog.open(EditPaymentDetailDialogComponent, {
      width: '540px',
      maxWidth: '95vw',
      autoFocus: false,
      data: detail
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.loadPaymentDetails();
        this.snackBar.open(this.msg('updated'), this.closeLabel, {
          duration: 3000,
          horizontalPosition: 'center',
          verticalPosition: 'bottom'
        });
      }
    });
  }

  openHistoryDialog(detail: PaymentDetailView): void {
    this.dialog.open(PaymentDetailHistoryDialogComponent, {
      width: '600px',
      data: detail
    });
  }

  /** Traduit un message de notification de la gestion des paiements. */
  private msg(key: string): string {
    return this.translate.instant(`payment.admin.messages.${key}`);
  }

  /** Libellé traduit de l'action de fermeture des notifications. */
  private get closeLabel(): string {
    return this.translate.instant('common.close');
  }

  /**
   * Ouvre le dialogue de saisie du motif (remplace le {@code window.prompt} natif) et
   * renvoie le motif saisi, ou {@code undefined} si l'utilisateur annule.
   */
  private askReason(data: ReasonDialogData) {
    return this.dialog.open(ReasonDialogComponent, {
      width: '460px',
      maxWidth: '95vw',
      autoFocus: false,
      data
    }).afterClosed();
  }

  /** Récapitulatif de la ligne concernée, affiché dans l'en-tête du dialogue de motif. */
  private detailSummary(detail: PaymentDetailView): string {
    const student = `${detail.studentFirstName ?? ''} ${detail.studentLastName ?? ''}`.trim();
    const parts = [student, detail.groupName, detail.seriesName].filter(Boolean);
    return parts.join(' · ');
  }

  deletePaymentDetail(detail: PaymentDetailView): void {
    this.askReason({
      // Le backend pose permanentlyDeleted = true : l'action est irréversible, le libellé
      // doit le dire. L'ancien texte annonçait une simple désactivation réversible.
      title: this.translate.instant('payment.admin.reasonDialog.deleteTitle'),
      message: this.translate.instant('payment.admin.reasonDialog.deleteMessage'),
      confirmLabel: this.translate.instant('payment.admin.reasonDialog.deleteConfirm'),
      placeholder: this.translate.instant('payment.admin.reasonDialog.deletePlaceholder'),
      tone: 'danger',
      summary: this.detailSummary(detail)
    }).subscribe((reason?: string) => {
      if (!reason) {
        return;
      }

      this.http.delete(`${API_BASE_URL}/api/payment-details/${detail.id}`, { body: { reason } })
        .subscribe({
          next: () => {
            this.loadPaymentDetails();
            this.snackBar.open(this.msg('deleted'), this.closeLabel, {
              duration: 5000,
              horizontalPosition: 'center',
              verticalPosition: 'bottom'
            });
          },
          // Une suppression définitive qui échoue en silence est le pire des cas :
          // l'utilisateur croirait l'opération faite.
          error: error => this.notifyError(error, 'deleteError')
        });
    });
  }

  reactivatePaymentDetail(detail: PaymentDetailView): void {
    // Vérifier si c'est une suppression définitive
    if (detail.permanentlyDeleted) {
      this.snackBar.open(this.msg('permanentlyDeleted'), this.closeLabel, {
        duration: 5000,
        horizontalPosition: 'center',
        verticalPosition: 'bottom',
        panelClass: ['error-snackbar']
      });
      return;
    }

    this.askReason({
      title: this.translate.instant('payment.admin.reasonDialog.reactivateTitle'),
      message: this.translate.instant('payment.admin.reasonDialog.reactivateMessage'),
      confirmLabel: this.translate.instant('payment.admin.reasonDialog.reactivateConfirm'),
      placeholder: this.translate.instant('payment.admin.reasonDialog.reactivatePlaceholder'),
      tone: 'primary',
      summary: this.detailSummary(detail)
    }).subscribe((reason?: string) => {
      if (!reason) {
        return;
      }

      this.http.post(`${API_BASE_URL}/api/payment-details/${detail.id}/reactivate`, { reason })
        .subscribe({
          next: () => {
            this.loadPaymentDetails();
            this.snackBar.open(this.msg('reactivated'), this.closeLabel, {
              duration: 3000,
              horizontalPosition: 'center',
              verticalPosition: 'bottom'
            });
          },
          error: error => this.notifyError(error, 'reactivateError')
        });
    });
  }

  resetFilters(): void {
    this.filterForm.reset(undefined, { emitEvent: false });
    this.filterForm.get('sessionId')!.disable({ emitEvent: false });
    this.sessions = [];
    this.series = this.allSeries;
    this.studentSearch.setValue('', { emitEvent: false });
    this.groupMemberIds = [];
    this.studentOptions = this.students.slice(0, 50);
    this.pageIndex = 0;
    this.loadPaymentDetails();
    this.snackBar.open(this.msg('filtersCleared'), this.closeLabel, {
      duration: 3000,
      horizontalPosition: 'center',
      verticalPosition: 'bottom'
    });
  }

  /**
   * Exporte la page courante au format CSV.
   *
   * <p>Seules les lignes affichées sont exportées : la pagination étant côté serveur,
   * exporter « tout » exigerait de rappeler l'API sans limite de taille, ce qui n'est pas
   * souhaitable sur un journal de paiements. L'utilisateur filtre puis exporte.</p>
   */
  exportToCSV(): void {
    const rows = this.dataSource.data;
    if (rows.length === 0) {
      return;
    }

    const headers = [
      this.translate.instant('payment.admin.table.id'),
      this.translate.instant('payment.admin.table.student'),
      this.translate.instant('payment.admin.table.group'),
      this.translate.instant('payment.admin.table.series'),
      this.translate.instant('payment.admin.table.session'),
      this.translate.instant('payment.admin.table.amount'),
      this.translate.instant('payment.admin.table.rowStatus'),
      this.translate.instant('payment.admin.table.paymentStatus'),
      this.translate.instant('payment.admin.table.createdAt')
    ];

    const lines = rows.map(row => [
      row.id,
      this.getStudentFullName(row),
      row.groupName,
      row.seriesName,
      row.sessionName,
      row.amountPaid,
      this.getStatusLabel(row),
      this.paymentStatusLabel(row),
      row.dateCreation ? new Date(row.dateCreation).toLocaleString(resolveLocale(this.translate.currentLang)) : ''
    ].map(value => this.csvCell(value)).join(';'));

    // BOM UTF-8 : sans lui Excel affiche « Ã© » à la place des accents.
    const csv = '\uFEFF' + [headers.map(h => this.csvCell(h)).join(';'), ...lines].join('\r\n');
    this.downloadCsv(csv);

    this.snackBar.open(this.translate.instant('payment.admin.messages.csvExported', { count: rows.length }),
      this.closeLabel, { duration: 3000, horizontalPosition: 'center', verticalPosition: 'bottom' });
  }

  /**
   * Échappe une cellule CSV. Le préfixe apostrophe neutralise l'injection de formule
   * (une valeur commençant par =, +, - ou @ serait exécutée par Excel).
   */
  private csvCell(value: unknown): string {
    if (value === null || value === undefined) {
      return '';
    }
    let text = String(value);
    if (/^[=+\-@]/.test(text)) {
      text = `'${text}`;
    }
    return `"${text.replace(/"/g, '""')}"`;
  }

  private downloadCsv(csv: string): void {
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `paiements-${new Date().toISOString().slice(0, 10)}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  }

  getStudentFullName(detail: PaymentDetailView): string {
    return `${detail.studentFirstName} ${detail.studentLastName}`;
  }

  /** Statut de la ligne de versement (active / désactivée / supprimée définitivement). */
  getStatusLabel(detail: PaymentDetailView): string {
    if (detail.permanentlyDeleted) {
      return this.translate.instant('payment.admin.rowStatus.deleted');
    }
    return this.translate.instant(
      detail.active ? 'payment.admin.rowStatus.active' : 'payment.admin.rowStatus.inactive');
  }

  getStatusColor(detail: PaymentDetailView): string {
    if (detail.permanentlyDeleted) {
      return 'warn';
    }
    return detail.active ? 'primary' : 'accent';
  }

  /** Statut du paiement parent (PENDING / IN_PROGRESS / COMPLETED / CANCELLED). */
  paymentStatusLabel(detail: PaymentDetailView): string {
    const status = detail.paymentStatus;
    return status
      ? this.translate.instant(`payment.admin.paymentStatus.${status}`)
      : '—';
  }

  paymentStatusTooltip(detail: PaymentDetailView): string {
    return detail.paymentStatus === 'CANCELLED'
      ? this.translate.instant('payment.admin.paymentStatus.cancelledHint')
      : '';
  }

  /**
   * Affiche une erreur HTTP : message du serveur s'il existe, sinon libellé traduit.
   * Sans ce traitement, les échecs restaient totalement invisibles.
   */
  private notifyError(error: unknown, fallbackKey: string): void {
    const serverMessage = (error as { error?: { message?: string } })?.error?.message;
    this.snackBar.open(serverMessage || this.msg(fallbackKey), this.closeLabel, {
      duration: 6000,
      horizontalPosition: 'center',
      verticalPosition: 'bottom',
      panelClass: ['error-snackbar']
    });
  }
}
