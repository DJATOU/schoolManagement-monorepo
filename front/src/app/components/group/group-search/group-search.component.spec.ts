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

  /** 16 groupes, comme la base de démonstration : « Groupe 01 » à « Groupe 16 ». */
  const sixteen = () => Array.from({ length: 16 }, (_, i) => group(i + 1, `Groupe ${String(i + 1).padStart(2, '0')}`, true));

  it('affiche toute la page annoncée par le paginateur : 16 sur 16, pas 10', () => {
    // Défaut corrigé : le paginateur annonçait « 1 – 16 sur 16 », mais les cartes venaient d'un
    // défilement infini qui en chargeait 10. L'écran ne défilant pas, les 6 autres ne venaient jamais.
    component.pageSize = 16;
    flushGroups(sixteen());

    expect(component.totalGroups).toBe(16);
    expect(component.displayedGroups.length).toBe(16);
    expect(fixture.nativeElement.querySelectorAll('app-group-card').length).toBe(16);
  });

  it('changer de page affiche la page demandée, et la taille choisie', () => {
    component.pageSize = 12;
    flushGroups(sixteen());
    expect(fixture.nativeElement.querySelectorAll('app-group-card').length).toBe(12);

    component.changePage({ pageIndex: 1, pageSize: 12, length: 16 });
    fixture.detectChanges();

    expect(component.displayedGroups.map(g => g.name)).toEqual(['Groupe 13', 'Groupe 14', 'Groupe 15', 'Groupe 16']);
    expect(fixture.nativeElement.querySelectorAll('app-group-card').length).toBe(4);

    component.changePage({ pageIndex: 0, pageSize: 20, length: 16 });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('app-group-card').length).toBe(16);
  });

  it('un filtre ramène à la première page', () => {
    component.pageSize = 12;
    flushGroups(sixteen());
    component.changePage({ pageIndex: 1, pageSize: 12, length: 16 });

    component.onActiveFilterChange(true);

    expect(component.currentPageIndex).toBe(0);
    expect(component.displayedGroups[0].name).toBe('Groupe 01');
  });
});
