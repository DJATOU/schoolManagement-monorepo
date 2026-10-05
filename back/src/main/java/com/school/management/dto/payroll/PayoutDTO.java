package com.school.management.dto.payroll;

import com.school.management.persistance.CorrectionReasonType;
import com.school.management.persistance.PayoutKind;
import com.school.management.persistance.PayoutStatus;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Une paie enregistrée, telle que l'onglet « Paies versées » et la fiche enseignant la montrent.
 *
 * @param id                   identifiant
 * @param payoutNumber         {@code PAIE-AAAA-NNNN}
 * @param kind                 paie initiale ou régularisation
 * @param initialPayoutNumber  numéro de la paie initiale, pour une régularisation
 * @param teacherId            enseignant
 * @param teacherName          prénom et nom
 * @param groupId              groupe
 * @param groupName            nom du groupe
 * @param seriesId             série
 * @param seriesName           nom de la série
 * @param rateLabel            libellé du taux, copie figée
 * @param teacherPercent       pourcentage de l'enseignant, copie figée
 * @param collectedGross       encaissé brut couvert
 * @param refunded             remboursé
 * @param collectedNet         encaissé net couvert
 * @param baseDelta            encaissé partagé par cette paie
 * @param teacherAmount        part de l'enseignant ; négative pour une retenue
 * @param schoolAmount         part de l'école
 * @param note                 note libre
 * @param paidAt               date fixée par le serveur
 * @param paidBy               auteur
 * @param status               active ou annulée
 * @param cancelledAt          date d'annulation
 * @param cancelledBy          auteur de l'annulation
 * @param cancelReasonType     motif d'annulation
 * @param cancelReasonText     précision du motif
 * @param replacesNumber       numéro de la paie que celle-ci remplace
 * @param replacedByNumber     numéro de la paie qui remplace celle-ci
 */
public record PayoutDTO(
        Long id,
        String payoutNumber,
        PayoutKind kind,
        String initialPayoutNumber,
        Long teacherId,
        String teacherName,
        Long groupId,
        String groupName,
        Long seriesId,
        String seriesName,
        String rateLabel,
        BigDecimal teacherPercent,
        BigDecimal collectedGross,
        BigDecimal refunded,
        BigDecimal collectedNet,
        BigDecimal baseDelta,
        BigDecimal teacherAmount,
        BigDecimal schoolAmount,
        String note,
        Date paidAt,
        String paidBy,
        PayoutStatus status,
        Date cancelledAt,
        String cancelledBy,
        CorrectionReasonType cancelReasonType,
        String cancelReasonText,
        String replacesNumber,
        String replacedByNumber) {
}
