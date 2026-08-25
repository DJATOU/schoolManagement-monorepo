package com.school.management.dto.revenue;

import java.math.BigDecimal;

/**
 * Encaissements d'un mois civil, par date d'encaissement.
 *
 * @param year      année civile
 * @param month     mois (1 = janvier)
 * @param collected montant encaissé ce mois-là
 */
public record MonthRevenueDTO(int year, int month, BigDecimal collected) {
}
