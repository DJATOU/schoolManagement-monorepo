/**
 * Résultat d'une modification de justification d'absence.
 */
export interface JustificationUpdateResult {
  attendanceId: number;
  /** Valeur courante après traitement. */
  justified: boolean;
  /**
   * Faux lorsque la valeur demandée égalait déjà la valeur courante.
   *
   * <p>La demande est alors un succès sans écriture ni trace : la piste d'audit ne consigne que les
   * changements réels, sinon elle se remplirait de lignes sans information.</p>
   */
  changed: boolean;
}

/**
 * Entrée de la piste d'audit d'une justification.
 *
 * <p>Sert à répondre à une contestation de parent : qui a changé la justification, quand, et
 * pourquoi.</p>
 */
export interface JustificationAudit {
  id: number;
  attendanceId: number;
  /**
   * Valeur avant modification. Nulle lorsque la justification n'avait jamais été renseignée — ce qui
   * est distinct d'un « non » explicite.
   */
  oldValue: boolean | null;
  newValue: boolean;
  /** Auteur, ou `system` en l'absence d'utilisateur authentifié. */
  performedBy: string;
  performedAt: string;
  comment: string | null;
}
