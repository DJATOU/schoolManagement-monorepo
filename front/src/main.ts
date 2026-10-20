import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';
import { registerAppLocales } from './app/shared/locale';
import { applyDocumentLanguage, readSavedLanguage } from './app/shared/app-languages';

// Angular n'embarque que la locale « en-US » par défaut. L'application étant multilingue
// (FR/EN/AR) et certains composants instanciant un `DatePipe` avec la langue courante
// (`new DatePipe('fr')`), les données de locale doivent être enregistrées ici. Sans cela,
// le pipe lève « NG0701: Missing locale data for the locale "fr" » et interrompt le
// traitement en cours (l'enregistrement d'une séance, par exemple).
registerAppLocales();

// Langue et sens d'écriture posés avant le démarrage : un utilisateur arabophone ne voit pas
// la page s'afficher de gauche à droite puis basculer.
applyDocumentLanguage(readSavedLanguage());

bootstrapApplication(AppComponent, appConfig)
  .catch((err) => console.error(err));
