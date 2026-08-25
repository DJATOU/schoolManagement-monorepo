package com.school.management.dto;

import lombok.*;

import java.util.List;

/**
 * DTO représentant le parcours complet d'un étudiant : la liste des années
 * scolaires (avec niveaux et groupes) dans lesquelles il a été inscrit,
 * triées par date de début d'année décroissante.
 * <p>
 * Version minimale nécessaire au {@code StudentParcoursService} (tâche 11.1) ;
 * pourra être affinée lors de la tâche 16.1 (DTOs/mappers).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParcoursDTO {

    // Identifiant de l'étudiant
    private Long studentId;

    // Parcours par année scolaire, trié par date de début décroissante
    private List<ParcoursYearDTO> years;
}
