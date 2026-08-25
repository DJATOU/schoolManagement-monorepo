import { ApplicationConfig, importProvidersFrom } from '@angular/core';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { provideAnimations } from '@angular/platform-browser/animations';
import { TranslateLoader, TranslateModule } from '@ngx-translate/core';
import { TranslateHttpLoader } from '@ngx-translate/http-loader';
import { HttpClient, HTTP_INTERCEPTORS } from '@angular/common/http';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { AuthInterceptor } from './services/auth.interceptor';
import { TranslatedPaginatorIntl } from './shared/translated-paginator-intl';

/**
 * Configuration globale de l'application
 * Utilise environment.ts (dev) ou environment.prod.ts (production)
 *
 * NOTE : {@code API_BASE_URL} est désormais défini dans {@code ./api-base-url}
 * et ré-exporté ici pour compatibilité. Les services doivent importer la
 * constante depuis {@code ./api-base-url} afin d'éviter une dépendance
 * circulaire (app.config → app.routes → composants → services → app.config).
 */
export { API_BASE_URL } from './api-base-url';

export function HttpLoaderFactory(http: HttpClient) {
  return new TranslateHttpLoader(http, './assets/i18n/', '.json');
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideAnimations(),
    provideHttpClient(withInterceptorsFromDi()),
    { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true },
    // Libellés des paginateurs Material traduits (FR/EN) au lieu de l'anglais par défaut.
    { provide: MatPaginatorIntl, useClass: TranslatedPaginatorIntl },
    importProvidersFrom(
      TranslateModule.forRoot({
        defaultLanguage: 'fr',
        loader: {
          provide: TranslateLoader,
          useFactory: HttpLoaderFactory,
          deps: [HttpClient]
        }
      })
    )
  ]
};
