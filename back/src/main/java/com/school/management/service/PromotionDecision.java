package com.school.management.service;

/**
 * Décision de fin d'année appliquée à un étudiant lors du passage à l'année
 * scolaire suivante.
 *
 * <p>À la clôture d'une année, l'administrateur choisit pour chaque étudiant :
 * {@link #PROMOTION} vers le niveau supérieur (par défaut), {@link #REDOUBLEMENT}
 * pour conserver le même niveau, ou {@link #DEPARTURE} pour marquer un départ.</p>
 */
public enum PromotionDecision {

    /** Passage au niveau supérieur (choix par défaut). */
    PROMOTION,

    /** Redoublement : l'étudiant conserve le même niveau. */
    REDOUBLEMENT,

    /** Départ de l'étudiant : statut passé à INACTIVE. */
    DEPARTURE
}
