import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { NavigationExtras, Router, RouterLink } from '@angular/router';
import { Observable, debounceTime, map, of, startWith, switchMap } from 'rxjs';
import { StudentService } from '../student/services/student.service';
import { TeacherService } from '../../services/teacher.service';
import { CommonModule } from '@angular/common';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { SearchService } from '../../services/SearchService ';
import { MatTooltip } from '@angular/material/tooltip';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';

@Component({
  selector: 'app-navigation',
  standalone: true,
  imports: [
    CommonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIcon,
    MatToolbarModule,
    FormsModule,
    MatAutocompleteModule,
    ReactiveFormsModule,
    MatMenuModule,
    RouterLink,
    MatTooltip,
    MatButtonModule,
    MatDividerModule
  ],
  templateUrl: './navigation.component.html',
  styleUrls: ['./navigation.component.scss']
})
export class NavigationComponent implements OnInit {
  userPhoto: any;
  hideSearch: boolean = false;
  selectedIcon: string = 'student'; // Default to student
  searchControl = new FormControl('');
  filteredSuggestions: Observable<string[]> | undefined;
  placeholder: string = 'Rechercher un élève...'; // Default placeholder
  currentSearchType: string = 'student';

  @Output() sidenavToggle = new EventEmitter<void>();
  @Output() searchEvent = new EventEmitter<string>();

  constructor(
    private router: Router,
    private studentService: StudentService,
    private teacherService: TeacherService,
    private searchService: SearchService
  ) {}

  ngOnInit() {
    // Initialize with student search by default
    this.setSearchType('student');

    // Setup autocomplete suggestions
    this.filteredSuggestions = this.searchControl.valueChanges.pipe(
      debounceTime(300),
      startWith(''),
      switchMap(value => this.determineSearchLogic(value ?? ''))
    );
  }

  toggleSidenav() {
    this.sidenavToggle.emit();
  }

  setSearchType(type: string): void {
    // Update search type and icon
    this.currentSearchType = type;
    this.selectedIcon = type;

    // Update placeholder based on type
    switch (type) {
      case 'student':
        this.placeholder = 'Rechercher un élève...';
        break;
      case 'group':
        this.placeholder = 'Rechercher un groupe...';
        break;
      case 'teacher':
        this.placeholder = 'Rechercher un professeur...';
        break;
      default:
        this.placeholder = 'Rechercher...';
    }

    // Clear current search
    this.clearSearch();

    // Focus on search input for better UX
    setTimeout(() => {
      const searchInput = document.querySelector('.search-input') as HTMLInputElement;
      if (searchInput) {
        searchInput.focus();
      }
    }, 100);
  }

  onSearch(): void {
    const rawValue = this.searchControl.value ?? '';
    const searchValue = rawValue.toString().trim();
    console.log('Searching for:', searchValue, 'Type:', this.currentSearchType);

    // Emit search event even without a query to load full lists if needed
    this.searchEvent.emit(searchValue);

    // Notify search subscribers (student/teacher/group search pages)
    if (this.searchService) {
      this.searchService.setSearch(searchValue);
    }

    // Navigate to the relevant route and keep query params only when needed
    const navigationExtras: NavigationExtras = searchValue
      ? { queryParams: { search: searchValue } }
      : {};
    this.router.navigate([`/${this.currentSearchType}`], navigationExtras);
  }

  clearSearch(): void {
    this.searchControl.setValue('');
  }

  private determineSearchLogic(value: string): Observable<string[]> {
    if (!value || value.trim().length < 2) {
      return of([]);
    }

    switch (this.currentSearchType) {
      case 'student':
        return this.performStudentSearch(value);
      case 'group':
        return this.performGroupSearch(value);
      case 'teacher':
        return this.performTeacherSearch(value);
      default:
        return of([]);
    }
  }

  private performStudentSearch(value: string): Observable<string[]> {
    return this.studentService.searchStudentsByNameStartingWith(value).pipe(
      map(students => students.map(student => `${student.firstName} ${student.lastName}`))
    );
  }

  private performGroupSearch(value: string): Observable<string[]> {
    // Implement group search if service is available
    // For now, return empty array
    return of([]);
  }

  private performTeacherSearch(value: string): Observable<string[]> {
    return this.teacherService.searchTeachersByNameStartingWith(value).pipe(
      map(teachers => teachers.map(teacher => `${teacher.firstName} ${teacher.lastName}`))
    );
  }

  onSelect(suggestion: string): void {
    console.log('User selected:', suggestion);
    this.searchControl.setValue(suggestion);
    this.onSearch();
  }

  // Theme toggle
  toggleDarkMode() {
    // Your dark mode logic here
    console.log('Toggle dark mode');
  }

  // Language change
  changeLanguage(lang: string) {
    console.log('Change language to:', lang);
    // Your language change logic here
  }

  // Logout
  logout() {
    console.log('Logging out...');
    // Your logout logic here
    this.router.navigate(['/login']);
  }

  // Settings
  openSettings() {
    console.log('Opening settings...');
    // Your settings logic here
    this.router.navigate(['/settings']);
  }
}
