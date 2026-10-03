import { downloadPdfBlob, imageDataUrl, pdfSafeText, PRINT_FRAME_LIFETIME_MS, printPdfBlob } from './pdf-print';

/** PNG 1 × 1 transparent. */
const PIXEL = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=';

describe('pdf-print', () => {
  describe('pdfSafeText', () => {
    it('la flèche, absente de la police, devient « -> » ; les espaces typographiques, une espace', () => {
      expect(pdfSafeText('absent → présent')).toBe('absent -> présent');
      expect(pdfSafeText('dû 0,00 → 2\u202F000,00\u00A0DA')).toBe('dû 0,00 -> 2 000,00 DA');
      expect(pdfSafeText('a\u2009b\u205Fc\u3000d')).toBe('a b c d');
      expect(pdfSafeText('« Math 1ère A » — Rattrapage')).toBe('« Math 1ère A » — Rattrapage');
    });
  });

  describe('printPdfBlob', () => {
    const blob = new Blob(['%PDF'], { type: 'application/pdf' });
    let frames: HTMLIFrameElement[];

    beforeEach(() => {
      jasmine.clock().install();
      spyOn(URL, 'createObjectURL').and.returnValue('blob:journal');
      spyOn(URL, 'revokeObjectURL');
      frames = [];
      const append = document.body.appendChild.bind(document.body);
      spyOn(document.body, 'appendChild').and.callFake(<T extends Node>(node: T): T => {
        if (node instanceof HTMLIFrameElement) {
          frames.push(node);
          return node;
        }
        return append(node);
      });
    });

    afterEach(() => jasmine.clock().uninstall());

    /** L'iframe chargée : son impression est remplacée par `print`. */
    function load(frame: HTMLIFrameElement, print: () => void): jasmine.Spy {
      const focus = jasmine.createSpy('focus');
      Object.defineProperty(frame, 'contentWindow', { value: { focus, print } });
      frame.onload!(new Event('load'));
      return focus;
    }

    it('charge le PDF dans une iframe invisible, imprime au chargement, la retire ensuite', () => {
      printPdfBlob(blob, 'journal_Amine.pdf');

      expect(frames.length).toBe(1);
      const frame = frames[0];
      expect(frame.src).toBe('blob:journal');
      expect(frame.title).toBe('journal_Amine.pdf');
      expect(frame.style.width).toBe('0px');
      expect(frame.style.height).toBe('0px');
      expect(frame.style.position).toBe('fixed');
      const print = jasmine.createSpy('print');
      expect(load(frame, print)).toHaveBeenCalled();
      expect(print).toHaveBeenCalled();

      const remove = spyOn(frame, 'remove');
      jasmine.clock().tick(PRINT_FRAME_LIFETIME_MS - 1);
      expect(remove).not.toHaveBeenCalled();
      jasmine.clock().tick(1);
      expect(remove).toHaveBeenCalled();
      expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:journal');
    });

    it('impression impossible en iframe : le PDF est téléchargé, jamais perdu', () => {
      const click = spyOn(HTMLAnchorElement.prototype, 'click');
      printPdfBlob(blob, 'journal_Amine.pdf');

      load(frames[0], () => { throw new Error('pas d\'impression de PDF ici'); });

      expect(click).toHaveBeenCalledTimes(1);
      const link = click.calls.mostRecent().object as HTMLAnchorElement;
      expect(link.download).toBe('journal_Amine.pdf');
      expect(link.href).toBe('blob:journal');
    });
  });

  it('downloadPdfBlob : un lien de téléchargement nommé, cliqué, puis libéré', () => {
    spyOn(URL, 'createObjectURL').and.returnValue('blob:x');
    const revoke = spyOn(URL, 'revokeObjectURL');
    const click = spyOn(HTMLAnchorElement.prototype, 'click');

    downloadPdfBlob(new Blob(['%PDF']), 'journal.pdf');

    expect((click.calls.mostRecent().object as HTMLAnchorElement).download).toBe('journal.pdf');
    expect(revoke).toHaveBeenCalledWith('blob:x');
  });

  describe('imageDataUrl', () => {
    it('une image chargée est rendue en PNG embarquable', async () => {
      await expectAsync(imageDataUrl(PIXEL)).toBeResolvedTo(jasmine.stringMatching(/^data:image\/png;base64,/));
    });

    it('une image introuvable donne une chaîne vide : le document s\'imprime sans elle', async () => {
      await expectAsync(imageDataUrl('assets/introuvable.png')).toBeResolvedTo('');
    });

    it('sans contexte 2D : chaîne vide', async () => {
      spyOn(HTMLCanvasElement.prototype, 'getContext').and.returnValue(null);
      await expectAsync(imageDataUrl(PIXEL)).toBeResolvedTo('');
    });
  });
});
