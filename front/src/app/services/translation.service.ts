import { DOCUMENT } from '@angular/common';
import { Inject, Injectable } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { AppDirectionality } from '../shared/app-directionality';
import {
  DEFAULT_LANGUAGE,
  LANGUAGE_STORAGE_KEY,
  SUPPORTED_LANGS,
  applyDocumentLanguage,
  directionOf,
  readSavedLanguage,
  toSupportedLanguage
} from '../shared/app-languages';

export { SUPPORTED_LANGS };

@Injectable({ providedIn: 'root' })
export class TranslationService {
  readonly defaultLang = DEFAULT_LANGUAGE;
  constructor(
    private translate: TranslateService,
    private directionality: AppDirectionality,
    @Inject(DOCUMENT) private document: Document
  ) {
    this.translate.setDefaultLang(this.defaultLang);
    this.useLanguage(readSavedLanguage());
  }
  /**
   * Change la langue de l'interface, à chaud : textes, `lang` / `dir` du document et sens
   * d'écriture des composants Material (menus, dialogues, sidenav). Une langue non proposée
   * est ramenée au français.
   */
  useLanguage(lang: string) {
    const language = toSupportedLanguage(lang);
    this.translate.use(language);
    applyDocumentLanguage(language, this.document);
    this.directionality.setDirection(directionOf(language));
    try {
      localStorage.setItem(LANGUAGE_STORAGE_KEY, language);
    } catch {
      // Stockage indisponible (navigation privée stricte) : le choix vaut pour la session.
    }
  }
  get currentLanguage(): string {
    return this.translate.currentLang || this.defaultLang;
  }
}
