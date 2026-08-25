package com.school.management.persistance;

/**
 * Statuts possibles d'une demande de rattrapage (catch-up).
 *
 * <p>Cycle de vie : une demande est créée à l'état {@link #PENDING}, peut être
 * planifiée ({@link #SCHEDULED}), puis complétée ({@link #COMPLETED}) lorsque
 * l'étudiant a effectué le rattrapage. Une demande peut être annulée
 * ({@link #CANCELLED}) à partir des états {@code PENDING} ou {@code SCHEDULED}.</p>
 */
public enum CatchUpStatus {

    /** Demande créée, en attente de planification. */
    PENDING,

    /** Demande planifiée sur une séance de rattrapage. */
    SCHEDULED,

    /** Rattrapage effectué et enregistré. */
    COMPLETED,

    /** Demande annulée. */
    CANCELLED
}
