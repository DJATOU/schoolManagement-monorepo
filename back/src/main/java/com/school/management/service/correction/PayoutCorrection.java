package com.school.management.service.correction;

import com.school.management.dto.payroll.PayoutDTO;

/**
 * Résultat d'un remplacement de paie : l'originale, annulée, et celle qui la remplace (spec
 * teacher-payroll, exigence 7.2).
 */
public record PayoutCorrection(PayoutDTO original, PayoutDTO replacement) {
}
