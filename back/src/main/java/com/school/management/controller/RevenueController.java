package com.school.management.controller;

import com.school.management.dto.revenue.RevenueReportDTO;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.payment.RevenueReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.Locale;

/**
 * Rapport de recettes transversal.
 *
 * <p>Réservé au rôle ADMIN par la règle {@code /api/revenue/**} de {@code SecurityConfig} :
 * ce sont des données financières, y compris en lecture.</p>
 */
@RestController
@RequestMapping("/api/revenue")
public class RevenueController {

    private final RevenueReportService revenueReportService;

    public RevenueController(RevenueReportService revenueReportService) {
        this.revenueReportService = revenueReportService;
    }

    @GetMapping
    public ResponseEntity<RevenueReportDTO> getReport(
            @RequestParam(defaultValue = "GROUP") String groupBy,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Long levelId,
            @RequestParam(required = false) Long seriesId,
            @RequestParam(required = false) Long schoolYearId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateFrom,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateTo,
            @RequestParam(required = false, defaultValue = "fr") String lang) {

        RevenueReportService.GroupBy axis = parseAxis(groupBy);

        return ResponseEntity.ok(revenueReportService.getReport(
                axis, groupId, levelId, seriesId, schoolYearId, dateFrom, dateTo, Locale.of(lang)));
    }

    /** Axe validé côté serveur : une valeur inconnue est refusée, pas silencieusement ignorée. */
    private RevenueReportService.GroupBy parseAxis(String groupBy) {
        try {
            return RevenueReportService.GroupBy.valueOf(groupBy.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new CustomServiceException(
                    "Axe d'agrégation inconnu : " + groupBy, e, HttpStatus.BAD_REQUEST);
        }
    }
}
