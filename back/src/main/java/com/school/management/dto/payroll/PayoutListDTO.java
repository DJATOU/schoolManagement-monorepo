package com.school.management.dto.payroll;

import java.math.BigDecimal;
import java.util.List;

/**
 * Paies d'un filtre et leurs totaux (spec teacher-payroll, exigence 9.2). Les totaux ne comptent que
 * les paies actives : une paie annulée reste listée, mais n'a rien versé.
 *
 * @param payouts      paies, la plus récente d'abord
 * @param teacherTotal somme des parts enseignant des paies actives
 * @param schoolTotal  somme des parts école des paies actives
 */
public record PayoutListDTO(List<PayoutDTO> payouts, BigDecimal teacherTotal, BigDecimal schoolTotal) {
}
