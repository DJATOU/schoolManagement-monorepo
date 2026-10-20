import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import frTranslations from '../../../../assets/i18n/fr.json';
import arTranslations from '../../../../assets/i18n/ar.json';

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

  // En-têtes = clés : français et arabe traduits, et le
  // PDF de la table (reusable-datatable.onPrint) les relit en français sous une interface arabe.
  it('traduit ses en-têtes en français et en arabe', () => {
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', frTranslations);
    translate.setTranslation('ar', arTranslations);
    const headers = (): string[] => Array.from(
      fixture.nativeElement.querySelectorAll('th[mat-header-cell]') as NodeListOf<HTMLElement>
    ).map(th => (th.textContent ?? '').trim()).filter(text => text.length > 0);

    translate.use('fr');
    fixture.detectChanges();
    expect(headers()).toEqual(['ID', 'Nom', 'Capacité', 'Description']);

    translate.use('ar');
    fixture.detectChanges();
    expect(headers()).toEqual(['ID', 'الاسم', 'السعة', 'الوصف']);
  });
});
