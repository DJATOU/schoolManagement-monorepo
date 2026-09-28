package com.school.management.dto;

import com.school.management.service.PromotionDecision;
import lombok.*;

/**
 * DTO portant la décision de fin d'année pour un étudiant donné dans le cadre du
 * {@code Year_End_Workflow}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentDecisionDTO {

    // Identifiant de l'étudiant concerné
    private Long studentId;

    // Décision appliquée (PROMOTION par défaut si absente)
    private PromotionDecision decision;
}
