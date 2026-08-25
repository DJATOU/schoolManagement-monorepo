/**
 * Encaissements d'un groupe.
 *
 * Quatre montants distincts, à ne pas confondre :
 * - `collected` : argent réellement entré en caisse, remboursements déduits ;
 * - `expected`  : coût total attendu, réductions appliquées ;
 * - `remaining` : reste à recouvrer, somme des manques **par étudiant** ;
 * - `overpaid`  : trop-perçu, somme des excédents **par étudiant**.
 *
 * `remaining` et `overpaid` sont agrégés individu par individu : un étudiant qui verse plus
 * que son dû ne doit pas effacer le retard d'un autre.
 */
export interface GroupRevenue {
  groupId: number;
  groupName: string;
  collected: number;
  refunded: number;
  expected: number;
  remaining: number;
  overpaid: number;
  series: SeriesRevenue[];
  months: MonthRevenue[];
  /** Part de l'encaissé qu'aucune série ne revendique (paiement sans série rattachée). */
  unassignedToSeries: number;
}

export interface SeriesRevenue {
  seriesId: number;
  seriesName: string;
  collected: number;
  refunded: number;
  expected: number;
  remaining: number;
  overpaid: number;
  sessions: SessionRevenue[];
}

export interface SessionRevenue {
  sessionId: number;
  sessionTitle: string;
  sessionDate?: string;
  collected: number;
}

/** Encaissements d'un mois civil, par date d'encaissement. */
export interface MonthRevenue {
  year: number;
  month: number;
  collected: number;
}
