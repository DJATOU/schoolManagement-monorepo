import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { take } from 'rxjs/operators';

import { SchoolYearContextService } from '../school-year-context.service';
import { SchoolYearService } from '../school-year.service';
import { SchoolYear } from '../../models/schoolYear/school-year';

/**
 * Tests de l'état lecture seule (Read_Only_History) du service de contexte
 * d'année scolaire (Requirement 9.4).
 *
 * Le rendu lecture seule côté frontend est piloté par `readOnly$` / `isReadOnly()` :
 *  - `readOnly$` émet `true` lorsqu'une année NON courante est sélectionnée,
 *  - `readOnly$` émet `false` lorsque l'année courante est sélectionnée.
 *
 * Ces flux/valeurs alimentent les liaisons `[disabled]` des vues
 * group/session/payment (contrôles désactivés pour l'historique en lecture seule).
 *
 * `getCurrent()` est appelé dans le constructeur : le spy est donc configuré
 * AVANT `TestBed.inject(SchoolYearContextService)`.
 */
describe('SchoolYearContextService - lecture seule (Requirement 9.4)', () => {
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

  function createService(): SchoolYearContextService {
    TestBed.configureTestingModule({
      providers: [
        SchoolYearContextService,
        { provide: SchoolYearService, useValue: schoolYearServiceSpy },
      ],
    });
    return TestBed.inject(SchoolYearContextService);
  }

  beforeEach(() => {
    schoolYearServiceSpy = jasmine.createSpyObj<SchoolYearService>('SchoolYearService', ['getCurrent']);
  });

  describe('année courante sélectionnée -> édition autorisée', () => {
    it('isReadOnly() retourne false quand l\'année courante est sélectionnée', () => {
      schoolYearServiceSpy.getCurrent.and.returnValue(of(currentYear));

      const service = createService();

      expect(service.isReadOnly()).toBeFalse();
    });

    it('readOnly$ émet false quand l\'année courante est sélectionnée', (done) => {
      schoolYearServiceSpy.getCurrent.and.returnValue(of(currentYear));
      const service = createService();

      service.readOnly$.pipe(take(1)).subscribe((readOnly) => {
        expect(readOnly).toBeFalse();
        done();
      });
    });
  });

  describe('année passée sélectionnée -> lecture seule (contrôles désactivés)', () => {
    it('isReadOnly() retourne true quand une année non courante est sélectionnée', () => {
      schoolYearServiceSpy.getCurrent.and.returnValue(of(currentYear));
      const service = createService();

      service.setSelectedSchoolYear(pastYear);

      expect(service.isReadOnly()).toBeTrue();
    });

    it('readOnly$ émet true quand une année non courante est sélectionnée', (done) => {
      schoolYearServiceSpy.getCurrent.and.returnValue(of(currentYear));
      const service = createService();

      service.setSelectedSchoolYear(pastYear);

      service.readOnly$.pipe(take(1)).subscribe((readOnly) => {
        expect(readOnly).toBeTrue();
        done();
      });
    });

    it('readOnly$ repasse à false lorsqu\'on re-sélectionne l\'année courante', () => {
      schoolYearServiceSpy.getCurrent.and.returnValue(of(currentYear));
      const service = createService();

      const emitted: boolean[] = [];
      service.readOnly$.subscribe((readOnly) => emitted.push(readOnly));

      service.setSelectedSchoolYear(pastYear);
      service.setSelectedSchoolYear(currentYear);

      // distinctUntilChanged: false (init) -> true (passée) -> false (courante)
      expect(emitted[emitted.length - 1]).toBeFalse();
    });
  });

  describe('année courante non résolue -> pas de lecture seule par défaut', () => {
    it('isReadOnly() retourne false si getCurrent échoue (édition non bloquée par défaut)', () => {
      schoolYearServiceSpy.getCurrent.and.returnValue(
        throwError(() => new Error('Année scolaire non trouvée'))
      );

      const service = createService();

      expect(service.isReadOnly()).toBeFalse();
    });
  });
});
