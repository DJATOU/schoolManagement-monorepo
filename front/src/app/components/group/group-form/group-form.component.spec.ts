import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GroupFormComponent } from './group-form.component';
import { setupComponentTestBed } from '../../../../testing/setup';
import { By } from '@angular/platform-browser';
import { MatTabGroup } from '@angular/material/tabs';
import { TranslateService } from '@ngx-translate/core';
import { Pricing } from '../../../models/pricing/pricing';

describe('GroupFormComponent', () => {
  let component: GroupFormComponent;
  let fixture: ComponentFixture<GroupFormComponent>;

  beforeEach(async () => {
    await setupComponentTestBed(GroupFormComponent);

    fixture = TestBed.createComponent(GroupFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('refuse un formulaire vide : un groupe a besoin de son type, niveau et matière', () => {
    expect(component.groupForm.valid).toBeFalse();
    expect(component.groupForm.get('basicInformation.name')?.hasError('required')).toBeTrue();
    expect(component.groupForm.get('basicInformation.groupTypeId')?.hasError('required')).toBeTrue();
    expect(component.groupForm.get('basicInformation.levelId')?.hasError('required')).toBeTrue();
    expect(component.groupForm.get('basicInformation.subjectId')?.hasError('required')).toBeTrue();
  });

  it('exige aussi le nombre de séances par série, le tarif et l\'enseignant', () => {
    // Le nombre de séances par série et le tarif fondent le coût du mois : ils sont
    // obligatoires, sans quoi aucun montant ne peut être calculé.
    expect(component.groupForm.get('additionalDetails.sessionNumberPerSerie')?.hasError('required')).toBeTrue();
    expect(component.groupForm.get('additionalDetails.priceId')?.hasError('required')).toBeTrue();
    expect(component.groupForm.get('additionalDetails.teacherId')?.hasError('required')).toBeTrue();
    // La description reste libre.
    expect(component.groupForm.get('additionalDetails.description')?.valid).toBeTrue();
  });

  it('devient valide une fois toutes les références renseignées', () => {
    component.groupForm.get('basicInformation')?.setValue({
      name: 'Maths 1B', groupTypeId: 1, levelId: 2, subjectId: 3
    });
    component.groupForm.get('additionalDetails')?.setValue({
      sessionNumberPerSerie: 8, priceId: 4, description: '', teacherId: 5
    });

    expect(component.groupForm.valid).toBeTrue();
  });

  /**
   * Tarif affiché comme dans le reste de l'application : montant au format de la langue, suffixe
   * « DA » / « دج ». Le pipe `currency` d'Angular affichait « $1,200.00 ». La valeur choisie reste
   * l'identifiant du tarif : rien ne change dans le formulaire.
   */
  describe('tarifs proposés', () => {
    async function options(lang: string): Promise<string[]> {
      const translate = TestBed.inject(TranslateService);
      translate.setTranslation('fr', { COMMON: { CURRENCY_SUFFIX: 'DA' } }, true);
      translate.setTranslation('ar', { COMMON: { CURRENCY_SUFFIX: 'دج' } }, true);
      translate.use(lang);
      component.prices = [{ id: 4, price: 1200 } as Pricing, { id: 5, price: 12500 } as Pricing];
      fixture.debugElement.query(By.directive(MatTabGroup)).componentInstance.selectedIndex = 1;
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
      const select = (fixture.nativeElement as HTMLElement).querySelector('mat-select[formControlName="priceId"] .mat-mdc-select-trigger') as HTMLElement;
      select.click();
      fixture.detectChanges();
      return Array.from(document.querySelectorAll('mat-option')).map(o => (o.textContent ?? '').replace(/\s+/g, ' ').trim());
    }

    it('français : « 1 200,00 DA », jamais « $ »', async () => {
      expect(await options('fr')).toEqual(['1 200,00 DA', '12 500,00 DA']);
    });

    it('arabe : « 1.200,00 دج », et la valeur choisie reste l\'identifiant du tarif', async () => {
      const texts = await options('ar');
      expect(texts).toEqual(['1.200,00 دج', '12.500,00 دج']);
      expect(texts.join(' ')).not.toMatch(/\$|DZD/);
      (document.querySelectorAll('mat-option')[0] as HTMLElement).click();
      expect(component.groupForm.get('additionalDetails.priceId')?.value).toBe(4);
    });
  });
});
