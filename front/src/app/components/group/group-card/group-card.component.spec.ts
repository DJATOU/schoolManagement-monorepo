import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { GroupCardComponent } from './group-card.component';
import { setupComponentTestBed } from '../../../../testing/setup';
import { aGroup } from '../../../../testing/fixtures';

/**
 * `group` est une entrée obligatoire, déréférencée par `ngOnInit` pour résoudre le niveau,
 * le type, la photo et l'année scolaire.
 *
 * <p>Le point que ces tests verrouillent : le niveau et le type viennent en priorité des
 * libellés déjà fournis par le backend (`levelName`, `groupTypeName`). S'appuyer uniquement
 * sur les tableaux `levels` / `groupTypes` affichait « — » dès qu'un écran ne les chargeait
 * pas, ce qui fut le cas du profil enseignant.</p>
 */
describe('GroupCardComponent', () => {
  let component: GroupCardComponent;
  let fixture: ComponentFixture<GroupCardComponent>;

  beforeEach(async () => {
    await setupComponentTestBed(GroupCardComponent);
    fixture = TestBed.createComponent(GroupCardComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('group', aGroup());
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('affiche le nom du groupe', () => {
    expect(fixture.nativeElement.textContent).toContain('Maths 1B');
  });

  it('résout le niveau et le type depuis les libellés du backend, sans tableau de correspondance', () => {
    expect(component.level).toBe('2AS');
    expect(component.type).toBe('Petit groupe');
  });

  it('retombe sur les tableaux de correspondance quand le backend ne fournit pas les libellés', () => {
    const fallback = TestBed.createComponent(GroupCardComponent);
    fallback.componentRef.setInput('group',
      aGroup({ levelName: undefined, groupTypeName: undefined }));
    fallback.componentRef.setInput('levels', [{ id: 3, name: '1AS' }]);
    fallback.componentRef.setInput('groupTypes', [{ id: 2, name: 'Grand groupe' }]);
    fallback.detectChanges();

    expect(fallback.componentInstance.level).toBe('1AS');
    expect(fallback.componentInstance.type).toBe('Grand groupe');
  });

  it('affiche un tiret plutôt que « Unknown » quand aucune source ne renseigne le niveau', () => {
    const unknown = TestBed.createComponent(GroupCardComponent);
    unknown.componentRef.setInput('group',
      aGroup({ levelName: undefined, groupTypeName: undefined }));
    unknown.detectChanges();

    expect(unknown.componentInstance.level).toBe('—');
    expect(unknown.componentInstance.type).toBe('—');
  });

  it('compose les initiales sur la première lettre des deux premiers mots', () => {
    // « Maths 1B » donne « M1 » : le chiffre du second mot est significatif, deux groupes
    // d'une même matière ne se distinguent que par lui.
    expect(component.getInitials()).toBe('M1');
  });

  it('prend les deux premières lettres quand le nom est d\'un seul mot', () => {
    const singleWord = TestBed.createComponent(GroupCardComponent);
    singleWord.componentRef.setInput('group', aGroup({ name: 'Physique' }));
    singleWord.detectChanges();

    expect(singleWord.componentInstance.getInitials()).toBe('PH');
  });

  it('navigue vers la fiche du groupe', () => {
    const router = TestBed.inject(Router);
    const navigate = spyOn(router, 'navigate');

    component.navigateToGroupProfile();

    expect(navigate).toHaveBeenCalledWith(['/group', 5]);
  });

  it('ne signale rien quand aucune année scolaire n\'est sélectionnée', () => {
    // Le signalement « hors année sélectionnée » ne doit pas s'allumer par défaut, sans quoi
    // il apparaîtrait sur tous les groupes.
    expect(component.outsideSelectedYear).toBeFalse();
  });
});
