import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { SchoolYearContextService } from '../school-year-context.service';
import { SchoolYearService } from '../school-year.service';
import { SchoolYear } from '../../models/schoolYear/school-year';

/**
 * Tests du service de contexte d'année scolaire (Selected_School_Year).
 *
 * Couvre:
 *  - Initialisation à l'année courante (Requirement 10.2)
 *  - Mise à jour lors d'une sélection (Requirement 10.3)
 *  - Préservation à travers la navigation dans la session (Requirement 10.6)
 *  - Cas d'absence d'année courante (sélection reste null)
 *
 * Le service appelle `getCurrent()` dans son constructeur : le spy est donc
 * configuré AVANT `TestBed.inject(SchoolYearContextService)`.
 */
describe('SchoolYearContextService', () => {
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

  /**
   * Configure le TestBed avec un spy `SchoolYearService` puis instancie le
   * service de contexte. Doit être appelé après avoir positionné la valeur de
   * retour de `getCurrent()`, car elle est consommée dès la construction.
   */
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

  it('should be created', () => {
    schoolYearServiceSpy.getCurrent.and.returnValue(of(currentYear));
    const service = createService();
    expect(service).toBeTruthy();
  });

  describe('initialisation - défaut sur l\'année courante (Requirement 10.2)', () => {
    it('positionne la sélection sur l\'année courante au chargement', () => {
      schoolYearServiceSpy.getCurrent.and.returnValue(of(currentYear));

      const service = createService();

      expect(schoolYearServiceSpy.getCurrent).toHaveBeenCalledTimes(1);
      expect(service.getSelectedSchoolYear()).toEqual(currentYear);
    });

    it('émet l\'année courante via selectedSchoolYear$', (done) => {
      schoolYearServiceSpy.getCurrent.and.returnValue(of(currentYear));

      const service = createService();

      service.selectedSchoolYear$.subscribe((year) => {
        expect(year).toEqual(currentYear);
        done();
      });
    });

    it('mémorise l\'année courante récupérée', () => {
      schoolYearServiceSpy.getCurrent.and.returnValue(of(currentYear));

      const service = createService();

      expect(service.getCurrentSchoolYear()).toEqual(currentYear);
      expect(service.isCurrentYearSelected()).toBeTrue();
    });
  });

  describe('mise à jour lors d\'une sélection (Requirement 10.3)', () => {
    it('met à jour l\'année sélectionnée via setSelectedSchoolYear', () => {
      schoolYearServiceSpy.getCurrent.and.returnValue(of(currentYear));
      const service = createService();

      service.setSelectedSchoolYear(pastYear);

      expect(service.getSelectedSchoolYear()).toEqual(pastYear);
    });

    it('émet la nouvelle sélection via selectedSchoolYear$', () => {
      schoolYearServiceSpy.getCurrent.and.returnValue(of(currentYear));
      const service = createService();

      const emitted: (SchoolYear | null)[] = [];
      service.selectedSchoolYear$.subscribe((year) => emitted.push(year));

      service.setSelectedSchoolYear(pastYear);

      // Première émission = année courante, seconde = nouvelle sélection.
      expect(emitted[emitted.length - 1]).toEqual(pastYear);
    });

    it('isCurrentYearSelected retourne false lorsqu\'une année passée est sélectionnée', () => {
      schoolYearServiceSpy.getCurrent.and.returnValue(of(currentYear));
      const service = createService();

      service.setSelectedSchoolYear(pastYear);

      expect(service.isCurrentYearSelected()).toBeFalse();
    });
  });

  describe('préservation à travers la navigation (Requirement 10.6)', () => {
    it('conserve la sélection sur le singleton injecté sans nouvel appel getCurrent', () => {
      schoolYearServiceSpy.getCurrent.and.returnValue(of(currentYear));
      const service = createService();

      service.setSelectedSchoolYear(pastYear);

      // Simule une "navigation" : ré-injection du même singleton root.
      const sameService = TestBed.inject(SchoolYearContextService);

      expect(sameService).toBe(service);
      expect(sameService.getSelectedSchoolYear()).toEqual(pastYear);
      // getCurrent n'est appelé qu'une fois (à la construction), pas à chaque navigation.
      expect(schoolYearServiceSpy.getCurrent).toHaveBeenCalledTimes(1);
    });
  });

  describe('absence d\'année courante', () => {
    it('laisse la sélection à null si getCurrent échoue', () => {
      schoolYearServiceSpy.getCurrent.and.returnValue(
        throwError(() => new Error('Année scolaire non trouvée'))
      );

      const service = createService();

      expect(service.getSelectedSchoolYear()).toBeNull();
      expect(service.getCurrentSchoolYear()).toBeNull();
      expect(service.isCurrentYearSelected()).toBeFalse();
    });

    it('permet tout de même de sélectionner une année après un échec initial', () => {
      schoolYearServiceSpy.getCurrent.and.returnValue(
        throwError(() => new Error('Année scolaire non trouvée'))
      );
      const service = createService();

      service.setSelectedSchoolYear(pastYear);

      expect(service.getSelectedSchoolYear()).toEqual(pastYear);
    });
  });
});
