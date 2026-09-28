import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { GroupSelectorComponent } from './group-selector.component';
import { setupComponentTestBed } from '../../../../testing/setup';
import { aGroup } from '../../../../testing/fixtures';
import { API_BASE_URL } from '../../../api-base-url';

/**
 * Sélecteur de groupe des écrans de filtrage.
 *
 * <p>Le composant préfixe la liste d'une entrée « All Groups » d'identifiant 0 et la
 * présélectionne. Sans elle, l'écran s'ouvrirait filtré sur un groupe arbitraire — le premier
 * renvoyé par l'API — sans que l'utilisateur l'ait demandé.</p>
 */
describe('GroupSelectorComponent', () => {
  let component: GroupSelectorComponent;
  let fixture: ComponentFixture<GroupSelectorComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await setupComponentTestBed(GroupSelectorComponent);
    fixture = TestBed.createComponent(GroupSelectorComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  /** Répond à la requête de chargement des groupes émise par `ngOnInit`. */
  function flushGroups(groups = [aGroup(), aGroup({ id: 6, name: 'Physique 1B' })]): void {
    httpMock.expectOne(req => req.url.startsWith(`${API_BASE_URL}/api/groups`)).flush(groups);
    fixture.detectChanges();
  }

  it('should create', () => {
    flushGroups();
    expect(component).toBeTruthy();
  });

  it('préfixe la liste d\'une entrée « tous les groupes » et la présélectionne', () => {
    flushGroups();

    expect(component.groups[0].id).toBe(0);
    expect(component.groups[0].name).toBe('All Groups');
    expect(component.selectedGroup.value).toBe(0);
  });

  it('conserve les groupes reçus après l\'entrée « tous les groupes »', () => {
    flushGroups();

    expect(component.groups.map(group => group.name))
      .toEqual(['All Groups', 'Maths 1B', 'Physique 1B']);
  });

  it('nomme le groupe sélectionné', () => {
    flushGroups();

    component.selectedGroup.setValue(6);

    expect(component.getSelectedGroupName()).toBe('Physique 1B');
  });

  it('retombe sur « All Groups » quand la sélection ne correspond à aucun groupe', () => {
    flushGroups();

    component.selectedGroup.setValue(999);

    expect(component.getSelectedGroupName()).toBe('All Groups');
  });
});
