import { Component, OnInit } from '@angular/core';
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
    ListHeaderComponent
  ],
  templateUrl: './group-search.component.html',
  styleUrls: ['./group-search.component.scss']
})
export class GroupSearchComponent implements OnInit {
  searchForm!: FormGroup;
  groups: Group[] = [];
  allGroups: Group[] = []; // Keep original list for filtering
  levels: Level[] = [];
  groupTypes: GroupType[] = [];
  filteredGroups: Group[] = [];
  currentPageGroups: Group[] = [];
  totalGroups: number = 0;
  pageSize: number = 8;
  pageSizeOptions: number[] = [8, 12, 16, 20];
  viewMode: 'card' | 'list' = 'card';
  isLoading = true;
  showActiveOnly = false;

  constructor(
    private fb: FormBuilder,
    private groupService: GroupService,
    private levelService: LevelService,
    private groupTypeService: GroupTypeService,
    private searchService: SearchService,
    private router: Router
  ) {
  }

  ngOnInit(): void {
    this.searchForm = this.fb.group({
      searchTerm: ['']
    });

    this.pageSize = this.getSmartPageSize();
    this.loadSelectOptions();
    this.listenToSearchEvents();
    this.loadAllGroups();
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
          this.allGroups = groups;
          this.applyFilters();
        }
      });
    }
  }

  loadAllGroups(): void {
    this.isLoading = true;
    this.groupService.getGroups().subscribe(groups => {
      this.allGroups = groups;
      this.applyFilters();
      this.isLoading = false;
    });
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
    const startIndex = event.pageIndex * event.pageSize;
    const endIndex = startIndex + event.pageSize;
    this.currentPageGroups = this.filteredGroups.slice(startIndex, endIndex);
    this.pageSize = event.pageSize;
  }

  changeViewMode(mode: 'card' | 'list'): void {
    this.viewMode = mode;
  }

  private updatePageGroups(): void {
    this.totalGroups = this.filteredGroups.length;
    this.currentPageGroups = this.filteredGroups.slice(0, this.pageSize);
  }
}
