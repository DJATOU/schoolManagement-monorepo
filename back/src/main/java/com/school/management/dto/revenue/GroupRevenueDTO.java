package com.school.management.dto.revenue;

import java.math.BigDecimal;
import java.util.List;

/**
 * Encaissements d'un groupe.
 *
 * <p>Trois montants distincts, à ne jamais confondre (cf. business-rules.md, audit H5) :</p>
 * <ul>
 *   <li>{@code collected} : argent réellement entré en caisse, net des remboursements ;</li>
 *   <li>{@code expected} : coût total attendu ({@code monthTotalCost} de chaque étudiant
 *       inscrit, réductions appliquées) ;</li>
 *   <li>{@code remaining} : reste à recouvrer, somme des manques <strong>par étudiant</strong> ;</li>
 *   <li>{@code overpaid} : trop-perçu, somme des excédents <strong>par étudiant</strong>.</li>
 * </ul>
 *
 * <p>{@code remaining} et {@code overpaid} sont agrégés individu par individu, et non
 * déduits du solde global : sinon un étudiant qui verse trop compenserait le retard d'un
 * autre et le groupe paraîtrait soldé.</p>
 *
 * @param groupId        identifiant du groupe
 * @param groupName      nom du groupe
 * @param collected      encaissé net (remboursements déduits)
 * @param refunded       total remboursé
 * @param expected       total attendu, réductions appliquées
 * @param remaining      reste à encaisser, somme des manques individuels
 * @param overpaid       trop-perçu, somme des excédents individuels
 * @param series         ventilation par série (le « mois de cours »)
 * @param months         ventilation par mois civil d'encaissement
 * @param unassignedToSeries part de l'encaissé non rattachée à une série
 */
public record GroupRevenueDTO(
        Long groupId,
        String groupName,
        BigDecimal collected,
        BigDecimal refunded,
        BigDecimal expected,
        BigDecimal remaining,
        BigDecimal overpaid,
        List<SeriesRevenueDTO> series,
        List<MonthRevenueDTO> months,
        BigDecimal unassignedToSeries) {
}
