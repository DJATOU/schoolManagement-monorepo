package com.school.management.dto;

import lombok.*;

import java.util.List;

/**
 * DTO de résultat du {@code Year_End_Workflow}.
 * <ul>
 *   <li>{@code newYear} : la nouvelle année scolaire créée et marquée courante.</li>
 *   <li>{@code reviewList} : étudiants au niveau le plus élevé à revoir par
 *       l'administrateur (Exigences 8.1, 8.2).</li>
 *   <li>{@code appliedCount} : nombre d'étudiants traités par le workflow.</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class YearEndResultDTO {

    // Nouvelle année scolaire créée et marquée courante
    private SchoolYearDTO newYear;

    // Étudiants au niveau le plus élevé à revoir par l'administrateur
    private List<StudentDTO> reviewList;

    // Nombre d'étudiants traités par le workflow
    private int appliedCount;
}
