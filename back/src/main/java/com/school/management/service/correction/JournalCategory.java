package com.school.management.service.correction;

/** Ce que corrige une entrée du Journal (exigence 12.1), pour l'afficher et la filtrer. */
public enum JournalCategory {
    /** Reçu annulé, remplacé, ou dont le mode et la note sont corrigés. */
    ENCASHMENT,
    /** Arrivée, départ, réouverture d'une inscription. */
    ENROLMENT,
    /** Présence changée, ajoutée ou retirée, sur une séance validée. */
    ATTENDANCE,
    /** Justification d'une absence. Sans effet sur le dû. */
    JUSTIFICATION,
    /** Rattrapage : séance manquée désignée, décision « déjà payée », présence de rattrapage retirée. */
    CATCH_UP
}
