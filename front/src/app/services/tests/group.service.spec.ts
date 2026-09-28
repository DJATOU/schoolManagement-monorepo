import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';

import { GroupService } from '../group.service';
import { Group } from '../../models/group/group';
import { API_BASE_URL } from '../../api-base-url';

/**
 * Tests du filtrage des groupes par année scolaire (Selected_School_Year).
 *
 * Couvre le filtrage de la liste des groupes réagissant à l'année sélectionnée
 * (Requirement 10.4) :
 *  - getGroups(id) émet une requête portant le paramètre `schoolYearId`
 *  - getGroups() (sans argument) émet une requête sans ce paramètre
 *    (le backend applique alors l'année courante par défaut, Requirement 10.5)
 *
 * Utilise HttpClientTestingModule + HttpTestingController pour vérifier les
 * paramètres de requête réellement envoyés.
 */
describe('GroupService - filtrage par année scolaire', () => {
  const groupsUrl = `${API_BASE_URL}/api/groups`;

  let service: GroupService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [GroupService],
    });
    service = TestBed.inject(GroupService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    // Vérifie qu'aucune requête inattendue ne subsiste.
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('getGroups(5) envoie une requête avec le paramètre schoolYearId=5 (Requirement 10.4)', () => {
    const mockGroups: Group[] = [];

    service.getGroups(5).subscribe((groups) => {
      expect(groups).toEqual(mockGroups);
    });

    const req = httpMock.expectOne(
      (request) => request.url === groupsUrl && request.params.get('schoolYearId') === '5'
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.params.has('schoolYearId')).toBeTrue();
    expect(req.request.params.get('schoolYearId')).toBe('5');
    req.flush(mockGroups);
  });

  it('getGroups() sans argument n\'envoie pas de paramètre schoolYearId (Requirement 10.5)', () => {
    const mockGroups: Group[] = [];

    service.getGroups().subscribe((groups) => {
      expect(groups).toEqual(mockGroups);
    });

    const req = httpMock.expectOne(groupsUrl);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.has('schoolYearId')).toBeFalse();
    req.flush(mockGroups);
  });
});
