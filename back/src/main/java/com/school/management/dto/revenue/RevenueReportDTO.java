package com.school.management.dto.revenue;

import java.math.BigDecimal;
import java.util.List;

/**
 * Rapport de recettes agrégé sur un axe au choix.
 *
 * <p>Les lignes portent le montant <strong>brut</strong> encaissé. Les remboursements sont
 * déduits au niveau du total et non ligne à ligne : un remboursement est rattaché à un
 * paiement, donc imputable à un groupe et à une série, mais pas à une séance ni à un mois
 * d'encaissement. Les ventiler partout donnerait des chiffres faux sur deux axes sur
 * quatre ; on préfère l'annoncer explicitement.</p>
 *
 * @param groupBy        axe d'agrégation appliqué
 * @param totalCollected total encaissé brut
 * @param totalRefunded  total remboursé sur le même périmètre
 * @param totalNet       {@code totalCollected − totalRefunded}
 * @param rows           lignes agrégées, du montant le plus élevé au plus faible
 */
public record RevenueReportDTO(
        String groupBy,
        BigDecimal totalCollected,
        BigDecimal totalRefunded,
        BigDecimal totalNet,
        List<RevenueRowDTO> rows) {
}
