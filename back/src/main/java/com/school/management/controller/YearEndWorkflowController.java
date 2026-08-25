package com.school.management.controller;

import com.school.management.dto.YearEndPreviewDTO;
import com.school.management.dto.YearEndRequestDTO;
import com.school.management.dto.YearEndResultDTO;
import com.school.management.service.YearEndWorkflowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrôleur REST (mince) de l'assistant de fin d'année ({@code Year_End_Workflow}).
 *
 * <p>Toute la logique métier est déléguée à {@link YearEndWorkflowService} : clôture de
 * l'année courante, ouverture de l'année suivante et application des décisions par étudiant
 * (Exigences 5.1, 5.3, 8.2). Le service renvoie directement les DTO, le contrôleur se contente
 * de les exposer.</p>
 */
@RestController
@RequestMapping("/api/year-end")
public class YearEndWorkflowController {

    private final YearEndWorkflowService yearEndWorkflowService;

    @Autowired
    public YearEndWorkflowController(YearEndWorkflowService yearEndWorkflowService) {
        this.yearEndWorkflowService = yearEndWorkflowService;
    }

    /**
     * Exécute le workflow de fin d'année (Exigences 5.1, 5.3) : clôture l'année courante, ouvre
     * l'année suivante et applique la décision de chaque étudiant (PROMOTION par défaut).
     *
     * @param request la requête (libellé/dates optionnels, décisions par étudiant optionnelles).
     * @return le résultat : nouvelle année courante, liste de revue et nombre d'étudiants traités.
     */
    @PostMapping("/run")
    public ResponseEntity<YearEndResultDTO> run(@RequestBody YearEndRequestDTO request) {
        return ResponseEntity.ok(yearEndWorkflowService.run(request));
    }

    /**
     * Prépare un aperçu du workflow sans rien modifier (Exigence 8.2) : libellé de l'année
     * suivante proposé et décision par défaut (PROMOTION) par étudiant actif, les étudiants au
     * niveau le plus élevé étant signalés pour revue.
     *
     * @return l'aperçu (libellé proposé + décisions par défaut par étudiant actif).
     */
    @GetMapping("/preview")
    public ResponseEntity<YearEndPreviewDTO> preview() {
        return ResponseEntity.ok(yearEndWorkflowService.preview());
    }
}
