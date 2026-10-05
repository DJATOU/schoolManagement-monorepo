package com.school.management.controller;

import com.school.management.dto.payroll.PayRequest;
import com.school.management.dto.payroll.PayableSeriesDTO;
import com.school.management.dto.payroll.PayoutDTO;
import com.school.management.dto.payroll.PayoutListDTO;
import com.school.management.dto.payroll.PayoutPreviewDTO;
import com.school.management.dto.payroll.PayoutSlipDTO;
import com.school.management.persistance.PayoutStatus;
import com.school.management.service.payroll.PayoutSlipService;
import com.school.management.service.payroll.TeacherPayoutService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;

/**
 * Paie des enseignants (spec teacher-payroll, exigences 2 à 6 et 9). Contrôleur mince : tout le
 * calcul est dans {@link TeacherPayoutService}. Réservé au rôle ADMIN, lecture comprise
 * ({@code SecurityConfig}) : ce sont des pièces de caisse.
 */
@RestController
@RequestMapping("/api")
public class TeacherPayoutController {

    private final TeacherPayoutService payoutService;
    private final PayoutSlipService slipService;

    public TeacherPayoutController(TeacherPayoutService payoutService, PayoutSlipService slipService) {
        this.payoutService = payoutService;
        this.slipService = slipService;
    }

    /** Séries à payer, en cours, ou à régulariser ; avec {@code includePaid}, aussi les séries payées. */
    @GetMapping("/teacher-payouts/payable")
    public ResponseEntity<List<PayableSeriesDTO>> payable(@RequestParam(required = false) Long teacherId,
                                                          @RequestParam(required = false) Long groupId,
                                                          @RequestParam(defaultValue = "false") boolean includePaid) {
        return ResponseEntity.ok(payoutService.payable(teacherId, groupId, includePaid));
    }

    /** Paies versées selon les filtres, bornes de date incluses, avec leurs totaux. */
    @GetMapping("/teacher-payouts")
    public ResponseEntity<PayoutListDTO> search(
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) PayoutStatus status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date from,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date to) {
        return ResponseEntity.ok(payoutService.search(teacherId, groupId, status, from, to));
    }

    @GetMapping("/teacher-payouts/{id}")
    public ResponseEntity<PayoutDTO> get(@PathVariable Long id) {
        return ResponseEntity.ok(payoutService.get(id));
    }

    /** Paies d'un enseignant, pour sa fiche. */
    @GetMapping("/teachers/{teacherId}/payouts")
    public ResponseEntity<PayoutListDTO> forTeacher(@PathVariable Long teacherId) {
        return ResponseEntity.ok(payoutService.forTeacher(teacherId));
    }

    @PostMapping("/teacher-payouts/series/{seriesId}/pay/preview")
    public ResponseEntity<PayoutPreviewDTO> previewPay(@PathVariable Long seriesId, @RequestBody PayRequest request) {
        return ResponseEntity.ok(payoutService.previewPay(seriesId, request));
    }

    @PostMapping("/teacher-payouts/series/{seriesId}/pay/confirm")
    public ResponseEntity<PayoutDTO> confirmPay(@PathVariable Long seriesId, @RequestBody PayRequest request) {
        return new ResponseEntity<>(payoutService.confirmPay(seriesId, request), HttpStatus.CREATED);
    }

    @PostMapping("/teacher-payouts/series/{seriesId}/regularize/preview")
    public ResponseEntity<PayoutPreviewDTO> previewRegularize(@PathVariable Long seriesId) {
        return ResponseEntity.ok(payoutService.previewRegularize(seriesId));
    }

    @PostMapping("/teacher-payouts/series/{seriesId}/regularize/confirm")
    public ResponseEntity<PayoutDTO> confirmRegularize(@PathVariable Long seriesId, @RequestBody PayRequest request) {
        return new ResponseEntity<>(payoutService.confirmRegularize(seriesId, request), HttpStatus.CREATED);
    }

    /** Émet le bordereau d'une paie : POST, chaque impression est comptée (« DUPLICATA »). */
    @PostMapping("/teacher-payouts/{id}/slips")
    public ResponseEntity<PayoutSlipDTO> issueSlip(@PathVariable Long id) {
        return new ResponseEntity<>(slipService.issue(id), HttpStatus.CREATED);
    }
}
