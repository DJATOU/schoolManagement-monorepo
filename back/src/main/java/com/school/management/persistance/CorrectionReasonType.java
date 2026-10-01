package com.school.management.persistance;

/**
 * Motif type d'une correction, choisi dans une liste (spec admin-corrections, exigence 11.1).
 *
 * <p>Une liste plutôt qu'un texte obligatoire : l'administratrice travaille seule, et un motif à
 * taper à chaque correction finit en « ok » ou en « . ». Seul {@link #OTHER} exige un texte, et la
 * base le vérifie.</p>
 */
public enum CorrectionReasonType {

    DATA_ENTRY_ERROR,
    DOCUMENT_RECEIVED,
    ARRIVAL_DATE_CORRECTED,
    STUDENT_LEFT,
    WRONG_STUDENT,
    WRONG_AMOUNT,

    /** Exige un texte libre, non vide. */
    OTHER
}
