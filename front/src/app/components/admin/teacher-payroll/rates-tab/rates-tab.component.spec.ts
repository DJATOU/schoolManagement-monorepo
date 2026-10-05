import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { TeacherPayRateService } from '../../../../services/teacher-pay-rate.service';
import { rate } from '../../../../../testing/payroll-fixtures';
import { setupComponentTestBed } from '../../../../../testing/setup';
import { parsePercent, RatesTabComponent } from './rates-tab.component';

describe('RatesTabComponent', () => {
  let fixture: ComponentFixture<RatesTabComponent>;
  let component: RatesTabComponent;
  let rates: jasmine.SpyObj<TeacherPayRateService>;

  beforeEach(async () => {
    rates = jasmine.createSpyObj<TeacherPayRateService>('TeacherPayRateService',
      ['getRates', 'createRate', 'updateRate', 'disableRate']);
    rates.getRates.and.returnValue(of([rate(), rate({ id: 9, label: 'Ancien', teacherPercent: 50, schoolPercent: 50, active: false })]));
    await setupComponentTestBed(RatesTabComponent, { providers: [{ provide: TeacherPayRateService, useValue: rates }] });
    fixture = TestBed.createComponent(RatesTabComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('liste le catalogue ; un taux désactivé ne se modifie ni ne se désactive plus', () => {
    const rows = (fixture.nativeElement as HTMLElement).querySelectorAll('tr.mat-mdc-row');
    expect(rows.length).toBe(2);
    expect(rows[0].querySelector('.rt-edit')).not.toBeNull();
    expect(rows[1].querySelector('.rt-edit')).toBeNull();
    expect(rows[1].querySelector('.rt-disable')).toBeNull();
  });

  it('pourcentage strictement entre 0 et 100, deux décimales au plus, virgule acceptée', () => {
    const percent = component.form.get('teacherPercent')!;
    for (const invalid of ['0', '100', '-5', '60.125', 'abc']) {
      percent.setValue(invalid);
      expect(percent.valid).withContext(invalid).toBeFalse();
    }
    for (const valid of ['0.01', '60', '62,5', '99.99']) {
      percent.setValue(valid);
      expect(percent.valid).withContext(valid).toBeTrue();
    }
    percent.setValue('62,5');
    expect(component.schoolPercentPreview).toBe(37.5);
  });

  it('création : libellé nettoyé, pourcentage converti, catalogue relu', () => {
    rates.createRate.and.returnValue(of(rate({ id: 10, label: 'Confirmé', teacherPercent: 62.5 })));
    component.form.setValue({ label: '  Confirmé ', teacherPercent: '62,5' });

    component.save();

    expect(rates.createRate).toHaveBeenCalledWith({ label: 'Confirmé', teacherPercent: 62.5 });
    expect(rates.getRates).toHaveBeenCalledTimes(2);
    expect(component.form.get('label')!.value).toBe('');
  });

  it('modification d\'un taux existant ; refus du serveur affiché tel quel', () => {
    rates.updateRate.and.returnValue(throwError(() => new Error('Un taux actif porte déjà le libellé « Expert ».')));
    component.edit(rate());
    component.form.patchValue({ label: 'Expert' });

    component.save();
    fixture.detectChanges();

    expect(rates.updateRate).toHaveBeenCalledWith(7, { label: 'Expert', teacherPercent: 60 });
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('déjà le libellé « Expert »');
    expect(component.editing).not.toBeNull();
  });

  it('désactivation : catalogue relu', () => {
    rates.disableRate.and.returnValue(of(rate({ active: false })));

    component.disable(rate());

    expect(rates.disableRate).toHaveBeenCalledWith(7);
    expect(rates.getRates).toHaveBeenCalledTimes(2);
  });

  it('lecture d\'un pourcentage saisi', () => {
    expect(parsePercent('62,5')).toBe(62.5);
    expect(parsePercent(' 60 ')).toBe(60);
    expect(parsePercent('')).toBeNull();
    expect(parsePercent('abc')).toBeNull();
  });
});
