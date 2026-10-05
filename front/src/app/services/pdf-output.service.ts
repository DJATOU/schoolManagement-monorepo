import { Injectable } from '@angular/core';
import pdfMake from 'pdfmake/build/pdfmake';
import pdfFonts from 'pdfmake/build/vfs_fonts';
import { TDocumentDefinitions } from 'pdfmake/interfaces';

import { imageDataUrl, printPdfBlob } from '../utils/pdf-print';

/**
 * Sortie d'un document composé avec pdfmake : impression, logo embarqué.
 *
 * <p>Un service plutôt que des fonctions appelées directement : un document se vérifie alors sans
 * imprimer, en remplaçant ce service.</p>
 */
@Injectable({ providedIn: 'root' })
export class PdfOutputService {

  /** Impression d'un PDF produit, remplaçable en test : une fonction de module ne s'espionne pas. */
  static printBlob: (blob: Blob, fileName: string) => void = printPdfBlob;

  constructor() {
    (pdfMake as unknown as { vfs: unknown }).vfs = pdfFonts.pdfMake.vfs;
  }

  /** Produit le PDF et ouvre la boîte d'impression ; le télécharge si elle ne s'ouvre pas. */
  print(doc: TDocumentDefinitions, fileName: string): void {
    pdfMake.createPdf(doc).getBlob((blob: Blob) => PdfOutputService.printBlob(blob, fileName));
  }

  /** Image en data URL, chaîne vide si elle ne se charge pas. */
  imageDataUrl(url: string): Promise<string> {
    return imageDataUrl(url);
  }
}
