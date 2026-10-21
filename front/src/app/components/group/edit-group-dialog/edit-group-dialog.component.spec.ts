import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EditGroupDialogComponent } from './edit-group-dialog.component';
import { createDialogRefSpy, DialogRefSpy, matDialogProviders, setupComponentTestBed } from '../../../../testing/setup';
import { aGroup } from '../../../../testing/fixtures';
import { By } from '@angular/platform-browser';
import { MatTabGroup } from '@angular/material/tabs';
import { TranslateService } from '@ngx-translate/core';
import { Pricing } from '../../../models/pricing/pricing';

/**
 * Édition d'un groupe existant.
 *
 * <p>`data.group` est déréférencé à la construction pour préremplir le formulaire. Le point
 * sous test est ce préremplissage : un formulaire d'édition ouvert vide ferait perdre les
 * valeurs actuelles dès l'enregistrement.</p>
 */
describe('EditGroupDialogComponent', () => {
  let component: EditGroupDialogComponent;
  let fixture: ComponentFixture<EditGroupDialogComponent>;
  let dialogRef: DialogRefSpy;

  const group = aGroup();

  beforeEach(async () => {
    dialogRef = createDialogRefSpy();
    await setupComponentTestBed(EditGroupDialogComponent, {
      providers: matDialogProviders({ group }, dialogRef)
    });
    fixture = TestBed.createComponent(EditGroupDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('porte le groupe à éditer', () => {
    expect(component.data.group).toEqual(group);
  });

  it('préremplit le formulaire avec le nom actuel du groupe', () => {
    // Un champ vide à l'ouverture effacerait le nom existant au premier enregistrement.
    expect(JSON.stringify(component.editGroupForm.value)).toContain('Maths 1B');
  });

  /** Tarif affiché comme ailleurs : « 1 200,00 DA » / « 1.200,00 دج », et non « DZD 1,200 ». */
  describe('tarifs proposés', () => {
    async function options(lang: string): Promise<string[]> {
      const translate = TestBed.inject(TranslateService);
      translate.setTranslation('fr', { COMMON: { CURRENCY_SUFFIX: 'DA' } }, true);
      translate.setTranslation('ar', { COMMON: { CURRENCY_SUFFIX: 'دج' } }, true);
      translate.use(lang);
      component.prices = [{ id: 4, price: 1200 } as Pricing];
      fixture.debugElement.query(By.directive(MatTabGroup)).componentInstance.selectedIndex = 1;
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();
      const select = (fixture.nativeElement as HTMLElement).querySelector('mat-select[formControlName="priceId"] .mat-mdc-select-trigger') as HTMLElement;
      select.click();
      fixture.detectChanges();
      return Array.from(document.querySelectorAll('mat-option')).map(o => (o.textContent ?? '').replace(/\s+/g, ' ').trim());
    }

    it('français : « 1 200,00 DA »', async () => {
      expect(await options('fr')).toEqual(['1 200,00 DA']);
    });

    it('arabe : « 1.200,00 دج », jamais « DZD »', async () => {
      const texts = await options('ar');
      expect(texts).toEqual(['1.200,00 دج']);
      expect(texts.join(' ')).not.toContain('DZD');
    });
  });
});
