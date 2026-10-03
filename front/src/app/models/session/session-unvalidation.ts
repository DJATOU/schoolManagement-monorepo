/**
 * Issue d'une dévalidation confirmée : la séance est de nouveau à valider, ses lignes de présence
 * désactivées (spec admin-corrections, D.3 ; exigence 10.1). Contrat serveur : `SessionUnvalidation`.
 */
export interface SessionUnvalidation {
  sessionId: number;
  /** Lignes de présence désactivées, rattrapages accueillis compris. */
  removedLines: number;
}
