package com.school.management.controller.correction;

import com.school.management.dto.correction.CancelEncashmentRequest;
import com.school.management.dto.correction.CorrectEncashmentRequest;
import com.school.management.dto.correction.CorrectionResponse;
import com.school.management.dto.payment.EncashmentDTO;
import com.school.management.persistance.CorrectionReasonType;
import com.school.management.service.correction.CorrectionMode;
import com.school.management.service.correction.CorrectionReason;
import com.school.management.service.correction.EncashmentChanges;
import com.school.management.service.correction.EncashmentCorrection;
import com.school.management.service.correction.EncashmentCorrectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Annuler et corriger un Encaissement, en Aperçu puis en confirmation (spec admin-corrections,
 * exigences 2, 3 et 4, D7).
 *
 * <p>Contrôleur mince : chaque route traduit la requête et délègue à
 * {@link EncashmentCorrectionService}. Les {@code preview} sont des {@code POST} : ils exécutent une
 * écriture, annulée ; ils sont donc réservés au rôle ADMIN comme toute écriture sous
 * {@code /api/**} ({@code SecurityConfig}, exigence 11.7).</p>
 *
 * <p>Refus : 400 (Motif, données), 404 (Encaissement), 409 (déjà annulé, année close, plancher des
 * remboursements avec {@code blockingRefunds}, Aperçu périmé avec le nouvel Aperçu) — corps rendus
 * par {@code GlobalExceptionHandler}.</p>
 */
@RestController
@RequestMapping("/api/encashments")
public class EncashmentCorrectionController {

    private final EncashmentCorrectionService corrections;

    public EncashmentCorrectionController(EncashmentCorrectionService corrections) {
        this.corrections = corrections;
    }

    /** Motifs proposés pour annuler ou corriger un versement, dans l'ordre d'affichage (D10). */
    @GetMapping("/correction-reasons")
    public ResponseEntity<List<CorrectionReasonType>> reasons() {
        return ResponseEntity.ok(List.copyOf(EncashmentCorrectionService.ENCASHMENT_REASONS));
    }

    @PostMapping("/{id}/cancel/preview")
    public ResponseEntity<CorrectionResponse<EncashmentDTO>> previewCancel(
            @PathVariable Long id, @RequestBody(required = false) CancelEncashmentRequest request) {
        return ResponseEntity.ok(cancel(id, request, CorrectionMode.PREVIEW));
    }

    @PostMapping("/{id}/cancel/confirm")
    public ResponseEntity<CorrectionResponse<EncashmentDTO>> confirmCancel(
            @PathVariable Long id, @RequestBody(required = false) CancelEncashmentRequest request) {
        return ResponseEntity.ok(cancel(id, request, CorrectionMode.CONFIRM));
    }

    @PostMapping("/{id}/correct/preview")
    public ResponseEntity<CorrectionResponse<EncashmentCorrection>> previewCorrect(
            @PathVariable Long id, @RequestBody(required = false) CorrectEncashmentRequest request) {
        return ResponseEntity.ok(correct(id, request, CorrectionMode.PREVIEW));
    }

    @PostMapping("/{id}/correct/confirm")
    public ResponseEntity<CorrectionResponse<EncashmentCorrection>> confirmCorrect(
            @PathVariable Long id, @RequestBody(required = false) CorrectEncashmentRequest request) {
        return ResponseEntity.ok(correct(id, request, CorrectionMode.CONFIRM));
    }

    // ------------------------------------------------------------------

    private CorrectionResponse<EncashmentDTO> cancel(Long id, CancelEncashmentRequest request, CorrectionMode mode) {
        CancelEncashmentRequest body = request == null ? new CancelEncashmentRequest(null, null, null) : request;
        return CorrectionResponse.of(corrections.cancel(id, reason(body.reasonType(), body.reasonText()), mode,
                body.previewToken()));
    }

    private CorrectionResponse<EncashmentCorrection> correct(Long id, CorrectEncashmentRequest request,
                                                             CorrectionMode mode) {
        CorrectEncashmentRequest body = request == null
                ? new CorrectEncashmentRequest(null, null, null, null, null, null, null, null, null) : request;
        EncashmentChanges changes = new EncashmentChanges(body.amount(), body.studentId(), body.groupId(),
                body.targetSeriesId(), body.paymentMethod(), body.notes());
        return CorrectionResponse.of(corrections.correct(id, changes, reason(body.reasonType(), body.reasonText()),
                mode, body.previewToken()));
    }

    /** Motif de la requête ; un type absent ou inconnu est une erreur de saisie, en français. */
    private static CorrectionReason reason(String type, String text) {
        return CorrectionReason.parse(type, text);
    }
}
