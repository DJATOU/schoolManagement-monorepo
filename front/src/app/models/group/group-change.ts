/**
 * Signalement d'un changement de groupe d'un étudiant (exigences 10.2, 10.3).
 *
 * Contrepartie de la décision d'unité de facturation : l'agrégation automatique entre groupes
 * sur un mois civil ayant été abandonnée, un étudiant qui quitte un groupe et en rejoint un
 * autre dans le même mois doit rester visible pour que l'administrateur ajuste sa facturation
 * à la main.
 *
 * Purement informatif : ce signalement n'altère aucun montant et ne conditionne aucun
 * enregistrement de versement (exigences 10.6, 10.7).
 */
export interface GroupChange {
  /** Année civile du changement. */
  year: number;
  /**
   * Mois civil du changement, de 1 à 12.
   *
   * Le serveur ne renvoie aucun libellé localisé : le mois est formaté côté client selon la
   * langue active.
   */
  month: number;
  /** Groupe quitté et ses séances suivies sur ce mois. */
  leftGroup: GroupChangeActivity;
  /** Groupe rejoint et ses séances suivies sur ce mois. */
  joinedGroup: GroupChangeActivity;
}

/**
 * Activité de l'étudiant dans l'un des deux groupes du signalement, sur le mois concerné.
 *
 * Le nom accompagne l'identifiant : « groupe 42 vers groupe 43 » obligerait l'administrateur à
 * relire la base pour comprendre le signalement.
 */
export interface GroupChangeActivity {
  groupId: number;
  groupName: string;
  /** Séances suivies (présent) dans ce groupe sur le mois. */
  attendedCount: number;
}
