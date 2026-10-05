import { Pipe, PipeTransform } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { resolveLocale } from '../shared/locale';

/** Formateurs par locale : `Intl.NumberFormat` est coûteux à construire, il est réutilisé. */
const FORMATTERS = new Map<string, Intl.NumberFormat>();

/**
 * Montant à deux décimales, au format de la langue active : « 2 400,00 » en français,
 * « 2,400.00 » en anglais.
 *
 * <p>Le pipe `number` d'Angular formate selon `LOCALE_ID`, que l'application ne fournit pas : il
 * retombait sur « en-US » et affichait « 2,400.00 DA » à côté de documents imprimés en
 * « 2 400,00 DA ». Le format suit désormais la langue de l'interface, comme les PDF.</p>
 *
 * @param value montant ; absent, il vaut zéro
 * @param lang  code de langue (« fr », « en ») ; inconnu, il est ramené à la locale par défaut
 */
export function formatAmount(value: number | null | undefined, lang?: string | null): string {
  const locale = resolveLocale(lang);
  let formatter = FORMATTERS.get(locale);
  if (!formatter) {
    formatter = new Intl.NumberFormat(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    FORMATTERS.set(locale, formatter);
  }
  return formatter.format(value ?? 0);
}

/**
 * `{{ montant | amount }}` : {@link formatAmount} dans la langue active de ngx-translate.
 *
 * <p>Impur, comme le pipe `translate` : un changement de langue doit reformater les montants déjà
 * affichés. Le formateur est mis en cache, le coût par détection de changements reste négligeable.</p>
 */
@Pipe({ name: 'amount', standalone: true, pure: false })
export class AmountPipe implements PipeTransform {

  constructor(private translate: TranslateService) {}

  transform(value: number | null | undefined): string {
    return formatAmount(value, this.translate.currentLang || this.translate.defaultLang);
  }
}
