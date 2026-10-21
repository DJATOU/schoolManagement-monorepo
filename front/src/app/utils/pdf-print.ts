/**
 * Sortie d'un document PDF : impression directe, téléchargement en repli.
 *
 * <p>L'impression passe par une iframe masquée et non par `pdfMake.print()`, qui ouvre un onglet
 * par `window.open` : le navigateur le bloque quand l'appel suit un traitement asynchrone (chargement
 * du logo, réponse HTTP) plutôt qu'un clic. Un navigateur qui n'imprime pas un PDF en iframe le
 * télécharge : le document n'est jamais perdu.</p>
 */

/** Durée de vie de l'iframe : la retirer tout de suite annulerait la boîte d'impression, asynchrone. */
export const PRINT_FRAME_LIFETIME_MS = 60_000;

/** Imprime un PDF déjà produit ; le télécharge si l'impression en iframe échoue. */
export function printPdfBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const iframe = document.createElement('iframe');
  iframe.style.position = 'fixed';
  iframe.style.right = '0';
  iframe.style.bottom = '0';
  iframe.style.width = '0';
  iframe.style.height = '0';
  iframe.style.border = '0';
  iframe.title = fileName;
  iframe.onload = () => {
    try {
      iframe.contentWindow!.focus();
      iframe.contentWindow!.print();
    } catch {
      downloadPdfBlob(blob, fileName);
    }
  };
  iframe.src = url;
  document.body.appendChild(iframe);
  window.setTimeout(() => {
    iframe.remove();
    URL.revokeObjectURL(url);
  }, PRINT_FRAME_LIFETIME_MS);
}

/** Attente maximale des polices et des images avant d'imprimer : une police distante injoignable (poste hors ligne) ne bloque pas l'impression. */
export const PRINT_READY_TIMEOUT_MS = 3_000;

/**
 * Imprime un document HTML (document en arabe, voir `pdfDefinitionToHtml`) par une iframe masquée.
 *
 * <p>Même iframe que {@link printPdfBlob}, chargée par `srcdoc` : l'impression attend que les
 * polices et le logo soient prêts (au plus {@link PRINT_READY_TIMEOUT_MS}), puis ouvre la boîte
 * d'impression, où « Enregistrer en PDF » produit le fichier. La promesse échoue si la boîte ne
 * peut pas s'ouvrir : il n'y a pas de fichier à télécharger en repli.</p>
 */
export function printHtmlDocument(html: string, title: string): Promise<void> {
  return new Promise<void>((resolve, reject) => {
    const iframe = document.createElement('iframe');
    iframe.style.position = 'fixed';
    iframe.style.right = '0';
    iframe.style.bottom = '0';
    iframe.style.width = '0';
    iframe.style.height = '0';
    iframe.style.border = '0';
    iframe.title = title;
    iframe.setAttribute('aria-hidden', 'true');
    iframe.onload = () => {
      const frameWindow = iframe.contentWindow;
      const frameDocument = iframe.contentDocument;
      if (!frameWindow || !frameDocument) {
        reject(new Error('Impression indisponible'));
        return;
      }
      documentReady(frameDocument).then(() => {
        try {
          frameWindow.focus();
          frameWindow.print();
          resolve();
        } catch (error) {
          reject(error);
        }
      });
    };
    iframe.srcdoc = html;
    document.body.appendChild(iframe);
    window.setTimeout(() => iframe.remove(), PRINT_FRAME_LIFETIME_MS);
  });
}

/** Polices et images du document prêtes, ou délai écoulé. */
function documentReady(doc: Document): Promise<void> {
  const fonts = doc.fonts ? doc.fonts.ready.then(() => undefined) : Promise.resolve();
  const images = Array.from(doc.images).map(image => image.decode().catch(() => undefined));
  const timeout = new Promise<void>(resolve => window.setTimeout(resolve, PRINT_READY_TIMEOUT_MS));
  return Promise.race([Promise.all([fonts, ...images]).then(() => undefined), timeout]);
}

/** Télécharge un PDF sous le nom donné. */
export function downloadPdfBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(url);
}

/**
 * Image en data URL PNG, pour l'embarquer dans un PDF ; chaîne vide si elle ne se charge pas : un
 * logo indisponible n'empêche pas d'imprimer.
 */
export function imageDataUrl(url: string): Promise<string> {
  return new Promise(resolve => {
    const img = new Image();
    img.crossOrigin = 'Anonymous';
    img.onload = () => {
      const canvas = document.createElement('canvas');
      canvas.width = img.width;
      canvas.height = img.height;
      const context = canvas.getContext('2d');
      if (!context) {
        resolve('');
        return;
      }
      context.drawImage(img, 0, 0);
      resolve(canvas.toDataURL('image/png'));
    };
    img.onerror = () => resolve('');
    img.src = url;
  });
}

/**
 * Texte imprimable par la police embarquée de pdfmake (Roboto), qui n'a ni flèche ni espaces
 * typographiques : sans cela ils sortent en carré « glyphe manquant ».
 *
 * <ul>
 *   <li>« → » devient « -> » : « absent -> présent ». Un chevron isolé se lirait « plus grand que »
 *       entre deux montants ;</li>
 *   <li>espaces insécables, fines, typographiques : une espace ordinaire.</li>
 * </ul>
 */
export function pdfSafeText(value: string): string {
  return value
    .replace(/\u2192/g, '->')
    .replace(/[\u00A0\u2000-\u200A\u202F\u205F\u3000]/g, ' ');
}
