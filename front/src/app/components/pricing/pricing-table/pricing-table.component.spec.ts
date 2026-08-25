import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { PricingTableComponent } from './pricing-table.component';
import { API_BASE_URL } from '../../../api-base-url';
import { setupComponentTestBed } from '../../../../testing/setup';

describe('PricingTableComponent', () => {
  let component: PricingTableComponent;
  let fixture: ComponentFixture<PricingTableComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await setupComponentTestBed(PricingTableComponent);

    fixture = TestBed.createComponent(PricingTableComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('déclare les colonnes du tarif', () => {
    expect(component.columns.map(c => c.columnDef))
      .toEqual(['id', 'price', 'effectiveDate', 'expirationDate', 'description']);
  });

  it('convertDate() rend une chaîne vide pour une date absente', () => {
    // Un tarif sans date de validité ne doit pas interrompre le rendu du tableau.
    expect(component.convertDate(null)).toBe('');
    expect(component.convertDate(undefined)).toBe('');
  });

  it('charge les tarifs et suffixe le montant de la devise', () => {
    const req = httpMock.expectOne(`${API_BASE_URL}/api/pricings`);
    expect(req.request.method).toBe('GET');
    req.flush([{ id: 1, price: 3000, effectiveDate: null, expirationDate: null, description: '' }]);
    fixture.detectChanges();

    const rows = fixture.nativeElement.querySelectorAll('tr[mat-row]');
    expect(rows.length).toBe(1);
    expect(rows[0].textContent).toContain('3000 DA');
  });
});
