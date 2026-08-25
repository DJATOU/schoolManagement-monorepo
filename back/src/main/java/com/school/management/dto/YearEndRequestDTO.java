package com.school.management.dto;

import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;
import java.util.List;

/**
 * DTO de requête du {@code Year_End_Workflow} : clôture de l'année courante et
 * ouverture de l'année suivante avec application des décisions par étudiant.
 * <ul>
 *   <li>{@code newLabel} : libellé de la nouvelle année (optionnel ; dérivé de
 *       l'année courante s'il est absent).</li>
 *   <li>{@code startDate}/{@code endDate} : dates de la nouvelle année
 *       (optionnelles ; dérivées de l'année courante si absentes).</li>
 *   <li>{@code decisions} : décisions par étudiant (optionnel ; PROMOTION par
 *       défaut pour les étudiants non listés).</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class YearEndRequestDTO {

    // Libellé de la nouvelle année scolaire (optionnel : dérivé sinon)
    private String newLabel;

    // Date de début de la nouvelle année (optionnelle : dérivée sinon)
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date startDate;

    // Date de fin de la nouvelle année (optionnelle : dérivée sinon)
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date endDate;

    // Décisions par étudiant (optionnel : PROMOTION par défaut)
    private List<StudentDecisionDTO> decisions;
}
