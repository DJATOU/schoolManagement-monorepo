import { Injectable } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

const LANGUAGE_KEY = 'app_language';

@Injectable({ providedIn: 'root' })
export class TranslationService {
  constructor(private translate: TranslateService) {
    this.initLanguage();
  }

  private initLanguage(): void {
    const savedLanguage = this.getSavedLanguage();
    const defaultLang = savedLanguage || 'fr';
    this.translate.setDefaultLang('fr');
    this.translate.use(defaultLang);
    this.saveLanguage(defaultLang);
  }

  changeLanguage(language: string): void {
    this.translate.use(language);
    this.saveLanguage(language);
  }

  getCurrentLanguage(): string {
    return this.translate.currentLang || this.translate.defaultLang || 'fr';
  }

  private saveLanguage(language: string): void {
    localStorage.setItem(LANGUAGE_KEY, language);
  }

  private getSavedLanguage(): string | null {
    return localStorage.getItem(LANGUAGE_KEY);
  }
}
