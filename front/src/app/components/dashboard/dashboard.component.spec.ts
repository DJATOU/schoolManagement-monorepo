import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { DashboardComponent } from './dashboard.component';
import { DashboardStats } from '../../models/dashboard/dashboard-stats';
import { API_BASE_URL } from '../../api-base-url';
import { setupComponentTestBed } from '../../../testing/setup';

describe('DashboardComponent', () => {
  let component: DashboardComponent;
  let fixture: ComponentFixture<DashboardComponent>;
  let httpMock: HttpTestingController;

  const stats: DashboardStats = {
    from: '2025-07-01',
    to: '2026-06-30',
    totalStudents: 120,
    newStudentsInPeriod: 18,
    leavingStudents: 3,
    maleStudents: 60,
    femaleStudents: 60,
    totalTeachers: 9,
    totalGroups: 14,
    sessionsValidated: 40,
    sessionsScheduled: 10,
    sessionsDeactivated: 2,
    catchUpSessions: 5,
    presentCount: 300,
    justifiedAbsences: 12,
    unjustifiedAbsences: 8,
  };

  beforeEach(async () => {
    await setupComponentTestBed(DashboardComponent);

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  /** Sert la requête de statistiques ouverte par ngOnInit. */
  function flushStats(body: DashboardStats = stats): void {
    const requests = httpMock.match(req => req.url === `${API_BASE_URL}/api/dashboard/stats`);
    expect(requests.length).toBeGreaterThan(0);
    requests.forEach(req => req.flush(body));
    fixture.detectChanges();
  }

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('interroge les statistiques dès l\'affichage, sur le preset « année »', () => {
    expect(component.activePreset).toBe('year');
    expect(component.loading).toBeTrue();

    flushStats();

    expect(component.loading).toBeFalse();
    expect(component.stats).toEqual(stats);
  });

  it('construit les six indicateurs à partir des statistiques reçues', () => {
    flushStats();

    expect(component.kpis.length).toBe(6);
    expect(component.kpis.map(k => k.value))
      .toEqual([120, 9, 14, 18, 3, 5]);
  });

  it('pct() et maleRate() ne divisent jamais par zéro', () => {
    // Avant réception des statistiques, les totaux sont nuls.
    expect(component.totalSessions).toBe(0);
    expect(component.pct(5, 0)).toBe(0);
    expect(component.maleRate).toBe(0);

    flushStats();

    expect(component.totalSessions).toBe(52);
    expect(component.maleRate).toBe(50);
  });
});
