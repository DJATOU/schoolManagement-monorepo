import { TDocumentDefinitions } from 'pdfmake/interfaces';
import { BidiSegment, bidiSegments, escapeHtml, isolateLtrRuns } from './bidi-isolate';

/**
 * Définition pdfmake → document HTML autonome, imprimé par le navigateur.
 *
 * <h2>Pourquoi</h2>
 * pdfmake 0.2 n'applique pas l'algorithme bidirectionnel d'Unicode : avec une police arabe, les
 * lettres se lient mais les mots d'une ligne sortent dans l'ordre inverse. Un document en arabe est
 * donc rendu par le navigateur, à partir de la <strong>même</strong> définition que le PDF français
 * ou anglais : chaque document reste décrit une seule fois.
 *
 * <h2>Mise en page</h2>
 * Le document est écrit de droite à gauche (`dir`) avec des propriétés logiques : la gauche d'une
 * définition devient le début de ligne, donc la droite de la page ; colonnes, cellules, marges et
 * alignements sont ainsi mis en miroir. Les passages latins (montants, dates, niveaux, numéros) sont
 * isolés pour ne jamais être réordonnés. Les en-têtes de tableau se répètent sur chaque page, les
 * lignes ne se coupent pas, et le pied de page (numéros compris) passe par les boîtes de marge
 * `@page`.
 *
 * <h2>Échec bruyant</h2>
 * Tout nœud, clé, mise en forme de tableau ou image que le convertisseur ne sait pas rendre lève
 * {@link UnsupportedPdfContentError} : un document ne perd jamais de contenu en silence. Chaque
 * document réel est converti par un test, si bien qu'un nouveau nœud casse un test et non une
 * impression.
 *
 * <h2>Sécurité</h2>
 * Tout texte et tout attribut est échappé (noms, motifs, descriptions viennent du serveur), les
 * couleurs sont validées, les images sont limitées aux data URL PNG / JPEG, et le document ne
 * contient aucun script.
 */

/** Contenu qu'aucune règle de conversion ne couvre : il serait perdu à l'impression. */
export class UnsupportedPdfContentError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'UnsupportedPdfContentError';
  }
}

export interface PdfToHtmlOptions {
  /** Langue du document (`lang` de la page). */
  lang: string;
  /** Sens d'écriture ; de droite à gauche par défaut. */
  dir?: 'rtl' | 'ltr';
  /** Titre de la page : nom proposé par « Enregistrer en PDF ». Repli : `info.title`. */
  title?: string;
}

/** Numéros de page fictifs passés au pied de page, remplacés par les compteurs CSS. */
export const PAGE_SENTINEL = 900001;
export const PAGES_SENTINEL = 900002;

/** Police des documents en arabe : Noto Naskh Arabic, puis les polices arabes du système (poste hors ligne). */
export const ARABIC_FONT_STACK = "'Noto Naskh Arabic', 'Geeza Pro', Tahoma, Arial, 'Segoe UI', sans-serif";

/** Même feuille de police que l'application (`index.html`). */
const FONT_LINK = 'https://fonts.googleapis.com/css2?family=Noto+Naskh+Arabic:wght@400;700&display=swap';

/** Formats de page, en points (comme pdfmake). */
const PAGE_SIZES: Record<string, [number, number]> = {
  A3: [841.89, 1190.55],
  A4: [595.28, 841.89],
  A5: [419.53, 595.28],
  LETTER: [612, 792]
};

/** Valeurs par défaut de pdfmake 0.2. */
const DEFAULT_PAGE_MARGIN = 40;
const DEFAULT_FONT_SIZE = 12;

type Dict = Record<string, unknown>;
type Margin = [number, number, number, number];

interface TextStyle {
  fontSize?: number;
  bold?: boolean;
  italics?: boolean;
  color?: string;
  alignment?: string;
  decoration?: string;
  noWrap?: boolean;
  lineHeight?: number;
  fillColor?: string;
  margin?: Margin;
}

/** Propriétés de style admises, dans un style nommé comme sur un nœud. */
const STYLE_KEYS = new Set([
  'fontSize', 'bold', 'italics', 'color', 'alignment', 'decoration', 'noWrap', 'lineHeight', 'fillColor', 'margin'
]);
/** Clés qui désignent le genre d'un nœud. */
const KIND_KEYS = ['text', 'stack', 'columns', 'ul', 'ol', 'table', 'image', 'canvas'] as const;
/** Clés admises sur un nœud, en plus des styles et de son genre. */
const NODE_KEYS = new Set([
  ...STYLE_KEYS, ...KIND_KEYS, 'style', 'pageBreak', 'width', 'height', 'columnGap', 'layout',
  'colSpan', 'border'
]);
const INLINE_KEYS = new Set(['text', 'style', 'fontSize', 'bold', 'italics', 'color', 'decoration', 'noWrap']);
const TABLE_KEYS = new Set(['body', 'widths', 'headerRows', 'dontBreakRows']);
const LAYOUT_KEYS = new Set([
  'hLineWidth', 'vLineWidth', 'hLineColor', 'vLineColor', 'paddingLeft', 'paddingRight', 'paddingTop',
  'paddingBottom', 'fillColor', 'defaultBorder'
]);
const LINE_KEYS = new Set(['type', 'x1', 'y1', 'x2', 'y2', 'lineWidth', 'lineColor']);
const DOC_KEYS = new Set([
  'content', 'styles', 'defaultStyle', 'footer', 'header', 'pageSize', 'pageOrientation', 'pageMargins', 'info',
  'watermark'
]);
const WATERMARK_KEYS = new Set(['text', 'color', 'opacity', 'bold', 'italics', 'angle', 'fontSize']);

const COLOR = /^(#[0-9a-fA-F]{3,8}|[a-zA-Z]+)$/;
const IMAGE = /^data:image\/(png|jpe?g);base64,[A-Za-z0-9+/=]+$/;

type LayoutFn = (i: number, node: Dict, other?: number) => unknown;
interface Layout {
  hLineWidth: LayoutFn;
  vLineWidth: LayoutFn;
  hLineColor: LayoutFn;
  vLineColor: LayoutFn;
  paddingLeft: LayoutFn;
  paddingRight: LayoutFn;
  paddingTop: LayoutFn;
  paddingBottom: LayoutFn;
  fillColor: LayoutFn;
  defaultBorder: boolean;
}

/** Mise en forme par défaut d'un tableau pdfmake (`docMeasure.js`, `getLayout`). */
const DEFAULT_LAYOUT: Layout = {
  hLineWidth: () => 1,
  vLineWidth: () => 1,
  hLineColor: () => 'black',
  vLineColor: () => 'black',
  paddingLeft: () => 4,
  paddingRight: () => 4,
  paddingTop: () => 2,
  paddingBottom: () => 2,
  fillColor: () => null,
  defaultBorder: true
};

/** Mises en forme nommées de pdfmake (`printer.js`, `registerDefaultTableLayouts`). */
const NAMED_LAYOUTS: Record<string, Partial<Layout>> = {
  noBorders: {
    hLineWidth: () => 0,
    vLineWidth: () => 0,
    paddingLeft: i => (i && 4) || 0,
    paddingRight: (i, node) => (i < widthsOf(node).length - 1 ? 4 : 0)
  },
  headerLineOnly: {
    hLineWidth: (i, node) => (i === 0 || i === bodyOf(node).length ? 0 : (i === headerRowsOf(node) ? 2 : 0)),
    vLineWidth: () => 0,
    paddingLeft: i => (i === 0 ? 0 : 8),
    paddingRight: (i, node) => (i === widthsOf(node).length - 1 ? 0 : 8)
  },
  lightHorizontalLines: {
    hLineWidth: (i, node) => (i === 0 || i === bodyOf(node).length ? 0 : (i === headerRowsOf(node) ? 2 : 1)),
    vLineWidth: () => 0,
    hLineColor: i => (i === 1 ? 'black' : '#aaa'),
    paddingLeft: i => (i === 0 ? 0 : 8),
    paddingRight: (i, node) => (i === widthsOf(node).length - 1 ? 0 : 8)
  }
};

function tableOf(node: Dict): Dict {
  return node['table'] as Dict;
}
function widthsOf(node: Dict): unknown[] {
  return tableOf(node)['widths'] as unknown[];
}
function bodyOf(node: Dict): unknown[][] {
  return tableOf(node)['body'] as unknown[][];
}
function headerRowsOf(node: Dict): number {
  return (tableOf(node)['headerRows'] as number | undefined) ?? 0;
}

/** Convertit une définition pdfmake en document HTML complet. */
export function pdfDefinitionToHtml(doc: TDocumentDefinitions, options: PdfToHtmlOptions): string {
  return new PdfHtmlConverter(doc as unknown as Dict, options).render();
}

class PdfHtmlConverter {
  private readonly styles: Record<string, Dict>;
  private readonly dir: 'rtl' | 'ltr';

  constructor(private readonly doc: Dict, private readonly options: PdfToHtmlOptions) {
    checkKeys(doc, DOC_KEYS, 'document');
    this.styles = (doc['styles'] as Record<string, Dict> | undefined) ?? {};
    for (const [name, style] of Object.entries(this.styles)) {
      checkKeys(style, STYLE_KEYS, `style « ${name} »`);
    }
    if (doc['defaultStyle']) {
      checkKeys(doc['defaultStyle'] as Dict, STYLE_KEYS, 'defaultStyle');
    }
    this.dir = options.dir ?? 'rtl';
  }

  private get rtl(): boolean {
    return this.dir === 'rtl';
  }

  render(): string {
    const info = this.doc['info'] as Dict | undefined;
    const title = this.options.title ?? (typeof info?.['title'] === 'string' ? info['title'] as string : '');
    const body = this.node(this.doc['content'], 'content') + this.watermark();
    return '<!DOCTYPE html>'
      + `<html lang="${escapeHtml(this.options.lang)}" dir="${this.dir}">`
      + '<head><meta charset="utf-8">'
      + `<title>${escapeHtml(title)}</title>`
      + `<link rel="stylesheet" href="${FONT_LINK}">`
      + `<style>${this.css()}</style>`
      + '</head>'
      + `<body>${body}</body>`
      + '</html>';
  }

  // ------------------------------------------------------------------
  // Page
  // ------------------------------------------------------------------

  private pageSize(): { width: number; height: number; orientation: 'portrait' | 'landscape'; name: string } {
    const raw = this.doc['pageSize'] ?? 'A4';
    const orientation = this.doc['pageOrientation'] === 'landscape' ? 'landscape' : 'portrait';
    if (this.doc['pageOrientation'] !== undefined && !['portrait', 'landscape'].includes(this.doc['pageOrientation'] as string)) {
      throw new UnsupportedPdfContentError(`pageOrientation « ${String(this.doc['pageOrientation'])} »`);
    }
    if (typeof raw !== 'string' || !PAGE_SIZES[raw.toUpperCase()]) {
      throw new UnsupportedPdfContentError(`pageSize « ${JSON.stringify(raw)} »`);
    }
    const [w, h] = PAGE_SIZES[raw.toUpperCase()];
    const [width, height] = orientation === 'landscape' ? [h, w] : [w, h];
    return { width, height, orientation, name: raw.toUpperCase() };
  }

  private pageMargins(): Margin {
    const raw = this.doc['pageMargins'];
    if (raw === undefined) {
      return [DEFAULT_PAGE_MARGIN, DEFAULT_PAGE_MARGIN, DEFAULT_PAGE_MARGIN, DEFAULT_PAGE_MARGIN];
    }
    return toMargin(raw, 'pageMargins');
  }

  private css(): string {
    const size = this.pageSize();
    const [left, top, right, bottom] = this.pageMargins();
    // Marges de page en miroir : la marge « gauche » d'une définition borde le début de ligne.
    const [physicalLeft, physicalRight] = this.dir === 'rtl' ? [right, left] : [left, right];
    const defaults = this.resolve(this.doc['defaultStyle'] as Dict | undefined);
    const fontSize = defaults.fontSize ?? DEFAULT_FONT_SIZE;
    const boxes = [...this.marginBoxes('footer', 'bottom'), ...this.marginBoxes('header', 'top')].join('');
    return `@page{size:${size.name} ${size.orientation};margin:${pt(top)} ${pt(physicalRight)} ${pt(bottom)} `
      + `${pt(physicalLeft)};${boxes}}`
      + `html{font-family:${ARABIC_FONT_STACK};font-size:${pt(fontSize)};line-height:1.2;color:#000;}`
      + 'body{margin:0;white-space:pre-line;}'
      + '*{box-sizing:border-box;print-color-adjust:exact;-webkit-print-color-adjust:exact;}'
      + 'p,ul,ol{margin:0;}'
      + 'ul,ol{padding-inline-start:1.4em;}'
      + 'img{display:block;max-inline-size:100%;}'
      + 'table{border-collapse:collapse;}'
      + 'thead{display:table-header-group;}'
      + 'tr{break-inside:avoid;page-break-inside:avoid;}'
      + 'td{vertical-align:top;}'
      + `bdi{unicode-bidi:isolate;${this.rtl ? 'font-style:var(--pdf-it,normal);' : ''}}`
      + '.pdf-cols{display:flex;align-items:flex-start;break-inside:avoid;page-break-inside:avoid;}'
      + '.pdf-col{min-inline-size:0;}'
      + '.pdf-wm{position:fixed;top:50%;left:50%;white-space:nowrap;pointer-events:none;z-index:1000;}'
      + `body{${styleCss(defaults, false, this.rtl)}}`;
  }

  /**
   * Pied (ou en-tête) de page dans les boîtes de marge `@page`.
   *
   * <p>La fonction de la définition est appelée avec des numéros fictifs, remplacés ensuite par
   * `counter(page)` / `counter(pages)`. Chaque texte va dans la boîte de son alignement : début de
   * ligne à droite en arabe, fin de ligne à gauche, centré au centre.</p>
   */
  private marginBoxes(part: 'footer' | 'header', edge: 'bottom' | 'top'): string[] {
    const source = this.doc[part];
    if (source === undefined || source === null) {
      return [];
    }
    const size = this.pageSize();
    const produced = typeof source === 'function'
      ? (source as (current: number, total: number, pageSize: unknown) => unknown)(
        PAGE_SENTINEL, PAGES_SENTINEL, { width: size.width, height: size.height, orientation: size.orientation })
      : source;
    if (produced === undefined || produced === null || produced === '') {
      return [];
    }
    const texts: Dict[] = [];
    if (typeof produced === 'string') {
      texts.push({ text: produced });
    } else if (isDict(produced) && Array.isArray(produced['columns'])) {
      checkKeys(produced, new Set(['columns', 'margin', 'columnGap']), part);
      for (const column of produced['columns'] as unknown[]) {
        texts.push(this.marginText(column, part));
      }
    } else {
      texts.push(this.marginText(produced, part));
    }
    const used = new Set<string>();
    return texts.map(text => {
      const own = this.resolve(text);
      const position = this.boxPosition(own.alignment);
      if (used.has(position)) {
        throw new UnsupportedPdfContentError(`${part} : deux textes dans la même boîte (${position})`);
      }
      used.add(position);
      const margin = own.margin ?? [0, 0, 0, 0];
      const padding = edge === 'bottom' ? `padding-top:${pt(margin[1])};` : `padding-bottom:${pt(margin[3])};`;
      const side = position === 'center' ? 'center' : position;
      return `@${edge}-${position}{content:${cssContent(String(text['text']))};`
        + `font-family:${ARABIC_FONT_STACK};direction:${this.dir};text-align:${side};`
        + `vertical-align:${edge === 'bottom' ? 'top' : 'bottom'};${padding}`
        + `${styleCss({ ...own, alignment: undefined, margin: undefined, fillColor: undefined }, false, this.rtl)}}`;
    });
  }

  private marginText(node: unknown, part: string): Dict {
    if (typeof node === 'string') {
      return { text: node };
    }
    if (!isDict(node) || typeof node['text'] !== 'string') {
      throw new UnsupportedPdfContentError(`${part} : seul du texte est imprimable en pied de page`);
    }
    checkKeys(node, new Set([...STYLE_KEYS, 'text', 'style']), part);
    return node;
  }

  private boxPosition(alignment: string | undefined): 'left' | 'center' | 'right' {
    if (alignment === 'center' || alignment === 'justify') {
      return 'center';
    }
    const end = alignment === 'right';
    if (this.dir === 'rtl') {
      return end ? 'left' : 'right';
    }
    return end ? 'right' : 'left';
  }

  private watermark(): string {
    const raw = this.doc['watermark'];
    if (raw === undefined || raw === null) {
      return '';
    }
    const mark: Dict = typeof raw === 'string' ? { text: raw } : raw as Dict;
    checkKeys(mark, WATERMARK_KEYS, 'watermark');
    const text = String(mark['text'] ?? '');
    const size = this.pageSize();
    // Taille automatique, comme pdfmake : le tampon occupe la diagonale de la page.
    const diagonal = Math.sqrt(size.width ** 2 + size.height ** 2);
    const fontSize = (mark['fontSize'] as number | undefined) ?? Math.min(120, (diagonal * 0.7) / Math.max(1, text.length * 0.6));
    const angle = (mark['angle'] as number | undefined) ?? -45;
    const css = [
      `font-size:${pt(fontSize)}`,
      `transform:translate(-50%,-50%) rotate(${num(angle)}deg)`,
      mark['color'] ? `color:${color(mark['color'])}` : 'color:#000',
      `opacity:${num((mark['opacity'] as number | undefined) ?? 0.6)}`,
      mark['bold'] ? 'font-weight:700' : '',
      mark['italics'] && !this.rtl ? 'font-style:italic' : ''
    ].filter(Boolean).join(';');
    return `<div class="pdf-wm" aria-hidden="true" style="${css}">${isolateLtrRuns(text)}</div>`;
  }

  // ------------------------------------------------------------------
  // Contenu
  // ------------------------------------------------------------------

  private node(node: unknown, path: string): string {
    if (node === undefined || node === null) {
      throw new UnsupportedPdfContentError(`${path} : nœud vide`);
    }
    if (typeof node === 'string' || typeof node === 'number') {
      return `<div>${isolateLtrRuns(String(node))}</div>`;
    }
    if (Array.isArray(node)) {
      return `<div>${node.map((child, i) => this.node(child, `${path}[${i}]`)).join('')}</div>`;
    }
    if (!isDict(node)) {
      throw new UnsupportedPdfContentError(`${path} : nœud de type ${typeof node}`);
    }
    checkKeys(node, NODE_KEYS, path);
    const kinds = KIND_KEYS.filter(key => node[key] !== undefined);
    if (kinds.length !== 1) {
      throw new UnsupportedPdfContentError(`${path} : nœud ${kinds.length ? kinds.join(' + ') : 'sans contenu'}`);
    }
    const own = this.resolve(node);
    const css = styleCss(own, true, this.rtl) + pageBreakCss(node, path);
    switch (kinds[0]) {
      case 'text':
        return `<div style="${css}">${this.inline(node['text'], `${path}.text`)}</div>`;
      case 'stack':
        return `<div style="${css}">${this.children(node['stack'], `${path}.stack`)}</div>`;
      case 'columns':
        return this.columns(node, css, path);
      case 'ul':
      case 'ol':
        return this.list(node, kinds[0], css, path);
      case 'table':
        return this.table(node, css, path);
      case 'image':
        return this.image(node, css, path);
      default:
        return this.canvas(node, css, path);
    }
  }

  private children(children: unknown, path: string): string {
    if (!Array.isArray(children)) {
      throw new UnsupportedPdfContentError(`${path} : liste attendue`);
    }
    return children.map((child, i) => this.node(child, `${path}[${i}]`)).join('');
  }

  /** Texte d'un paragraphe : chaîne, ou suite de morceaux mis en forme. */
  private inline(text: unknown, path: string): string {
    if (typeof text === 'string' || typeof text === 'number') {
      return isolateLtrRuns(String(text));
    }
    if (Array.isArray(text)) {
      return text.map((part, i) => this.inlinePart(part, `${path}[${i}]`)).join('');
    }
    throw new UnsupportedPdfContentError(`${path} : texte de type ${typeof text}`);
  }

  private inlinePart(part: unknown, path: string): string {
    if (typeof part === 'string' || typeof part === 'number') {
      return isolateLtrRuns(String(part));
    }
    if (!isDict(part)) {
      throw new UnsupportedPdfContentError(`${path} : morceau de texte de type ${typeof part}`);
    }
    checkKeys(part, INLINE_KEYS, path);
    const own = this.resolve(part);
    return `<span style="${styleCss(own, false, this.rtl)}">${this.inline(part['text'], `${path}.text`)}</span>`;
  }

  private columns(node: Dict, css: string, path: string): string {
    const columns = node['columns'];
    if (!Array.isArray(columns)) {
      throw new UnsupportedPdfContentError(`${path}.columns : liste attendue`);
    }
    const gap = (node['columnGap'] as number | undefined) ?? 0;
    const cells = columns.map((column, i) => {
      const width = isDict(column) ? column['width'] : undefined;
      return `<div class="pdf-col" style="${flexCss(width, `${path}.columns[${i}]`)}">`
        + `${this.node(column, `${path}.columns[${i}]`)}</div>`;
    });
    return `<div class="pdf-cols" style="gap:${pt(gap)};${css}">${cells.join('')}</div>`;
  }

  private list(node: Dict, tag: 'ul' | 'ol', css: string, path: string): string {
    const items = node[tag];
    if (!Array.isArray(items)) {
      throw new UnsupportedPdfContentError(`${path}.${tag} : liste attendue`);
    }
    const lis = items.map((item, i) => `<li>${this.node(item, `${path}.${tag}[${i}]`)}</li>`).join('');
    return `<${tag} style="${css}">${lis}</${tag}>`;
  }

  private image(node: Dict, css: string, path: string): string {
    const src = node['image'];
    const width = node['width'];
    const height = node['height'];
    if (width !== undefined && typeof width !== 'number') {
      throw new UnsupportedPdfContentError(`${path} : largeur d'image « ${String(width)} »`);
    }
    const size = (width !== undefined ? `inline-size:${pt(width as number)};` : '')
      + (height !== undefined ? `block-size:${pt(height as number)};` : '');
    // Logo indisponible : la place reste réservée, comme dans le PDF.
    if (src === '') {
      return `<div style="${size}${css}"></div>`;
    }
    if (typeof src !== 'string' || !IMAGE.test(src)) {
      throw new UnsupportedPdfContentError(`${path} : seules les images en data URL PNG / JPEG sont imprimables`);
    }
    return `<img alt="" src="${escapeHtml(src)}" style="${size}${css}">`;
  }

  private canvas(node: Dict, css: string, path: string): string {
    const shapes = node['canvas'];
    if (!Array.isArray(shapes)) {
      throw new UnsupportedPdfContentError(`${path}.canvas : liste attendue`);
    }
    const lines = shapes.map((shape, i) => {
      const where = `${path}.canvas[${i}]`;
      if (!isDict(shape)) {
        throw new UnsupportedPdfContentError(`${where} : forme inconnue`);
      }
      checkKeys(shape, LINE_KEYS, where);
      const { x1, y1, x2, y2 } = shape as { x1: number; y1: number; x2: number; y2: number };
      if (shape['type'] !== 'line' || y1 !== y2 || ![x1, y1, x2].every(v => typeof v === 'number')) {
        throw new UnsupportedPdfContentError(`${where} : seules les lignes horizontales sont imprimables`);
      }
      const lineWidth = (shape['lineWidth'] as number | undefined) ?? 1;
      const lineColor = shape['lineColor'] !== undefined ? color(shape['lineColor']) : '#000';
      return `<div style="margin-block-start:${pt(y1)};margin-inline-start:${pt(Math.min(x1, x2))};`
        + `inline-size:${pt(Math.abs(x2 - x1))};max-inline-size:100%;`
        + `border-block-start:${pt(lineWidth)} solid ${lineColor};"></div>`;
    });
    return `<div style="${css}">${lines.join('')}</div>`;
  }

  // ------------------------------------------------------------------
  // Tableaux
  // ------------------------------------------------------------------

  private table(node: Dict, css: string, path: string): string {
    const table = node['table'];
    if (!isDict(table) || !Array.isArray(table['body'])) {
      throw new UnsupportedPdfContentError(`${path}.table : corps attendu`);
    }
    checkKeys(table, TABLE_KEYS, `${path}.table`);
    const body = table['body'] as unknown[][];
    const columnCount = Array.isArray(table['widths'])
      ? (table['widths'] as unknown[]).length
      : Math.max(0, ...body.map(row => rowSpanCount(row)));
    const widths: unknown[] = Array.isArray(table['widths'])
      ? table['widths'] as unknown[]
      : new Array(columnCount).fill('auto');
    const headerRows = (table['headerRows'] as number | undefined) ?? 0;
    // Nœud tel que pdfmake le passe aux fonctions de mise en forme.
    const layoutNode: Dict = { ...node, table: { ...table, widths, headerRows, body } };
    const layout = this.layout(node['layout'], `${path}.layout`);

    const stars = widths.filter(w => w === '*').length;
    // Les colonnes `*` reçoivent leur part en pourcentage seulement si rien d'autre ne se mesure au
    // contenu (`auto`) ni n'a de largeur fixe : sinon le navigateur leur laisse le reste.
    const shared = widths.every(w => w === '*' || isPercent(w));
    const percentTotal = widths.reduce<number>((sum, w) => sum + (isPercent(w) ? parseFloat(w as string) : 0), 0);
    const cols = widths.map((w, i) => `<col style="${colCss(w, stars, shared, percentTotal, `${path}.table.widths[${i}]`)}">`);
    const fullWidth = stars > 0 || percentTotal > 0;
    const nowrap = stars > 0 ? this.shortAutoColumns(widths, body) : new Set<number>();

    const rows = body.map((row, r) => {
      if (!Array.isArray(row)) {
        throw new UnsupportedPdfContentError(`${path}.table.body[${r}] : ligne attendue`);
      }
      return `<tr>${this.row(row, r, layout, layoutNode, nowrap, `${path}.table.body[${r}]`)}</tr>`;
    });
    const head = rows.slice(0, headerRows).join('');
    const rest = rows.slice(headerRows).join('');
    return `<table style="${fullWidth ? 'inline-size:100%;' : ''}${css}">`
      + `<colgroup>${cols.join('')}</colgroup>`
      + (head ? `<thead>${head}</thead>` : '')
      + `<tbody>${rest}</tbody></table>`;
  }

  /**
   * Colonnes `auto` à garder sur une ligne, comme pdfmake leur donne leur largeur naturelle.
   *
   * <p>Le navigateur répartit la largeur entre toutes les colonnes selon leur contenu : à côté d'une
   * colonne `*` au texte long, « Salle 1 » ou « مصادق عليها » passaient sur deux lignes. Une colonne
   * `auto` dont chaque texte est court reste donc sur une ligne, tant que ces colonnes ensemble
   * occupent nettement moins que la page : rien ne peut déborder.</p>
   */
  private shortAutoColumns(widths: unknown[], body: unknown[][]): Set<number> {
    const longest = new Map<number, number>();
    widths.forEach((width, c) => {
      if (width === 'auto') {
        longest.set(c, 0);
      }
    });
    for (const row of body) {
      let c = 0;
      for (let i = 0; i < row.length;) {
        const cell = row[i];
        const span = isDict(cell) && typeof cell['colSpan'] === 'number' ? cell['colSpan'] as number : 1;
        if (span === 1 && longest.has(c)) {
          const length = Math.max(0, ...cellTexts(cell).flatMap(text => text.split('\n')).map(line => line.trim().length));
          longest.set(c, Math.max(longest.get(c)!, length));
        }
        c += span;
        i += span;
      }
    }
    const short = [...longest].filter(([, length]) => length <= SHORT_CELL_CHARS);
    const size = this.pageSize();
    const [left, , right] = this.pageMargins();
    const fontSize = this.resolve(this.doc['defaultStyle'] as Dict | undefined).fontSize ?? DEFAULT_FONT_SIZE;
    // Estimation large : 0,55 cadratin par caractère et 24 pt de retraits par colonne.
    const estimate = short.reduce((sum, [, length]) => sum + length * fontSize * 0.55 + 24, 0);
    return estimate <= (size.width - left - right) * 0.6 ? new Set(short.map(([c]) => c)) : new Set();
  }

  private row(row: unknown[], r: number, layout: Layout, node: Dict, nowrap: Set<number>, path: string): string {
    const cells: string[] = [];
    let c = 0;
    let i = 0;
    while (i < row.length) {
      const cell = row[i];
      const where = `${path}[${i}]`;
      const span = isDict(cell) && typeof cell['colSpan'] === 'number' ? cell['colSpan'] as number : 1;
      if (isDict(cell) && cell['rowSpan'] !== undefined) {
        throw new UnsupportedPdfContentError(`${where} : rowSpan`);
      }
      cells.push(this.cell(cell, r, c, span, layout, node, span === 1 && nowrap.has(c), where));
      c += span;
      // Les cases couvertes par la fusion sont des marques de place (`{}`) : sautées.
      i += span;
    }
    return cells.join('');
  }

  private cell(cell: unknown, r: number, c: number, span: number, layout: Layout, node: Dict, nowrap: boolean,
               path: string): string {
    const dict: Dict = typeof cell === 'string' || typeof cell === 'number' ? { text: String(cell) } : (cell as Dict);
    if (!isDict(dict)) {
      throw new UnsupportedPdfContentError(`${path} : cellule de type ${typeof cell}`);
    }
    const isPlaceholder = Object.keys(dict).length === 0;
    if (!isPlaceholder) {
      checkKeys(dict, NODE_KEYS, path);
    }
    const own = isPlaceholder ? {} : this.resolve(dict);
    const border = Array.isArray(dict['border'])
      ? dict['border'] as boolean[]
      : new Array(4).fill(layout.defaultBorder !== false);
    const side = (on: boolean | undefined, width: unknown, lineColor: unknown): string => {
      const w = typeof width === 'number' ? width : 0;
      return on === false || w <= 0 ? '0 none' : `${pt(w)} solid ${color(lineColor ?? 'black')}`;
    };
    const margin = own.margin ?? [0, 0, 0, 0];
    const fill = own.fillColor ?? (layout.fillColor(r, node, c) as string | null | undefined);
    const lastCol = c + span;
    const css = [
      `border-inline-start:${side(border[0], layout.vLineWidth(c, node, r), layout.vLineColor(c, node, r))}`,
      `border-block-start:${side(border[1], layout.hLineWidth(r, node, c), layout.hLineColor(r, node, c))}`,
      `border-inline-end:${side(border[2], layout.vLineWidth(lastCol, node, r), layout.vLineColor(lastCol, node, r))}`,
      `border-block-end:${side(border[3], layout.hLineWidth(r + 1, node, c), layout.hLineColor(r + 1, node, c))}`,
      `padding-inline-start:${pt(numberOf(layout.paddingLeft(c, node)) + margin[0])}`,
      `padding-block-start:${pt(numberOf(layout.paddingTop(r, node)) + margin[1])}`,
      `padding-inline-end:${pt(numberOf(layout.paddingRight(lastCol - 1, node)) + margin[2])}`,
      `padding-block-end:${pt(numberOf(layout.paddingBottom(r, node)) + margin[3])}`,
      fill ? `background-color:${color(fill)}` : '',
      typeof dict['width'] === 'number' ? `min-inline-size:${pt(dict['width'] as number)}` : '',
      typeof dict['height'] === 'number' ? `block-size:${pt(dict['height'] as number)}` : '',
      nowrap ? 'white-space:nowrap' : ''
    ].filter(Boolean).join(';');
    const text = styleCss({ ...own, margin: undefined, fillColor: undefined }, false, this.rtl);
    const colspan = span > 1 ? ` colspan="${span}"` : '';
    // Contenu de la cellule : son texte ou ses blocs, sans ce qui est déjà porté par la cellule.
    const content = isPlaceholder ? '' : this.node(contentOf(dict), path);
    return `<td${colspan} style="${css};${text}">${content}</td>`;
  }

  private layout(raw: unknown, path: string): Layout {
    if (raw === undefined || raw === null) {
      return DEFAULT_LAYOUT;
    }
    if (typeof raw === 'string') {
      const named = NAMED_LAYOUTS[raw];
      if (!named) {
        throw new UnsupportedPdfContentError(`${path} : mise en forme « ${raw} » inconnue`);
      }
      return { ...DEFAULT_LAYOUT, ...named };
    }
    if (!isDict(raw)) {
      throw new UnsupportedPdfContentError(`${path} : mise en forme de type ${typeof raw}`);
    }
    checkKeys(raw, LAYOUT_KEYS, path);
    const custom: Partial<Layout> = {};
    for (const [key, value] of Object.entries(raw)) {
      if (key === 'defaultBorder') {
        custom.defaultBorder = value as boolean;
      } else if (typeof value === 'function') {
        (custom as Record<string, unknown>)[key] = value;
      } else {
        throw new UnsupportedPdfContentError(`${path}.${key} : fonction attendue`);
      }
    }
    return { ...DEFAULT_LAYOUT, ...custom };
  }

  // ------------------------------------------------------------------
  // Styles
  // ------------------------------------------------------------------

  /** Style propre d'un nœud : ses styles nommés, dans l'ordre, puis ses propriétés directes. */
  private resolve(node: Dict | undefined): TextStyle {
    if (!node) {
      return {};
    }
    const resolved: Dict = {};
    const names = node['style'] === undefined ? [] : (Array.isArray(node['style']) ? node['style'] : [node['style']]);
    for (const name of names) {
      if (typeof name !== 'string') {
        throw new UnsupportedPdfContentError(`style de type ${typeof name}`);
      }
      const style = this.styles[name];
      if (!style) {
        throw new UnsupportedPdfContentError(`style « ${name} » non défini`);
      }
      Object.assign(resolved, style);
    }
    for (const key of STYLE_KEYS) {
      if (node[key] !== undefined) {
        resolved[key] = node[key];
      }
    }
    const style = resolved as TextStyle;
    if (resolved['margin'] !== undefined) {
      style.margin = toMargin(resolved['margin'], 'margin');
    }
    if (style.alignment !== undefined && !['left', 'right', 'center', 'justify'].includes(style.alignment)) {
      throw new UnsupportedPdfContentError(`alignement « ${style.alignment} »`);
    }
    if (style.decoration !== undefined && !['underline', 'lineThrough', 'overline'].includes(style.decoration)) {
      throw new UnsupportedPdfContentError(`décoration « ${style.decoration} »`);
    }
    return style;
  }
}

/** Textes d'une cellule (son texte, ses morceaux, ses blocs empilés), pour estimer sa largeur. */
function cellTexts(node: unknown): string[] {
  if (typeof node === 'string' || typeof node === 'number') {
    return [String(node)];
  }
  if (Array.isArray(node)) {
    return node.flatMap(cellTexts);
  }
  if (isDict(node)) {
    return [...cellTexts(node['text'] ?? []), ...cellTexts(node['stack'] ?? [])];
  }
  return [];
}

/** Longueur au-delà de laquelle une cellule `auto` peut passer à la ligne. */
const SHORT_CELL_CHARS = 12;

/** Contenu d'une cellule, sans les propriétés portées par la cellule elle-même. */
function contentOf(cell: Dict): Dict {
  const content: Dict = {};
  for (const [key, value] of Object.entries(cell)) {
    if (!['style', 'margin', 'fillColor', 'colSpan', 'border', 'width', 'height', 'fontSize', 'bold', 'italics',
      'color', 'alignment', 'decoration', 'lineHeight'].includes(key)) {
      content[key] = value;
    }
  }
  return content;
}

/**
 * CSS d'un style ; `block` ajoute les marges (un morceau de texte n'en a pas).
 *
 * <p>De droite à gauche, l'italique ne s'applique qu'aux passages latins (`bdi`, par la variable
 * `--pdf-it`) : l'arabe n'a pas d'italique, et l'oblique que le navigateur fabriquerait penche les
 * lettres hors de la marge.</p>
 */
function styleCss(style: TextStyle, block: boolean, rtl = false): string {
  const parts: string[] = [];
  if (style.fontSize !== undefined) {
    parts.push(`font-size:${pt(style.fontSize)}`);
  }
  if (style.bold !== undefined) {
    parts.push(`font-weight:${style.bold ? 700 : 400}`);
  }
  if (style.italics !== undefined) {
    parts.push(rtl ? `--pdf-it:${style.italics ? 'italic' : 'normal'}` : `font-style:${style.italics ? 'italic' : 'normal'}`);
  }
  if (style.color !== undefined) {
    parts.push(`color:${color(style.color)}`);
  }
  if (style.alignment !== undefined) {
    // Gauche et droite de la définition : début et fin de ligne, mis en miroir par `dir`.
    const align = { left: 'start', right: 'end', center: 'center', justify: 'justify' }[style.alignment];
    parts.push(`text-align:${align}`);
  }
  if (style.decoration !== undefined) {
    const decoration = { underline: 'underline', lineThrough: 'line-through', overline: 'overline' }[style.decoration];
    parts.push(`text-decoration:${decoration}`);
  }
  if (style.noWrap) {
    parts.push('white-space:nowrap');
  }
  if (style.lineHeight !== undefined) {
    parts.push(`line-height:${num(1.2 * style.lineHeight)}`);
  }
  if (block && style.margin) {
    const [left, top, right, bottom] = style.margin;
    parts.push(`margin-inline-start:${pt(left)}`, `margin-block-start:${pt(top)}`,
      `margin-inline-end:${pt(right)}`, `margin-block-end:${pt(bottom)}`);
  }
  return parts.map(part => part + ';').join('');
}

function pageBreakCss(node: Dict, path: string): string {
  const pageBreak = node['pageBreak'];
  if (pageBreak === undefined) {
    return '';
  }
  if (pageBreak === 'before') {
    return 'break-before:page;';
  }
  if (pageBreak === 'after') {
    return 'break-after:page;';
  }
  throw new UnsupportedPdfContentError(`${path} : pageBreak « ${String(pageBreak)} »`);
}

/** Largeur d'une colonne : fixe, au contenu, en pourcentage, ou part du reste (`*`). */
function flexCss(width: unknown, path: string): string {
  if (width === undefined || width === '*') {
    return 'flex:1 1 0;';
  }
  if (width === 'auto') {
    return 'flex:0 0 auto;';
  }
  if (typeof width === 'number') {
    return `flex:0 0 ${pt(width)};`;
  }
  if (isPercent(width)) {
    return `flex:0 0 ${width};`;
  }
  throw new UnsupportedPdfContentError(`${path} : largeur de colonne « ${String(width)} »`);
}

/**
 * Largeur d'une colonne de tableau.
 *
 * <p>Les colonnes `*` se partagent le reste : entre colonnes `*` et en pourcentage, chacune reçoit sa
 * part en pourcentage ; à côté de colonnes `auto` ou fixes, le navigateur répartit selon le contenu,
 * comme pdfmake qui donne aux colonnes `auto` leur largeur naturelle.</p>
 */
function colCss(width: unknown, stars: number, shared: boolean, percentTotal: number, path: string): string {
  if (typeof width === 'number') {
    return `inline-size:${pt(width)};`;
  }
  if (isPercent(width)) {
    return `inline-size:${width};`;
  }
  if (width === '*') {
    return shared ? `inline-size:${num(Math.max(0, 100 - percentTotal) / stars)}%;` : '';
  }
  if (width === 'auto') {
    return '';
  }
  throw new UnsupportedPdfContentError(`${path} : largeur « ${String(width)} »`);
}

/** Nombre de colonnes couvertes par une ligne, fusions comprises. */
function rowSpanCount(row: unknown[]): number {
  let count = 0;
  for (let i = 0; i < row.length;) {
    const cell = row[i];
    const span = isDict(cell) && typeof cell['colSpan'] === 'number' ? cell['colSpan'] as number : 1;
    count += span;
    i += span;
  }
  return count;
}

/** Contenu CSS d'une boîte de marge : texte en chaînes, passages latins isolés, numéros en compteurs. */
function cssContent(text: string): string {
  const parts: string[] = [];
  const segments: BidiSegment[] = bidiSegments(text);
  for (const segment of segments) {
    const pieces = segment.text.split(new RegExp(`(${PAGE_SENTINEL}|${PAGES_SENTINEL})`));
    if (segment.ltr) {
      parts.push('"\\002066"');
    }
    for (const piece of pieces) {
      if (piece === String(PAGE_SENTINEL)) {
        parts.push('counter(page)');
      } else if (piece === String(PAGES_SENTINEL)) {
        parts.push('counter(pages)');
      } else if (piece) {
        parts.push(cssString(piece));
      }
    }
    if (segment.ltr) {
      parts.push('"\\002069"');
    }
  }
  return parts.length ? parts.join(' ') : '""';
}

function cssString(text: string): string {
  return '"' + text.replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\n/g, '\\A ').replace(/</g, '\\00003c') + '"';
}

function toMargin(raw: unknown, path: string): Margin {
  if (typeof raw === 'number') {
    return [raw, raw, raw, raw];
  }
  if (Array.isArray(raw) && raw.every(v => typeof v === 'number')) {
    if (raw.length === 2) {
      return [raw[0], raw[1], raw[0], raw[1]];
    }
    if (raw.length === 4) {
      return raw as Margin;
    }
  }
  throw new UnsupportedPdfContentError(`${path} : marge « ${JSON.stringify(raw)} »`);
}

function checkKeys(node: Dict, allowed: Set<string>, path: string): void {
  const unknown = Object.keys(node).filter(key => !allowed.has(key) && node[key] !== undefined);
  if (unknown.length > 0) {
    throw new UnsupportedPdfContentError(`${path} : propriété(s) non imprimable(s) ${unknown.join(', ')}`);
  }
}

function isDict(value: unknown): value is Dict {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isPercent(value: unknown): value is string {
  return typeof value === 'string' && /^\d+(\.\d+)?%$/.test(value);
}

function color(value: unknown): string {
  if (typeof value !== 'string' || !COLOR.test(value)) {
    throw new UnsupportedPdfContentError(`couleur « ${String(value)} »`);
  }
  return value;
}

function numberOf(value: unknown): number {
  return typeof value === 'number' && isFinite(value) ? value : 0;
}

function num(value: number): string {
  return String(Math.round(value * 1000) / 1000);
}

function pt(value: number): string {
  return `${num(value)}pt`;
}
