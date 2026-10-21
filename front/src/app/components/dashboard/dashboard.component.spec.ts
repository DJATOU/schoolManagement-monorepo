import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { DashboardComponent } from './dashboard.component';
import { DashboardStats } from '../../models/dashboard/dashboard-stats';
import { TranslateService } from '@ngx-translate/core';
import { API_BASE_URL } from '../../api-base-url';
import { setupComponentTestBed } from '../../../testing/setup';
import frTranslations from '../../../assets/i18n/fr.json';
import enTranslations from '../../../assets/i18n/en.json';
import arTranslations from '../../../assets/i18n/ar.json';
import { By } from '@angular/platform-browser';
import { PrintLanguageMenuComponent } from '../shared/print-language-menu/print-language-menu.component';

describe('DashboardComponent', () => {
  let component: DashboardComponent;
  let fixture: ComponentFixture<DashboardComponent>;
  let httpMock: HttpTestingController;

  const stats: DashboardStats = {
    from: '2025-07-01',
    to: '2026-06-30',
    totalStudents: 120,
    newStudentsInPeriod: 18,
    leavingStudents: 3,
    maleStudents: 60,
    femaleStudents: 60,
    totalTeachers: 9,
    totalGroups: 14,
    activeGroups: 4,
    sessionsValidated: 40,
    sessionsScheduled: 10,
    sessionsDeactivated: 2,
    catchUpSessions: 5,
    presentCount: 300,
    justifiedAbsences: 12,
    unjustifiedAbsences: 8,
  };

  beforeEach(async () => {
    await setupComponentTestBed(DashboardComponent);

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  /** Sert la requête de statistiques ouverte par ngOnInit. */
  function flushStats(body: DashboardStats = stats): void {
    const requests = httpMock.match(req => req.url === `${API_BASE_URL}/api/dashboard/stats`);
    expect(requests.length).toBeGreaterThan(0);
    requests.forEach(req => req.flush(body));
    fixture.detectChanges();
  }

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('interroge les statistiques dès l\'affichage, sur le preset « année »', () => {
    expect(component.activePreset).toBe('year');
    expect(component.loading).toBeTrue();

    flushStats();

    expect(component.loading).toBeFalse();
    expect(component.stats).toEqual(stats);
  });

  it('construit les six indicateurs à partir des statistiques reçues', () => {
    flushStats();

    expect(component.kpis.length).toBe(6);
    expect(component.kpis.map(k => k.value))
      .toEqual([120, 9, 4, 18, 3, 5]);
  });

  it('la carte Groupes compte les groupes qui ont des élèves, et rappelle le total créé', () => {
    // Le total compte aussi les groupes créés sans élèves (16 groupes dont 1 seul avec des
    // inscrits sur la base de démonstration) : affiché en grand, il se lisait comme 16 classes
    // qui tournent.
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', {
      dashboard: { kpi: { activeGroups: 'Groupes en activité', groupsTotal: 'sur {{count}} créés' } }
    }, true);
    translate.use('fr');

    flushStats();

    const groups = component.kpis[2];
    expect(groups.value).toBe(4);
    expect(groups.label).toBe('Groupes en activité');
    expect(groups.detail).toBe('sur 14 créés');

    const details = Array.from(fixture.nativeElement.querySelectorAll('.kpi-detail') as NodeListOf<HTMLElement>)
      .map(el => el.textContent?.trim());
    expect(details).toEqual(['sur 14 créés']);
  });

  /**
   * Export PDF : une capture de l'écran. Sous l'arabe, comme les autres documents, elle sort en
   * français ; l'écran revient à l'arabe aussitôt la page clonée.
   */
  describe('export PDF', () => {
    let translate: TranslateService;

    beforeEach(() => {
      translate = TestBed.inject(TranslateService);
      translate.setTranslation('fr', frTranslations);
      translate.setTranslation('en', enTranslations);
      translate.setTranslation('ar', arTranslations);
    });

    /** Ce que voit la capture : langue, libellé d'un indicateur, texte de la page, attributs du clone. */
    async function capture(lang: string): Promise<{ lang: string; kpi: string; page: string; clone: Document }> {
      translate.use(lang);
      flushStats();
      let seen = { lang: '', kpi: '', page: '', clone: document.implementation.createHTMLDocument('clone') };
      spyOn(component as never, 'renderCanvas').and.callFake(((_el: HTMLElement, options: { onclone: (doc: Document) => void }) => {
        options.onclone(seen.clone);
        seen = { ...seen, lang: translate.currentLang, kpi: component.kpis[2].label,
          page: (fixture.nativeElement as HTMLElement).textContent ?? '' };
        // Arrête l'export ici : seule la capture est vérifiée, pas la composition jsPDF.
        return Promise.reject(new Error('capture checked'));
      }) as never);
      await expectAsync(component.printPdf()).toBeRejectedWithError('capture checked');
      return seen;
    }

    /** Ce que voit la capture déclenchée par `run`, interface en `screen`. */
    async function captureBy(screen: string, run: () => Promise<void>): Promise<{ lang: string; kpi: string; clone: Document }> {
      translate.use(screen);
      flushStats();
      let seen = { lang: '', kpi: '', clone: document.implementation.createHTMLDocument('clone') };
      spyOn(component as never, 'renderCanvas').and.callFake(((_el: HTMLElement, options: { onclone: (doc: Document) => void }) => {
        options.onclone(seen.clone);
        seen = { ...seen, lang: translate.currentLang, kpi: component.kpis[2].label };
        return Promise.reject(new Error('capture checked'));
      }) as never);
      await expectAsync(run()).toBeRejectedWithError('capture checked');
      return seen;
    }

    it('« العربية » choisi sous le français : capture en arabe et de droite à gauche, écran rétabli', async () => {
      const seen = await captureBy('fr', () => component.printPdf('ar'));
      expect(seen.lang).toBe('ar');
      expect(seen.kpi).toBe(arTranslations.dashboard.kpi.activeGroups);
      expect(seen.clone.documentElement.lang).toBe('ar');
      expect(seen.clone.documentElement.dir).toBe('rtl');
      expect(translate.currentLang).toBe('fr');
      expect(component.kpis[2].label).toBe(frTranslations.dashboard.kpi.activeGroups);
    });

    it('bouton sous l\'arabe : langue de l\'écran, capture en arabe sans changement de langue', async () => {
      const changes: string[] = [];
      const seen = await captureBy('ar', () => {
        translate.onLangChange.subscribe(event => changes.push(event.lang));
        const menu = fixture.debugElement.query(By.directive(PrintLanguageMenuComponent)).componentInstance as PrintLanguageMenuComponent;
        expect(menu.screenLanguage).toBe('ar');
        expect(menu.arabicOpensPrintDialog).toBeFalse();
        return component.printPdf(menu.screenLanguage);
      });
      expect(seen.lang).toBe('ar');
      expect(seen.kpi).toBe(arTranslations.dashboard.kpi.activeGroups);
      expect(seen.clone.documentElement.dir).toBe('rtl');
      expect(changes).toEqual([]);
    });

    it('interface en arabe : capture en français et en LTR, écran rétabli en arabe', async () => {
      const seen = await capture('ar');

      expect(seen.lang).toBe('fr');
      expect(seen.kpi).toBe(frTranslations.dashboard.kpi.activeGroups);
      expect(seen.page).toContain(frTranslations.dashboard.kpi.activeGroups);
      expect(seen.page).not.toMatch(/[\u0600-\u06FF]/);
      expect(seen.clone.documentElement.lang).toBe('fr');
      expect(seen.clone.documentElement.dir).toBe('ltr');
      expect(translate.currentLang).toBe('ar');
      expect(component.kpis[2].label).toBe(arTranslations.dashboard.kpi.activeGroups);
    });

    it('interface en français : capture telle que l\'écran, sans changement de langue', async () => {
      translate.use('fr');
      const changes: string[] = [];
      translate.onLangChange.subscribe(event => changes.push(event.lang));
      const seen = await capture('fr');

      expect(seen.lang).toBe('fr');
      expect(seen.kpi).toBe(frTranslations.dashboard.kpi.activeGroups);
      expect(changes).toEqual([]);
    });
  });

  it('pct() et maleRate() ne divisent jamais par zéro', () => {
    // Avant réception des statistiques, les totaux sont nuls.
    expect(component.totalSessions).toBe(0);
    expect(component.pct(5, 0)).toBe(0);
    expect(component.maleRate).toBe(0);

    flushStats();

    expect(component.totalSessions).toBe(52);
    expect(component.maleRate).toBe(50);
  });
});
