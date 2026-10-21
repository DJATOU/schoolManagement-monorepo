import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { TranslateService } from '@ngx-translate/core';

import { ReusableDatatableComponent } from './reusable-datatable.component';
import { ProfilePdfService } from '../../../services/profile-pdf.service';
import { setupComponentTestBed } from '../../../../testing/setup';
import frTranslations from '../../../../assets/i18n/fr.json';
import enTranslations from '../../../../assets/i18n/en.json';
import arTranslations from '../../../../assets/i18n/ar.json';
import { pdfLanguage } from '../../../shared/pdf-i18n';
import { choosePrintLanguage } from '../../../../testing/print-language';

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

  /**
   * Export PDF : en français sous l'arabe (pdfmake ne dessine pas l'arabe), en-têtes-clés traduits
   * dans la langue du document, cellules de l'écran appelant calculées dans cette langue.
   */
  describe('export PDF', () => {
    let translate: TranslateService;
    let generate: jasmine.Spy;

    beforeEach(() => {
      translate = TestBed.inject(TranslateService);
      translate.setTranslation('fr', frTranslations);
      translate.setTranslation('en', enTranslations);
      translate.setTranslation('ar', arTranslations);
      generate = spyOn(TestBed.inject(ProfilePdfService), 'generateProfilePdf');
      // Une cellule qui dépend de la langue, comme les dates de tarif (noms de mois).
      component.columns = [
        { columnDef: 'name', header: 'LEVEL_TABLE.NAME', cell: (row: Row) => row.name },
        { columnDef: 'lang', header: 'ID', cell: () => `langue ${translate.currentLang}` }
      ];
    });

    it('interface en arabe : titre, sous-titre, en-têtes et cellules en français, aucun caractère arabe', () => {
      translate.use('ar');
      component.onPrint();

      const pdf = generate.calls.mostRecent().args[0];
      expect(pdf.title).toBe(frTranslations.DATATABLE.ENTITIES.level.title);
      expect(pdf.tableColumns).toEqual([frTranslations.LEVEL_TABLE.NAME, 'ID']);
      expect(pdf.tableRows).toEqual([['Première année', 'langue fr']]);
      expect(JSON.stringify(pdf)).not.toMatch(/[\u0600-\u06FF]/);
      expect(translate.currentLang).toBe('ar');
    });

    it('interface en anglais : document en anglais', () => {
      translate.use('en');
      component.onPrint();

      const pdf = generate.calls.mostRecent().args[0];
      expect(pdf.title).toBe(enTranslations.DATATABLE.ENTITIES.level.title);
      expect(pdf.tableColumns).toEqual([enTranslations.LEVEL_TABLE.NAME, 'ID']);
      expect(pdf.tableRows).toEqual([['Première année', 'langue en']]);
    });

    it('bouton « Imprimer » sous l\'arabe : document en arabe, cellules calculées en arabe', () => {
      translate.use('ar');
      fixture.detectChanges();
      let printedIn = '';
      generate.and.callFake(() => { printedIn = pdfLanguage(translate); });
      (fixture.nativeElement.querySelector('.toolbar-actions button.dt-btn') as HTMLButtonElement).click();
      const pdf = generate.calls.mostRecent().args[0];
      expect(printedIn).toBe('ar');
      expect(pdf.title).toBe(arTranslations.DATATABLE.ENTITIES.level.title);
      expect(pdf.tableColumns).toEqual([arTranslations.LEVEL_TABLE.NAME, 'ID']);
      expect(pdf.tableRows).toEqual([['Première année', 'langue ar']]);
    });

    it('menu « English » sous le français : document et cellules en anglais, écran inchangé', () => {
      translate.use('fr');
      fixture.detectChanges();
      let printedIn = '';
      generate.and.callFake(() => { printedIn = pdfLanguage(translate); });
      choosePrintLanguage(fixture, '.toolbar-actions', 'en');
      const pdf = generate.calls.mostRecent().args[0];
      expect(printedIn).toBe('en');
      expect(pdf.tableColumns).toEqual([enTranslations.LEVEL_TABLE.NAME, 'ID']);
      expect(pdf.tableRows).toEqual([['Première année', 'langue en']]);
      expect(translate.currentLang).toBe('fr');
    });
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
