import { Component, OnInit } from '@angular/core';
import { StudentService } from '../services/student.service';
import { Student } from '../domain/student';
import { StudentCardComponent } from '../student-card/student-card.component';
import { StudentListComponent } from '../student-list/student-list.component';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { SearchService } from '../../../services/SearchService ';
import { StudentListItemComponent } from '../student-list/student-list-item/student-list-item.component';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { FadeInDirective } from '../../shared/FadeInDirective';
import { ViewToggleComponent } from '../../shared/view-toggle/view-toggle.component';
import { ListHeaderComponent } from '../../shared/list-header/list-header.component';

@Component({
  selector: 'app-student-search',
  standalone: true,
  templateUrl: './student-search.component.html',
  styleUrls: ['./student-search.component.scss'],
  imports: [
    CommonModule, MatPaginatorModule, StudentCardComponent,
    StudentListComponent, StudentListItemComponent, MatProgressSpinnerModule,
    FadeInDirective, ViewToggleComponent, ListHeaderComponent
  ]
})
export class StudentSearchComponent implements OnInit {
  viewMode: 'card' | 'list' = 'card';
  students: Student[] = [];
  allStudents: Student[] = []; // Keep original list for filtering
  filteredStudents: Student[] = [];
  currentPageStudents: Student[] = [];
  totalStudents: number = 0;
  pageSize: number = 8;
  pageSizeOptions: number[] = [8, 12, 16, 20];
  isLoading = true;
  showLateOnly = false;

  constructor(
    private studentService: StudentService,
    private searchService: SearchService,
    private router: Router
  ) { }

  ngOnInit(): void {
    this.pageSize = this.getSmartPageSize();
    this.listenToSearchEvents();
    this.loadAllStudents();
  }

  /**
   * Smart page size calculation based on screen width
   * Very large screens (≥1600px): 20 items
   * Large screens (≥1200px): 16 items
   * Medium screens (≥900px): 12 items
   * Small screens (<900px): 8 items
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
    this.isLoading = true;
    if (!searchTerm) {
      this.loadAllStudents();
    } else {
      this.studentService.searchStudentsByNameStartingWith(searchTerm).subscribe(students => {
        if (students.length === 1) {
          this.router.navigate(['/student', students[0].id]);
        } else {
          this.allStudents = students;
          this.applyFilters();
          this.isLoading = false;
        }
      });
    }
  }

  loadAllStudents(): void {
    this.isLoading = true;
    this.studentService.getStudents().subscribe(students => {
      this.allStudents = students;
      this.applyFilters();
      this.isLoading = false;
    });
  }

  /**
   * Apply late payment filter if active
   * NOTE: This currently requires payment status to be included in student data
   * For now, this filter is disabled until backend support is added
   */
  private applyFilters(): void {
    // TODO: Implement late filter when paymentStatus is available in Student model
    // if (this.showLateOnly) {
    //   this.filteredStudents = this.allStudents.filter(s => s.paymentStatus === 'LATE');
    // } else {
    this.filteredStudents = [...this.allStudents];
    // }
    this.updatePageStudents();
  }

  /**
   * Handle late payment filter toggle
   * Currently disabled - requires backend support
   */
  onLateFilterChange(showLateOnly: boolean): void {
    this.showLateOnly = showLateOnly;
    // TODO: Enable when backend provides payment status in student list
    // this.applyFilters();
  }

  changePage(event: PageEvent): void {
    const startIndex = event.pageIndex * event.pageSize;
    const endIndex = startIndex + event.pageSize;
    this.currentPageStudents = this.filteredStudents.slice(startIndex, endIndex);
    this.pageSize = event.pageSize;
  }

  changeViewMode(mode: 'card' | 'list'): void {
    this.viewMode = mode;
  }

  /**
   * Update the students displayed on the current page
   */
  private updatePageStudents(): void {
    this.totalStudents = this.filteredStudents.length;
    this.currentPageStudents = this.filteredStudents.slice(0, this.pageSize);
  }
}
