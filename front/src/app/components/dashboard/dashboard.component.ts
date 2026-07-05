import { Component, OnInit, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { DashboardService } from '../../services/dashboard.service';
import { DashboardStats } from '../../models/dashboard/dashboard-stats';
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
    MatProgressSpinnerModule, TranslateModule
  ]
})
export class DashboardComponent implements OnInit {
  loading = true;
  today = new Date();
  stats?: DashboardStats;

  activePreset: RangePreset = 'year';
  customFrom: Date | null = null;
  customTo: Date | null = null;

  kpis: Kpi[] = [];

  @ViewChild('dashboardContent') dashboardContent!: ElementRef<HTMLElement>;

  constructor(private dashboardService: DashboardService,
              private translate: TranslateService) {}

  ngOnInit(): void {
    this.applyPreset('year');
  }

  applyPreset(preset: RangePreset): void {
    this.activePreset = preset;
    const now = new Date();
    if (preset === 'year') {
      this.load(new Date(now.getFullYear(), 0, 1), now);
    } else if (preset === 'month') {
      this.load(new Date(now.getFullYear(), now.getMonth(), 1), now);
    }
    // 'custom' : on attend que l'utilisateur valide les dates
  }

  applyCustom(): void {
    if (this.customFrom && this.customTo) {
      this.activePreset = 'custom';
      this.load(this.customFrom, this.customTo);
    }
  }

  private load(from: Date, to: Date): void {
    this.loading = true;
    this.dashboardService.getStats(this.fmt(from), this.fmt(to)).subscribe({
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
