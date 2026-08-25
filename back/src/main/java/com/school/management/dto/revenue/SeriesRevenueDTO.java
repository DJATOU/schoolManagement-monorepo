package com.school.management.dto.revenue;

import java.math.BigDecimal;
import java.util.List;

/**
 * Encaissements d'une série (le « mois de cours » du domaine).
 *
 * @param seriesId   identifiant de la série
 * @param seriesName nom de la série
 * @param collected  encaissé net pour cette série
 * @param refunded   remboursements imputés à cette série
 * @param expected   coût attendu de la série pour les étudiants inscrits
 * @param remaining  reste à recouvrer, somme des manques par étudiant
 * @param overpaid   trop-perçu, somme des excédents par étudiant
 * @param sessions   ventilation par séance
 */
public record SeriesRevenueDTO(
        Long seriesId,
        String seriesName,
        BigDecimal collected,
        BigDecimal refunded,
        BigDecimal expected,
        BigDecimal remaining,
        BigDecimal overpaid,
        List<SessionRevenueDTO> sessions) {
}
