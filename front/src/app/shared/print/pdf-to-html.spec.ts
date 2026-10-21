import { Content, TDocumentDefinitions } from 'pdfmake/interfaces';
import { PAGE_SENTINEL, PAGES_SENTINEL, UnsupportedPdfContentError, pdfDefinitionToHtml } from './pdf-to-html';
import { marginBoxTexts } from '../../../testing/print-language';

/**
 * Convertisseur définition pdfmake → page HTML de droite à gauche : chaque nœud employé par les
 * documents de l'application, et un échec bruyant pour tout le reste.
 */
describe('pdfDefinitionToHtml', () => {
  /** Un pixel PNG transparent. */
  const PNG = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=';

  const convert = (doc: Partial<TDocumentDefinitions>): string =>
    pdfDefinitionToHtml({ content: [], ...doc } as TDocumentDefinitions, { lang: 'ar', title: 'وثيقة' });

  /** Page convertie, analysée. */
  const parse = (doc: Partial<TDocumentDefinitions>): Document =>
    new DOMParser().parseFromString(convert(doc), 'text/html');

  const styleOf = (page: Document): string => page.querySelector('style')?.textContent ?? '';

  it('page : arabe, de droite à gauche, titre, police arabe et polices du système', () => {
    const page = parse({ content: 'وصل' });
    expect(page.documentElement.lang).toBe('ar');
    expect(page.documentElement.dir).toBe('rtl');
    expect(page.title).toBe('وثيقة');
    expect(styleOf(page)).toContain("font-family:'Noto Naskh Arabic', 'Geeza Pro', Tahoma, Arial, 'Segoe UI', sans-serif");
    expect(page.querySelector('link')?.getAttribute('href')).toContain('Noto+Naskh+Arabic');
    expect(page.querySelector('script')).toBeNull();
  });

  it('format : A4 portrait par défaut, A5, paysage, marges mises en miroir', () => {
    expect(styleOf(parse({}))).toContain('@page{size:A4 portrait;margin:40pt 40pt 40pt 40pt;');
    expect(styleOf(parse({ pageSize: 'A5', pageMargins: [10, 20, 30, 40] })))
      .toContain('@page{size:A5 portrait;margin:20pt 10pt 40pt 30pt;');
    expect(styleOf(parse({ pageOrientation: 'landscape' }))).toContain('size:A4 landscape');
  });

  it('en-têtes de tableau répétés, lignes jamais coupées, couleurs imprimées, passages isolés', () => {
    const css = styleOf(parse({}));
    expect(css).toContain('thead{display:table-header-group;}');
    expect(css).toContain('tr{break-inside:avoid;');
    expect(css).toContain('print-color-adjust:exact');
    expect(css).toContain('bdi{unicode-bidi:isolate;font-style:var(--pdf-it,normal);}');
  });

  it('texte : gras, italique, taille, couleur, souligné, sans retour, marges logiques', () => {
    const div = parse({
      content: { text: 'عنوان', bold: true, italics: true, fontSize: 14, color: '#4f46e5', decoration: 'underline',
        noWrap: true, margin: [1, 2, 3, 4] }
    }).querySelector('body > div') as HTMLElement;
    expect(div.textContent).toBe('عنوان');
    const style = div.getAttribute('style') ?? '';
    for (const part of ['font-weight:700', '--pdf-it:italic', 'font-size:14pt', 'color:#4f46e5',
      'text-decoration:underline', 'white-space:nowrap', 'margin-inline-start:1pt', 'margin-block-start:2pt',
      'margin-inline-end:3pt', 'margin-block-end:4pt']) {
      expect(style).withContext(part).toContain(part);
    }
  });

  it('italique : passages latins seulement, l\'arabe reste droit ; de gauche à droite, italique ordinaire', () => {
    const rtl = parse({ content: { text: 'ملاحظة Feuille mal saisie', italics: true } }).querySelector('body > div') as HTMLElement;
    expect(rtl.getAttribute('style')).toContain('--pdf-it:italic');
    expect(rtl.getAttribute('style')).not.toContain('font-style');
    const ltr = new DOMParser().parseFromString(pdfDefinitionToHtml(
      { content: { text: 'Note', italics: true } } as TDocumentDefinitions, { lang: 'fr', dir: 'ltr' }), 'text/html');
    expect(ltr.querySelector('body > div')?.getAttribute('style')).toContain('font-style:italic');
  });

  it('alignement : gauche et droite deviennent début et fin de ligne, mis en miroir par dir', () => {
    const page = parse({ content: [{ text: 'أ', alignment: 'left' }, { text: 'ب', alignment: 'right' }, { text: 'ج', alignment: 'center' }] });
    const styles = Array.from(page.querySelectorAll('body > div > div')).map(d => d.getAttribute('style'));
    expect(styles).toEqual(['text-align:start;', 'text-align:end;', 'text-align:center;']);
  });

  it('styles nommés, dans l\'ordre, propriétés du nœud en dernier ; marge du style', () => {
    const div = parse({
      content: { text: 'x', style: ['a', 'b'], color: '#000000' },
      styles: { a: { fontSize: 9, color: '#111111' }, b: { bold: true, margin: [0, 6, 0, 8] } }
    }).querySelector('body > div') as HTMLElement;
    const style = div.getAttribute('style') ?? '';
    expect(style).toContain('font-size:9pt');
    expect(style).toContain('font-weight:700');
    expect(style).toContain('color:#000000');
    expect(style).toContain('margin-block-start:6pt');
  });

  it('style par défaut : taille du corps du document', () => {
    expect(styleOf(parse({ defaultStyle: { fontSize: 9 } }))).toContain('body{font-size:9pt;}');
    expect(styleOf(parse({}))).toContain('font-size:12pt');
  });

  it('texte en morceaux mis en forme', () => {
    const page = parse({ content: { text: ['أ ', { text: 'RECU-1', bold: true }] } });
    const span = page.querySelector('span') as HTMLElement;
    expect(span.getAttribute('style')).toContain('font-weight:700');
    expect(span.innerHTML).toBe('<bdi dir="ltr">RECU-1</bdi>');
  });

  it('montants, niveaux et noms latins isolés dans le texte arabe', () => {
    const html = convert({ content: 'المبلغ : 1.200,00 دج — Amine Belkacem, 4 AM' });
    expect(html).toContain('<bdi dir="ltr">1.200,00</bdi> دج');
    expect(html).toContain('<bdi dir="ltr">Amine Belkacem, 4 AM</bdi>');
  });

  it('colonnes : fixe, au contenu, en pourcentage, part du reste, espacement', () => {
    const page = parse({
      content: { columns: [{ image: PNG, width: 46 }, { text: 'a', width: 'auto' }, { text: 'b', width: '50%' }, { text: 'c' }], columnGap: 10 }
    });
    const row = page.querySelector('.pdf-cols') as HTMLElement;
    expect(row.getAttribute('style')).toContain('gap:10pt');
    expect(Array.from(row.children).map(c => c.getAttribute('style')))
      .toEqual(['flex:0 0 46pt;', 'flex:0 0 auto;', 'flex:0 0 50%;', 'flex:1 1 0;']);
  });

  it('pile et listes à puces', () => {
    const page = parse({ content: [{ stack: ['أ', 'ب'] }, { ul: ['G1 — 4 AM', 'G2'], margin: [0, 0, 0, 14] }] });
    expect(page.querySelectorAll('li').length).toBe(2);
    expect(page.querySelector('ul')?.getAttribute('style')).toContain('margin-block-end:14pt');
    expect(page.querySelector('li')?.innerHTML).toContain('<bdi dir="ltr">G1 — 4 AM</bdi>');
  });

  it('image : logo en data URL, largeur ; logo indisponible : place réservée', () => {
    const page = parse({ content: [{ image: PNG, width: 40 }, { image: '', width: 100 }] });
    const img = page.querySelector('img') as HTMLImageElement;
    expect(img.getAttribute('src')).toBe(PNG);
    expect(img.getAttribute('style')).toContain('inline-size:40pt');
    expect(page.querySelectorAll('img').length).toBe(1);
    expect(page.body.innerHTML).toContain('inline-size:100pt');
  });

  it('trait horizontal, avec un décalage vertical', () => {
    const page = parse({ content: { canvas: [{ type: 'line', x1: 0, y1: 34, x2: 160, y2: 34, lineWidth: 0.6, lineColor: '#64748b' }] } });
    const line = page.querySelector('body > div > div') as HTMLElement;
    const style = line.getAttribute('style') ?? '';
    expect(style).toContain('margin-block-start:34pt');
    expect(style).toContain('inline-size:160pt');
    expect(style).toContain('border-block-start:0.6pt solid #64748b');
  });

  it('saut de page avant et après', () => {
    const page = parse({ content: [{ text: 'أ', pageBreak: 'before' }, { text: 'ب', pageBreak: 'after' }] });
    const styles = Array.from(page.querySelectorAll('body > div > div')).map(d => d.getAttribute('style'));
    expect(styles).toEqual(['break-before:page;', 'break-after:page;']);
  });

  describe('tableaux', () => {
    const table = (node: Partial<Content> & Record<string, unknown>): Document =>
      parse({ content: node as Content, styles: { th: { bold: true, fillColor: '#6366f1' } } });

    it('en-tête dans thead, largeurs, fonds, fusion de colonnes avec ou sans marques de place', () => {
      const page = table({
        table: {
          headerRows: 1,
          widths: ['auto', '*', 60, '30%'],
          body: [
            [{ text: '#', style: 'th' }, { text: 'الاسم', style: 'th' }, { text: 'ج', style: 'th' }, { text: 'د', style: 'th' }],
            [{ text: 'فارغ', colSpan: 4 }, {}, {}, {}],
            [{ text: 'وحيد', colSpan: 4 }]
          ]
        }
      });
      expect(page.querySelectorAll('thead tr').length).toBe(1);
      expect(page.querySelectorAll('tbody tr').length).toBe(2);
      expect(Array.from(page.querySelectorAll('col')).map(c => c.getAttribute('style')))
        .toEqual(['', '', 'inline-size:60pt;', 'inline-size:30%;']);
      expect(page.querySelector('table')?.getAttribute('style')).toContain('inline-size:100%');
      const spans = Array.from(page.querySelectorAll('tbody td')).map(td => td.getAttribute('colspan'));
      expect(spans).toEqual(['4', '4']);
    });

    it('colonne « auto » aux textes courts : sur une ligne, comme pdfmake ; texte long : libre', () => {
      const page = table({ table: { widths: ['auto', '*', 'auto'], body: [
        ['Salle 1', 'Séance 1 — Maths 4 AM A', 'Nadia Aït Ahmed Benali'],
        ['مصادق عليها', 'Séance 2', 'Amine']
      ] } });
      const tds = Array.from(page.querySelectorAll('td')).map(td => td.getAttribute('style') ?? '');
      expect(tds[0]).toContain('white-space:nowrap');
      expect(tds[3]).toContain('white-space:nowrap');
      expect(tds[1]).not.toContain('white-space:nowrap');
      expect(tds[2]).not.toContain('white-space:nowrap');
    });

    it('colonnes « * » et en pourcentage : chacune sa part', () => {
      const page = table({ table: { widths: ['*', '*', '50%'], body: [['أ', 'ب', 'ج']] } });
      expect(Array.from(page.querySelectorAll('col')).map(c => c.getAttribute('style')))
        .toEqual(['inline-size:25%;', 'inline-size:25%;', 'inline-size:50%;']);
    });

    it('fond de cellule (propre ou par style), cellule-pastille de taille minimale', () => {
      const page = parse({
        content: { table: { widths: ['auto', '*'], body: [[{ text: '', fillColor: '#32a852', width: 15, height: 15 }, { text: 'x', style: 'h' }]] } },
        styles: { h: { fillColor: '#6366f1', color: '#ffffff', margin: [6, 6, 6, 6] } }
      });
      const [swatch, header] = Array.from(page.querySelectorAll('td')).map(td => td.getAttribute('style') ?? '');
      expect(swatch).toContain('background-color:#32a852');
      expect(swatch).toContain('min-inline-size:15pt');
      expect(header).toContain('background-color:#6366f1');
      expect(header).toContain('color:#ffffff');
      // Marge de cellule ajoutée au retrait de la mise en forme par défaut (4 / 2).
      expect(header).toContain('padding-inline-start:10pt');
      expect(header).toContain('padding-block-start:8pt');
    });

    it('mise en forme par défaut : traits noirs de 1pt', () => {
      const td = table({ table: { body: [['أ']] } }).querySelector('td') as HTMLElement;
      expect(td.getAttribute('style')).toContain('border-inline-start:1pt solid black');
    });

    it('« noBorders » : aucun trait, pas de retrait aux bords', () => {
      const tds = Array.from(table({ table: { body: [['أ', 'ب']] }, layout: 'noBorders' }).querySelectorAll('td'))
        .map(td => td.getAttribute('style') ?? '');
      expect(tds[0]).toContain('border-block-start:0 none');
      expect(tds[0]).toContain('padding-inline-start:0pt');
      expect(tds[0]).toContain('padding-inline-end:4pt');
      expect(tds[1]).toContain('padding-inline-start:4pt');
      expect(tds[1]).toContain('padding-inline-end:0pt');
    });

    it('« lightHorizontalLines » : trait épais sous l\'en-tête, traits gris ensuite', () => {
      const page = table({ table: { headerRows: 1, widths: ['*'], body: [['h'], ['a'], ['b']] }, layout: 'lightHorizontalLines' });
      const tds = Array.from(page.querySelectorAll('td')).map(td => td.getAttribute('style') ?? '');
      expect(tds[0]).toContain('border-block-start:0 none');
      expect(tds[0]).toContain('border-block-end:2pt solid black');
      expect(tds[1]).toContain('border-block-end:1pt solid #aaa');
      expect(tds[2]).toContain('border-block-end:0 none');
    });

    it('« headerLineOnly »', () => {
      const tds = Array.from(table({ table: { headerRows: 1, body: [['h'], ['a']] }, layout: 'headerLineOnly' }).querySelectorAll('td'))
        .map(td => td.getAttribute('style') ?? '');
      expect(tds[0]).toContain('border-block-end:2pt solid black');
      expect(tds[1]).toContain('border-block-end:0 none');
    });

    it('mise en forme sur mesure, qui lit node.table.body.length ; bordures de cellule', () => {
      const page = table({
        table: { widths: ['38%', '62%'], body: [['أ', 'ب'], ['ج', { text: 'د', border: [false, false, false, false] }]] },
        layout: {
          hLineWidth: (i: number, node: { table: { body: unknown[] } }) => (i === 0 || i === node.table.body.length ? 0 : 0.5),
          vLineWidth: () => 0,
          hLineColor: () => '#e2e8f0',
          paddingLeft: () => 0,
          paddingRight: () => 4
        }
      });
      const tds = Array.from(page.querySelectorAll('td')).map(td => td.getAttribute('style') ?? '');
      expect(tds[0]).toContain('border-block-start:0 none');
      expect(tds[0]).toContain('border-block-end:0.5pt solid #e2e8f0');
      expect(tds[2]).toContain('border-block-end:0 none');
      expect(tds[3]).toContain('border-block-start:0 none');
      expect(tds[0]).toContain('padding-inline-end:4pt');
    });

    it('style et alignement du tableau, hérités par les cellules', () => {
      const page = parse({
        content: { table: { body: [['أ']] }, style: 'small', alignment: 'center', margin: [0, 10, 0, 10] },
        styles: { small: { fontSize: 9.5 } }
      });
      const style = page.querySelector('table')?.getAttribute('style') ?? '';
      expect(style).toContain('font-size:9.5pt');
      expect(style).toContain('text-align:center');
      expect(style).toContain('margin-block-start:10pt');
    });
  });

  describe('pied de page', () => {
    it('texte fixe centré', () => {
      const html = convert({ footer: () => ({ text: 'لا يغطي هذا الوصل إلا الدفعة.', alignment: 'center', fontSize: 7, color: '#64748b', margin: [32, 10, 32, 0] }) });
      expect(marginBoxTexts(html)).toEqual({ 'bottom-center': 'لا يغطي هذا الوصل إلا الدفعة.' });
      const css = styleOf(new DOMParser().parseFromString(html, 'text/html'));
      expect(css).toContain('@bottom-center{');
      expect(css).toContain('padding-top:10pt');
      expect(css).toContain('font-size:7pt');
    });

    it('numéros de page : compteurs CSS, isolés de gauche à droite', () => {
      const html = convert({ footer: (current: number, total: number) => ({ text: `الصفحة ${current} / ${total}`, alignment: 'center' }) });
      const css = styleOf(new DOMParser().parseFromString(html, 'text/html'));
      expect(css).toContain('content:"الصفحة " "\\002066" counter(page) " / " counter(pages) "\\002069"');
      expect(marginBoxTexts(html)['bottom-center']).toBe(`الصفحة ${PAGE_SENTINEL} / ${PAGES_SENTINEL}`);
    });

    it('deux colonnes : début de ligne à droite, fin de ligne à gauche', () => {
      const html = convert({
        footer: (current: number, total: number) => ({
          columns: [
            { text: 'أُنشئت في 05/01/2030 14:30', fontSize: 8, margin: [40, 8, 0, 0] },
            { text: `الصفحة ${current} / ${total}`, alignment: 'right', fontSize: 8, margin: [0, 8, 40, 0] }
          ]
        })
      });
      expect(marginBoxTexts(html)).toEqual({
        'bottom-right': 'أُنشئت في 05/01/2030 14:30',
        'bottom-left': `الصفحة ${PAGE_SENTINEL} / ${PAGES_SENTINEL}`
      });
    });

    it('le pied de page ne peut pas sortir de sa chaîne CSS', () => {
      const html = convert({ footer: () => ({ text: '"}</style><script>alert(1)</script>' }) });
      const page = new DOMParser().parseFromString(html, 'text/html');
      expect(page.querySelector('script')).toBeNull();
      expect(marginBoxTexts(html)['bottom-right']).toBe('"}</style><script>alert(1)</script>');
    });
  });

  it('tampon : répété sur chaque page, pivoté, couleur et opacité', () => {
    const page = parse({ watermark: { text: 'ملغى', color: '#dc2626', opacity: 0.25, bold: true, angle: -35 } });
    const mark = page.querySelector('.pdf-wm') as HTMLElement;
    expect(mark.textContent).toBe('ملغى');
    const style = mark.getAttribute('style') ?? '';
    expect(style).toContain('rotate(-35deg)');
    expect(style).toContain('opacity:0.25');
    expect(style).toContain('color:#dc2626');
    expect(styleOf(page)).toContain('.pdf-wm{position:fixed;');
  });

  it('échappe le texte et les attributs venus du serveur', () => {
    const html = convert({ content: [{ text: '<img src=x onerror=alert(1)>' }], info: { title: '</title><script>' } });
    const page = new DOMParser().parseFromString(html, 'text/html');
    expect(page.querySelector('img')).toBeNull();
    expect(page.querySelector('script')).toBeNull();
    expect(page.body.textContent).toContain('<img src=x onerror=alert(1)>');
  });

  describe('échec bruyant : rien n\'est perdu en silence', () => {
    const fails = (doc: Record<string, unknown>): void => {
      expect(() => convert(doc as Partial<TDocumentDefinitions>)).toThrowError(UnsupportedPdfContentError);
    };

    it('nœud inconnu ou propriété non imprimable', () => {
      fails({ content: [{ qr: 'x' }] });
      fails({ content: [{ text: 'x', link: 'https://example.org' }] });
      fails({ content: [{ text: 'x', absolutePosition: { x: 1, y: 1 } }] });
      fails({ content: [{}] });
      fails({ content: [{ text: 'x', stack: [] }] });
    });

    it('fusion de lignes', () => {
      fails({ content: { table: { body: [[{ text: 'x', rowSpan: 2 }], [{}]] } } });
    });

    it('forme autre qu\'un trait horizontal', () => {
      fails({ content: { canvas: [{ type: 'rect', x: 0, y: 0, w: 10, h: 10 }] } });
      fails({ content: { canvas: [{ type: 'line', x1: 0, y1: 0, x2: 10, y2: 10 }] } });
    });

    it('image qui n\'est pas une data URL PNG / JPEG', () => {
      fails({ content: { image: 'https://example.org/logo.png' } });
      fails({ content: { image: 'data:image/svg+xml;base64,PHN2Zz4=' } });
    });

    it('mise en forme de tableau inconnue, ou propriété de tableau inconnue', () => {
      fails({ content: { table: { body: [['x']] }, layout: 'zebra' } });
      fails({ content: { table: { body: [['x']], heights: 20 } } });
    });

    it('pied de page qui n\'est pas du texte', () => {
      fails({ footer: () => ({ image: PNG }) });
      fails({ footer: () => ({ columns: [{ text: 'a' }, { text: 'b' }] }) });
    });

    it('format, style, couleur ou clé de document inconnus', () => {
      fails({ pageSize: 'B4' });
      fails({ content: { text: 'x', style: 'absent' } });
      fails({ content: { text: 'x', color: 'red;background:url(x)' } });
      fails({ background: () => 'x' });
      fails({ styles: { a: { characterSpacing: 2 } } });
    });
  });
});
