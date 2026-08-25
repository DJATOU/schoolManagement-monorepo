import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GroupListComponent } from './group-list.component';
import { Group } from '../../../models/group/group';
import { setupComponentTestBed } from '../../../../testing/setup';

/**
 * Ligne de liste d'un groupe. Le groupe est une entrée obligatoire : le composant résout
 * niveau, type et initiales à partir de lui dans `ngOnInit`.
 */
describe('GroupListComponent', () => {
  let component: GroupListComponent;
  let fixture: ComponentFixture<GroupListComponent>;

  const group: Group = {
    id: 12,
    name: 'Maths Avancées',
    groupTypeId: 1,
    levelId: 2,
    subjectId: 3,
    sessionNumberPerSerie: 8,
    priceId: 4,
    teacherId: 5,
    levelName: 'Première année',
    groupTypeName: 'Petit groupe'
  };

  beforeEach(async () => {
    await setupComponentTestBed(GroupListComponent);

    fixture = TestBed.createComponent(GroupListComponent);
    component = fixture.componentInstance;
    component.group = group;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('affiche le nom du groupe, son niveau et son type', () => {
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Maths Avancées');
    expect(text).toContain('Première année');
    expect(text).toContain('Petit groupe');
  });

  it('privilégie les libellés du serveur sur les tableaux de référence', () => {
    // Les libellés renvoyés par l'API sont prioritaires ; les tableaux de référence ne
    // servent que de repli pour les écrans qui les chargent déjà.
    expect(component.level).toBe('Première année');
    expect(component.type).toBe('Petit groupe');
  });

  it('getInitials() rend au plus deux lettres', () => {
    expect(component.getInitials()).toBe('MA');
  });
});
