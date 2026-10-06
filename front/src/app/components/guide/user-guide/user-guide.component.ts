import {
  AfterViewInit, ChangeDetectorRef, Component, ElementRef, HostListener, NgZone, OnDestroy, OnInit, ViewChild,
  ViewEncapsulation
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateService } from '@ngx-translate/core';
import { gsap } from 'gsap';
import { Subscription } from 'rxjs';
import { GuideBook, GuideLang, GuidePage } from '../../../models/guide/guide';
import { GUIDE_LANGS, UserGuideService } from '../../../services/user-guide.service';

/** Format d'une page, en pixels CSS avant mise à l'échelle. Le test de mise en page s'y réfère. */
export const GUIDE_PAGE_WIDTH = 440;
export const GUIDE_PAGE_HEIGHT = 600;

/** En dessous de cette largeur disponible, le livre montre une page à la fois. */
const SINGLE_PAGE_BELOW = 760;

/** Durée d'une page tournée en entier, en secondes. */
const FLIP_SECONDS = 0.85;

const LANGUAGE_KEY = 'guide.lang';

/** Nom de chaque langue dans sa propre langue : le sélecteur se lit quelle que soit la langue affichée. */
export const GUIDE_LANG_NAMES: Record<GuideLang, string> = { fr: 'Français', en: 'English', ar: 'العربية' };

/** Message d'échec quand le manifeste lui-même n'a pas pu être lu (ses libellés manquent alors). */
const LOAD_ERROR: Record<GuideLang, { message: string; retry: string }> = {
  fr: { message: 'Le guide n\'a pas pu être chargé.', retry: 'Réessayer' },
  en: { message: 'The guide could not be loaded.', retry: 'Try again' },
  ar: { message: 'تعذّر تحميل الدليل.', retry: 'إعادة المحاولة' }
};

type Slot = 'first' | 'second' | 'single';

/**
 * Une page en train de tourner.
 *
 * <p>La feuille porte deux faces : celle qu'on voit au départ ({@code front}) et son verso
 * ({@code back}), qui se pose sur l'autre moitié du livre à la fin. Sous elle, l'écran montre déjà
 * les pages qu'elle découvre ({@code underFirst}, {@code underSecond}).</p>
 */
export interface GuideFlip {
  target: number;
  slot: Slot;
  front: GuidePage | null;
  back: GuidePage | null;
  underFirst: GuidePage | null;
  underSecond: GuidePage | null;
  /** Bord de la feuille autour duquel elle tourne : toujours la reliure. */
  origin: 'left' | 'right';
  from: number;
  to: number;
}

interface Drag {
  pointerId: number;
  x: number;
  y: number;
  time: number;
  width: number;
  started: boolean;
}

/**
 * Guide d'utilisation de l'administrateur, présenté comme un livre dont on tourne les pages.
 *
 * <p>Une page tourne réellement : la feuille pivote en 3D autour de la reliure, son ombre et sa
 * lumière suivent l'angle, et la page suivante apparaît dessous. On la tourne au clavier (flèches),
 * avec les boutons, en cliquant sur un coin, ou en la tirant du doigt ou de la souris. En arabe, le
 * livre s'ouvre dans l'autre sens. Si l'ordinateur demande de réduire les animations, les pages
 * changent sans mouvement.</p>
 *
 * <p>La lecture continue montre le même contenu d'un seul tenant, pour chercher un passage.</p>
 */
@Component({
  selector: 'app-user-guide',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule, MatTooltipModule],
  templateUrl: './user-guide.component.html',
  styleUrls: ['./user-guide.component.scss', './user-guide-pages.scss'],
  // Le contenu des pages est du HTML injecté : ses styles doivent l'atteindre. Tout est préfixé par
  // .user-guide pour ne rien déborder sur le reste de l'application.
  encapsulation: ViewEncapsulation.None
})
export class UserGuideComponent implements OnInit, AfterViewInit, OnDestroy {
  readonly langs = GUIDE_LANGS;
  readonly langNames = GUIDE_LANG_NAMES;
  readonly pageWidth = GUIDE_PAGE_WIDTH;
  readonly pageHeight = GUIDE_PAGE_HEIGHT;

  lang: GuideLang = 'fr';
  book: GuideBook | null = null;
  loading = false;
  failed = false;
  mode: 'book' | 'scroll' = 'book';

  /** Une page à la fois (écran étroit), ou une double page. */
  single = false;
  /** Double page : numéro de la double page. Une page : index de la page. */
  position = 0;
  scale = 1;
  flip: GuideFlip | null = null;

  /** Faux si l'ordinateur demande de réduire les animations ; les tests le fixent eux-mêmes. */
  animate = !(typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches);

  @ViewChild('stage') stageRef?: ElementRef<HTMLElement>;
  @ViewChild('sheet') sheetRef?: ElementRef<HTMLElement>;
  @ViewChild('bookEl') bookRef?: ElementRef<HTMLElement>;

  private tween: gsap.core.Tween | null = null;
  private drag: Drag | null = null;
  private resizeObserver?: ResizeObserver;
  private loadSub?: Subscription;
  /** Chapitre à ouvrir une fois le livre chargé (lien `?chapitre=`, changement de langue). */
  private pendingChapter: string | null = null;

  constructor(private guideService: UserGuideService,
              private translate: TranslateService,
              private route: ActivatedRoute,
              private cdr: ChangeDetectorRef,
              private zone: NgZone) {}

  ngOnInit(): void {
    this.pendingChapter = this.route.snapshot.queryParamMap.get('chapitre');
    this.load(this.initialLang());
  }

  ngAfterViewInit(): void {
    if (typeof ResizeObserver !== 'undefined') {
      this.resizeObserver = new ResizeObserver(() => this.zone.run(() => this.layout()));
    }
    this.observeStage();
  }

  ngOnDestroy(): void {
    this.tween?.kill();
    this.resizeObserver?.disconnect();
    this.loadSub?.unsubscribe();
  }

  // ------------------------------------------------------------------
  // Langue et chargement
  // ------------------------------------------------------------------

  get rtl(): boolean {
    return this.book?.dir === 'rtl';
  }

  setLang(lang: GuideLang): void {
    if (lang === this.lang && this.book) {
      return;
    }
    // On garde le chapitre en cours : changer de langue ne renvoie pas à la couverture.
    this.pendingChapter = this.currentChapterId ?? this.pendingChapter;
    localStorage.setItem(LANGUAGE_KEY, lang);
    this.load(lang);
  }

  retry(): void {
    this.load(this.lang);
  }

  get errorText(): { message: string; retry: string } {
    return LOAD_ERROR[this.lang];
  }

  private initialLang(): GuideLang {
    const saved = localStorage.getItem(LANGUAGE_KEY) as GuideLang | null;
    if (saved && GUIDE_LANGS.includes(saved)) {
      return saved;
    }
    const appLang = this.translate.currentLang as GuideLang;
    return GUIDE_LANGS.includes(appLang) ? appLang : 'fr';
  }

  private load(lang: GuideLang): void {
    this.lang = lang;
    this.loading = true;
    this.failed = false;
    this.cancelMotion();
    this.loadSub?.unsubscribe();
    this.loadSub = this.guideService.load(lang).subscribe({
      next: book => this.setBook(book),
      error: () => {
        this.loading = false;
        this.failed = true;
      }
    });
  }

  /** Installe un livre chargé, sur le chapitre demandé s'il y en a un. */
  setBook(book: GuideBook): void {
    this.book = book;
    this.loading = false;
    this.failed = false;
    const chapter = book.chapters.find(entry => entry.id === this.pendingChapter);
    this.pendingChapter = null;
    this.position = this.positionOf(chapter ? chapter.firstPage : 0);
    this.cdr.detectChanges();
    this.observeStage();
    this.layout();
  }

  // ------------------------------------------------------------------
  // Mise en page
  // ------------------------------------------------------------------

  setMode(mode: 'book' | 'scroll'): void {
    if (mode === this.mode) {
      return;
    }
    this.cancelMotion();
    this.mode = mode;
    this.cdr.detectChanges();
    this.observeStage();
    this.layout();
  }

  /**
   * Décalage du livre, en % de sa largeur : fermé (couverture seule) ou refermé (dernière page
   * seule), il glisse d'une demi-page pour rester centré, comme un livre posé sur la table. Pendant
   * qu'une page tourne, on vise déjà la position d'arrivée : le livre glisse en même temps.
   */
  get bookShift(): number {
    if (this.single || !this.book) {
      return 0;
    }
    const position = this.flip ? this.flip.target : this.position;
    const first = this.page(position * 2 - 1);
    const second = this.page(position * 2);
    const sign = this.rtl ? -1 : 1;
    if (!first && second) {
      return -25 * sign;
    }
    if (first && !second) {
      return 25 * sign;
    }
    return 0;
  }

  /** Largeur du livre fermé à plat, avant mise à l'échelle. */
  get bookWidth(): number {
    return this.single ? this.pageWidth : this.pageWidth * 2;
  }

  /** Une page à la fois sous {@link SINGLE_PAGE_BELOW} ; le livre est mis à l'échelle de la place disponible. */
  layout(): void {
    const stage = this.stageRef?.nativeElement;
    if (!stage || !this.book) {
      return;
    }
    const width = stage.clientWidth;
    const height = stage.clientHeight;
    if (width === 0) {
      return;
    }
    const readingPage = this.readingPage;
    const single = width < SINGLE_PAGE_BELOW;
    if (single !== this.single) {
      this.cancelMotion();
      this.single = single;
      this.position = this.positionOf(readingPage);
    }
    const byWidth = (width - 24) / this.bookWidth;
    // Le cadre réserve 22 px en haut et en bas : la page qui tourne a besoin de cette marge.
    const byHeight = height > 0 ? (height - 44) / this.pageHeight : byWidth;
    this.scale = Math.max(0.45, Math.min(byWidth, byHeight, 1.3));
  }

  private observeStage(): void {
    const stage = this.stageRef?.nativeElement;
    if (stage && this.resizeObserver) {
      this.resizeObserver.disconnect();
      this.resizeObserver.observe(stage);
    }
  }

  // ------------------------------------------------------------------
  // Position dans le livre
  // ------------------------------------------------------------------

  private page(index: number): GuidePage | null {
    return this.book && index >= 0 && index < this.book.pages.length ? this.book.pages[index] : null;
  }

  /** Double page qui contient cette page : la couverture est seule, à droite (à gauche en arabe). */
  private positionOf(pageIndex: number): number {
    return this.single ? pageIndex : Math.floor((pageIndex + 1) / 2);
  }

  /** Première page visible : celle qu'on lit. */
  get readingPage(): number {
    return this.single ? this.position : Math.max(0, this.position * 2 - 1);
  }

  get lastPosition(): number {
    const count = this.book?.pages.length ?? 0;
    return count === 0 ? 0 : this.positionOf(count - 1);
  }

  /** Pages visibles, dans l'ordre de lecture : la première puis la seconde. */
  get visible(): (GuidePage | null)[] {
    if (this.single) {
      return [this.page(this.position)];
    }
    return [this.page(this.position * 2 - 1), this.page(this.position * 2)];
  }

  get canPrevious(): boolean {
    return !!this.book && this.position > 0;
  }

  get canNext(): boolean {
    return !!this.book && this.position < this.lastPosition;
  }

  get currentChapterId(): string | null {
    if (!this.book) {
      return null;
    }
    const pages = this.visible.filter((page): page is GuidePage => !!page && !!page.chapterId);
    return pages.length > 0 ? pages[0].chapterId : null;
  }

  /** « Page 4–5 sur 31 », pour la barre du bas et le lecteur d'écran. */
  get pageLabel(): string {
    if (!this.book) {
      return '';
    }
    const numbers = this.visible.filter((page): page is GuidePage => !!page).map(page => page.index + 1);
    return this.book.labels.pageOf
      .replace('{{n}}', numbers.join('–'))
      .replace('{{total}}', String(this.book.pages.length));
  }

  // ------------------------------------------------------------------
  // Tourner les pages
  // ------------------------------------------------------------------

  next(): void {
    if (this.canNext) {
      this.turnTo(this.position + 1);
    }
  }

  previous(): void {
    if (this.canPrevious) {
      this.turnTo(this.position - 1);
    }
  }

  /** Ouvre le livre à une page : depuis le sommaire, ou un lien vers un chapitre. */
  goToPage(pageIndex: number): void {
    if (this.mode === 'scroll') {
      const chapterId = this.page(pageIndex)?.chapterId;
      if (chapterId) {
        this.scrollToChapter(chapterId);
      }
      return;
    }
    this.turnTo(this.positionOf(pageIndex));
  }

  goToChapter(chapterId: string): void {
    const chapter = this.book?.chapters.find(entry => entry.id === chapterId);
    if (chapter) {
      this.goToPage(chapter.firstPage);
    }
  }

  /** Tourne vers une position : une seule feuille, même pour un saut de plusieurs pages. */
  turnTo(target: number): void {
    if (!this.book || this.flip || target === this.position || target < 0 || target > this.lastPosition) {
      return;
    }
    if (!this.animate) {
      this.position = target;
      return;
    }
    this.beginFlip(target);
    this.playTo(1);
  }

  /** Calcule la feuille qui tourne et ce qu'elle découvre ; en arabe, tout se fait en miroir. */
  buildFlip(target: number): GuideFlip {
    const forward = target > this.position;
    const current = this.position;
    // Sens de rotation d'une page tournée vers l'avant : vers la gauche en français, vers la droite en arabe.
    const turned = this.rtl ? 180 : -180;
    if (this.single) {
      return forward
        ? { target, slot: 'single', front: this.page(current), back: null, underFirst: this.page(target),
            underSecond: null, origin: this.rtl ? 'right' : 'left', from: 0, to: turned }
        : { target, slot: 'single', front: this.page(target), back: null, underFirst: this.page(current),
            underSecond: null, origin: this.rtl ? 'right' : 'left', from: turned, to: 0 };
    }
    return forward
      ? { target, slot: 'second', front: this.page(current * 2), back: this.page(target * 2 - 1),
          underFirst: this.page(current * 2 - 1), underSecond: this.page(target * 2),
          origin: this.rtl ? 'right' : 'left', from: 0, to: turned }
      : { target, slot: 'first', front: this.page(current * 2 - 1), back: this.page(target * 2),
          underFirst: this.page(target * 2 - 1), underSecond: this.page(current * 2),
          origin: this.rtl ? 'left' : 'right', from: 0, to: -turned };
  }

  private beginFlip(target: number): void {
    this.flip = this.buildFlip(target);
    this.cdr.detectChanges();
    const sheet = this.sheetRef?.nativeElement;
    if (sheet) {
      gsap.set(sheet, { rotationY: this.flip.from });
      this.shade(this.flip.from);
    }
  }

  /** Termine (1) ou annule (0) la page en cours, depuis l'angle où elle se trouve. */
  private playTo(end: 0 | 1): void {
    const flip = this.flip;
    const sheet = this.sheetRef?.nativeElement;
    if (!flip || !sheet) {
      this.settle(end);
      return;
    }
    const angle = end === 1 ? flip.to : flip.from;
    const remaining = Math.abs(angle - this.currentAngle()) / 180;
    this.tween?.kill();
    this.zone.runOutsideAngular(() => {
      this.tween = gsap.to(sheet, {
        rotationY: angle,
        duration: Math.max(0.18, FLIP_SECONDS * remaining),
        ease: 'power2.inOut',
        onUpdate: () => this.shade(this.currentAngle()),
        onComplete: () => this.zone.run(() => this.settle(end))
      });
    });
  }

  /** La feuille s'est posée : la nouvelle double page devient la page courante. */
  private settle(end: 0 | 1): void {
    if (this.flip && end === 1) {
      this.position = this.flip.target;
    }
    this.tween = null;
    this.flip = null;
    this.bookRef?.nativeElement.style.setProperty('--lift', '0');
    this.cdr.detectChanges();
  }

  /** Arrête une page en mouvement sans la poser : changement de langue, de mode, de format. */
  private cancelMotion(): void {
    this.tween?.kill();
    this.tween = null;
    this.flip = null;
    this.drag = null;
  }

  private currentAngle(): number {
    const sheet = this.sheetRef?.nativeElement;
    return sheet ? Number(gsap.getProperty(sheet, 'rotationY')) || 0 : 0;
  }

  /**
   * Lumière de la feuille selon son angle : la face qui s'éloigne s'assombrit, le verso s'éclaire
   * en se posant, et la page découverte reçoit l'ombre de la feuille levée.
   */
  private shade(angle: number): void {
    const turned = Math.min(1, Math.abs(angle) / 180);
    this.sheetRef?.nativeElement.style.setProperty('--turn', turned.toFixed(3));
    this.bookRef?.nativeElement.style.setProperty('--lift', Math.sin(Math.PI * turned).toFixed(3));
  }

  // ------------------------------------------------------------------
  // Tirer une page du doigt ou de la souris
  // ------------------------------------------------------------------

  onPointerDown(event: PointerEvent): void {
    if (this.flip || !this.book || this.mode !== 'book' || event.button !== 0) {
      return;
    }
    const slot = (event.target as HTMLElement).closest('.guide-slot') as HTMLElement | null;
    this.drag = {
      pointerId: event.pointerId, x: event.clientX, y: event.clientY, time: performance.now(),
      width: slot?.getBoundingClientRect().width || this.pageWidth * this.scale, started: false
    };
  }

  onPointerMove(event: PointerEvent): void {
    const drag = this.drag;
    if (!drag || event.pointerId !== drag.pointerId) {
      return;
    }
    const dx = event.clientX - drag.x;
    if (!drag.started) {
      if (Math.abs(dx) < 12 || Math.abs(dx) < Math.abs(event.clientY - drag.y)) {
        return;
      }
      // Tirer vers la reliure tourne la page : vers la gauche en français, vers la droite en arabe.
      const forward = this.rtl ? dx > 0 : dx < 0;
      const target = this.position + (forward ? 1 : -1);
      if (target < 0 || target > this.lastPosition) {
        this.drag = null;
        return;
      }
      drag.started = true;
      try {
        // La page suit le pointeur même s'il sort du livre.
        (event.currentTarget as HTMLElement | null)?.setPointerCapture?.(event.pointerId);
      } catch {
        // Pointeur déjà relâché : la page suit tout de même tant que les événements arrivent.
      }
      if (!this.animate) {
        this.drag = null;
        this.position = target;
        return;
      }
      this.beginFlip(target);
    }
    const flip = this.flip;
    const sheet = this.sheetRef?.nativeElement;
    if (flip && sheet) {
      const progress = Math.min(1, Math.abs(dx) / drag.width);
      const angle = flip.from + (flip.to - flip.from) * progress;
      gsap.set(sheet, { rotationY: angle });
      this.shade(angle);
    }
  }

  onPointerUp(event: PointerEvent): void {
    const drag = this.drag;
    this.drag = null;
    if (!drag || !drag.started || !this.flip || event.pointerId !== drag.pointerId) {
      return;
    }
    const dx = Math.abs(event.clientX - drag.x);
    const fast = dx / Math.max(1, performance.now() - drag.time) > 0.5;
    this.playTo(dx / drag.width > 0.3 || fast ? 1 : 0);
  }

  // ------------------------------------------------------------------
  // Clavier, liens
  // ------------------------------------------------------------------

  @HostListener('document:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    if (this.mode !== 'book' || !this.book) {
      return;
    }
    const target = event.target as HTMLElement | null;
    if (target && /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName)) {
      return;
    }
    // En arabe, la page suivante est à gauche.
    const forwardKey = this.rtl ? 'ArrowLeft' : 'ArrowRight';
    const backwardKey = this.rtl ? 'ArrowRight' : 'ArrowLeft';
    let handled = true;
    switch (event.key) {
      case forwardKey:
      case 'PageDown':
        this.next();
        break;
      case backwardKey:
      case 'PageUp':
        this.previous();
        break;
      case 'Home':
        this.turnTo(0);
        break;
      case 'End':
        this.turnTo(this.lastPosition);
        break;
      default:
        handled = false;
    }
    if (handled) {
      event.preventDefault();
    }
  }

  /** Un lien `#paiements` dans une page ouvre ce chapitre, en mode livre comme en lecture continue. */
  @HostListener('click', ['$event'])
  onContentClick(event: MouseEvent): void {
    const link = (event.target as HTMLElement).closest('a') as HTMLAnchorElement | null;
    const href = link?.getAttribute('href') ?? '';
    if (href.startsWith('#') && href.length > 1) {
      event.preventDefault();
      this.goToChapter(href.slice(1));
    }
  }

  scrollToChapter(chapterId: string): void {
    document.getElementById(`guide-chapter-${chapterId}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  /** Icônes « précédent / suivant » : elles pointent dans le sens de lecture. */
  get previousIcon(): string {
    return this.rtl ? 'chevron_right' : 'chevron_left';
  }

  get nextIcon(): string {
    return this.rtl ? 'chevron_left' : 'chevron_right';
  }

  /** Côté du livre où se pose le verso de la feuille : l'opposé de celui qu'elle quitte. */
  backSide(flip: GuideFlip): Slot {
    if (flip.slot === 'single') {
      return 'single';
    }
    return flip.slot === 'second' ? 'first' : 'second';
  }
}
