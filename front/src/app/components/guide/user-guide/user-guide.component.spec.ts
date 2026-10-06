import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';

import { GuideBook } from '../../../models/guide/guide';
import { UserGuideService } from '../../../services/user-guide.service';
import { guideBook } from '../../../../testing/guide-fixtures';
import { setupComponentTestBed } from '../../../../testing/setup';
import { UserGuideComponent } from './user-guide.component';

/**
 * Le guide présenté comme un livre (livre de 8 pages des fixtures).
 *
 * <p>En double page : position 0 = [—, couverture], 1 = [sommaire, p.3], 2 = [p.4, p.5],
 * 3 = [p.6, p.7], 4 = [p.8, —].</p>
 */
describe('UserGuideComponent', () => {
  let fixture: ComponentFixture<UserGuideComponent>;
  let component: UserGuideComponent;
  let guide: jasmine.SpyObj<UserGuideService>;
  let host: HTMLElement;

  async function open(options: { width?: number; book?: GuideBook; query?: Record<string, string>; fail?: boolean } = {}) {
    localStorage.removeItem('guide.lang');
    guide = jasmine.createSpyObj<UserGuideService>('UserGuideService', ['load']);
    guide.load.and.callFake(lang => options.fail ? throwError(() => new Error('404'))
      : of(options.book ?? guideBook({ lang, dir: lang === 'ar' ? 'rtl' : 'ltr' })));
    await setupComponentTestBed(UserGuideComponent, {
      providers: [
        { provide: UserGuideService, useValue: guide },
        ...(options.query ? [{ provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(options.query) } } }] : [])
      ]
    });
    fixture = TestBed.createComponent(UserGuideComponent);
    component = fixture.componentInstance;
    host = fixture.nativeElement as HTMLElement;
    host.style.display = 'block';
    host.style.width = `${options.width ?? 1100}px`;
    host.style.height = '760px';
    document.body.appendChild(host);
    component.animate = false;
    fixture.detectChanges();
    component.layout();
    fixture.detectChanges();
  }

  afterEach(() => {
    host?.remove();
    localStorage.removeItem('guide.lang');
  });

  const text = (selector: string) => (host.querySelector(selector)?.textContent ?? '').replace(/\s+/g, ' ').trim();

  describe('double page', () => {
    beforeEach(async () => open());

    it('s\'ouvre fermé : la couverture seule, à droite, dans la langue de l\'application', () => {
      expect(guide.load).toHaveBeenCalledWith('fr');
      expect(component.single).toBeFalse();
      expect(component.position).toBe(0);
      expect(component.visible.map(page => page?.kind ?? null)).toEqual([null, 'cover']);
      expect(host.querySelector('.guide-slot--second .guide-cover')).not.toBeNull();
      expect(component.pageLabel).toBe('Page 1 sur 8');
      expect(component.canPrevious).toBeFalse();
    });

    it('page suivante : sommaire à gauche, première page du premier chapitre à droite', () => {
      component.next();
      fixture.detectChanges();

      expect(component.position).toBe(1);
      expect(text('.guide-slot--first')).toContain('Sommaire');
      expect(text('.guide-slot--second')).toContain('Première page.');
      expect(text('.guide-slot--second .guide-page__running')).toBe('Démarrer');
      expect(text('.ug-page-label')).toBe('Page 2–3 sur 8');
    });

    it('le sommaire ouvre un chapitre ; un lien interne ramène à un autre', () => {
      component.next();
      fixture.detectChanges();

      host.querySelectorAll<HTMLButtonElement>('.guide-contents__link')[1].click();
      fixture.detectChanges();
      expect(component.position).toBe(3);
      expect(text('.guide-slot--first')).toContain('Lucas verse 800 DA.');

      host.querySelector<HTMLAnchorElement>('.guide-slot--second a[href="#demarrer"]')!.click();
      fixture.detectChanges();
      expect(component.position).toBe(1);
    });

    it('clavier : flèches, début et fin ; rien au-delà des couvertures', () => {
      const press = (key: string) => document.dispatchEvent(new KeyboardEvent('keydown', { key }));

      press('ArrowLeft');
      expect(component.position).toBe(0);
      press('ArrowRight');
      press('ArrowRight');
      expect(component.position).toBe(2);
      press('End');
      expect(component.position).toBe(4);
      press('ArrowRight');
      expect(component.position).toBe(4);
      press('Home');
      expect(component.position).toBe(0);
    });

    it('la feuille tourne autour de la reliure : recto, verso, et ce qu\'elle découvre', () => {
      component.position = 1;
      const forward = component.buildFlip(2);
      expect([forward.front?.index, forward.back?.index, forward.underFirst?.index, forward.underSecond?.index])
        .toEqual([2, 3, 1, 4]);
      expect([forward.slot, forward.origin, forward.from, forward.to]).toEqual(['second', 'left', 0, -180]);

      component.position = 2;
      const backward = component.buildFlip(1);
      expect([backward.front?.index, backward.back?.index, backward.underFirst?.index, backward.underSecond?.index])
        .toEqual([3, 2, 1, 4]);
      expect([backward.slot, backward.origin, backward.from, backward.to]).toEqual(['first', 'right', 0, 180]);
    });

    it('animée : la feuille apparaît, tourne, puis la double page suivante est posée', () => {
      component.animate = true;
      component.next();

      expect(component.flip).not.toBeNull();
      expect(host.querySelector('.ug-sheet--second .ug-face--front .guide-cover')).not.toBeNull();
      expect(text('.ug-sheet .ug-face--back')).toContain('Sommaire');
      expect(host.querySelector<HTMLButtonElement>('.ug-next')!.disabled).toBeTrue();
      // Une seconde demande pendant que la feuille tourne est ignorée.
      component.next();

      (component as unknown as { tween: gsap.core.Tween }).tween.progress(1);
      fixture.detectChanges();
      expect(component.flip).toBeNull();
      expect(component.position).toBe(1);
      expect(host.querySelector('.ug-sheet')).toBeNull();
    });

    it('tirer la page vers la reliure la tourne ; un petit geste ne fait rien', () => {
      const book = host.querySelector<HTMLElement>('.ug-book')!;
      const second = host.querySelector<HTMLElement>('.guide-slot--second')!;
      const box = second.getBoundingClientRect();
      const pointer = (type: string, x: number, target: HTMLElement = second) =>
        target.dispatchEvent(new PointerEvent(type, { pointerId: 7, clientX: x, clientY: box.top + 100, button: 0, bubbles: true }));

      pointer('pointerdown', box.right - 20);
      pointer('pointermove', box.right - 26);
      pointer('pointerup', box.right - 26, book);
      expect(component.position).toBe(0);

      pointer('pointerdown', box.right - 20);
      pointer('pointermove', box.left + 10);
      pointer('pointerup', box.left + 10, book);
      expect(component.position).toBe(1);
    });

    it('animée, tirée à moitié puis relâchée trop tôt : la page retombe', () => {
      component.animate = true;
      const second = host.querySelector<HTMLElement>('.guide-slot--second')!;
      const box = second.getBoundingClientRect();
      const pointer = (type: string, x: number) =>
        second.dispatchEvent(new PointerEvent(type, { pointerId: 3, clientX: x, clientY: box.top + 50, button: 0, bubbles: true }));

      pointer('pointerdown', box.right - 10);
      pointer('pointermove', box.right - 40);
      expect(component.flip).not.toBeNull();
      // Relâchée lentement, à moins d'un tiers de la largeur : elle n'est pas tournée.
      (component as unknown as { drag: { time: number } }).drag.time -= 5000;
      pointer('pointerup', box.right - 40);
      (component as unknown as { tween: gsap.core.Tween }).tween.progress(1);

      expect(component.flip).toBeNull();
      expect(component.position).toBe(0);
    });

    it('fermé ou refermé, le livre glisse d\'une demi-page pour rester centré', () => {
      expect(component.bookShift).toBe(-25);
      component.turnTo(2);
      expect(component.bookShift).toBe(0);
      component.turnTo(4);
      expect(component.bookShift).toBe(25);
      expect(component.visible.map(page => page?.index ?? null)).toEqual([7, null]);
    });

    it('lecture continue : tous les chapitres d\'un seul tenant, sommaire qui y mène', () => {
      const scroll = spyOn(Element.prototype, 'scrollIntoView');
      host.querySelector<HTMLButtonElement>('.ug-mode-scroll')!.click();
      fixture.detectChanges();

      expect(host.querySelector('.ug-book')).toBeNull();
      expect(host.querySelectorAll('.ug-scroll-chapter').length).toBe(2);
      expect(text('#guide-chapter-paiements')).toContain('Rien sans motif.');
      host.querySelectorAll<HTMLButtonElement>('.ug-scroll-toc__item')[1].click();
      expect(scroll).toHaveBeenCalled();
    });

    it('changer de langue garde le chapitre ouvert, et retient le choix', () => {
      component.turnTo(3);
      host.querySelectorAll<HTMLButtonElement>('.ug-segment')[2].click();
      fixture.detectChanges();

      expect(guide.load).toHaveBeenCalledWith('ar');
      expect(localStorage.getItem('guide.lang')).toBe('ar');
      expect(component.position).toBe(3);
      expect(host.querySelector('.user-guide')!.getAttribute('dir')).toBe('rtl');
    });
  });

  it('écran étroit : une page à la fois, la page lue est conservée', async () => {
    await open();
    component.turnTo(2);
    host.style.width = '520px';
    component.layout();
    fixture.detectChanges();

    expect(component.single).toBeTrue();
    expect(component.position).toBe(3);
    expect(host.querySelectorAll('.guide-slot').length).toBe(1);
    expect(component.bookWidth).toBe(component.pageWidth);

    component.animate = true;
    const flip = component.buildFlip(4);
    expect([flip.slot, flip.front?.index, flip.underFirst?.index, flip.from, flip.to]).toEqual(['single', 3, 4, 0, -180]);
    const back = component.buildFlip(2);
    expect([back.front?.index, back.underFirst?.index, back.from, back.to]).toEqual([2, 3, -180, 0]);
  });

  it('arabe : le livre s\'ouvre dans l\'autre sens, la flèche gauche avance', async () => {
    await open({ book: guideBook({ lang: 'ar', dir: 'rtl' }) });

    const flip = component.buildFlip(1);
    expect([flip.origin, flip.to]).toEqual(['right', 180]);
    expect(component.nextIcon).toBe('chevron_left');
    // La couverture est à gauche : le livre fermé glisse vers la droite.
    expect(component.bookShift).toBe(25);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft' }));
    expect(component.position).toBe(1);
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    expect(component.position).toBe(0);
  });

  it('lien vers un chapitre (?chapitre=) : le livre s\'ouvre à sa première page', async () => {
    await open({ query: { chapitre: 'paiements' } });

    expect(component.position).toBe(3);
    expect(text('.guide-slot--first')).toContain('Lucas verse 800 DA.');
  });

  it('guide introuvable : le dit, et propose de réessayer', async () => {
    await open({ fail: true });

    expect(component.failed).toBeTrue();
    expect(text('.ug-error')).toContain('Le guide n\'a pas pu être chargé.');
    host.querySelector<HTMLButtonElement>('.ug-error button')!.click();
    expect(guide.load).toHaveBeenCalledTimes(2);
  });
});
