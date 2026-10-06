import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { marked } from 'marked';
import { forkJoin, Observable, throwError } from 'rxjs';
import { catchError, map, shareReplay, switchMap } from 'rxjs/operators';
import { GuideBook, GuideChapter, GuideLang, GuideManifest, GuidePage } from '../models/guide/guide';

/** Langues du guide, dans l'ordre du sélecteur. */
export const GUIDE_LANGS: GuideLang[] = ['fr', 'en', 'ar'];

/** Séparateur de pages dans un chapitre : invisible dans un aperçu Markdown (GitHub, éditeur). */
const PAGE_BREAK = /^[ \t]*<!--\s*page\s*-->[ \t]*$/m;

/** Encadrés à la manière de GitHub : `> [!TIP]` (exemple), `> [!NOTE]` (à savoir), `> [!WARNING]`. */
const CALLOUT = /<blockquote>\s*<p>\[!(TIP|NOTE|WARNING)\]\s*/g;

/**
 * Assemble le livre : couverture, sommaire, puis les pages de chaque chapitre.
 *
 * <p>Fonction pure, sans réseau : le test de mise en page l'appelle sur les vrais fichiers.</p>
 *
 * @param manifest manifeste de la langue
 * @param sources  Markdown de chaque chapitre, dans l'ordre du manifeste
 */
export function buildGuideBook(manifest: GuideManifest, sources: string[]): GuideBook {
  const calloutTitles: Record<string, string> = {
    TIP: manifest.labels.example,
    NOTE: manifest.labels.note,
    WARNING: manifest.labels.warning
  };
  const render = (markdown: string): string => withCallouts(
    marked.parse(markdown, { async: false, gfm: true }) as string, calloutTitles);

  const pages: GuidePage[] = [
    { index: 0, kind: 'cover', chapterId: null, chapterTitle: null, html: '' },
    { index: 1, kind: 'contents', chapterId: null, chapterTitle: null, html: '' }
  ];
  const chapters: GuideChapter[] = manifest.chapters.map((entry, position) => {
    const markdown = sources[position] ?? '';
    const title = chapterTitle(markdown, entry.id);
    const firstPage = pages.length;
    markdown.split(PAGE_BREAK)
      .map(part => part.trim())
      .filter(part => part.length > 0)
      .forEach(part => pages.push({
        index: pages.length, kind: 'content', chapterId: entry.id, chapterTitle: title, html: render(part)
      }));
    return { id: entry.id, title, firstPage, html: render(markdown.replace(new RegExp(PAGE_BREAK, 'gm'), '')) };
  });

  return {
    lang: manifest.lang,
    dir: manifest.dir,
    languageName: manifest.languageName,
    title: manifest.title,
    labels: manifest.labels,
    chapters,
    pages
  };
}

/** Titre du chapitre : son premier titre de niveau 1, sinon son identifiant. */
function chapterTitle(markdown: string, fallback: string): string {
  const heading = /^#[ \t]+(.+)$/m.exec(markdown);
  return heading ? heading[1].trim() : fallback;
}

/** Transforme `> [!TIP]` en encadré titré : la classe porte la couleur, le titre la langue. */
function withCallouts(html: string, titles: Record<string, string>): string {
  return html.replace(CALLOUT, (_match, kind: string) =>
    `<blockquote class="guide-callout guide-callout--${kind.toLowerCase()}">`
    + `<p class="guide-callout__title">${titles[kind]}</p><p>`);
}

/**
 * Charge le guide d'une langue depuis les fichiers statiques de l'application.
 *
 * <p>Une langue chargée est gardée : revenir au français après l'arabe ne relit rien. Un échec
 * n'est pas gardé, pour qu'un nouvel essai relise les fichiers.</p>
 */
@Injectable({ providedIn: 'root' })
export class UserGuideService {
  private readonly cache = new Map<GuideLang, Observable<GuideBook>>();

  constructor(private http: HttpClient) {}

  load(lang: GuideLang): Observable<GuideBook> {
    const cached = this.cache.get(lang);
    if (cached) {
      return cached;
    }
    const base = `assets/guides/${lang}/`;
    const book$ = this.http.get<GuideManifest>(`${base}manifest.json`).pipe(
      switchMap(manifest => forkJoin(manifest.chapters.map(chapter =>
        this.http.get(`${base}${chapter.file}`, { responseType: 'text' })))
        .pipe(map(sources => buildGuideBook(manifest, sources)))),
      catchError(error => {
        this.cache.delete(lang);
        return throwError(() => error);
      }),
      shareReplay({ bufferSize: 1, refCount: false })
    );
    this.cache.set(lang, book$);
    return book$;
  }
}
