import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { TranslateModule } from '@ngx-translate/core';

import { SchoolYearSelectorComponent } from './school-year-selector.component';
import { SchoolYearService } from '../../services/school-year.service';
import { SchoolYearContextService } from '../../services/school-year-context.service';
import { SchoolYear } from '../../models/schoolYear/school-year';

/**
 * Tests du sélecteur global d'année scolaire (School_Year_Selector).
 *
 * Couvre:
 *  - Chargement de la liste des années via SchoolYearService.getAll()
 *  - Reflet de la sélection courante issue du service de contexte (Requirement 10.3)
 *  - onSchoolYearChange(id) délègue à SchoolYearContextService.setSelectedSchoolYear
 *    avec l'année correspondante (Requirement 10.3)
 *
 * Les services sont mockés avec des spies jasmine ; les observables utilisent `of(...)`.
 */
describe('SchoolYearSelectorComponent', () => {
  const currentYear: SchoolYear = {
    id: 2,
    label: '2025-2026',
    startDate: '2025-09-01',
    endDate: '2026-06-30',
    isCurrent: true,
  };

  const pastYear: SchoolYear = {
    id: 1,
    label: '2024-2025',
    startDate: '2024-09-01',
    endDate: '2025-06-30',
    isCurrent: false,
  };

  let schoolYearServiceSpy: jasmine.SpyObj<SchoolYearService>;
  let contextServiceSpy: jasmine.SpyObj<SchoolYearContextService>;

  /**
   * Construit le composant après avoir configuré les valeurs de retour des
   * spies (getAll() et selectedSchoolYear$ sont consommés dans ngOnInit).
   */
  function createComponent(): ComponentFixture<SchoolYearSelectorComponent> {
    TestBed.configureTestingModule({
      imports: [
        SchoolYearSelectorComponent,
        NoopAnimationsModule,
        TranslateModule.forRoot(),
      ],
      providers: [
        { provide: SchoolYearService, useValue: schoolYearServiceSpy },
        { provide: SchoolYearContextService, useValue: contextServiceSpy },
      ],
    });
    const fixture = TestBed.createComponent(SchoolYearSelectorComponent);
    fixture.detectChanges(); // déclenche ngOnInit
    return fixture;
  }

  beforeEach(() => {
    schoolYearServiceSpy = jasmine.createSpyObj<SchoolYearService>('SchoolYearService', ['getAll']);
    contextServiceSpy = jasmine.createSpyObj<SchoolYearContextService>(
      'SchoolYearContextService',
      ['setSelectedSchoolYear'],
      { selectedSchoolYear$: of(currentYear) }
    );
  });

  it('should be created', () => {
    schoolYearServiceSpy.getAll.and.returnValue(of([currentYear, pastYear]));
    const fixture = createComponent();
    expect(fixture.componentInstance).toBeTruthy();
  });

  describe('chargement des années scolaires', () => {
    it('charge la liste des années via getAll()', () => {
      schoolYearServiceSpy.getAll.and.returnValue(of([currentYear, pastYear]));

      const fixture = createComponent();

      expect(schoolYearServiceSpy.getAll).toHaveBeenCalledTimes(1);
      expect(fixture.componentInstance.schoolYears).toEqual([currentYear, pastYear]);
    });

    it('gère une liste vide en cas d\'erreur de chargement', () => {
      schoolYearServiceSpy.getAll.and.returnValue(throwError(() => new Error('boom')));

      const fixture = createComponent();

      expect(fixture.componentInstance.schoolYears).toEqual([]);
    });

    it('reflète la sélection courante issue du service de contexte (Requirement 10.3)', () => {
      schoolYearServiceSpy.getAll.and.returnValue(of([currentYear, pastYear]));

      const fixture = createComponent();

      expect(fixture.componentInstance.selectedSchoolYearId).toBe(currentYear.id!);
    });
  });

  describe('changement de sélection (Requirement 10.3)', () => {
    it('appelle setSelectedSchoolYear avec l\'année correspondant à l\'id choisi', () => {
      schoolYearServiceSpy.getAll.and.returnValue(of([currentYear, pastYear]));
      const fixture = createComponent();

      fixture.componentInstance.onSchoolYearChange(pastYear.id!);

      expect(contextServiceSpy.setSelectedSchoolYear).toHaveBeenCalledTimes(1);
      expect(contextServiceSpy.setSelectedSchoolYear).toHaveBeenCalledWith(pastYear);
    });

    it('ne fait rien si l\'id choisi ne correspond à aucune année connue', () => {
      schoolYearServiceSpy.getAll.and.returnValue(of([currentYear, pastYear]));
      const fixture = createComponent();

      fixture.componentInstance.onSchoolYearChange(999);

      expect(contextServiceSpy.setSelectedSchoolYear).not.toHaveBeenCalled();
    });
  });
});
