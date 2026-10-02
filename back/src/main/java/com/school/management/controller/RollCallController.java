package com.school.management.controller;

import com.school.management.dto.session.RollCallDTO;
import com.school.management.service.session.RollCallService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Feuille_Appel d'une Séance (exigences 6.2, 7.1, 7.2).
 *
 * <p>Désignée par la Séance, et non par un groupe et une date venus du navigateur : le serveur
 * connaît le jour de la Séance dans le fuseau qui a écrit les dates d'inscription (D1).</p>
 */
@RestController
public class RollCallController {

    private final RollCallService rollCallService;

    public RollCallController(RollCallService rollCallService) {
        this.rollCallService = rollCallService;
    }

    @GetMapping("/api/sessions/{sessionId}/roll-call")
    public ResponseEntity<RollCallDTO> rollCall(@PathVariable Long sessionId) {
        return ResponseEntity.ok(rollCallService.rollCall(sessionId));
    }
}
