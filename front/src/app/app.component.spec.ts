import { TestBed } from '@angular/core/testing';
import { AppComponent } from './app.component';
import { setupComponentTestBed } from '../testing/setup';

/**
 * Le composant racine ne fait qu'une chose : choisir entre le shell applicatif (navigation +
 * menu latéral) et la sortie de routeur publique, selon qu'un utilisateur est connecté.
 */
describe('AppComponent', () => {
  beforeEach(async () => {
    await setupComponentTestBed(AppComponent);
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it(`should have the 'schoolManagement-front' title`, () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app.title).toEqual('schoolManagement-front');
  });

  it('hors session, n\'affiche que la sortie de routeur publique', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    // Aucun utilisateur connecté : le shell (barre de navigation, menu latéral) reste absent,
    // seule la page publique — l'écran de connexion — est rendue.
    expect(compiled.querySelector('router-outlet')).not.toBeNull();
    expect(compiled.querySelector('app-navigation')).toBeNull();
    expect(compiled.querySelector('app-side-menu')).toBeNull();
  });

  it('toggleSidenav() inverse l\'état d\'ouverture du menu latéral', () => {
    const app = TestBed.createComponent(AppComponent).componentInstance;

    expect(app.isSidenavOpen).toBeTrue();
    app.toggleSidenav();
    expect(app.isSidenavOpen).toBeFalse();
  });
});
