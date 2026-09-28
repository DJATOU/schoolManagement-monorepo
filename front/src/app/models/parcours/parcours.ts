import { Group } from '../group/group';
import { Level } from '../level/level';

/**
 * Parcours d'un étudiant pour une année scolaire donnée : le ou les niveaux
 * suivis et les groupes fréquentés durant cette année.
 * Reflète le backend {@code ParcoursYearDTO}.
 */
export interface ParcoursYear {
  schoolYearId: number;
  schoolYearLabel: string;
  levels: Level[];
  groups: Group[];
}

/**
 * Parcours complet d'un étudiant : la liste des années scolaires (avec niveaux
 * et groupes) dans lesquelles il a été inscrit, triées par date de début d'année
 * décroissante. Reflète le backend {@code ParcoursDTO}.
 */
export interface Parcours {
  studentId: number;
  years: ParcoursYear[];
}
