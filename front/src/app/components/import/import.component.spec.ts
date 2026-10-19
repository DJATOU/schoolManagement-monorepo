import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ImportComponent } from './import.component';
import { ImportService } from '../../services/import.service';
import { setupComponentTestBed } from '../../../testing/setup';

/**
 * Carte « Inscriptions » de la page d'import : placée après les groupes (ordre de dépendance),
 * avec sa note d'aide, et ses erreurs et avertissements par ligne affichés comme ceux des autres
 * cartes.
 */
describe('ImportComponent', () => {
  let fixture: ComponentFixture<ImportComponent>;
  let component: ImportComponent;
  let importService: jasmine.SpyObj<ImportService>;

  beforeEach(async () => {
    importService = jasmine.createSpyObj<ImportService>('ImportService', ['importEnrolments', 'importGroups']);
    await setupComponentTestBed(ImportComponent, {
      providers: [{ provide: ImportService, useValue: importService }]
    });
    fixture = TestBed.createComponent(ImportComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('la carte des inscriptions est la dernière, juste après celle des groupes, avec une note', () => {
    const kinds = component.cards.map((card) => card.kind);
    expect(kinds.length).toBe(9);
    expect(kinds[kinds.length - 1]).toBe('enrolments');
    expect(kinds[kinds.length - 2]).toBe('groups');

    const enrolments = component.cards[kinds.length - 1];
    expect(enrolments.titleKey).toBe('IMPORT.ENROLMENTS.TITLE');
    expect(enrolments.columnsKey).toBe('IMPORT.ENROLMENTS.COLUMNS');
    expect(enrolments.noteKey).toBe('IMPORT.ENROLMENTS.NOTE');

    const cards = fixture.nativeElement.querySelectorAll('mat-card');
    expect(cards.length).toBe(9);
    expect(cards[8].textContent).toContain('IMPORT.ENROLMENTS.NOTE');
  });

  it('runImport(\'enrolments\') appelle importEnrolments et liste les erreurs par ligne', () => {
    const file = new File(['firstName,lastName,group,arrivalDate\n'], '9-inscriptions.csv', { type: 'text/csv' });
    importService.importEnrolments.and.returnValue(of({
      imported: 2,
      errors: [{ line: 3, message: 'Déjà inscrit : Léa Haddad suit déjà le groupe « Physique 4 AM ».' }],
      warnings: []
    }));
    component.selectedFiles['enrolments'] = file;

    component.runImport('enrolments');
    fixture.detectChanges();

    expect(importService.importEnrolments).toHaveBeenCalledOnceWith(file);
    expect(importService.importGroups).not.toHaveBeenCalled();
    const card: HTMLElement = fixture.nativeElement.querySelectorAll('mat-card')[8];
    expect(card.querySelector('.import-summary')?.textContent).toContain('2');
    const errors = Array.from(card.querySelectorAll('.import-errors li')).map((li) => li.textContent?.trim());
    expect(errors).toEqual(['IMPORT.LINE 3 : Déjà inscrit : Léa Haddad suit déjà le groupe « Physique 4 AM ».']);
  });

  it('runImport(\'enrolments\') affiche les avertissements (téléphone différent) à part des erreurs', () => {
    const file = new File(['firstName,lastName,group,arrivalDate,phoneNumber\n'], '9-inscriptions.csv', { type: 'text/csv' });
    const warning = 'Téléphone différent : Léa Haddad est inscrit, mais le téléphone du fichier (0699999999) '
      + 'ne correspond pas à celui de sa fiche (0550000102). Vérifiez qu\'il s\'agit du bon élève.';
    importService.importEnrolments.and.returnValue(of({ imported: 1, errors: [], warnings: [{ line: 2, message: warning }] }));
    component.selectedFiles['enrolments'] = file;

    component.runImport('enrolments');
    fixture.detectChanges();

    const card: HTMLElement = fixture.nativeElement.querySelectorAll('mat-card')[8];
    expect(card.querySelectorAll('.import-errors li').length).toBe(0);
    const warnings = Array.from(card.querySelectorAll('.import-warnings li')).map((li) => li.textContent ?? '');
    expect(warnings.length).toBe(1);
    expect(warnings[0]).toContain(`IMPORT.LINE 2 : ${warning}`);
  });
});
