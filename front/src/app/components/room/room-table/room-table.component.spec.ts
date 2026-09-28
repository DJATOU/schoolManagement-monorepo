import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { RoomTableComponent } from './room-table.component';
import { API_BASE_URL } from '../../../api-base-url';
import { setupComponentTestBed } from '../../../../testing/setup';

describe('RoomTableComponent', () => {
  let component: RoomTableComponent;
  let fixture: ComponentFixture<RoomTableComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await setupComponentTestBed(RoomTableComponent);

    fixture = TestBed.createComponent(RoomTableComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('déclare les colonnes de la salle, dont sa capacité', () => {
    expect(component.columns.map(c => c.columnDef))
      .toEqual(['id', 'name', 'capacity', 'description']);
  });

  it('charge les salles et affiche leur capacité', () => {
    const req = httpMock.expectOne(`${API_BASE_URL}/api/rooms`);
    expect(req.request.method).toBe('GET');
    req.flush([{ id: 1, name: 'Salle A', capacity: 24, description: '' }]);
    fixture.detectChanges();

    const rows = fixture.nativeElement.querySelectorAll('tr[mat-row]');
    expect(rows.length).toBe(1);
    expect(rows[0].textContent).toContain('Salle A');
    expect(rows[0].textContent).toContain('24');
  });
});
