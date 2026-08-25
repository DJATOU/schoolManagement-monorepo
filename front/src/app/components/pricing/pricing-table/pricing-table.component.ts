import { Component } from '@angular/core';
import { ReusableDatatableComponent } from '../../shared/reusable-datatable/reusable-datatable.component';
import { Observable } from 'rxjs';
import { Pricing } from '../../../models/pricing/pricing';
import { PricingService } from '../../../services/pricing.service';
import { DatePipe } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';
import { DeleteCommand } from '../../shared/reusable-datatable/DeleteCommand';
import { parseJavaDate } from '../../../shared/java-date';
import { resolveLocale } from '../../../shared/locale';

@Component({
  selector: 'app-pricing-table',
  standalone: true,
  imports: [ReusableDatatableComponent],
  templateUrl: './pricing-table.component.html',
  styleUrl: './pricing-table.component.scss'
})
export class PricingTableComponent implements DeleteCommand {

  observable: Observable<any[]> = new Observable<any[]>();

  /**
   * Colonnes du tableau.
   *
   * <p>Construites dans le constructeur et non en initialiseur de champ : elles ont besoin
   * du service de traduction injecté. Elles sont reconstruites à chaque changement de
   * langue pour que les en-têtes suivent.</p>
   */
  columns: { columnDef: string; header: string; cell: (element: Pricing) => string }[] = [];

  datePipe: DatePipe;

  constructor(private pricingService: PricingService, private translate: TranslateService) {
    this.observable = pricingService.getPricings();
    // Locale active plutôt que 'en-US' figé : « 19 August 2026 » n'a pas sa place dans
    // l'interface française.
    this.datePipe = new DatePipe(resolveLocale(this.translate.currentLang));
    this.columns = this.buildColumns();

    this.translate.onLangChange.subscribe(event => {
      this.datePipe = new DatePipe(resolveLocale(event.lang));
      this.columns = this.buildColumns();
    });
  }

  private buildColumns(): { columnDef: string; header: string; cell: (element: Pricing) => string }[] {
    const label = (key: string) => this.translate.instant(`pricing.table.${key}`);

    return [
      {
        columnDef: 'id',
        header: label('id'),
        cell: (element: Pricing) => `${element.id ?? ''}`
      },
      {
        columnDef: 'price',
        header: label('price'),
        cell: (element: Pricing) => element.price != null ? `${element.price} DA` : ''
      },
      {
        columnDef: 'effectiveDate',
        header: label('effectiveDate'),
        cell: (element: Pricing) => this.convertDate(element.effectiveDate)
      },
      {
        columnDef: 'expirationDate',
        header: label('expirationDate'),
        cell: (element: Pricing) => this.convertDate(element.expirationDate)
      },
      {
        columnDef: 'description',
        header: label('description'),
        cell: (element: Pricing) => element.description ?? ''
      }
    ];
  }

  /**
   * Formate une date de tarif.
   *
   * <p>Une date absente rend une chaîne vide. L'implémentation précédente appelait
   * {@code date.toString()} sans garde : les tarifs sans date de validité levaient une
   * TypeError <strong>pendant le rendu du gabarit</strong>, ce qui interrompait la passe de
   * détection de changement — la première ligne restait tronquée et toutes les suivantes
   * apparaissaient vides.</p>
   */
  convertDate(date: unknown): string {
    const parsed = parseJavaDate(date);
    return parsed ? (this.datePipe.transform(parsed, 'dd MMMM yyyy') ?? '') : '';
  }

  disableItems(id_list: Number[]): Observable<boolean> {
    return this.pricingService.disablePricings(id_list);
  }
}
