package com.school.management.dto.revenue;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Encaissements imputés à une séance.
 *
 * @param sessionId    identifiant de la séance
 * @param sessionTitle titre de la séance
 * @param sessionDate  date de début de la séance
 * @param collected    montant encaissé pour cette séance (brut, hors remboursements :
 *                     un remboursement est rattaché à un paiement, pas à une séance)
 */
public record SessionRevenueDTO(
        Long sessionId,
        String sessionTitle,
        Date sessionDate,
        BigDecimal collected) {
}
