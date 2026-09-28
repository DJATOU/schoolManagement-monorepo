import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { SubjectTableComponent } from './subject-table.component';
import { API_BASE_URL } from '../../../api-base-url';
import { setupComponentTestBed } from '../../../../testing/setup';

describe('SubjectTableComponent', () => {
  let component: SubjectTableComponent;
  let fixture: ComponentFixture<SubjectTableComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await setupComponentTestBed(SubjectTableComponent);

    fixture = TestBed.createComponent(SubjectTableComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('déclare les colonnes de la matière', () => {
    expect(component.columns.map(c => c.columnDef))
      .toEqual(['id', 'name', 'description']);
  });

  it('charge les matières et rend une ligne par matière', () => {
    const req = httpMock.expectOne(`${API_BASE_URL}/api/subjects`);
    expect(req.request.method).toBe('GET');
    req.flush([
      { id: 1, name: 'Mathématiques', description: '' },
      { id: 2, name: 'Physique', description: '' }
    ]);
    fixture.detectChanges();

    const rows = fixture.nativeElement.querySelectorAll('tr[mat-row]');
    expect(rows.length).toBe(2);
    expect(fixture.nativeElement.textContent).toContain('Mathématiques');
  });
});
