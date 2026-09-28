package com.school.management.dto;

import lombok.*;

import java.util.List;

/**
 * DTO d'aperçu du {@code Year_End_Workflow} : libellé de l'année suivante proposé
 * et décisions par défaut (PROMOTION) pour chaque étudiant actif, les étudiants au
 * niveau le plus élevé étant signalés pour revue.
 * <p>
 * Version minimale nécessaire au {@code YearEndWorkflowService.preview} (tâche 12.1) ;
 * pourra être affinée lors de la tâche 16.3.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class YearEndPreviewDTO {

    // Libellé proposé pour l'année scolaire suivante (dérivé de l'année courante)
    private String proposedNextLabel;

    // Décisions par défaut proposées par étudiant actif
    private List<StudentDecisionPreviewDTO> decisions;
}
