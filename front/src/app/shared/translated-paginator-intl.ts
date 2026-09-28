import { Injectable, OnDestroy } from '@angular/core';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { TranslateService } from '@ngx-translate/core';
import { Subscription, merge } from 'rxjs';

/**
 * Libellés du paginateur Angular Material traduits via ngx-translate.
 *
 * <p>Par défaut, Material affiche ses libellés en anglais (« Items per page », « Next page »,
 * « 1 – 5 of 30 »), y compris dans une interface française. Cette implémentation les reprend
 * depuis les clés {@code PAGINATOR.*} et se réactualise au changement de langue.</p>
 */
@Injectable()
export class TranslatedPaginatorIntl extends MatPaginatorIntl implements OnDestroy {

  private sub: Subscription;

  constructor(private translate: TranslateService) {
    super();
    this.applyLabels();
    // Les fichiers de traduction sont chargés en asynchrone : à la construction, les clés
    // peuvent ne pas être encore résolues. On se réabonne donc au chargement des
    // traductions et au changement de langue pour réappliquer les libellés.
    this.sub = merge(
      this.translate.onLangChange,
      this.translate.onTranslationChange,
      this.translate.onDefaultLangChange
    ).subscribe(() => this.applyLabels());
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  /** Libellé de plage « 1 – 5 sur 30 », avec le cas de la liste vide. */
  override getRangeLabel = (page: number, pageSize: number, length: number): string => {
    if (length === 0 || pageSize === 0) {
      return this.translate.instant('PAGINATOR.RANGE_EMPTY', { total: length });
    }
    const total = Math.max(length, 0);
    const start = page * pageSize;
    const end = start < total ? Math.min(start + pageSize, total) : start + pageSize;
    return this.translate.instant('PAGINATOR.RANGE', {
      start: start + 1,
      end,
      total
    });
  };

  private applyLabels(): void {
    this.itemsPerPageLabel = this.translate.instant('PAGINATOR.ITEMS_PER_PAGE');
    this.nextPageLabel = this.translate.instant('PAGINATOR.NEXT_PAGE');
    this.previousPageLabel = this.translate.instant('PAGINATOR.PREVIOUS_PAGE');
    this.firstPageLabel = this.translate.instant('PAGINATOR.FIRST_PAGE');
    this.lastPageLabel = this.translate.instant('PAGINATOR.LAST_PAGE');
    // Notifie les paginateurs déjà affichés pour qu'ils se rafraîchissent.
    this.changes.next();
  }
}
