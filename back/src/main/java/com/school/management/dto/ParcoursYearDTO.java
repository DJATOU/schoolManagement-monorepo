package com.school.management.dto;

import lombok.*;

import java.util.List;

/**
 * DTO représentant le parcours d'un étudiant pour une année scolaire donnée :
 * le ou les niveaux suivis et les groupes fréquentés durant cette année.
 * <p>
 * Version minimale nécessaire au {@code StudentParcoursService} (tâche 11.1) ;
 * pourra être affinée lors de la tâche 16.1 (DTOs/mappers).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParcoursYearDTO {

    // Identifiant de l'année scolaire
    private Long schoolYearId;

    // Libellé de l'année scolaire (ex. "2024-2025")
    private String schoolYearLabel;

    // Niveaux distincts suivis durant cette année scolaire (un seul ou plusieurs)
    private List<LevelDto> levels;

    // Groupes fréquentés durant cette année scolaire
    private List<GroupDTO> groups;
}
