import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PricingFormComponent } from './pricing-form.component';
import { setupComponentTestBed } from '../../../../testing/setup';

describe('PricingFormComponent', () => {
  let component: PricingFormComponent;
  let fixture: ComponentFixture<PricingFormComponent>;

  beforeEach(async () => {
    await setupComponentTestBed(PricingFormComponent);

    fixture = TestBed.createComponent(PricingFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('démarre en mode création (aucun identifiant dans l\'URL)', () => {
    expect(component.editingId).toBeNull();
  });

  it('exige prix et période de validité', () => {
    expect(component.pricingForm.valid).toBeFalse();
    expect(component.pricingForm.get('price')?.hasError('required')).toBeTrue();
    expect(component.pricingForm.get('effectiveDate')?.hasError('required')).toBeTrue();
    expect(component.pricingForm.get('expirationDate')?.hasError('required')).toBeTrue();
  });

  it('rejette un prix négatif et accepte un tarif complet', () => {
    component.pricingForm.setValue({
      price: -1,
      effectiveDate: '2025-09-01',
      expirationDate: '2026-06-30',
      description: ''
    });
    expect(component.pricingForm.get('price')?.hasError('min')).toBeTrue();

    component.pricingForm.get('price')?.setValue(3000);
    expect(component.pricingForm.valid).toBeTrue();
  });
});
