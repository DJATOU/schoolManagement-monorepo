import { ComponentFixture } from '@angular/core/testing';
import { TDocumentDefinitions } from 'pdfmake/interfaces';

import { PrintLanguage } from '../app/shared/pdf-i18n';
import { stripBidiControls } from '../app/shared/print/bidi-isolate';
import { PAGE_SENTINEL, PAGES_SENTINEL } from '../app/shared/print/pdf-to-html';

/**
 * Outils de test du choix de langue à l'impression et du rendu HTML des documents en arabe.
 */

/**
 * Ouvre le menu de langue placé dans `host` (sélecteur ou élément) et choisit `lang`.
 *
 * <p>Le menu est rendu dans l'overlay du CDK, hors du composant : l'entrée est cherchée dans le
 * document.</p>
 */
export function choosePrintLanguage(fixture: ComponentFixture<unknown>, host: string | Element,
                                    lang: PrintLanguage): void {
  const root = typeof host === 'string'
    ? (fixture.nativeElement as HTMLElement).querySelector(host)
    : host;
  const trigger = root?.querySelector<HTMLButtonElement>('.plm-trigger');
  if (!trigger) {
    throw new Error(`Menu de langue introuvable dans ${String(host)}`);
  }
  trigger.click();
  fixture.detectChanges();
  const option = document.querySelector<HTMLButtonElement>(`.plm-menu .plm-option[data-lang="${lang}"]`);
  if (!option) {
    throw new Error(`Entrée « ${lang} » introuvable dans le menu de langue`);
  }
  option.click();
  fixture.detectChanges();
}

/** Tous les textes d'un nœud pdfmake (`text` en chaîne, chaînes nues, listes), à plat. */
export function definitionTexts(node: unknown, acc: string[] = []): string[] {
  if (typeof node === 'string') {
    acc.push(node);
    return acc;
  }
  if (Array.isArray(node)) {
    node.forEach(child => definitionTexts(child, acc));
    return acc;
  }
  if (node && typeof node === 'object') {
    const record = node as Record<string, unknown>;
    for (const [key, value] of Object.entries(record)) {
      // Les clés techniques ne portent pas de texte imprimé.
      if (['image', 'style', 'alignment', 'color', 'fillColor', 'lineColor', 'decoration', 'type', 'pageBreak',
        'width', 'widths', 'layout'].includes(key)) {
        continue;
      }
      definitionTexts(value, acc);
    }
  }
  return acc;
}

/** Texte de la page HTML (corps), entités décodées. */
export function htmlBodyText(html: string): string {
  return new DOMParser().parseFromString(html, 'text/html').body.textContent ?? '';
}

/** Feuille de style de la page HTML, où se trouvent les pieds de page (`@page`). */
export function htmlStyle(html: string): string {
  return new DOMParser().parseFromString(html, 'text/html').querySelector('style')?.textContent ?? '';
}

/**
 * Textes du contenu absents de la page HTML : vide si rien n'a été perdu à la conversion.
 *
 * <p>Le pied de page est vérifié à part ({@link missingFooterTexts}) : il passe par les boîtes de
 * marge, en chaînes CSS.</p>
 */
export function missingContentTexts(doc: TDocumentDefinitions, html: string): string[] {
  const body = htmlBodyText(html);
  const texts = definitionTexts(doc.content);
  if (doc.watermark) {
    texts.push(...definitionTexts(typeof doc.watermark === 'string' ? doc.watermark : doc.watermark.text));
  }
  return texts
    .map(text => stripBidiControls(text))
    .filter(text => text.trim() !== '' && !body.includes(text));
}

/**
 * Texte de chaque boîte de marge (`@bottom-right`…) : chaînes CSS décodées et mises bout à bout,
 * compteurs `counter(page)` / `counter(pages)` remplacés par les numéros fictifs du pied de page,
 * marques de direction retirées.
 */
export function marginBoxTexts(html: string): Record<string, string> {
  const boxes: Record<string, string> = {};
  const pattern = /@((?:top|bottom)-(?:left|center|right))\{content:((?:"(?:[^"\\]|\\.)*"|counter\(pages?\)|\s)+);/g;
  for (const match of htmlStyle(html).matchAll(pattern)) {
    const tokens = match[2].match(/"(?:[^"\\]|\\.)*"|counter\(pages?\)/g) ?? [];
    boxes[match[1]] = stripBidiControls(tokens.map(token => {
      if (token === 'counter(page)') {
        return String(PAGE_SENTINEL);
      }
      if (token === 'counter(pages)') {
        return String(PAGES_SENTINEL);
      }
      return token.slice(1, -1)
        .replace(/\\([0-9a-fA-F]{1,6}) ?/g, (_, hex: string) => String.fromCodePoint(parseInt(hex, 16)))
        .replace(/\\(.)/g, '$1');
    }).join(''));
  }
  return boxes;
}

/** Textes du pied de page absents des boîtes de marge : vide si rien n'a été perdu. */
export function missingFooterTexts(doc: TDocumentDefinitions, html: string): string[] {
  if (typeof doc.footer !== 'function') {
    return [];
  }
  const footer = (doc.footer as (current: number, total: number, size: unknown) => unknown)(
    PAGE_SENTINEL, PAGES_SENTINEL, { width: 595, height: 842, orientation: 'portrait' });
  const boxes = Object.values(marginBoxTexts(html));
  return definitionTexts(footer)
    .map(text => stripBidiControls(text))
    .filter(text => text.trim() !== '' && !boxes.includes(text));
}
