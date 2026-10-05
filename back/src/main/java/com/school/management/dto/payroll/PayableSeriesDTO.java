package com.school.management.dto.payroll;

import java.math.BigDecimal;

/**
 * Une série de l'onglet « À payer » : terminée et pas encore payée, encore en cours, ou payée mais
 * dont l'encaissé a changé depuis (spec teacher-payroll, exigence 2).
 *
 * @param seriesId            série
 * @param seriesName          nom de la série
 * @param groupId             groupe
 * @param groupName           nom du groupe
 * @param teacherId           enseignant du groupe, nul s'il n'en a pas ; celui de la paie initiale
 *                            pour une série payée
 * @param teacherName         prénom et nom
 * @param state               ce qu'il y a à faire sur la série
 * @param activeSessions      séances actives
 * @param validatedSessions   séances actives validées
 * @param collectedGross      encaissé brut
 * @param refunded            remboursé
 * @param collectedNet        encaissé net : la base
 * @param initialPayoutNumber paie initiale, pour une série payée
 * @param teacherPercent      pourcentage figé de la paie initiale, pour une série payée
 * @param teacherPaid         parts enseignant déjà versées
 * @param gap                 écart à régulariser : positif complément, négatif retenue
 */
public record PayableSeriesDTO(
        Long seriesId,
        String seriesName,
        Long groupId,
        String groupName,
        Long teacherId,
        String teacherName,
        PayableState state,
        long activeSessions,
        long validatedSessions,
        BigDecimal collectedGross,
        BigDecimal refunded,
        BigDecimal collectedNet,
        String initialPayoutNumber,
        BigDecimal teacherPercent,
        BigDecimal teacherPaid,
        BigDecimal gap) {

    /** Ce que l'onglet « À payer » propose pour une série. */
    public enum PayableState {
        /** Terminée, enseignant connu, encaissé positif : payable. */
        PAYABLE,
        /** Des séances restent à valider. */
        NOT_FINISHED,
        /** Terminée, mais le groupe n'a pas d'enseignant. */
        NO_TEACHER,
        /** Terminée, mais rien n'a été encaissé : il n'y a rien à partager. */
        NOTHING_COLLECTED,
        /** Payée, mais l'encaissé a changé depuis : une régularisation est due. */
        TO_REGULARIZE
    }
}
