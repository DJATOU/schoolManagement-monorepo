package com.school.management.dto;

import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * DTO représentant une année scolaire.
 * <p>
 * Version minimale nécessaire au {@code YearEndWorkflowService} (tâche 12.1) ;
 * pourra être affinée lors de la tâche 16.1 (DTOs/mappers via {@code MappingContext}).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchoolYearDTO {

    // Identifiant de l'année scolaire
    private Long id;

    // Libellé unique de l'année scolaire (ex. "2025-2026")
    private String label;

    // Date de début de l'année scolaire
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date startDate;

    // Date de fin de l'année scolaire
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date endDate;

    // Indique si l'année scolaire est l'année courante
    private Boolean isCurrent;
}
