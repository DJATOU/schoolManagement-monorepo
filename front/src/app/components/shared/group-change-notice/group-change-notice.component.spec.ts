import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { GroupChangeNoticeComponent } from './group-change-notice.component';
import { GroupChange } from '../../../models/group/group-change';
import { API_BASE_URL } from '../../../api-base-url';

/**
 * Le bandeau est informatif : il doit rester invisible dans le cas normal (aucun changement)
 * et absorber un échec de chargement sans rien casser à l'écran.
 */
describe('GroupChangeNoticeComponent', () => {
  let component: GroupChangeNoticeComponent;
  let fixture: ComponentFixture<GroupChangeNoticeComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GroupChangeNoticeComponent, HttpClientTestingModule, TranslateModule.forRoot()]
    }).compileComponents();

    // Libellés réels : sans eux, les clés s'afficheraient brutes et le test ne vérifierait pas
    // que le nom du groupe et son décompte sont bien interpolés.
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', {
      groupChange: {
        title: 'Changement de groupe signalé',
        leftGroup: 'Groupe quitté : {{group}} · {{count}} séance(s) suivie(s) ce mois-là',
        joinedGroup: 'Groupe rejoint : {{group}} · {{count}} séance(s) suivie(s) ce mois-là',
        hint: 'Information : la facturation reste inchangée.'
      }
    });
    translate.use('fr');

    fixture = TestBed.createComponent(GroupChangeNoticeComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function flushGroupChanges(body: GroupChange[], studentId = 7): void {
    fixture.componentRef.setInput('studentId', studentId);
    fixture.detectChanges();
    httpMock.expectOne(`${API_BASE_URL}/api/students/${studentId}/group-changes`).flush(body);
    fixture.detectChanges();
  }

  it('n\'affiche rien quand il n\'y a aucun changement à signaler', () => {
    flushGroupChanges([]);

    expect(component.changes).toEqual([]);
    expect(fixture.nativeElement.querySelector('.group-change-notice')).toBeNull();
  });

  it('affiche le mois, les deux groupes et leurs décomptes', () => {
    flushGroupChanges([{
      year: 2026,
      month: 8,
      leftGroup: { groupId: 12, groupName: 'Maths 1B', attendedCount: 2 },
      joinedGroup: { groupId: 15, groupName: 'Maths 1C', attendedCount: 1 }
    }]);

    const banner: HTMLElement = fixture.nativeElement.querySelector('.group-change-notice');
    expect(banner).not.toBeNull();
    // Information consultative, jamais une alerte bloquante.
    expect(banner.getAttribute('role')).toBe('status');
    expect(banner.textContent).toContain('Maths 1B');
    expect(banner.textContent).toContain('Maths 1C');
    // Décomptes de séances suivies dans chacun des deux groupes (exigence 10.3).
    expect(banner.textContent).toContain('2 séance(s)');
    expect(banner.textContent).toContain('1 séance(s)');
    // Le mois est formaté côté client : le serveur ne renvoie que l'année et le mois.
    expect(banner.textContent).toContain(component.monthLabel(component.changes[0]));
    expect(component.monthLabel(component.changes[0])).toContain('2026');
  });

  it('reste silencieux à l\'écran lorsque le chargement échoue', () => {
    const studentId = 9;
    fixture.componentRef.setInput('studentId', studentId);
    fixture.detectChanges();
    httpMock.expectOne(`${API_BASE_URL}/api/students/${studentId}/group-changes`)
      .error(new ProgressEvent('network error'));
    fixture.detectChanges();

    expect(component.changes).toEqual([]);
    expect(fixture.nativeElement.querySelector('.group-change-notice')).toBeNull();
  });
});
