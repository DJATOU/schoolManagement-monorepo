import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';

import { SideMenuComponent } from './side-menu.component';
import { setupComponentTestBed } from '../../../testing/setup';
import { AuthService } from '../../services/auth.service';
import frTranslations from '../../../assets/i18n/fr.json';
import enTranslations from '../../../assets/i18n/en.json';

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

/**
 * Aucun libellé du menu n'est coupé, à la largeur réelle du menu (250 px, `app.component.scss`).
 *
 * <p>« Teacher payroll » s'affichait « Teacher payrc » : le libellé ne pouvait pas passer à la ligne
 * et le bouton coupait ce qui dépassait. La mesure est faite dans le navigateur des tests, styles du
 * composant appliqués, avec les vraies traductions, administrateur connecté (sinon la paie et les
 * recettes sont masquées).</p>
 */
describe('SideMenuComponent — libellés à 250 px', () => {
  for (const [lang, translations] of [['fr', frTranslations], ['en', enTranslations]] as const) {
    it(`aucun libellé coupé (${lang})`, async () => {
      await setupComponentTestBed(SideMenuComponent);
      spyOn(TestBed.inject(AuthService), 'hasRole').and.returnValue(true);
      const translate = TestBed.inject(TranslateService);
      translate.setTranslation(lang, translations);
      translate.use(lang);

      const fixture = TestBed.createComponent(SideMenuComponent);
      const host = fixture.nativeElement as HTMLElement;
      host.style.display = 'block';
      host.style.width = '250px';
      document.body.appendChild(host);
      fixture.detectChanges();
      await fixture.whenStable();
      fixture.detectChanges();

      const labels = Array.from(host.querySelectorAll<HTMLElement>('.nav-item .item-text'));
      expect(labels.map(label => label.textContent?.trim()))
        .toContain(lang === 'fr' ? 'Paie des enseignants' : 'Teacher payroll');

      const clipped = labels
        .filter(label => {
          const item = label.closest<HTMLElement>('.nav-item')!;
          // Débordement du libellé lui-même, ou libellé qui sort du bouton (coupé par son overflow),
          // à droite ou, passé à la ligne, en bas.
          const box = label.getBoundingClientRect();
          const itemBox = item.getBoundingClientRect();
          return label.scrollWidth > label.clientWidth + 1
            || box.right > itemBox.right + 1
            || box.bottom > itemBox.bottom + 1;
        })
        .map(label => label.textContent?.trim());
      expect(clipped).withContext('libellés coupés').toEqual([]);

      host.remove();
    });
  }
});
