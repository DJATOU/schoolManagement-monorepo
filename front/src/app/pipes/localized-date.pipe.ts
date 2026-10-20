import { formatDate } from '@angular/common';
import { Pipe, PipeTransform } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { parseJavaDate } from '../shared/java-date';
import { resolveLocale } from '../shared/locale';

/** Valeurs acceptées : celles du pipe `date`, plus le tableau Jackson d'un `LocalDateTime`. */
export type LocalizedDateInput = Date | string | number | number[] | null | undefined;

/**
 * `{{ valeur | appDate:'mediumDate' }}` : le pipe `date` d'Angular, dans la langue de l'interface.
 *
 * <p>L'application ne fournit pas de `LOCALE_ID` : le pipe `date` retombe sur « en-US » et affiche
 * « May 15, 2026 » dans une interface française. Fournir un `LOCALE_ID` global reformaterait aussi
 * tous les pipes `number`, montants compris : ce pipe ne sert donc qu'aux formats qui affichent un
 * nom de mois ou de jour (`mediumDate`, `medium`, `short`, `EEE d MMM y`…). Les formats purement
 * numériques (`dd/MM/yyyy`, `HH:mm`) restent sur le pipe `date`.</p>
 *
 * <p>Impur, comme le pipe `translate` : un changement de langue doit reformater les dates déjà
 * affichées.</p>
 */
@Pipe({ name: 'appDate', standalone: true, pure: false })
export class LocalizedDatePipe implements PipeTransform {
  constructor(private translate: TranslateService) {}

  /**
   * @param value    date, chaîne ISO, horodatage ou tableau Jackson ; absente, rien n'est affiché
   * @param format   format Angular (`mediumDate` par défaut, comme le pipe `date`)
   * @param timezone fuseau facultatif, comme pour le pipe `date`
   */
  transform(value: LocalizedDateInput, format = 'mediumDate', timezone?: string): string | null {
    if (value === null || value === undefined || value === '') {
      return null;
    }
    // Les chaînes et horodatages sont confiés tels quels à formatDate, qui les lit comme le pipe
    // `date` (une chaîne « yyyy-MM-dd » reste un jour local). Seul le tableau Jackson est converti.
    const input = Array.isArray(value) ? parseJavaDate(value) : value;
    if (input === null) {
      return null;
    }
    const lang = this.translate.currentLang || this.translate.defaultLang;
    return formatDate(input, format, resolveLocale(lang), timezone);
  }
}
