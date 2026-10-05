package com.school.management.dto.payroll;

import java.math.BigDecimal;

/**
 * Un taux du catalogue, tel que l'écran le montre : la part de l'enseignant et, à côté, celle de
 * l'école (spec teacher-payroll, exigence 1.4).
 *
 * @param id             identifiant
 * @param label          libellé, par exemple « Standard »
 * @param teacherPercent part de l'enseignant, en pourcentage
 * @param schoolPercent  part de l'école : {@code 100 − teacherPercent}
 * @param active         faux si le taux n'est plus proposé pour une nouvelle paie
 */
public record TeacherPayRateDTO(
        Long id,
        String label,
        BigDecimal teacherPercent,
        BigDecimal schoolPercent,
        boolean active) {
}
