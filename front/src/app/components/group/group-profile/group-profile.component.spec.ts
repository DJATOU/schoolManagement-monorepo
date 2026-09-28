import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GroupProfileComponent } from './group-profile.component';
import { activatedRouteProviders, setupComponentTestBed } from '../../../../testing/setup';

/**
 * Fiche d'un groupe, ouverte sur l'identifiant porté par l'URL.
 *
 * <p>Les chargements déclenchés par `ngOnInit` sont laissés en attente : ce qui est vérifié
 * est que la fiche se construit sur l'identifiant de la route et n'affiche, avant réponse,
 * ni photo cassée ni signalement d'année passée — deux états qui induiraient en erreur.</p>
 */
describe('GroupProfileComponent', () => {
  let component: GroupProfileComponent;
  let fixture: ComponentFixture<GroupProfileComponent>;

  beforeEach(async () => {
    await setupComponentTestBed(GroupProfileComponent, {
      providers: activatedRouteProviders({ id: '5' })
    });
    fixture = TestBed.createComponent(GroupProfileComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('n\'expose aucune URL de photo avant le chargement du groupe', () => {
    // Une URL construite sur un groupe encore inconnu produirait une image cassée.
    expect(component.groupPhotoUrl).toBe('');
  });

  it('ne signale pas d\'année passée par défaut', () => {
    // Le gel des modifications ne doit pas s'appliquer par défaut, sans quoi la fiche
    // s'ouvrirait en lecture seule sur un groupe de l'année courante.
    component.groupIsPastYear$.subscribe(isPastYear => expect(isPastYear).toBeFalse());
  });
});
