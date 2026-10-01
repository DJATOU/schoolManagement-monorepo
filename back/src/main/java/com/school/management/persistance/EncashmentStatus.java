package com.school.management.persistance;

/**
 * État d'un Encaissement.
 *
 * <p>Un encaissement n'est jamais supprimé : annulé, il reste enregistré et visible dans
 * l'historique, mais ne compte plus dans aucun montant (spec admin-corrections, exigence 2).</p>
 */
public enum EncashmentStatus {

    /** Compte dans les montants versés de l'étudiant. */
    ACTIVE,

    /** Neutralisé : ses imputations et sa ventilation ne comptent plus. */
    CANCELLED
}
