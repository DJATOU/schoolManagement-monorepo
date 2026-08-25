/**
 * Portée d'application d'une réduction, alignée sur le backend (DiscountScope).
 * - GROUP   : s'applique à tous les groupes/séries/séances du groupe visé
 * - SERIES  : s'applique à une série précise
 * - SESSION : s'applique à une séance précise
 */
export type DiscountScope = 'GROUP' | 'SERIES' | 'SESSION';

/**
 * Réduction telle qu'exposée par le backend (DiscountResponseDTO).
 * Le taux (`rate`) est un décimal dans l'intervalle [0.00, 1.00] (ex. 0.5 = 50%).
 */
export interface Discount {
  id?: number;
  studentId: number;
  scope: DiscountScope;
  groupId?: number | null;
  seriesId?: number | null;
  sessionId?: number | null;
  rate: number;

  /** Nom complet de l'étudiant, résolu par le backend pour l'affichage. */
  studentName?: string | null;
  /** Libellé de la cible (groupe / série / séance), résolu par le backend. */
  targetName?: string | null;
}

/**
 * Requête de création d'une réduction (DiscountRequestDTO côté backend).
 */
export interface DiscountRequest {
  studentId: number;
  scope: DiscountScope;
  groupId?: number | null;
  seriesId?: number | null;
  sessionId?: number | null;
  rate: number;
}
