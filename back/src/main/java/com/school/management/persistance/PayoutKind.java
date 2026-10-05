package com.school.management.persistance;

/** Nature d'une Paie d'enseignant (spec teacher-payroll). */
public enum PayoutKind {

    /** Première paie d'une série : partage tout l'Encaissé_Net de la série. */
    INITIAL,

    /**
     * Écart d'Encaissé_Net apparu après la paie initiale, au pourcentage de celle-ci : complément si
     * de l'argent est arrivé, retenue s'il a été rendu (exigence 6).
     */
    REGULARIZATION
}
