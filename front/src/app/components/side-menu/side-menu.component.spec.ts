import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SideMenuComponent } from './side-menu.component';
import { setupComponentTestBed } from '../../../testing/setup';

describe('SideMenuComponent', () => {
  let component: SideMenuComponent;
  let fixture: ComponentFixture<SideMenuComponent>;

  beforeEach(async () => {
    await setupComponentTestBed(SideMenuComponent);

    fixture = TestBed.createComponent(SideMenuComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('rend les quatre rubriques de navigation', () => {
    // Inscription, Gestion financière, Suivi pédagogique, Ressources pédagogiques.
    const panels = fixture.nativeElement.querySelectorAll('mat-expansion-panel');
    expect(panels.length).toBe(4);
  });

  it('toggleSidenav() inverse l\'état d\'ouverture', () => {
    expect(component.isOpen).toBeTrue();
    component.toggleSidenav();
    expect(component.isOpen).toBeFalse();
  });
});
