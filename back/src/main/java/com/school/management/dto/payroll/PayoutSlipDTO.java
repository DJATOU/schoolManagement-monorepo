package com.school.management.dto.payroll;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * Données d'un bordereau de paie, et le rang de cette impression (spec teacher-payroll, exigence 5).
 *
 * @param payout        la paie, annulée comprise : son bordereau porte alors « ANNULÉE »
 * @param issuanceRank  1 pour l'original ; au-delà, le bordereau porte « DUPLICATA »
 * @param issuedAt      date de cette impression, {@code yyyy-MM-ddTHH:mm:ss} à l'heure de l'école :
 *                      sans format, {@code @EnableWebMvc} la sérialise en tableau, que le navigateur
 *                      ne sait pas lire, et le duplicata ne s'imprimait pas
 * @param issuedBy      auteur de cette impression
 * @param fileName      nom de fichier proposé, identique d'une impression à l'autre
 */
public record PayoutSlipDTO(PayoutDTO payout, int issuanceRank,
                            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
                            LocalDateTime issuedAt,
                            String issuedBy, String fileName) {
}
