import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { ReusableDatatableComponent } from './reusable-datatable.component';
import { setupComponentTestBed } from '../../../../testing/setup';

/**
 * Le tableau réutilisable est piloté entièrement par ses entrées : les colonnes et
 * l'observable de données sont obligatoires (le composant les consomme dans `ngOnInit`).
 */
describe('ReusableDatatableComponent', () => {
  let component: ReusableDatatableComponent;
  let fixture: ComponentFixture<ReusableDatatableComponent>;

  /** Ligne du jeu d'essai : deux colonnes suffisent à exercer l'affichage et la sélection. */
  interface Row { id: number; name: string }

  const columns = [
    { columnDef: 'id', header: 'ID', cell: (row: Row) => `${row.id}` },
    { columnDef: 'name', header: 'Nom', cell: (row: Row) => `${row.name}` },
  ];

  beforeEach(async () => {
    await setupComponentTestBed(ReusableDatatableComponent);

    fixture = TestBed.createComponent(ReusableDatatableComponent);
    component = fixture.componentInstance;
    component.columns = columns;
    component.dataType = 'level';
    component.observable = of([{ id: 1, name: 'Première année' }]);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('préfixe les colonnes affichées par la colonne de sélection', () => {
    expect(component.displayedColumns).toEqual(['select', 'id', 'name']);
  });

  it('rend l\'en-tête et une ligne par élément reçu', () => {
    const headers = Array.from<HTMLElement>(
      fixture.nativeElement.querySelectorAll('th[mat-header-cell]')
    ).map(th => th.textContent?.trim());
    expect(headers).toContain('ID');
    expect(headers).toContain('Nom');

    const rows = fixture.nativeElement.querySelectorAll('tr[mat-row]');
    expect(rows.length).toBe(1);
    expect(rows[0].textContent).toContain('Première année');
  });

  it('display() efface les valeurs manquantes au lieu d\'écrire « undefined »', () => {
    // Les fabriques `cell()` interpolent des champs absents : la chaîne « undefined »
    // ne doit jamais atteindre l'écran ni le PDF.
    expect(component.display(undefined)).toBe('');
    expect(component.display(null)).toBe('');
    expect(component.display('undefined')).toBe('');
    expect(component.display(' Salle A ')).toBe('Salle A');
  });

  it('la sélection totale et le compteur de lignes suivent les données', () => {
    expect(component.rowCount).toBe(1);
    expect(component.hasData).toBeTrue();
    expect(component.selection.hasValue()).toBeFalse();

    component.toggleAllRows();
    expect(component.isAllSelected()).toBeTrue();
    expect(component.isOneItemSelected()).toBeTrue();
  });
});
