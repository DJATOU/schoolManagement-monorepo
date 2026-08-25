import { Component, OnInit, OnDestroy, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { DashboardService } from '../../services/dashboard.service';
import { DashboardStats } from '../../models/dashboard/dashboard-stats';
import { SchoolYearContextService } from '../../services/school-year-context.service';
import { SchoolYear } from '../../models/schoolYear/school-year';
import { Subject, takeUntil } from 'rxjs';
import html2canvas from 'html2canvas';
import jsPDF from 'jspdf';

interface Kpi {
  label: string;
  value: number;
  icon: string;
  variant: 'indigo' | 'cyan' | 'violet' | 'emerald' | 'amber' | 'rose';
}

type RangePreset = 'year' | 'month' | 'custom';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
  imports: [
    CommonModule, FormsModule, MatIconModule, MatButtonModule,
    MatFormFieldModule, MatInputModule, MatDatepickerModule, MatNativeDateModule,
    MatProgressSpinnerModule, MatTooltipModule, TranslateModule
  ]
})
export class DashboardComponent implements OnInit, OnDestroy {
  loading = true;
  today = new Date();
  stats?: DashboardStats;

  activePreset: RangePreset = 'year';
  customFrom: Date | null = null;
  customTo: Date | null = null;

  kpis: Kpi[] = [];

  /** Année scolaire sélectionnée (contexte global) appliquée aux statistiques. */
  private selectedSchoolYear: SchoolYear | null = null;

  private get selectedSchoolYearId(): number | null {
    return this.selectedSchoolYear?.id ?? null;
  }

  /** Dernière plage de dates chargée (pour recharger au changement d'année). */
  private lastFrom: Date | null = null;
  private lastTo: Date | null = null;

  private readonly destroy$ = new Subject<void>();

  @ViewChild('dashboardContent') dashboardContent!: ElementRef<HTMLElement>;

  constructor(private dashboardService: DashboardService,
              private translate: TranslateService,
              private schoolYearContext: SchoolYearContextService) {}

  ngOnInit(): void {
    // Recharge les statistiques à chaque changement d'année sélectionnée.
    this.schoolYearContext.selectedSchoolYear$
      .pipe(takeUntil(this.destroy$))
      .subscribe((year) => {
        this.selectedSchoolYear = year;
        // Le preset « Année » suit l'année scolaire : au changement d'année, sa plage change
        // aussi. On le réapplique plutôt que de recharger l'ancienne plage.
        if (this.activePreset === 'custom' && this.lastFrom && this.lastTo) {
          this.load(this.lastFrom, this.lastTo);
        } else {
          this.applyPreset(this.activePreset === 'month' ? 'month' : 'year');
        }
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  applyPreset(preset: RangePreset): void {
    this.activePreset = preset;
    const now = new Date();
    if (preset === 'year') {
      const range = this.schoolYearRange();
      this.load(range.from, range.to);
    } else if (preset === 'month') {
      this.load(new Date(now.getFullYear(), now.getMonth(), 1), now);
    }
    // 'custom' : on attend que l'utilisateur valide les dates
  }

  /**
   * Plage du bouton « Année » : celle de l'année scolaire sélectionnée, élargie à la
   * période d'inscription qui la précède (l'été).
   *
   * <p>Le preset couvrait l'année <strong>civile</strong> en cours (1er janvier → aujourd'hui)
   * alors que le sélecteur affiche une année <strong>scolaire</strong> (septembre → juin). Les
   * deux ne se recouvrent pas : avec 2026-2027 sélectionnée, la plage 1er janvier →
   * 21 août 2026 ne contenait aucune séance de l'année scolaire, et comptait à la place des
   * séances de juillet-août antérieures à sa rentrée.</p>
   *
   * <p>Utiliser les bornes exactes de l'année scolaire (1er septembre → 30 juin) laissait
   * cependant juillet et août dans un angle mort : une inscription enregistrée en août 2026
   * (pour la rentrée 2026-2027) tombait avant le 1er septembre et n'était donc comptée dans
   * « Nouveaux inscrits » d'aucune année. On rattache ce creux estival à l'année qui
   * <strong>arrive</strong> — c'est bien pour elle que l'on inscrit en juillet/août — en
   * démarrant la plage au 1er juillet précédant la rentrée. Les plages restent disjointes et
   * couvrent l'ensemble du calendrier.</p>
   *
   * <p>Sans année sélectionnée, on retombe sur l'année civile en cours.</p>
   */
  private schoolYearRange(): { from: Date; to: Date } {
    const now = new Date();
    const start = this.selectedSchoolYear?.startDate;
    const end = this.selectedSchoolYear?.endDate;

    if (!start || !end) {
      return { from: new Date(now.getFullYear(), 0, 1), to: now };
    }
    const startDate = new Date(start);
    // 1er juillet de l'année civile de la rentrée : inclut la période d'inscription estivale.
    const enrollmentWindowStart = new Date(startDate.getFullYear(), 6, 1);
    const from = enrollmentWindowStart < startDate ? enrollmentWindowStart : startDate;
    return { from, to: new Date(end) };
  }

  /** Libellé de la plage couverte par le bouton « Année », pour l'infobulle. */
  get yearPresetLabel(): string {
    return this.selectedSchoolYear?.label ?? String(new Date().getFullYear());
  }

  applyCustom(): void {
    if (this.customFrom && this.customTo) {
      this.activePreset = 'custom';
      this.load(this.customFrom, this.customTo);
    }
  }

  private load(from: Date, to: Date): void {
    this.loading = true;
    this.lastFrom = from;
    this.lastTo = to;
    this.dashboardService.getStats(this.fmt(from), this.fmt(to), this.selectedSchoolYearId).subscribe({
      next: (stats) => {
        this.stats = stats;
        this.buildKpis(stats);
        this.loading = false;
      },
      error: (err) => {
        console.error('Error loading dashboard stats:', err);
        this.loading = false;
      }
    });
  }

  private buildKpis(s: DashboardStats): void {
    const t = (k: string) => this.translate.instant('dashboard.kpi.' + k);
    this.kpis = [
      { label: t('activeStudents'), value: s.totalStudents, icon: 'school', variant: 'indigo' },
      { label: t('teachers'), value: s.totalTeachers, icon: 'workspace_premium', variant: 'cyan' },
      { label: t('groups'), value: s.totalGroups, icon: 'groups', variant: 'violet' },
      { label: t('newStudents'), value: s.newStudentsInPeriod, icon: 'person_add', variant: 'emerald' },
      { label: t('leaving'), value: s.leavingStudents, icon: 'person_remove', variant: 'rose' },
      { label: t('catchUps'), value: s.catchUpSessions, icon: 'history_edu', variant: 'amber' }
    ];
  }

  /** Exporte le tableau de bord affiché en PDF (capture visuelle fidèle). */
  async printPdf(): Promise<void> {
    const el = this.dashboardContent?.nativeElement;
    if (!el) return;

    // Masquer la barre de filtre/boutons pendant la capture (optionnel : on capture tel quel)
    const canvas = await html2canvas(el, {
      scale: 2,
      backgroundColor: '#ffffff',
      useCORS: true,
      onclone: (doc) => {
        // Neutralise l'animation fade-in (sinon le clone est capturé à opacity 0 → délavé)
        doc.querySelectorAll('.fade-in').forEach((node) => {
          const elem = node as HTMLElement;
          elem.style.opacity = '1';
          elem.style.animation = 'none';
        });
        // Masque la barre de filtre/boutons dans le PDF
        const filter = doc.querySelector('.period-filter') as HTMLElement | null;
        if (filter) filter.style.display = 'none';
      }
    });

    const imgData = canvas.toDataURL('image/png');
    const pdf = new jsPDF('p', 'mm', 'a4');
    const pageWidth = pdf.internal.pageSize.getWidth();
    const pageHeight = pdf.internal.pageSize.getHeight();
    const margin = 8;
    const imgWidth = pageWidth - margin * 2;
    const imgHeight = (canvas.height * imgWidth) / canvas.width;

    let heightLeft = imgHeight;
    let position = margin;

    pdf.addImage(imgData, 'PNG', margin, position, imgWidth, imgHeight);
    heightLeft -= (pageHeight - margin * 2);

    // Pagination si le contenu dépasse une page
    while (heightLeft > 0) {
      pdf.addPage();
      position = margin - (imgHeight - heightLeft);
      pdf.addImage(imgData, 'PNG', margin, position, imgWidth, imgHeight);
      heightLeft -= (pageHeight - margin * 2);
    }

    const title = this.translate.instant('dashboard.title');
    pdf.save(`${title}_${this.stats?.from || ''}_${this.stats?.to || ''}.pdf`);
  }

  private fmt(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  // ---- Helpers pour les graphes ----
  get totalSessions(): number {
    if (!this.stats) return 0;
    return this.stats.sessionsValidated + this.stats.sessionsScheduled + this.stats.sessionsDeactivated;
  }

  get totalAttendance(): number {
    if (!this.stats) return 0;
    return this.stats.presentCount + this.stats.justifiedAbsences + this.stats.unjustifiedAbsences;
  }

  pct(part: number, total: number): number {
    return total > 0 ? Math.round((part / total) * 100) : 0;
  }

  // Anneau genre : % hommes
  get maleRate(): number {
    if (!this.stats) return 0;
    const total = this.stats.maleStudents + this.stats.femaleStudents;
    return total > 0 ? Math.round((this.stats.maleStudents / total) * 100) : 0;
  }
  get genderRingOffset(): number {
    const circ = 339.292;
    return circ - (circ * this.maleRate) / 100;
  }
}
