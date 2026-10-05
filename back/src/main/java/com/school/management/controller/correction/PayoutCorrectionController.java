package com.school.management.controller.correction;

import com.school.management.dto.correction.CorrectionResponse;
import com.school.management.dto.payroll.CancelPayoutRequest;
import com.school.management.dto.payroll.PayoutDTO;
import com.school.management.dto.payroll.ReplacePayoutRequest;
import com.school.management.persistance.CorrectionReasonType;
import com.school.management.service.correction.CorrectionMode;
import com.school.management.service.correction.CorrectionReason;
import com.school.management.service.correction.PayoutCorrection;
import com.school.management.service.correction.PayoutCorrectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Corrections d'une paie d'enseignant : annuler, remplacer (spec teacher-payroll, exigence 7).
 * Contrôleur mince : les règles sont dans {@link PayoutCorrectionService}. Réservé au rôle ADMIN
 * ({@code SecurityConfig}). Un Aperçu périmé répond 409 {@code STALE_PREVIEW} avec le nouvel Aperçu.
 */
@RestController
@RequestMapping("/api/teacher-payouts")
public class PayoutCorrectionController {

    private final PayoutCorrectionService corrections;

    public PayoutCorrectionController(PayoutCorrectionService corrections) {
        this.corrections = corrections;
    }

    /** Motifs proposés pour corriger une paie. */
    @GetMapping("/correction-reasons")
    public ResponseEntity<List<CorrectionReasonType>> reasons() {
        return ResponseEntity.ok(List.copyOf(PayoutCorrectionService.PAYOUT_REASONS));
    }

    @PostMapping("/{id}/cancel/preview")
    public ResponseEntity<CorrectionResponse<PayoutDTO>> previewCancel(
            @PathVariable Long id, @RequestBody(required = false) CancelPayoutRequest request) {
        return ResponseEntity.ok(cancel(id, request, CorrectionMode.PREVIEW));
    }

    @PostMapping("/{id}/cancel/confirm")
    public ResponseEntity<CorrectionResponse<PayoutDTO>> confirmCancel(
            @PathVariable Long id, @RequestBody(required = false) CancelPayoutRequest request) {
        return ResponseEntity.ok(cancel(id, request, CorrectionMode.CONFIRM));
    }

    @PostMapping("/{id}/replace/preview")
    public ResponseEntity<CorrectionResponse<PayoutCorrection>> previewReplace(
            @PathVariable Long id, @RequestBody(required = false) ReplacePayoutRequest request) {
        return ResponseEntity.ok(replace(id, request, CorrectionMode.PREVIEW));
    }

    @PostMapping("/{id}/replace/confirm")
    public ResponseEntity<CorrectionResponse<PayoutCorrection>> confirmReplace(
            @PathVariable Long id, @RequestBody(required = false) ReplacePayoutRequest request) {
        return ResponseEntity.ok(replace(id, request, CorrectionMode.CONFIRM));
    }

    // ------------------------------------------------------------------

    private CorrectionResponse<PayoutDTO> cancel(Long id, CancelPayoutRequest request, CorrectionMode mode) {
        CancelPayoutRequest body = request == null ? new CancelPayoutRequest(null, null, null) : request;
        return CorrectionResponse.of(corrections.cancel(id,
                CorrectionReason.parse(body.reasonType(), body.reasonText()), mode, body.previewToken()));
    }

    private CorrectionResponse<PayoutCorrection> replace(Long id, ReplacePayoutRequest request, CorrectionMode mode) {
        ReplacePayoutRequest body = request == null ? new ReplacePayoutRequest(null, null, null, null, null) : request;
        return CorrectionResponse.of(corrections.replace(id, body.rateId(), body.note(),
                CorrectionReason.parse(body.reasonType(), body.reasonText()), mode, body.previewToken()));
    }
}
