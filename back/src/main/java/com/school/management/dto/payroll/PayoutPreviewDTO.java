package com.school.management.dto.payroll;

import com.school.management.persistance.PayoutKind;

import java.math.BigDecimal;

/**
 * Aperçu d'une paie avant confirmation : le calcul en clair et le jeton qui le scelle (spec
 * teacher-payroll, exigences 3.2 et 4.1).
 *
 * @param seriesId        série payée
 * @param seriesName      nom de la série
 * @param groupId         groupe de la série
 * @param groupName       nom du groupe
 * @param teacherId       enseignant payé
 * @param teacherName     prénom et nom
 * @param kind            paie initiale ou régularisation
 * @param rateId          taux choisi ; celui de la paie initiale pour une régularisation
 * @param rateLabel       libellé du taux
 * @param teacherPercent  pourcentage de l'enseignant
 * @param collectedGross  encaissé brut de la série
 * @param refunded        remboursé sur la série
 * @param collectedNet    encaissé net : la base
 * @param netCovered      encaissé net déjà couvert par les paies précédentes ; zéro pour une initiale
 * @param baseDelta       encaissé que cette paie partage
 * @param teacherAmount   part de l'enseignant ; négative pour une retenue
 * @param schoolAmount    part de l'école
 * @param teacherPaid     parts enseignant déjà versées sur la série
 * @param previewToken    empreinte de l'aperçu, à renvoyer à la confirmation
 */
public record PayoutPreviewDTO(
        Long seriesId,
        String seriesName,
        Long groupId,
        String groupName,
        Long teacherId,
        String teacherName,
        PayoutKind kind,
        Long rateId,
        String rateLabel,
        BigDecimal teacherPercent,
        BigDecimal collectedGross,
        BigDecimal refunded,
        BigDecimal collectedNet,
        BigDecimal netCovered,
        BigDecimal baseDelta,
        BigDecimal teacherAmount,
        BigDecimal schoolAmount,
        BigDecimal teacherPaid,
        String previewToken) {
}
