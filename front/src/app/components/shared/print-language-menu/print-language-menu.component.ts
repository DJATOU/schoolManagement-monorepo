import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Direction } from '@angular/cdk/bidi';

import { APP_LANGUAGES, directionOf } from '../../../shared/app-languages';
import { PrintLanguage, printLanguageOf } from '../../../shared/pdf-i18n';

/** Entrée du menu : une langue, nommée dans sa propre langue. */
interface PrintLanguageOption {
  code: PrintLanguage;
  name: string;
  dir: Direction;
  screen: boolean;
}

/**
 * Choix de la langue d'un document au moment de l'imprimer : Français, English, العربية.
 *
 * <p>Se place à côté du bouton d'impression, qui garde son geste : un clic imprime dans la langue de
 * l'écran (`screenLanguage`), sans étape de plus. Ce déclencheur ouvre le menu des trois langues,
 * celle de l'écran en premier et signalée comme telle. Le choix ne vaut que pour ce document :
 * l'interface ne change pas de langue.</p>
 *
 * <p>En arabe, le document est produit par le navigateur : la boîte d'impression s'ouvre, et
 * « Enregistrer au format PDF » donne le fichier. Le menu le dit sous « العربية » (désactivable avec
 * `arabicOpensPrintDialog` pour un document qui reste un vrai téléchargement).</p>
 */
@Component({
  selector: 'app-print-language-menu',
  standalone: true,
  exportAs: 'printLanguageMenu',
  imports: [CommonModule, MatButtonModule, MatIconModule, MatMenuModule, MatTooltipModule, TranslateModule],
  templateUrl: './print-language-menu.component.html',
  styleUrls: ['./print-language-menu.component.scss']
})
export class PrintLanguageMenuComponent {
  private static nextId = 0;

  /** Identifiant de la mention « boîte d'impression », lue avec l'entrée « العربية ». */
  readonly hintId = `plm-hint-${PrintLanguageMenuComponent.nextId++}`;

  /** Désactivé avec le bouton d'impression qu'il accompagne. */
  @Input() disabled = false;

  /** Classes du déclencheur, pour l'assortir au bouton d'impression (ex. « pf-hb pf-hb-print »). */
  @Input() triggerClass = '';

  /** Mentionne sous « العربية » que la boîte d'impression s'ouvre (« Enregistrer au format PDF »). */
  @Input() arabicOpensPrintDialog = true;

  /** Langue choisie pour ce document. */
  @Output() readonly selected = new EventEmitter<PrintLanguage>();

  constructor(private translate: TranslateService) {}

  /** Langue de l'écran : celle du clic sur le bouton d'impression, et première du menu. */
  get screenLanguage(): PrintLanguage {
    return printLanguageOf(this.translate);
  }

  /** Langue de l'écran d'abord, puis les autres dans l'ordre du sélecteur de langue. */
  get options(): PrintLanguageOption[] {
    const screen = this.screenLanguage;
    const toOption = (language: { code: PrintLanguage; name: string }): PrintLanguageOption => ({
      code: language.code,
      name: language.name,
      dir: directionOf(language.code),
      screen: language.code === screen
    });
    const options = APP_LANGUAGES.map(toOption);
    return [...options.filter(option => option.screen), ...options.filter(option => !option.screen)];
  }

  choose(language: PrintLanguage): void {
    this.selected.emit(language);
  }

  trackByCode(_: number, option: PrintLanguageOption): PrintLanguage {
    return option.code;
  }
}
