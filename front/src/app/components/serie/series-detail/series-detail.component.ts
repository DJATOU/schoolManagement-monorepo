import { Component, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { MatSort, MatSortModule } from '@angular/material/sort';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateModule } from '@ngx-translate/core';

import { SessionService } from '../../../services/SessionService';
import { SeriesService } from '../../../services/series.service';
import { Session } from '../../../models/session/session';
import { SessionSeries } from '../../../models/sessionSerie/sessionSerie';
import { SessionModalComponent } from '../../session/session-modal/session-modal.component';
import { SeriesSessionsPdfService } from '../../../services/series-sessions-pdf.service';

/**
 * Détail d'une série : affiche les informations de la série et la liste de ses
 * sessions dans un tableau filtrable et triable. Chaque ligne ouvre la session
 * (modale existante avec présence, édition et impression PDF). La liste peut
 * aussi être téléchargée en PDF.
 */
@Component({
  selector: 'app-series-detail',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatSortModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    MatChipsModule,
    TranslateModule
  ],
  templateUrl: './series-detail.component.html',
  styleUrls: ['./series-detail.component.scss']
})
export class SeriesDetailComponent implements OnInit {
  series: SessionSeries | null = null;
  groupId: number | null = null;
  seriesId: number | null = null;

  loadingSeries = true;
  loadingSessions = true;

  displayedColumns = ['index', 'title', 'sessionType', 'teacherName', 'roomName', 'sessionTimeStart', 'status', 'actions'];
  dataSource = new MatTableDataSource<Session>([]);

  /**
   * Rattache le tri dès que le tableau apparaît.
   *
   * <p>Le tri était affecté depuis la réponse HTTP, alors que le tableau est encore masqué par
   * son {@code *ngIf} de chargement : {@code MatSort} n'existait pas encore et
   * {@code dataSource.sort} recevait {@code undefined}. Les en-têtes de colonnes étaient donc
   * inertes, en plus de l'absence de tri par défaut. Un setter garantit le branchement au
   * moment où le tableau est réellement rendu.</p>
   */
  @ViewChild(MatSort)
  set matSort(sort: MatSort | undefined) {
    if (sort) {
      this.dataSource.sort = sort;
    }
  }

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private sessionService: SessionService,
    private seriesService: SeriesService,
    private seriesSessionsPdfService: SeriesSessionsPdfService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    // Accesseurs de tri définis une fois pour toutes : ils ne dépendent pas des données.
    this.configureSorting();

    this.groupId = this.numberParam('groupId');
    this.seriesId = this.numberParam('seriesId');

    if (this.seriesId) {
      this.loadSeries(this.seriesId);
      this.loadSessions(this.seriesId);
    } else {
      this.loadingSeries = false;
      this.loadingSessions = false;
    }
  }

  private numberParam(name: string): number | null {
    const v = this.route.snapshot.paramMap.get(name);
    return v ? +v : null;
  }

  private loadSeries(seriesId: number): void {
    this.seriesService.getSeriesById(seriesId).subscribe({
      next: (series) => {
        this.series = series;
        this.loadingSeries = false;
      },
      error: (error) => {
        console.error('Erreur lors du chargement de la série :', error);
        this.loadingSeries = false;
        this.showError('Erreur lors du chargement de la série.');
      }
    });
  }

  private loadSessions(seriesId: number): void {
    this.sessionService.getSessionsBySeriesId(seriesId).subscribe({
      next: (sessions) => {
        // Le serveur renvoie déjà les séances de la plus ancienne à la plus récente ; le tri
        // par défaut du tableau reprend le même ordre pour rester cohérent après un clic
        // d'en-tête puis un retour sur la colonne Date.
        this.dataSource.data = sessions ?? [];
        this.loadingSessions = false;
      },
      error: (error) => {
        console.error('Erreur lors du chargement des sessions :', error);
        this.loadingSessions = false;
        this.showError('Erreur lors du chargement des sessions.');
      }
    });
  }

  /** Tri personnalisé pour les dates et le statut. */
  private configureSorting(): void {
    this.dataSource.sortingDataAccessor = (item: Session, property: string) => {
      switch (property) {
        case 'sessionTimeStart':
          return item.sessionTimeStart ? new Date(item.sessionTimeStart).getTime() : 0;
        case 'status':
          return item.isFinished ? 1 : 0;
        default:
          return (item as any)[property] ?? '';
      }
    };
  }

  /** Filtre texte global sur le tableau. */
  applyFilter(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.dataSource.filter = value.trim().toLowerCase();
  }

  /** Ouvre une session dans la modale dédiée (présence, édition, impression). */
  openSession(session: Session): void {
    const sessionData = { ...session, students: [] as any[] };
    const dialogRef = this.dialog.open(SessionModalComponent, {
      data: sessionData,
      // Même largeur que depuis le calendrier, pour que la feuille de présence tienne.
      width: '760px',
      maxWidth: '95vw',
      maxHeight: '90vh'
    });

    dialogRef.afterClosed().subscribe((result) => {
      // Recharger la liste pour refléter une validation / dévalidation / édition.
      if (result && this.seriesId) {
        this.loadSessions(this.seriesId);
      }
    });
  }

  /** Télécharge la liste des sessions de la série au format PDF. */
  downloadPdf(): void {
    this.seriesSessionsPdfService.generate(
      {
        name: this.series?.name,
        groupName: this.groupName(),
        totalSessions: this.series?.totalSessions,
        sessionsCompleted: this.series?.sessionsCompleted,
        numberOfSessionsCreated: this.series?.numberOfSessionsCreated,
        serieTimeStart: this.series?.serieTimeStart,
        serieTimeEnd: this.series?.serieTimeEnd
      },
      this.dataSource.data.map(s => ({
        title: s.title,
        sessionType: s.sessionType,
        teacherName: s.teacherName,
        roomName: s.roomName,
        sessionTimeStart: s.sessionTimeStart,
        sessionTimeEnd: s.sessionTimeEnd,
        isFinished: s.isFinished
      }))
    );
  }

  /** Retour vers le profil du groupe. */
  goBack(): void {
    if (this.groupId) {
      this.router.navigate(['/group', this.groupId]);
    } else {
      this.router.navigate(['/group']);
    }
  }

  private groupName(): string {
    const first = this.dataSource.data[0];
    return first?.groupName || '';
  }

  /** Nom du groupe pour l'affichage dans l'en-tête. */
  groupNameText(): string {
    return this.groupName();
  }

  get validatedCount(): number {
    return this.dataSource.data.filter(s => s.isFinished).length;
  }

  private showError(message: string): void {
    this.snackBar.open(message, 'OK', { duration: 4000, panelClass: ['snack-bar-error'] });
  }
}
