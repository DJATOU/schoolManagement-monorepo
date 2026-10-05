import { Component, OnInit, ElementRef, ViewChild } from '@angular/core';
import { TeacherService } from '../../../services/teacher.service';
import { Teacher } from '../../../models/teacher/teacher';
import { TeacherCardComponent } from '../teacher-card/teacher-card.component';
import { TeacherListComponent } from '../teacher-list/teacher-list.component';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router } from '@angular/router';
import { SearchService } from '../../../services/SearchService ';
import { ViewToggleComponent } from '../../shared/view-toggle/view-toggle.component';
import { ListHeaderComponent } from '../../shared/list-header/list-header.component';
import { FadeInDirective } from '../../shared/FadeInDirective';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-teacher-search',
  standalone: true,
  templateUrl: './teacher-search.component.html',
  styleUrls: ['./teacher-search.component.scss'],
  imports: [
    CommonModule, MatPaginatorModule, TeacherCardComponent, TeacherListComponent,
    MatIconModule, MatProgressSpinnerModule, ViewToggleComponent, ListHeaderComponent,
    FadeInDirective, TranslateModule
  ]
})
export class TeacherSearchComponent implements OnInit {
  viewMode: 'card' | 'list' = 'card';
  teachers: Teacher[] = [];
  allTeachers: Teacher[] = [];
  filteredTeachers: Teacher[] = [];
  currentPageTeachers: Teacher[] = [];

  /** Cartes affichées : la page courante du paginateur, et elle seule. */
  displayedTeachers: Teacher[] = [];
  currentPageIndex: number = 0;

  totalTeachers: number = 0;
  pageSize: number = 8;
  pageSizeOptions: number[] = [8, 12, 16, 20];
  isLoading = true;
  showWithGroupsOnly = false;

  @ViewChild('contentArea') contentArea!: ElementRef;

  constructor(
    private teacherService: TeacherService,
    private searchService: SearchService,
    private router: Router
  ) {
  }

  ngOnInit(): void {
    this.pageSize = this.getSmartPageSize();
    this.listenToSearchEvents();
    this.loadAllTeachers();
  }

  /**
   * Smart page size calculation based on screen width
   */
  private getSmartPageSize(): number {
    const width = window.innerWidth;
    if (width >= 1600) return 20;
    if (width >= 1200) return 16;
    if (width >= 900) return 12;
    return 8;
  }

  listenToSearchEvents(): void {
    this.searchService.getSearch().subscribe((searchTerm: string) => {
      this.handleSearch(searchTerm);
    });
  }

  handleSearch(searchTerm: string): void {
    if (!searchTerm) {
      this.loadAllTeachers();
    } else {
      this.teacherService.searchTeachersByNameStartingWith(searchTerm).subscribe(teachers => {
        if (teachers.length === 1) {
          this.router.navigate(['/teacher', teachers[0].id]);
        } else {
          this.allTeachers = this.sortByName(teachers);
          this.applyFilters();
        }
      });
    }
  }

  loadAllTeachers(): void {
    this.isLoading = true;
    this.teacherService.getTeachers().subscribe(teachers => {
      this.allTeachers = this.sortByName(teachers);
      this.applyFilters();
      this.isLoading = false;
    });
  }

  /** Tri alphabétique par défaut (nom puis prénom), insensible à la casse/accents. */
  private sortByName(teachers: Teacher[]): Teacher[] {
    return [...(teachers || [])].sort((a, b) => {
      const an = `${a.lastName ?? ''} ${a.firstName ?? ''}`.trim();
      const bn = `${b.lastName ?? ''} ${b.firstName ?? ''}`.trim();
      return an.localeCompare(bn, 'fr', { sensitivity: 'base' });
    });
  }

  /**
   * Apply 'has groups' filter if enabled
   */
  private applyFilters(): void {
    if (this.showWithGroupsOnly) {
      this.filteredTeachers = this.allTeachers.filter(t => t.groups && t.groups.length > 0);
    } else {
      this.filteredTeachers = [...this.allTeachers];
    }
    this.currentPageIndex = 0;
    this.updatePageTeachers();
  }

  /**
   * Handle 'has groups' filter toggle
   */
  onHasGroupsFilterChange(showWithGroupsOnly: boolean): void {
    this.showWithGroupsOnly = showWithGroupsOnly;
    this.applyFilters();
  }

  changePage(event: PageEvent): void {
    this.currentPageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.updatePageTeachers();
  }

  changeViewMode(mode: 'card' | 'list'): void {
    this.viewMode = mode;
  }

  /**
   * Affiche la page courante du paginateur.
   *
   * <p>Même défaut que la liste des groupes : le paginateur comptait toute la liste, les cartes
   * venaient d'un défilement infini par lots de 10 qui ne se déclenchait pas sur un écran assez
   * grand. Au-delà de 10 enseignants, les suivants ne s'affichaient jamais.</p>
   */
  private updatePageTeachers(): void {
    this.totalTeachers = this.filteredTeachers.length;
    const startIndex = this.currentPageIndex * this.pageSize;
    this.currentPageTeachers = this.filteredTeachers.slice(startIndex, startIndex + this.pageSize);
    this.displayedTeachers = this.currentPageTeachers;
  }
}
