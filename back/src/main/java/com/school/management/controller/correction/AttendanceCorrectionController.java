package com.school.management.controller.correction;

import com.school.management.dto.correction.AttendanceCorrectionRequest;
import com.school.management.dto.correction.CorrectionResponse;
import com.school.management.persistance.CorrectionReasonType;
import com.school.management.service.correction.AttendanceCorrection;
import com.school.management.service.correction.AttendanceCorrectionService;
import com.school.management.service.correction.CorrectionMode;
import com.school.management.service.correction.CorrectionReason;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Corriger une présence sur une Séance validée, en Aperçu puis en confirmation (spec
 * admin-corrections, exigences 8 et 4, D7).
 *
 * <p>Contrôleur mince : chaque route traduit la requête et délègue à
 * {@link AttendanceCorrectionService}. Les {@code preview} sont des {@code POST}, réservés au rôle
 * ADMIN comme toute écriture sous {@code /api/**} (exigence 11.7).</p>
 */
@RestController
public class AttendanceCorrectionController {

    private final AttendanceCorrectionService corrections;

    public AttendanceCorrectionController(AttendanceCorrectionService corrections) {
        this.corrections = corrections;
    }

    /** Motifs proposés, dans l'ordre d'affichage (D10). */
    @GetMapping("/api/attendances/correction-reasons")
    public ResponseEntity<List<CorrectionReasonType>> reasons() {
        return ResponseEntity.ok(List.copyOf(AttendanceCorrectionService.REASONS));
    }

    @PostMapping("/api/attendances/{id}/correct/preview")
    public ResponseEntity<CorrectionResponse<AttendanceCorrection>> previewChange(
            @PathVariable Long id, @RequestBody(required = false) AttendanceCorrectionRequest request) {
        return ResponseEntity.ok(change(id, request, CorrectionMode.PREVIEW));
    }

    @PostMapping("/api/attendances/{id}/correct/confirm")
    public ResponseEntity<CorrectionResponse<AttendanceCorrection>> confirmChange(
            @PathVariable Long id, @RequestBody(required = false) AttendanceCorrectionRequest request) {
        return ResponseEntity.ok(change(id, request, CorrectionMode.CONFIRM));
    }

    @PostMapping("/api/attendances/{id}/remove/preview")
    public ResponseEntity<CorrectionResponse<AttendanceCorrection>> previewRemove(
            @PathVariable Long id, @RequestBody(required = false) AttendanceCorrectionRequest request) {
        return ResponseEntity.ok(remove(id, request, CorrectionMode.PREVIEW));
    }

    @PostMapping("/api/attendances/{id}/remove/confirm")
    public ResponseEntity<CorrectionResponse<AttendanceCorrection>> confirmRemove(
            @PathVariable Long id, @RequestBody(required = false) AttendanceCorrectionRequest request) {
        return ResponseEntity.ok(remove(id, request, CorrectionMode.CONFIRM));
    }

    @PostMapping("/api/sessions/{id}/attendances/add/preview")
    public ResponseEntity<CorrectionResponse<AttendanceCorrection>> previewAdd(
            @PathVariable Long id, @RequestBody(required = false) AttendanceCorrectionRequest request) {
        return ResponseEntity.ok(add(id, request, CorrectionMode.PREVIEW));
    }

    @PostMapping("/api/sessions/{id}/attendances/add/confirm")
    public ResponseEntity<CorrectionResponse<AttendanceCorrection>> confirmAdd(
            @PathVariable Long id, @RequestBody(required = false) AttendanceCorrectionRequest request) {
        return ResponseEntity.ok(add(id, request, CorrectionMode.CONFIRM));
    }

    // ------------------------------------------------------------------

    private CorrectionResponse<AttendanceCorrection> change(Long id, AttendanceCorrectionRequest request,
                                                            CorrectionMode mode) {
        AttendanceCorrectionRequest body = AttendanceCorrectionRequest.orEmpty(request);
        return CorrectionResponse.of(corrections.changePresence(id, body.present(), body.justified(),
                reason(body), mode, body.previewToken()));
    }

    private CorrectionResponse<AttendanceCorrection> remove(Long id, AttendanceCorrectionRequest request,
                                                            CorrectionMode mode) {
        AttendanceCorrectionRequest body = AttendanceCorrectionRequest.orEmpty(request);
        return CorrectionResponse.of(corrections.remove(id, reason(body), mode, body.previewToken()));
    }

    private CorrectionResponse<AttendanceCorrection> add(Long sessionId, AttendanceCorrectionRequest request,
                                                         CorrectionMode mode) {
        AttendanceCorrectionRequest body = AttendanceCorrectionRequest.orEmpty(request);
        return CorrectionResponse.of(corrections.add(sessionId, body.studentId(), body.present(), body.justified(),
                reason(body), mode, body.previewToken()));
    }

    private static CorrectionReason reason(AttendanceCorrectionRequest body) {
        return CorrectionReason.parse(body.reasonType(), body.reasonText());
    }
}
