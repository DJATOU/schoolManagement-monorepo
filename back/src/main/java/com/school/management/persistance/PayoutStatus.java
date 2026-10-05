package com.school.management.persistance;

/**
 * État d'une Paie d'enseignant. Une paie annulée reste lisible, avec sa date, son auteur et son
 * motif, mais ne compte plus dans les montants versés (spec teacher-payroll, exigence 7).
 */
public enum PayoutStatus {
    ACTIVE,
    CANCELLED
}
