import { TestBed } from '@angular/core/testing';

import { GuideBook, GuideManifest } from '../../../models/guide/guide';
import { buildGuideBook, GUIDE_LANGS } from '../../../services/user-guide.service';
import { setupComponentTestBed } from '../../../../testing/setup';
import { UserGuideComponent } from './user-guide.component';

/**
 * Le vrai contenu du guide, dans les trois langues, lu dans `src/assets/guides/`.
 *
 * <p>Un livre ne se met pas en page tout seul : chaque page est écrite pour tenir dans le format.
 * Ce test le vérifie dans le navigateur, styles appliqués : un texte qui déborde d'une page serait
 * coupé à l'écran (ou forcerait un ascenseur dans la page). Il vérifie aussi que les trois langues
 * racontent le même livre : mêmes chapitres, même nombre de pages, liens internes valides.</p>
 */
describe('Guide d\'utilisation — contenu réel', () => {
  /** Marge laissée en bas de chaque page : les polices varient d'un ordinateur à l'autre. */
  const MAX_FILL = 0.96;

  /**
   * Part de la hauteur de la page occupée par le texte : bas du dernier bloc, marge comprise,
   * rapporté à la hauteur du corps de page. Mesuré en rectangles affichés : le rapport ne dépend pas
   * de la mise à l'échelle du livre.
   */
  function fillOf(body: HTMLElement): number {
    const box = body.getBoundingClientRect();
    const bottom = Math.max(box.top, ...Array.from(body.children).map(child => {
      const rect = child.getBoundingClientRect();
      const scale = box.height / body.clientHeight || 1;
      return rect.bottom + parseFloat(getComputedStyle(child).marginBottom || '0') * scale;
    }));
    return (bottom - box.top) / box.height;
  }

  async function readBook(lang: string): Promise<{ manifest: GuideManifest; book: GuideBook; sources: string[] }> {
    const base = `/assets/guides/${lang}/`;
    const manifest = await (await fetch(`${base}manifest.json`)).json() as GuideManifest;
    const sources = await Promise.all(manifest.chapters.map(async chapter => {
      const response = await fetch(`${base}${chapter.file}`);
      expect(response.status).withContext(`${lang}/${chapter.file}`).toBe(200);
      return response.text();
    }));
    return { manifest, book: buildGuideBook(manifest, sources), sources };
  }

  it('les trois langues ont les mêmes chapitres, dans le même ordre, et autant de pages', async () => {
    const books = await Promise.all(GUIDE_LANGS.map(lang => readBook(lang)));
    const reference = books[0].book;
    const pagesPerChapter = (book: GuideBook) =>
      book.chapters.map(chapter => book.pages.filter(page => page.chapterId === chapter.id).length);

    for (const { book } of books) {
      expect(book.chapters.map(chapter => chapter.id)).withContext(book.lang)
        .toEqual(reference.chapters.map(chapter => chapter.id));
      expect(pagesPerChapter(book)).withContext(`${book.lang} : pages par chapitre`)
        .toEqual(pagesPerChapter(reference));
    }
  });

  it('chaque manifeste est complet : sens d\'écriture, libellés, titres de chapitre', async () => {
    for (const lang of GUIDE_LANGS) {
      const { manifest, book } = await readBook(lang);
      expect(manifest.lang).toBe(lang);
      expect(manifest.dir).withContext(lang).toBe(lang === 'ar' ? 'rtl' : 'ltr');
      const emptyLabels = Object.entries(manifest.labels).filter(([, value]) => !value?.trim()).map(([key]) => key);
      expect(emptyLabels).withContext(`${lang} : libellés vides`).toEqual([]);
      expect(manifest.labels.pageOf).toContain('{{n}}');
      expect(manifest.labels.pageOf).toContain('{{total}}');
      // Un chapitre sans titre « # … » afficherait son identifiant technique.
      expect(book.chapters.filter(chapter => chapter.title === chapter.id).map(chapter => chapter.id))
        .withContext(`${lang} : chapitres sans titre`).toEqual([]);
    }
  });

  it('encadrés reconnus et liens internes valides, dans chaque langue', async () => {
    for (const lang of GUIDE_LANGS) {
      const { book } = await readBook(lang);
      const ids = new Set(book.chapters.map(chapter => chapter.id));
      for (const page of book.pages) {
        // Un encadré mal écrit resterait affiché tel quel : « [!TIPS] ».
        expect(page.html).withContext(`${lang} page ${page.index + 1}`).not.toMatch(/\[![A-Z]+\]/);
        const links = Array.from(page.html.matchAll(/href="#([^"]+)"/g)).map(match => match[1]);
        expect(links.filter(link => !ids.has(link))).withContext(`${lang} page ${page.index + 1} : liens`).toEqual([]);
      }
    }
  });

  for (const lang of GUIDE_LANGS) {
    it(`chaque page tient dans le format du livre (${lang})`, async () => {
      const { book } = await readBook(lang);
      await setupComponentTestBed(UserGuideComponent);
      const fixture = TestBed.createComponent(UserGuideComponent);
      const host = fixture.nativeElement as HTMLElement;
      document.body.appendChild(host);
      const component = fixture.componentInstance;
      component.animate = false;
      fixture.detectChanges();
      component.setBook(book);
      // Une page à la fois : même format qu'une moitié de double page.
      component.single = true;

      const overflowing: string[] = [];
      for (const page of book.pages.filter(entry => entry.kind !== 'cover')) {
        component.position = page.index;
        fixture.detectChanges();
        const body = host.querySelector<HTMLElement>('.guide-slot--single .guide-page__body');
        expect(body).withContext(`${lang} page ${page.index + 1}`).not.toBeNull();
        const fill = fillOf(body!);
        if (fill > MAX_FILL) {
          overflowing.push(`page ${page.index + 1} (${page.chapterTitle ?? 'sommaire'}) : ${Math.round(fill * 100)} %`);
        }
      }
      expect(overflowing).withContext(`${lang} : pages trop pleines`).toEqual([]);
      host.remove();
    });
  }
});
