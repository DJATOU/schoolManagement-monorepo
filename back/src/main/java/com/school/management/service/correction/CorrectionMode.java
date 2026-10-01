package com.school.management.service.correction;

/**
 * Mode d'exécution d'une correction (spec admin-corrections, D7).
 *
 * <p>Les deux modes passent par le même code : seule l'issue de la transaction diffère.</p>
 */
public enum CorrectionMode {

    /** La correction est exécutée, mesurée, puis la transaction est annulée : rien n'est écrit. */
    PREVIEW,

    /**
     * La correction est exécutée et mesurée de nouveau ; elle n'est validée que si l'Aperçu obtenu
     * est celui que l'administratrice a lu.
     */
    CONFIRM
}
