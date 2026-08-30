package com.school.management.persistance;

/**
 * Décision de facturation d'un rattrapage susceptible d'être corrigée, et donc auditée.
 *
 * <p>Les deux décisions vivent dans le même journal : relire la chronologie d'un rattrapage ne doit
 * pas obliger à fusionner deux historiques.</p>
 */
public enum CatchUpBillingAuditField {

    /** La séance manquée désignée par ce rattrapage. */
    MISSED_SESSION,

    /** La décision « la séance manquée était-elle déjà payée dans sa série d'origine ? ». */
    ALREADY_PAID
}
