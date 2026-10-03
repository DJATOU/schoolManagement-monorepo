package com.school.management.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

/**
 * Une inscription d'un étudiant à un groupe et sa Fenêtre_Inscription (spec admin-corrections,
 * exigences 5 et 6). Les dates sont des jours, sans heure ni fuseau (5.4, D1).
 *
 * @param id           l'inscription, que désignent les corrections {@code /api/enrolments/{id}/…}
 * @param schoolYearId année scolaire du groupe
 * @param arrival      Date_Inscription
 * @param departure    Date_Sortie, {@code null} tant que l'inscription est ouverte
 * @param active       vrai si l'inscription est ouverte
 */
public record EnrolmentDTO(
        Long id,
        Long studentId,
        Long groupId,
        String groupName,
        Long schoolYearId,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate arrival,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate departure,
        boolean active) {
}
