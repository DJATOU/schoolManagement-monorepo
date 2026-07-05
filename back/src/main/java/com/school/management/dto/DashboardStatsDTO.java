package com.school.management.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * Statistiques agrégées du tableau de bord, calculées sur une période donnée.
 */
@Data
@Builder
public class DashboardStatsDTO {

    // Période couverte
    private LocalDate from;
    private LocalDate to;

    // Étudiants
    private long totalStudents;        // actifs
    private long newStudentsInPeriod;  // inscrits dans la période
    private long leavingStudents;      // désactivés (sortants, active = false)
    private long maleStudents;
    private long femaleStudents;

    // Effectifs généraux
    private long totalTeachers;
    private long totalGroups;

    // Sessions (sur la période, par statut)
    private long sessionsValidated;    // terminées (isFinished = true, actives)
    private long sessionsScheduled;    // programmées (non terminées, actives)
    private long sessionsDeactivated;  // dévalidées (active = false)
    private long catchUpSessions;      // séances de rattrapage

    // Présences (sur la période)
    private long presentCount;
    private long justifiedAbsences;
    private long unjustifiedAbsences;
}
