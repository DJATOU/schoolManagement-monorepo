package com.school.management.dto.payroll;

import java.time.LocalDateTime;

/**
 * Données d'un bordereau de paie, et le rang de cette impression (spec teacher-payroll, exigence 5).
 *
 * @param payout        la paie, annulée comprise : son bordereau porte alors « ANNULÉE »
 * @param issuanceRank  1 pour l'original ; au-delà, le bordereau porte « DUPLICATA »
 * @param issuedAt      date de cette impression
 * @param issuedBy      auteur de cette impression
 * @param fileName      nom de fichier proposé, identique d'une impression à l'autre
 */
public record PayoutSlipDTO(PayoutDTO payout, int issuanceRank, LocalDateTime issuedAt, String issuedBy,
                            String fileName) {
}
