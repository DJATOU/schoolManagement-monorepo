import { Injectable } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

const LANGUAGE_STORAGE_KEY = 'app-language';

@Injectable({ providedIn: 'root' })
export class TranslationService {
  readonly defaultLang = 'fr';

  constructor(private translate: TranslateService) {
    const savedLanguage = localStorage.getItem(LANGUAGE_STORAGE_KEY) || this.defaultLang;
    this.translate.setDefaultLang(this.defaultLang);
    this.useLanguage(savedLanguage);
  }

  useLanguage(lang: string) {
    this.translate.use(lang);
    localStorage.setItem(LANGUAGE_STORAGE_KEY, lang);
  }

  toggleLanguage(): void {
    const nextLang = this.translate.currentLang === 'fr' ? 'en' : 'fr';
    this.useLanguage(nextLang);
  }

  get currentLanguage(): string {
    return this.translate.currentLang || this.defaultLang;
  }
}
