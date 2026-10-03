package com.school.management.controller.correction;

import com.school.management.dto.correction.CorrectionResponse;
import com.school.management.dto.correction.EnrolmentCorrectionRequest;
import com.school.management.persistance.CorrectionReasonType;
import com.school.management.service.correction.CorrectionMode;
import com.school.management.service.correction.CorrectionReason;
import com.school.management.service.correction.EnrolmentCorrection;
import com.school.management.service.correction.EnrolmentCorrectionService;
import com.school.management.service.exception.CustomServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Corriger les dates d'une inscription, en Aperçu puis en confirmation (spec admin-corrections,
 * exigences 5, 6 et 4, D7).
 *
 * <p>Contrôleur mince : chaque route traduit la requête et délègue à
 * {@link EnrolmentCorrectionService}. Les {@code preview} sont des {@code POST}, réservés au rôle
 * ADMIN comme toute écriture sous {@code /api/**} (exigence 11.7).</p>
 */
@RestController
@RequestMapping("/api/enrolments")
public class EnrolmentCorrectionController {

    private final EnrolmentCorrectionService corrections;

    public EnrolmentCorrectionController(EnrolmentCorrectionService corrections) {
        this.corrections = corrections;
    }

    /** Motifs proposés par correction, dans l'ordre d'affichage (D10). */
    @GetMapping("/correction-reasons")
    public ResponseEntity<Map<String, List<CorrectionReasonType>>> reasons() {
        Map<String, List<CorrectionReasonType>> reasons = new LinkedHashMap<>();
        reasons.put("ARRIVAL", List.copyOf(EnrolmentCorrectionService.ARRIVAL_REASONS));
        reasons.put("DEPARTURE", List.copyOf(EnrolmentCorrectionService.DEPARTURE_REASONS));
        reasons.put("REOPEN", List.copyOf(EnrolmentCorrectionService.REOPEN_REASONS));
        return ResponseEntity.ok(reasons);
    }

    @PostMapping("/{id}/arrival/preview")
    public ResponseEntity<CorrectionResponse<EnrolmentCorrection>> previewArrival(
            @PathVariable Long id, @RequestBody(required = false) EnrolmentCorrectionRequest request) {
        return ResponseEntity.ok(arrival(id, request, CorrectionMode.PREVIEW));
    }

    @PostMapping("/{id}/arrival/confirm")
    public ResponseEntity<CorrectionResponse<EnrolmentCorrection>> confirmArrival(
            @PathVariable Long id, @RequestBody(required = false) EnrolmentCorrectionRequest request) {
        return ResponseEntity.ok(arrival(id, request, CorrectionMode.CONFIRM));
    }

    @PostMapping("/{id}/departure/preview")
    public ResponseEntity<CorrectionResponse<EnrolmentCorrection>> previewDeparture(
            @PathVariable Long id, @RequestBody(required = false) EnrolmentCorrectionRequest request) {
        return ResponseEntity.ok(departure(id, request, CorrectionMode.PREVIEW));
    }

    @PostMapping("/{id}/departure/confirm")
    public ResponseEntity<CorrectionResponse<EnrolmentCorrection>> confirmDeparture(
            @PathVariable Long id, @RequestBody(required = false) EnrolmentCorrectionRequest request) {
        return ResponseEntity.ok(departure(id, request, CorrectionMode.CONFIRM));
    }

    @PostMapping("/{id}/reopen/preview")
    public ResponseEntity<CorrectionResponse<EnrolmentCorrection>> previewReopen(
            @PathVariable Long id, @RequestBody(required = false) EnrolmentCorrectionRequest request) {
        return ResponseEntity.ok(reopen(id, request, CorrectionMode.PREVIEW));
    }

    @PostMapping("/{id}/reopen/confirm")
    public ResponseEntity<CorrectionResponse<EnrolmentCorrection>> confirmReopen(
            @PathVariable Long id, @RequestBody(required = false) EnrolmentCorrectionRequest request) {
        return ResponseEntity.ok(reopen(id, request, CorrectionMode.CONFIRM));
    }

    // ------------------------------------------------------------------

    private CorrectionResponse<EnrolmentCorrection> arrival(Long id, EnrolmentCorrectionRequest request,
                                                            CorrectionMode mode) {
        EnrolmentCorrectionRequest body = orEmpty(request);
        return CorrectionResponse.of(corrections.correctArrival(id, body.arrival(), marks(body),
                CorrectionReason.parse(body.reasonType(), body.reasonText()), mode, body.previewToken()));
    }

    private CorrectionResponse<EnrolmentCorrection> departure(Long id, EnrolmentCorrectionRequest request,
                                                              CorrectionMode mode) {
        EnrolmentCorrectionRequest body = orEmpty(request);
        return CorrectionResponse.of(corrections.setDeparture(id, body.departure(),
                Boolean.TRUE.equals(body.removePresencesAfter()), marks(body),
                CorrectionReason.parse(body.reasonType(), body.reasonText()), mode, body.previewToken()));
    }

    private CorrectionResponse<EnrolmentCorrection> reopen(Long id, EnrolmentCorrectionRequest request,
                                                           CorrectionMode mode) {
        EnrolmentCorrectionRequest body = orEmpty(request);
        return CorrectionResponse.of(corrections.reopen(id, marks(body),
                CorrectionReason.parse(body.reasonType(), body.reasonText()), mode, body.previewToken()));
    }

    private static EnrolmentCorrectionRequest orEmpty(EnrolmentCorrectionRequest request) {
        return request == null ? new EnrolmentCorrectionRequest(null, null, null, null, null, null, null) : request;
    }

    /** Présences à noter, une par séance ; une séance nommée deux fois ou sans valeur est refusée. */
    private static Map<Long, Boolean> marks(EnrolmentCorrectionRequest body) {
        Map<Long, Boolean> marks = new LinkedHashMap<>();
        if (body.attendances() == null) {
            return marks;
        }
        for (EnrolmentCorrectionRequest.AttendanceMark mark : body.attendances()) {
            if (mark == null || mark.sessionId() == null || mark.present() == null) {
                throw new CustomServiceException(
                        "Chaque présence à noter nomme une séance et dit présent ou absent.", HttpStatus.BAD_REQUEST);
            }
            if (marks.put(mark.sessionId(), mark.present()) != null) {
                throw new CustomServiceException("La séance " + mark.sessionId()
                        + " est notée deux fois : une seule présence par séance.", HttpStatus.BAD_REQUEST);
            }
        }
        return marks;
    }
}
