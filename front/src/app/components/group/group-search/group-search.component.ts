import { Component, OnInit, OnDestroy, ElementRef, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { HttpClientModule } from '@angular/common/http';
import { Router, RouterModule } from '@angular/router';
import { MatSelectModule } from '@angular/material/select';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { GroupService } from '../../../services/group.service';
import { LevelService } from '../../../services/level.service';
import { GroupTypeService } from '../../../services/GroupTypeService';
import { Group } from '../../../models/group/group';
import { Level } from '../../../models/level/level';
import { GroupType } from '../../../models/GroupType/groupType';
import { CommonModule } from '@angular/common';
import { GroupCardComponent } from '../group-card/group-card.component';
import { GroupListComponent } from '../group-list/group-list.component';
import { SearchService } from '../../../services/SearchService ';
import { ViewToggleComponent } from '../../shared/view-toggle/view-toggle.component';
import { ListHeaderComponent } from '../../shared/list-header/list-header.component';
import { TranslateModule } from '@ngx-translate/core';
import { Subject, takeUntil } from 'rxjs';
import { SchoolYearContextService } from '../../../services/school-year-context.service';

@Component({
  selector: 'app-group-search',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    HttpClientModule,
    RouterModule,
    MatIconModule,
    MatSelectModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    CommonModule,
    GroupCardComponent,
    GroupListComponent,
    ViewToggleComponent,
    ListHeaderComponent,
    TranslateModule
  ],
  templateUrl: './group-search.component.html',
  styleUrls: ['./group-search.component.scss']
})
export class GroupSearchComponent implements OnInit, OnDestroy {
  searchForm!: FormGroup;
  groups: Group[] = [];
  allGroups: Group[] = [];
  levels: Level[] = [];
  groupTypes: GroupType[] = [];
  filteredGroups: Group[] = [];
  currentPageGroups: Group[] = [];

  /** Cartes affichées : la page courante du paginateur, et elle seule. */
  displayedGroups: Group[] = [];
  currentPageIndex: number = 0;

  totalGroups: number = 0;
  pageSize: number = 8;
  pageSizeOptions: number[] = [8, 12, 16, 20];
  viewMode: 'card' | 'list' = 'card';
  isLoading = true;
  showActiveOnly = false;

  @ViewChild('contentArea') contentArea!: ElementRef;

  /** Année scolaire sélectionnée (contexte global) appliquée au chargement des groupes. */
  private selectedSchoolYearId: number | null = null;

  /** Désabonnement à la destruction du composant. */
  private readonly destroy$ = new Subject<void>();

  constructor(
    private fb: FormBuilder,
    private groupService: GroupService,
    private levelService: LevelService,
    private groupTypeService: GroupTypeService,
    private searchService: SearchService,
    private router: Router,
    private schoolYearContext: SchoolYearContextService
  ) {
  }

  ngOnInit(): void {
    this.searchForm = this.fb.group({
      searchTerm: ['']
    });

    this.pageSize = this.getSmartPageSize();
    this.loadSelectOptions();
    this.listenToSearchEvents();

    // Recharge la liste des groupes à chaque changement d'année sélectionnée
    // (groupes de l'année choisie ; lecture seule pour une année passée).
    this.schoolYearContext.selectedSchoolYear$
      .pipe(takeUntil(this.destroy$))
      .subscribe((year) => {
        this.selectedSchoolYearId = year?.id ?? null;
        this.loadAllGroups();
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
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

  loadSelectOptions(): void {
    this.levelService.getLevels().subscribe(data => this.levels = data);
    this.groupTypeService.getAllGroupTypes().subscribe(data => this.groupTypes = data);
  }

  listenToSearchEvents(): void {
    this.searchService.getSearch().subscribe((searchTerm: string) => {
      this.handleSearch(searchTerm);
    });
  }

  handleSearch(searchTerm: string): void {
    if (!searchTerm) {
      this.loadAllGroups();
    } else {
      this.groupService.searchGroupsByNameStartingWith(searchTerm).subscribe(groups => {
        if (groups.length === 1) {
          this.router.navigate(['/group', groups[0].id]);
        } else {
          this.allGroups = this.sortByName(groups);
          this.applyFilters();
        }
      });
    }
  }

  loadAllGroups(): void {
    this.isLoading = true;
    this.groupService.getGroups(this.selectedSchoolYearId ?? undefined).subscribe(groups => {
      this.allGroups = this.sortByName(groups);
      this.applyFilters();
      this.isLoading = false;
    });
  }

  /** Tri alphabétique par défaut sur le nom du groupe, insensible à la casse/accents. */
  private sortByName(groups: Group[]): Group[] {
    return [...(groups || [])].sort((a, b) =>
      (a.name ?? '').localeCompare(b.name ?? '', 'fr', { sensitivity: 'base' })
    );
  }

  /**
   * Apply active filter if enabled
   */
  private applyFilters(): void {
    if (this.showActiveOnly) {
      this.filteredGroups = this.allGroups.filter(g => g.active === true);
    } else {
      this.filteredGroups = [...this.allGroups];
    }
    this.currentPageIndex = 0;
    this.updatePageGroups();
  }

  /**
   * Handle active filter toggle
   */
  onActiveFilterChange(showActiveOnly: boolean): void {
    this.showActiveOnly = showActiveOnly;
    this.applyFilters();
  }

  changePage(event: PageEvent): void {
    this.currentPageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.updatePageGroups();
  }

  changeViewMode(mode: 'card' | 'list'): void {
    this.viewMode = mode;
  }

  /**
   * Affiche la page courante du paginateur.
   *
   * <p>L'écran mêlait deux mécanismes : le compteur et le paginateur lisaient la liste entière
   * (« 1 – 16 sur 16 »), mais les cartes venaient d'un défilement infini qui en chargeait 10, puis
   * la suite au défilement. Un écran assez grand pour les 10 premières ne défile pas : les 6 autres
   * ne s'affichaient jamais, et changer de page ne changeait rien. Le paginateur décide désormais
   * seul, comme sur la liste des élèves.</p>
   */
  private updatePageGroups(): void {
    this.totalGroups = this.filteredGroups.length;
    const startIndex = this.currentPageIndex * this.pageSize;
    this.currentPageGroups = this.filteredGroups.slice(startIndex, startIndex + this.pageSize);
    this.displayedGroups = this.currentPageGroups;
  }
}
