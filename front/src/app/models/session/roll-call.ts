/**
 * Feuille_Appel d'une séance, servie par `GET /api/sessions/{id}/roll-call`.
 *
 * Les dates sont des jours `yyyy-MM-dd`, sans heure ni fuseau : le serveur a déjà décidé qui la
 * séance concerne, l'écran ne fait que les mettre en forme.
 */
export interface RollCall {
  sessionId: number;
  groupId: number | null;
  sessionDay: string;
  /** Étudiants attendus, par nom puis prénom. */
  students: RollCallStudent[];
  /** Étudiants du groupe hors fenêtre ce jour-là : ils expliquent une feuille vide. */
  notConcerned: NotConcernedStudent[];
}

export interface RollCallStudent {
  id: number;
  firstName: string;
  lastName: string;
  gender: string | null;
  arrival: string | null;
  /** Jour de départ si l'inscription est close : l'étudiant reste attendu jusqu'à ce jour inclus. */
  departure: string | null;
}

export interface NotConcernedStudent {
  id: number;
  firstName: string;
  lastName: string;
  windows: EnrolmentWindow[];
}

/** Fenêtre d'inscription : `departure` nul tant qu'elle est ouverte. */
export interface EnrolmentWindow {
  arrival: string | null;
  departure: string | null;
}
