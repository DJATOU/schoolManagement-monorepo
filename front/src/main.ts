import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';
import { HttpClientModule } from '@angular/common/http';
import { importProvidersFrom } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';

appConfig.providers.push(importProvidersFrom(HttpClientModule));
appConfig.providers.push(
  importProvidersFrom(
    TranslateModule.forRoot({
      defaultLanguage: 'fr'
    })
  )
);
appConfig.providers.push(
  ...provideTranslateHttpLoader({
    prefix: './assets/i18n/',
    suffix: '.json'
  })
);

bootstrapApplication(AppComponent, appConfig).catch((err) => console.error(err));
