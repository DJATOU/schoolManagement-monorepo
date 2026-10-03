package com.school.management.service.correction;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

/**
 * Inscription telle qu'une correction de dates la laisse (spec admin-corrections, exigences 5, 6).
 *
 * @param enrolmentId l'inscription
 * @param arrival     Date_Inscription
 * @param departure   Date_Sortie, {@code null} si l'inscription est ouverte
 * @param active      vrai si l'inscription est ouverte
 */
public record EnrolmentCorrection(
        Long enrolmentId,
        Long studentId,
        Long groupId,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate arrival,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate departure,
        boolean active) {
}
