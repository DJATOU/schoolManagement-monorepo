import { Component, OnInit, OnDestroy, ViewChild, ElementRef, AfterViewInit } from '@angular/core';
import { StudentService } from '../services/student.service';
import { Student } from '../domain/student';
import { StudentCardComponent } from '../student-card/student-card.component';
import { StudentListComponent } from '../student-list/student-list.component';
import { MatPaginatorModule, PageEvent, MatPaginator } from '@angular/material/paginator';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { SearchService } from '../../../services/SearchService ';
import { StudentListItemComponent } from '../student-list/student-list-item/student-list-item.component';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { FadeInDirective } from '../../shared/FadeInDirective';
import { ViewToggleComponent } from '../../shared/view-toggle/view-toggle.component';
import { ListHeaderComponent } from '../../shared/list-header/list-header.component';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { FormsModule } from '@angular/forms';
import { GroupService } from '../../../services/group.service';
import { LevelService } from '../../../services/level.service';
import { StudentPaymentStatusService } from '../../../services/student-payment-status.service';
import { Group } from '../../../models/group/group';
import { Level } from '../../../models/level/level';
import { forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

interface StudentWithPayment extends Student {
  paymentStatus?: 'GOOD' | 'LATE' | 'NA';
}

@Component({
  selector: 'app-student-search',
  standalone: true,
  templateUrl: './student-search.component.html',
  styleUrls: ['./student-search.component.scss'],
  imports: [
    CommonModule, MatPaginatorModule, StudentCardComponent,
    StudentListComponent, StudentListItemComponent, MatProgressSpinnerModule,
    FadeInDirective, ViewToggleComponent, ListHeaderComponent,
    MatButtonModule, MatIconModule, MatSelectModule,
    MatFormFieldModule, FormsModule
  ]
})
export class StudentSearchComponent implements OnInit, OnDestroy, AfterViewInit {
  viewMode: 'card' | 'list' = 'card';
  students: StudentWithPayment[] = [];
  allStudents: StudentWithPayment[] = [];
  filteredStudents: StudentWithPayment[] = [];
  currentPageStudents: StudentWithPayment[] = [];
  totalStudents: number = 0;
  pageSize: number = 8;
  currentPageIndex: number = 0;
  pageSizeOptions: number[] = [8, 12, 16, 20, 24];
  isLoading = true;

  // Filter states
  showLateOnly = false;
  selectedGroupId: number | null = null;
  selectedLevelId: number | null = null;
  filtersVisible = false;

  // Filter options
  groups: Group[] = [];
  levels: Level[] = [];

  // Late payment counter
  latePaymentCount: number = 0;

  @ViewChild('contentArea') contentArea!: ElementRef;
  @ViewChild('topPaginator') topPaginator!: MatPaginator;
  @ViewChild('bottomPaginator') bottomPaginator!: MatPaginator;

  private resizeObserver?: ResizeObserver;

  constructor(
    private studentService: StudentService,
    private searchService: SearchService,
    private router: Router,
    private groupService: GroupService,
    private levelService: LevelService,
    private paymentStatusService: StudentPaymentStatusService
  ) { }

  ngOnInit(): void {
    this.pageSize = this.getSmartPageSize();
    this.listenToSearchEvents();
    this.loadFilterOptions();
    this.loadAllStudents();
  }

  ngAfterViewInit(): void {
    this.setupDynamicPageSize();
    // Force recalculation after DOM is fully rendered
    setTimeout(() => {
      this.calculateDynamicPageSize();
    }, 100);
  }

  ngOnDestroy(): void {
    if (this.resizeObserver) {
      this.resizeObserver.disconnect();
    }
  }

  /**
   * Smart page size calculation based on screen dimensions
   * Uses actual container measurements when available
   */
  private getSmartPageSize(): number {
    const columns = this.getColumnCount();
    const rows = this.getRowCount();
    return Math.max(columns, rows * columns);
  }

  /**
   * Get number of rows that fit in available height (fallback)
   */
  private getRowCount(): number {
    // Card height from global styles: min 13rem (~208px)
    const cardRowHeight = 208;
    const gap = 16;
    const height = window.innerHeight;

    // Subtract: header(64) + topbar(~80) + footer(~56) + container padding(48)
    const usedHeight = 248;
    const availableHeight = height - usedHeight;

    return Math.max(1, Math.floor(availableHeight / (cardRowHeight + gap)));
  }

  /**
   * Setup dynamic page size calculation based on available height
   */
  private setupDynamicPageSize(): void {
    if (!this.contentArea) return;

    this.resizeObserver = new ResizeObserver(() => {
      this.calculateDynamicPageSize();
    });

    this.resizeObserver.observe(this.contentArea.nativeElement);
  }

  /**
   * Calculate page size dynamically - EXPERT SOLUTION
   * Uses window dimensions with measured offsets for reliability
   */
  private calculateDynamicPageSize(): void {
    if (!this.contentArea) return;

    const containerWidth = this.contentArea.nativeElement.clientWidth;

    // Use window height for reliability (container height can be unreliable)
    const viewportHeight = window.innerHeight;

    // Measure actual offsets from the page layout
    // Header: 64px, Topbar: ~60px, Footer: ~56px, Container padding: ~48px  
    const fixedHeightUsed = 228; // Total fixed elements
    const availableHeight = viewportHeight - fixedHeightUsed;

    // Card dimensions from CSS - must match exactly
    const cardMinWidth = 220; // minmax(220px, 1fr)
    const cardHeight = 208;   // min-height: 13rem = 208px
    const gap = 16;

    // Calculate columns from container width
    const columns = Math.max(1, Math.floor((containerWidth + gap) / (cardMinWidth + gap)));

    // Calculate rows from available height  
    const rowHeight = cardHeight + gap;
    const rows = Math.max(1, Math.floor(availableHeight / rowHeight));

    const itemsPerPage = rows * columns;

    // Remove debug log in production
    console.log('📊 PageSize calc:', { viewportHeight, availableHeight, columns, rows, itemsPerPage });

    // Update page size
    if (itemsPerPage !== this.pageSize && itemsPerPage > 0) {
      this.pageSize = itemsPerPage;
      // Update pageSizeOptions to include calculated size
      if (!this.pageSizeOptions.includes(itemsPerPage)) {
        this.pageSizeOptions = [...new Set([...this.pageSizeOptions, itemsPerPage])].sort((a, b) => a - b);
      }
      this.updatePageStudents();
    }
  }

  /**
   * Get number of columns based on container width
   */
  private getColumnCount(): number {
    if (this.viewMode === 'list') return 1;

    // Use container width if available
    if (this.contentArea?.nativeElement) {
      const containerWidth = this.contentArea.nativeElement.clientWidth;
      const cardMinWidth = 220;
      const gap = 16;
      return Math.max(1, Math.floor((containerWidth + gap) / (cardMinWidth + gap)));
    }

    // Fallback: use window width minus sidebar
    const availableWidth = window.innerWidth - 300;
    const cardMinWidth = 220;
    const gap = 16;
    return Math.max(1, Math.floor((availableWidth + gap) / (cardMinWidth + gap)));
  }

  /**
   * Load filter options (groups and levels)
   */
  private loadFilterOptions(): void {
    this.groupService.getGroups().subscribe(groups => {
      this.groups = groups;
    });

    this.levelService.getLevels().subscribe(levels => {
      this.levels = levels;
    });
  }

  listenToSearchEvents(): void {
    this.searchService.getSearch().subscribe((searchTerm: string) => {
      this.handleSearch(searchTerm);
    });
  }

  handleSearch(searchTerm: string): void {
    this.isLoading = true;
    if (!searchTerm) {
      this.loadAllStudents();
    } else {
      this.studentService.searchStudentsByNameStartingWith(searchTerm).subscribe(students => {
        if (students.length === 1) {
          this.router.navigate(['/student', students[0].id]);
        } else {
          this.allStudents = students as StudentWithPayment[];
          this.loadPaymentStatuses();
        }
      });
    }
  }

  loadAllStudents(): void {
    this.isLoading = true;
    this.studentService.getStudents().subscribe(students => {
      this.allStudents = students as StudentWithPayment[];
      this.loadPaymentStatuses();
    });
  }

  /**
   * Load payment statuses for all students
   */
  private loadPaymentStatuses(): void {
    const statusRequests = this.allStudents.map(student =>
      this.paymentStatusService.getStudentPaymentStatus(student.id!).pipe(
        map(status => ({ studentId: student.id, status: status.paymentStatus as 'GOOD' | 'LATE' | 'NA' })),
        catchError(() => of({ studentId: student.id, status: 'NA' as const }))
      )
    );

    forkJoin(statusRequests).subscribe(results => {
      results.forEach(result => {
        const student = this.allStudents.find(s => s.id === result.studentId);
        if (student) {
          student.paymentStatus = result.status;
        }
      });

      this.applyAllFilters();
      this.isLoading = false;
    });
  }

  /**
   * Apply all active filters
   */
  private applyAllFilters(): void {
    let filtered = [...this.allStudents];

    // Filter by late payment
    if (this.showLateOnly) {
      filtered = filtered.filter(s => s.paymentStatus === 'LATE');
    }

    // Filter by group
    if (this.selectedGroupId) {
      filtered = filtered.filter(s => s.groupIds?.includes(this.selectedGroupId!));
    }

    // Filter by level
    if (this.selectedLevelId) {
      filtered = filtered.filter(s => s.levelId === this.selectedLevelId);
    }

    this.filteredStudents = filtered;
    this.latePaymentCount = this.allStudents.filter(s => s.paymentStatus === 'LATE').length;
    this.currentPageIndex = 0;
    this.updatePageStudents();
  }

  onLateFilterChange(showLateOnly: boolean): void {
    this.showLateOnly = showLateOnly;
    this.applyAllFilters();
  }

  onGroupFilterChange(): void {
    this.applyAllFilters();
  }

  onLevelFilterChange(): void {
    this.applyAllFilters();
  }

  toggleFilters(): void {
    this.filtersVisible = !this.filtersVisible;
  }

  clearGroupFilter(): void {
    this.selectedGroupId = null;
    this.applyAllFilters();
  }

  clearLevelFilter(): void {
    this.selectedLevelId = null;
    this.applyAllFilters();
  }

  getActiveFiltersCount(): number {
    let count = 0;
    if (this.selectedGroupId) count++;
    if (this.selectedLevelId) count++;
    return count;
  }

  getGroupName(groupId: number): string {
    return this.groups.find(g => g.id === groupId)?.name || '';
  }

  getLevelName(levelId: number): string {
    return this.levels.find(l => l.id === levelId)?.name || '';
  }

  changePage(event: PageEvent): void {
    this.currentPageIndex = event.pageIndex;
    this.pageSize = event.pageSize;

    // Synchronize both paginators
    if (this.topPaginator && this.topPaginator.pageIndex !== event.pageIndex) {
      this.topPaginator.pageIndex = event.pageIndex;
    }
    if (this.bottomPaginator && this.bottomPaginator.pageIndex !== event.pageIndex) {
      this.bottomPaginator.pageIndex = event.pageIndex;
    }

    this.updatePageStudents();
  }

  changeViewMode(mode: 'card' | 'list'): void {
    this.viewMode = mode;
    this.calculateDynamicPageSize();
  }

  /**
   * Update the students displayed on the current page
   */
  private updatePageStudents(): void {
    this.totalStudents = this.filteredStudents.length;
    const startIndex = this.currentPageIndex * this.pageSize;
    const endIndex = startIndex + this.pageSize;
    this.currentPageStudents = this.filteredStudents.slice(startIndex, endIndex);
  }

  /**
   * Get page range label for compact pagination
   */
  getPageRangeLabel(): string {
    if (this.totalStudents === 0) return '0 sur 0';
    const startIndex = this.currentPageIndex * this.pageSize + 1;
    const endIndex = Math.min((this.currentPageIndex + 1) * this.pageSize, this.totalStudents);
    return `${startIndex}-${endIndex} sur ${this.totalStudents}`;
  }

  /**
   * Navigate to previous page
   */
  previousPage(): void {
    if (this.currentPageIndex > 0) {
      const event: PageEvent = {
        pageIndex: this.currentPageIndex - 1,
        pageSize: this.pageSize,
        length: this.totalStudents
      };
      this.changePage(event);
    }
  }

  /**
   * Navigate to next page
   */
  nextPage(): void {
    const maxPage = Math.ceil(this.totalStudents / this.pageSize) - 1;
    if (this.currentPageIndex < maxPage) {
      const event: PageEvent = {
        pageIndex: this.currentPageIndex + 1,
        pageSize: this.pageSize,
        length: this.totalStudents
      };
      this.changePage(event);
    }
  }

  /**
   * Check if previous page button should be disabled
   */
  isPreviousDisabled(): boolean {
    return this.currentPageIndex === 0;
  }

  /**
   * Check if next page button should be disabled
   */
  isNextDisabled(): boolean {
    const maxPage = Math.ceil(this.totalStudents / this.pageSize) - 1;
    return this.currentPageIndex >= maxPage;
  }
}
