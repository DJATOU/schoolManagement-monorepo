import { Injectable } from '@angular/core';
import pdfMake from 'pdfmake/build/pdfmake';
import pdfFonts from 'pdfmake/build/vfs_fonts';
import { TDocumentDefinitions } from 'pdfmake/interfaces';

import { directionOf } from '../shared/app-languages';
import { PrintLanguage, withPrintLanguage } from '../shared/pdf-i18n';
import { pdfDefinitionToHtml } from '../shared/print/pdf-to-html';
import { imageDataUrl, printHtmlDocument, printPdfBlob } from '../utils/pdf-print';

/**
 * Sortie d'un document composé avec pdfmake : impression, logo embarqué.
 *
 * <p>Un service plutôt que des fonctions appelées directement : un document se vérifie alors sans
 * imprimer, en remplaçant ce service.</p>
 *
 * <p>Un document en arabe ne passe pas par pdfmake : la même définition est convertie en page HTML
 * de droite à gauche, imprimée par le navigateur ({@link printHtml}).</p>
 */
@Injectable({ providedIn: 'root' })
export class PdfOutputService {

  /** Impression d'un PDF produit, remplaçable en test : une fonction de module ne s'espionne pas. */
  static printBlob: (blob: Blob, fileName: string) => void = printPdfBlob;

  /** Impression d'une page HTML, remplaçable en test pour la même raison. */
  static printHtmlDocument: (html: string, title: string) => Promise<void> = printHtmlDocument;

  constructor() {
    (pdfMake as unknown as { vfs: unknown }).vfs = pdfFonts.pdfMake.vfs;
  }

  /** Produit le PDF et ouvre la boîte d'impression ; le télécharge si elle ne s'ouvre pas. */
  print(doc: TDocumentDefinitions, fileName: string): void {
    pdfMake.createPdf(doc).getBlob((blob: Blob) => PdfOutputService.printBlob(blob, fileName));
  }

  /**
   * Page HTML du document dans `lang` (l'arabe par défaut).
   *
   * <p>Les fonctions de pied de page de la définition sont appelées ici : elles s'exécutent dans la
   * langue du document.</p>
   */
  htmlFor(doc: TDocumentDefinitions, title: string, lang: PrintLanguage = 'ar'): string {
    return withPrintLanguage(lang, () => pdfDefinitionToHtml(doc, { lang, dir: directionOf(lang), title }));
  }

  /**
   * Imprime le document par le navigateur : la boîte d'impression s'ouvre, « Enregistrer en PDF »
   * produit le fichier. `title` est le nom de fichier proposé.
   */
  async printHtml(doc: TDocumentDefinitions, title: string, lang: PrintLanguage = 'ar'): Promise<void> {
    return PdfOutputService.printHtmlDocument(this.htmlFor(doc, title, lang), title);
  }

  /** Image en data URL, chaîne vide si elle ne se charge pas. */
  imageDataUrl(url: string): Promise<string> {
    return imageDataUrl(url);
  }
}
