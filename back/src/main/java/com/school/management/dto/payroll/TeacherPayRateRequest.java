package com.school.management.dto.payroll;

import java.math.BigDecimal;

/**
 * Création ou modification d'un taux du catalogue.
 *
 * @param label          libellé, 100 caractères au plus
 * @param teacherPercent part de l'enseignant, strictement entre 0 et 100, deux décimales au plus
 */
public record TeacherPayRateRequest(String label, BigDecimal teacherPercent) {
}
