package com.school.management.controller;

import com.school.management.dto.DashboardStatsDTO;
import com.school.management.service.DashboardStatsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardStatsService dashboardStatsService;

    public DashboardController(DashboardStatsService dashboardStatsService) {
        this.dashboardStatsService = dashboardStatsService;
    }

    /**
     * Statistiques du tableau de bord.
     * Paramètres optionnels : from / to (yyyy-MM-dd).
     * Par défaut : du 1er janvier de l'année courante à aujourd'hui.
     */
    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsDTO> getStats(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate to,
            @RequestParam(required = false) Long schoolYearId) {
        return ResponseEntity.ok(dashboardStatsService.getStats(from, to, schoolYearId));
    }
}
