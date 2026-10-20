import { Pipe, PipeTransform } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { CurrencyAmount, withCurrency } from '../shared/currency';

/**
 * `{{ montant | amount | appCurrency }}` : ajoute le suffixe du dinar dans la langue de
 * l'interface, « DA » en français et en anglais, « دج » en arabe.
 *
 * <p>Le pipe ne formate pas le nombre : il se place après le pipe qui le formate déjà (`amount`,
 * `number`…), dont le résultat reste inchangé. Pour un PDF, utiliser `pdfWithCurrency`, qui suit la
 * langue du document et non celle de l'écran.</p>
 *
 * <p>Impur, comme le pipe `translate` : un changement de langue doit changer le suffixe des montants
 * déjà affichés.</p>
 */
@Pipe({ name: 'appCurrency', standalone: true, pure: false })
export class CurrencySuffixPipe implements PipeTransform {
  constructor(private translate: TranslateService) {}

  transform(value: CurrencyAmount): string {
    return withCurrency(value, this.translate);
  }
}
