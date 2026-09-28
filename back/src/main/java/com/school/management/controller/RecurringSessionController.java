package com.school.management.controller;

import com.school.management.dto.session.RecurringSessionRequestDTO;
import com.school.management.dto.session.RecurringSessionResultDTO;
import com.school.management.service.session.RecurringSessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Création de séances récurrentes (créneau fixe répété sur une période).
 *
 * <p>Deux opérations : une simulation qui ne touche pas la base, et la génération
 * effective. L'interface affiche le récapitulatif de la simulation avant de confirmer,
 * ce qui évite de créer cent séances sur une erreur de saisie de dates.</p>
 */
@RestController
@RequestMapping("/api/sessions/recurring")
public class RecurringSessionController {

    private final RecurringSessionService recurringSessionService;

    public RecurringSessionController(RecurringSessionService recurringSessionService) {
        this.recurringSessionService = recurringSessionService;
    }

    /** Simule la récurrence : nombre d'occurrences et conflits, sans rien enregistrer. */
    @PostMapping("/preview")
    public ResponseEntity<RecurringSessionResultDTO> preview(
            @Valid @RequestBody RecurringSessionRequestDTO request) {
        return ResponseEntity.ok(recurringSessionService.preview(request));
    }

    /** Crée les séances de la récurrence. */
    @PostMapping
    public ResponseEntity<RecurringSessionResultDTO> generate(
            @Valid @RequestBody RecurringSessionRequestDTO request) {
        return new ResponseEntity<>(recurringSessionService.generate(request), HttpStatus.CREATED);
    }
}
