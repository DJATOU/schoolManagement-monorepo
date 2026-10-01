package com.school.management.controller;

import com.school.management.dto.payment.EncashmentDTO;
import com.school.management.service.payment.EncashmentQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Lecture des Encaissements : un reçu pour sa réimpression, et l'historique d'un élève (spec
 * admin-corrections, A.8 et A.9). Données financières : réservées au rôle ADMIN, lecture comprise
 * ({@code SecurityConfig}), comme les relevés de recettes.
 */
@RestController
@RequestMapping("/api")
public class EncashmentController {

    private final EncashmentQueryService encashmentQueryService;

    public EncashmentController(EncashmentQueryService encashmentQueryService) {
        this.encashmentQueryService = encashmentQueryService;
    }

    /** Un Encaissement et sa répartition : de quoi réimprimer son reçu à l'identique. */
    @GetMapping("/encashments/{id}")
    public ResponseEntity<EncashmentDTO> getEncashment(@PathVariable Long id) {
        return ResponseEntity.ok(encashmentQueryService.get(id));
    }

    /** Encaissements d'un élève, le plus récent d'abord, annulés compris. */
    @GetMapping("/students/{studentId}/encashments")
    public ResponseEntity<List<EncashmentDTO>> getStudentEncashments(@PathVariable Long studentId) {
        return ResponseEntity.ok(encashmentQueryService.forStudent(studentId));
    }
}
