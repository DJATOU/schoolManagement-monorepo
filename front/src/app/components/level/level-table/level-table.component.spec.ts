import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { LevelTableComponent } from './level-table.component';
import { API_BASE_URL } from '../../../api-base-url';
import { setupComponentTestBed } from '../../../../testing/setup';

describe('LevelTableComponent', () => {
  let component: LevelTableComponent;
  let fixture: ComponentFixture<LevelTableComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await setupComponentTestBed(LevelTableComponent);

    fixture = TestBed.createComponent(LevelTableComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('expose la colonne « Rang » du niveau', () => {
    // Le rang ordonne les niveaux entre eux : sans lui la table perd sa raison d'être.
    expect(component.columns.map(c => c.columnDef))
      .toEqual(['id', 'name', 'levelCode', 'levelSequence', 'description']);
  });

  it('charge les niveaux et rend une ligne par niveau', () => {
    const req = httpMock.expectOne(`${API_BASE_URL}/api/levels`);
    expect(req.request.method).toBe('GET');
    req.flush([
      { id: 1, name: 'Première année', levelCode: '1AS', levelSequence: 1, description: '' },
      { id: 2, name: 'Deuxième année', levelCode: '2AS', levelSequence: 2, description: '' }
    ]);
    fixture.detectChanges();

    const rows = fixture.nativeElement.querySelectorAll('tr[mat-row]');
    expect(rows.length).toBe(2);
    expect(fixture.nativeElement.textContent).toContain('Première année');
  });
});
