package com.school.management.persistance;

/** Domaine de la donnée corrigée, pour filtrer et rédiger le Journal. */
public enum CorrectionDomain {
    ENCASHMENT,
    ENROLMENT,
    ATTENDANCE,
    SESSION,
    /** Paie d'un enseignant (spec teacher-payroll, exigence 7) : aucun étudiant, la trace porte la série. */
    TEACHER_PAYOUT
}
