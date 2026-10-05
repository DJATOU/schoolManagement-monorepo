package com.school.management.controller.correction;

import com.school.management.service.correction.CorrectionJournal;
import com.school.management.service.correction.CorrectionJournalService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Journal d'un élève (spec admin-corrections, exigence 12 ; D8).
 *
 * <p>Réservé au rôle ADMIN comme les versements d'un élève, dont il nomme les reçus et les montants
 * (règle dans {@code SecurityConfig}). Ouvert sur une année close (12.5).</p>
 */
@RestController
public class CorrectionJournalController {

    private final CorrectionJournalService journalService;

    public CorrectionJournalController(CorrectionJournalService journalService) {
        this.journalService = journalService;
    }

    /**
     * {@code GET /api/students/{id}/journal?from=2030-01-01&to=2030-01-31} : bornes incluses,
     * facultatives.
     */
    @GetMapping("/api/students/{id}/journal")
    public ResponseEntity<CorrectionJournal> journal(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate to) {
        return ResponseEntity.ok(journalService.journalOf(id, from, to));
    }
}
