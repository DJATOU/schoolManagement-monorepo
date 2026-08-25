import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { GroupSearchComponent } from './group-search.component';
import { Group } from '../../../models/group/group';
import { API_BASE_URL } from '../../../api-base-url';
import { setupComponentTestBed } from '../../../../testing/setup';

describe('GroupSearchComponent', () => {
  let component: GroupSearchComponent;
  let fixture: ComponentFixture<GroupSearchComponent>;
  let httpMock: HttpTestingController;

  function group(id: number, name: string, active: boolean): Group {
    return {
      id, name, active,
      groupTypeId: 1, levelId: 1, subjectId: 1,
      sessionNumberPerSerie: 8, priceId: 1, teacherId: 1
    };
  }

  /** Sert la requête de liste ouverte par ngOnInit. */
  function flushGroups(groups: Group[]): void {
    httpMock.match(req => req.url === `${API_BASE_URL}/api/groups`)
      .forEach(req => req.flush(groups));
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await setupComponentTestBed(GroupSearchComponent);

    fixture = TestBed.createComponent(GroupSearchComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('affiche les cartes par défaut et démarre en chargement', () => {
    expect(component.viewMode).toBe('card');
    expect(component.isLoading).toBeTrue();
  });

  it('trie les groupes par nom, insensiblement à la casse et aux accents', () => {
    flushGroups([group(1, 'Physique', true), group(2, 'élémentaire', true), group(3, 'Maths', true)]);

    expect(component.isLoading).toBeFalse();
    expect(component.filteredGroups.map(g => g.name)).toEqual(['élémentaire', 'Maths', 'Physique']);
  });

  it('le filtre « actifs seulement » écarte les groupes désactivés', () => {
    flushGroups([group(1, 'Maths', true), group(2, 'Physique', false)]);

    component.onActiveFilterChange(true);

    expect(component.filteredGroups.map(g => g.name)).toEqual(['Maths']);
    expect(component.totalGroups).toBe(1);
  });
});
