import { bootstrapApplication } from '@angular/platform-browser';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import localeEn from '@angular/common/locales/en';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';

// Angular n'embarque que la locale « en-US » par défaut. L'application étant bilingue
// (FR/EN) et certains composants instanciant un `DatePipe` avec la langue courante
// (`new DatePipe('fr')`), les données de locale doivent être enregistrées ici. Sans cela,
// le pipe lève « NG0701: Missing locale data for the locale "fr" » et interrompt le
// traitement en cours (l'enregistrement d'une séance, par exemple).
registerLocaleData(localeFr);
registerLocaleData(localeEn);

bootstrapApplication(AppComponent, appConfig)
  .catch((err) => console.error(err));
