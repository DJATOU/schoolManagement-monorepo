package com.school.management.dto;

import com.school.management.service.PromotionDecision;
import lombok.*;

/**
 * DTO d'aperçu de la décision par défaut proposée pour un étudiant avant
 * l'exécution du {@code Year_End_Workflow}.
 * <p>
 * Version minimale nécessaire au {@code YearEndWorkflowService.preview} (tâche 12.1) ;
 * pourra être affinée lors de la tâche 16.3.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentDecisionPreviewDTO {

    // L'étudiant concerné
    private StudentDTO student;

    // Décision proposée par défaut (PROMOTION)
    private PromotionDecision decision;

    // true si l'étudiant est déjà au niveau le plus élevé (à revoir par l'administrateur)
    private boolean needsReview;
}
