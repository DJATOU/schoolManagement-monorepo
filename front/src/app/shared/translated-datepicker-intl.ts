import { Injectable, OnDestroy, Provider } from '@angular/core';
import { MatDatepickerIntl } from '@angular/material/datepicker';
import { TranslateService } from '@ngx-translate/core';
import { Subscription, merge } from 'rxjs';

/**
 * Libellés du sélecteur de date Angular Material traduits via ngx-translate.
 *
 * <p>Par défaut, Material nomme ses commandes en anglais (« Open calendar », « Previous month »,
 * « Choose month and year »), y compris dans une interface française : ce sont les libellés lus
 * par les lecteurs d'écran et les infobulles du calendrier. Cette implémentation les reprend
 * depuis les clés {@code DATEPICKER.*} et se réactualise au changement de langue, comme
 * {@link TranslatedPaginatorIntl}.</p>
 */
@Injectable()
export class TranslatedDatepickerIntl extends MatDatepickerIntl implements OnDestroy {
  private sub: Subscription;

  constructor(private translate: TranslateService) {
    super();
    this.applyLabels();
    // Les traductions sont chargées en asynchrone : on réapplique les libellés à leur chargement
    // et à chaque changement de langue.
    this.sub = merge(
      this.translate.onLangChange,
      this.translate.onTranslationChange,
      this.translate.onDefaultLangChange
    ).subscribe(() => this.applyLabels());
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  private applyLabels(): void {
    this.calendarLabel = this.translate.instant('DATEPICKER.CALENDAR');
    this.openCalendarLabel = this.translate.instant('DATEPICKER.OPEN_CALENDAR');
    this.closeCalendarLabel = this.translate.instant('DATEPICKER.CLOSE_CALENDAR');
    this.prevMonthLabel = this.translate.instant('DATEPICKER.PREV_MONTH');
    this.nextMonthLabel = this.translate.instant('DATEPICKER.NEXT_MONTH');
    this.prevYearLabel = this.translate.instant('DATEPICKER.PREV_YEAR');
    this.nextYearLabel = this.translate.instant('DATEPICKER.NEXT_YEAR');
    this.prevMultiYearLabel = this.translate.instant('DATEPICKER.PREV_MULTI_YEAR');
    this.nextMultiYearLabel = this.translate.instant('DATEPICKER.NEXT_MULTI_YEAR');
    this.switchToMonthViewLabel = this.translate.instant('DATEPICKER.SWITCH_TO_MONTH_VIEW');
    this.switchToMultiYearViewLabel = this.translate.instant('DATEPICKER.SWITCH_TO_MULTI_YEAR_VIEW');
    this.startDateLabel = this.translate.instant('DATEPICKER.START_DATE');
    this.endDateLabel = this.translate.instant('DATEPICKER.END_DATE');
    // Notifie les calendriers déjà affichés pour qu'ils se rafraîchissent.
    this.changes.next();
  }
}

/**
 * Fournisseur à déclarer dans les `providers` d'un composant autonome qui importe
 * `MatDatepickerModule`.
 *
 * <p>Ce module déclare lui-même `MatDatepickerIntl` : un composant autonome qui l'importe reçoit son
 * propre injecteur, où cette déclaration masque celle de `app.config.ts`. Sans ce fournisseur, ses
 * calendriers resteraient en anglais.</p>
 */
export function provideTranslatedDatepickerIntl(): Provider {
  return { provide: MatDatepickerIntl, useClass: TranslatedDatepickerIntl };
}
