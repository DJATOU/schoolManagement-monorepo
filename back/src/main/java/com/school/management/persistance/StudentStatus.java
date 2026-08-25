package com.school.management.persistance;

/**
 * Statut d'un étudiant vis-à-vis de la scolarité.
 *
 * <p>Un étudiant est {@link #ACTIVE} tant qu'il est inscrit et suivi par l'école.
 * Lors d'un départ (fin d'année, désinscription), il passe à l'état
 * {@link #INACTIVE} : ses données historiques sont conservées mais il est exclu
 * des listes par défaut. Une réactivation le repasse à {@link #ACTIVE}.</p>
 */
public enum StudentStatus {

    /** Étudiant actif, inscrit et suivi. */
    ACTIVE,

    /** Étudiant inactif (départ / archivage), conservé pour l'historique. */
    INACTIVE
}
