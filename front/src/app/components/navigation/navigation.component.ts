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
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TranslationService } from '../../services/translation.service';
import { SchoolYearSelectorComponent } from '../school-year-selector/school-year-selector.component';
import { AuthService } from '../../services/auth.service';

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
    MatDividerModule,
    TranslateModule,
    SchoolYearSelectorComponent
  ],
  templateUrl: './navigation.component.html',
  styleUrls: ['./navigation.component.scss']
})
export class NavigationComponent implements OnInit {
  userPhoto: any;
  hideSearch = false;
  selectedIcon = 'student';
  searchControl = new FormControl('');
  filteredSuggestions: Observable<string[]> | undefined;
  placeholderKey = 'NAV.SEARCH.PLACEHOLDER_STUDENT';
  currentSearchType: string = 'student';

  @Output() sidenavToggle = new EventEmitter<void>();
  @Output() searchEvent = new EventEmitter<string>();

  constructor(
    private router: Router,
    private studentService: StudentService,
    private teacherService: TeacherService,
    private searchService: SearchService,
    public translationService: TranslationService,
    private translate: TranslateService,
    public authService: AuthService
  ) {}

  ngOnInit() {
    this.setSearchType('student');

    this.filteredSuggestions = this.searchControl.valueChanges.pipe(
      debounceTime(300),
      startWith(''),
      switchMap(value => this.determineSearchLogic(value ?? ''))
    );

    this.translate.onLangChange.subscribe(() => {
      this.updatePlaceholder();
    });
  }

  toggleSidenav() {
    this.sidenavToggle.emit();
  }

  setSearchType(type: string): void {
    this.currentSearchType = type;
    this.selectedIcon = type;
    this.updatePlaceholder();
    this.clearSearch();

    setTimeout(() => {
      const searchInput = document.querySelector('.search-input') as HTMLInputElement;
      if (searchInput) {
        searchInput.focus();
      }
    }, 100);
  }

  private updatePlaceholder(): void {
    switch (this.currentSearchType) {
      case 'student':
        this.placeholderKey = 'NAV.SEARCH.PLACEHOLDER_STUDENT';
        break;
      case 'group':
        this.placeholderKey = 'NAV.SEARCH.PLACEHOLDER_GROUP';
        break;
      case 'teacher':
        this.placeholderKey = 'NAV.SEARCH.PLACEHOLDER_TEACHER';
        break;
      default:
        this.placeholderKey = 'NAV.SEARCH.PLACEHOLDER_GENERIC';
    }
  }

  onSearch(): void {
    const rawValue = this.searchControl.value ?? '';
    const searchValue = rawValue.toString().trim();

    this.searchEvent.emit(searchValue);

    if (this.searchService) {
      this.searchService.setSearch(searchValue);
    }

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
    return of([]);
  }

  private performTeacherSearch(value: string): Observable<string[]> {
    return this.teacherService.searchTeachersByNameStartingWith(value).pipe(
      map(teachers => teachers.map(teacher => `${teacher.firstName} ${teacher.lastName}`))
    );
  }

  onSelect(suggestion: string): void {
    this.searchControl.setValue(suggestion);
    this.onSearch();
  }

  /** Initiales (max 2) à partir d'un libellé de suggestion. */
  initialsOf(suggestion: string): string {
    const parts = (suggestion || '').trim().split(/\s+/).filter(Boolean);
    if (parts.length === 0) return '?';
    if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
    return (parts[0][0] + parts[1][0]).toUpperCase();
  }

  /** Icône Material correspondant au type de recherche courant. */
  get typeIcon(): string {
    switch (this.selectedIcon) {
      case 'group': return 'groups';
      case 'teacher': return 'person';
      default: return 'school';
    }
  }

  toggleDarkMode() {
    console.log('Toggle dark mode');
  }

  changeLanguage(lang: string) {
    this.translationService.useLanguage(lang);
  }

  toggleLanguage(): void {
    this.translationService.toggleLanguage();
  }

  get currentLanguage(): string {
    return this.translationService.currentLanguage;
  }

  logout() {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  /** Utilisateur courant (pour l'affichage du pill de profil). */
  get currentUser() {
    return this.authService.currentUser;
  }

  /** Ouvre la gestion des comptes (ADMIN). */
  openUserManagement() {
    this.router.navigate(['/admin/users']);
  }

  openSettings() {
    this.router.navigate(['/settings']);
  }
}
