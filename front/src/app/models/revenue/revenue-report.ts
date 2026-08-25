/** Axes d'agrégation du rapport de recettes. */
export type RevenueGroupBy = 'GROUP' | 'SERIES' | 'SESSION' | 'MONTH';

/** Une ligne du rapport, quel que soit l'axe. */
export interface RevenueRow {
  /** Identifiant de l'entité agrégée, nul pour l'axe mensuel. */
  key: number | null;
  label: string;
  subLabel?: string | null;
  collected: number;
  /** Part de la ligne dans le total encaissé, en pourcentage. */
  share: number;
}

/**
 * Rapport de recettes.
 *
 * Les lignes portent le montant brut. Les remboursements ne sont déduits qu'au total :
 * ils sont rattachés à un paiement, donc imputables à un groupe et à une série, mais pas
 * à une séance ni à un mois d'encaissement.
 */
export interface RevenueReport {
  groupBy: RevenueGroupBy;
  totalCollected: number;
  totalRefunded: number;
  totalNet: number;
  rows: RevenueRow[];
}

/** Filtres du rapport. */
export interface RevenueFilters {
  groupBy: RevenueGroupBy;
  groupId?: number | null;
  levelId?: number | null;
  seriesId?: number | null;
  schoolYearId?: number | null;
  dateFrom?: Date | null;
  dateTo?: Date | null;
}
