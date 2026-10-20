import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import frTranslations from '../../../../assets/i18n/fr.json';
import arTranslations from '../../../../assets/i18n/ar.json';

import { GroupTypeTableComponent } from './group-type-table.component';
import { API_BASE_URL } from '../../../api-base-url';
import { setupComponentTestBed } from '../../../../testing/setup';

describe('GroupTypeTableComponent', () => {
  let component: GroupTypeTableComponent;
  let fixture: ComponentFixture<GroupTypeTableComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await setupComponentTestBed(GroupTypeTableComponent);

    fixture = TestBed.createComponent(GroupTypeTableComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('déclare les colonnes du type de groupe, dont sa taille', () => {
    // La taille du type de groupe conditionne l'effectif : elle doit rester visible.
    expect(component.columns.map(c => c.columnDef))
      .toEqual(['id', 'name', 'size', 'description']);
  });

  it('charge les types de groupe et affiche leur taille', () => {
    const req = httpMock.expectOne(`${API_BASE_URL}/api/grouptypes`);
    expect(req.request.method).toBe('GET');
    req.flush([{ id: 1, name: 'Petit groupe', size: 8, description: '' }]);
    fixture.detectChanges();

    const rows = fixture.nativeElement.querySelectorAll('tr[mat-row]');
    expect(rows.length).toBe(1);
    expect(rows[0].textContent).toContain('Petit groupe');
    expect(rows[0].textContent).toContain('8');
  });

  it('traduit ses en-têtes en français et en arabe', () => {
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', frTranslations);
    translate.setTranslation('ar', arTranslations);
    const headers = (): string[] => Array.from(
      fixture.nativeElement.querySelectorAll('th[mat-header-cell]') as NodeListOf<HTMLElement>
    ).map(th => (th.textContent ?? '').trim()).filter(text => text.length > 0);

    translate.use('fr');
    fixture.detectChanges();
    expect(headers()).toEqual(['ID', 'Nom', 'Effectif', 'Description']);

    translate.use('ar');
    fixture.detectChanges();
    expect(headers()).toEqual(['ID', 'الاسم', 'التعداد', 'الوصف']);
  });
});
