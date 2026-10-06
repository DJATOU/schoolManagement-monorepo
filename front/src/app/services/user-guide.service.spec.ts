import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { GuideBook } from '../models/guide/guide';
import { setupServiceTestBed } from '../../testing/setup';
import { CHAPTER_A, CHAPTER_B, guideManifest } from '../../testing/guide-fixtures';
import { buildGuideBook, UserGuideService } from './user-guide.service';

describe('buildGuideBook', () => {
  let book: GuideBook;

  beforeEach(() => {
    book = buildGuideBook(guideManifest(), [CHAPTER_A, CHAPTER_B]);
  });

  it('couverture, sommaire, puis les pages de chaque chapitre, découpées par <!-- page -->', () => {
    expect(book.pages.map(page => page.kind)).toEqual(['cover', 'contents', 'content', 'content', 'content',
      'content', 'content', 'content']);
    expect(book.pages.map(page => page.index)).toEqual([0, 1, 2, 3, 4, 5, 6, 7]);
    expect(book.pages.slice(2).map(page => page.chapterId))
      .toEqual(['demarrer', 'demarrer', 'demarrer', 'paiements', 'paiements', 'paiements']);
    expect(book.pages[3].html).toContain('Deuxième page.');
    expect(book.pages[3].html).not.toContain('page -->');
  });

  it('titre d\'un chapitre = son titre de niveau 1 ; première page de chaque chapitre', () => {
    expect(book.chapters.map(chapter => [chapter.id, chapter.title, chapter.firstPage]))
      .toEqual([['demarrer', 'Démarrer', 2], ['paiements', 'Paiements', 5]]);
    expect(book.pages[6].chapterTitle).toBe('Paiements');
  });

  it('encadrés : classe de couleur et titre dans la langue du guide', () => {
    expect(book.pages[5].html).toContain('<blockquote class="guide-callout guide-callout--tip">'
      + '<p class="guide-callout__title">Exemple</p><p>Lucas verse 800 DA.');
    expect(book.pages[6].html).toContain('guide-callout--warning"><p class="guide-callout__title">Attention</p>');
    expect(book.pages[7].html).toContain('guide-callout--note"><p class="guide-callout__title">À savoir</p>');
    expect(book.pages.map(page => page.html).join('')).not.toContain('[!');
  });

  it('lecture continue : chaque chapitre d\'un seul tenant, sans séparateur', () => {
    expect(book.chapters[0].html).toContain('Première page.');
    expect(book.chapters[0].html).toContain('Troisième page.');
    expect(book.chapters[0].html).not.toContain('<!--');
  });

  it('un lien interne garde sa cible ; sens d\'écriture et libellés recopiés du manifeste', () => {
    expect(book.pages[6].html).toContain('href="#demarrer"');
    const arabic = buildGuideBook(guideManifest({ lang: 'ar', dir: 'rtl', languageName: 'العربية' }), ['# البداية', '']);
    expect(arabic.dir).toBe('rtl');
    expect(arabic.chapters[0].title).toBe('البداية');
    // Chapitre vide ou sans titre : il garde son identifiant, et n'a pas de page.
    expect(arabic.chapters[1].title).toBe('paiements');
    expect(arabic.pages.filter(page => page.chapterId === 'paiements')).toEqual([]);
  });
});

describe('UserGuideService', () => {
  let service: UserGuideService;
  let http: HttpTestingController;

  beforeEach(() => {
    setupServiceTestBed();
    service = TestBed.inject(UserGuideService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function answer(lang: string): void {
    http.expectOne(`assets/guides/${lang}/manifest.json`).flush(guideManifest({ lang: lang as 'fr' }));
    http.expectOne(`assets/guides/${lang}/a.md`).flush(CHAPTER_A);
    http.expectOne(`assets/guides/${lang}/b.md`).flush(CHAPTER_B);
  }

  it('lit le manifeste de la langue, puis chacun de ses chapitres, et assemble le livre', () => {
    let book: GuideBook | undefined;
    service.load('en').subscribe(result => book = result);
    answer('en');

    expect(book?.lang).toBe('en');
    expect(book?.pages.length).toBe(8);
  });

  it('une langue chargée ne se relit pas', () => {
    service.load('fr').subscribe();
    answer('fr');

    let again: GuideBook | undefined;
    service.load('fr').subscribe(result => again = result);
    http.expectNone('assets/guides/fr/manifest.json');
    expect(again?.chapters.length).toBe(2);
  });

  it('un échec n\'est pas gardé : un nouvel essai relit les fichiers', () => {
    let failed = false;
    service.load('ar').subscribe({ error: () => failed = true });
    http.expectOne('assets/guides/ar/manifest.json').flush('absent', { status: 404, statusText: 'Not Found' });
    expect(failed).toBeTrue();

    let book: GuideBook | undefined;
    service.load('ar').subscribe(result => book = result);
    answer('ar');
    expect(book).toBeDefined();
  });
});
