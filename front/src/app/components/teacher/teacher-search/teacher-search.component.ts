import { Component, OnInit } from '@angular/core';
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

@Component({
  selector: 'app-teacher-search',
  standalone: true,
  templateUrl: './teacher-search.component.html',
  styleUrls: ['./teacher-search.component.scss'],
  imports: [
    CommonModule, MatPaginatorModule, TeacherCardComponent, TeacherListComponent,
    MatIconModule, MatProgressSpinnerModule, ViewToggleComponent, ListHeaderComponent,
    FadeInDirective
  ]
})
export class TeacherSearchComponent implements OnInit {
  viewMode: 'card' | 'list' = 'card';
  teachers: Teacher[] = [];
  allTeachers: Teacher[] = []; // Keep original list for filtering
  filteredTeachers: Teacher[] = [];
  currentPageTeachers: Teacher[] = [];
  totalTeachers: number = 0;
  pageSize: number = 8;
  pageSizeOptions: number[] = [8, 12, 16, 20];
  isLoading = true;
  showWithGroupsOnly = false;

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
          this.allTeachers = teachers;
          this.applyFilters();
        }
      });
    }
  }

  loadAllTeachers(): void {
    this.isLoading = true;
    this.teacherService.getTeachers().subscribe(teachers => {
      this.allTeachers = teachers;
      this.applyFilters();
      this.isLoading = false;
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
    const startIndex = event.pageIndex * event.pageSize;
    const endIndex = startIndex + event.pageSize;
    this.currentPageTeachers = this.filteredTeachers.slice(startIndex, endIndex);
    this.pageSize = event.pageSize;
  }

  changeViewMode(mode: 'card' | 'list'): void {
    this.viewMode = mode;
  }

  /**
   * Update the teachers displayed on the current page
   */
  private updatePageTeachers(): void {
    this.totalTeachers = this.filteredTeachers.length;
    this.currentPageTeachers = this.filteredTeachers.slice(0, this.pageSize);
  }
}
