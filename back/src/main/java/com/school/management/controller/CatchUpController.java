package com.school.management.controller;

import com.school.management.dto.CatchUpRequestDTO;
import com.school.management.dto.CatchUpResponseDTO;
import com.school.management.dto.StudentAbsenceDTO;
import com.school.management.dto.session.SessionDTO;
import com.school.management.mapper.CatchUpRequestMapper;
import com.school.management.mapper.SessionMapper;
import com.school.management.persistance.CatchUpRequestEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.service.CatchUpService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Contrôleur REST du workflow de rattrapage (catch-up).
 *
 * <p>Contrôleur volontairement mince : toute la logique métier (validations,
 * machine à états, effets de bord) est déléguée à {@link CatchUpService}. Les
 * endpoints correspondent exactement au service front {@code catch-up.service.ts} :</p>
 * <ul>
 *   <li>{@code POST   /api/catch-ups} — création d'une demande ;</li>
 *   <li>{@code GET    /api/catch-ups/pending} — demandes en attente ;</li>
 *   <li>{@code GET    /api/catch-ups/student/{studentId}} — demandes d'un étudiant ;</li>
 *   <li>{@code GET    /api/catch-ups/available-sessions} — séances compatibles ;</li>
 *   <li>{@code PATCH  /api/catch-ups/{requestId}/schedule} — planification ;</li>
 *   <li>{@code PATCH  /api/catch-ups/{requestId}/complete} — complétion ;</li>
 *   <li>{@code PATCH  /api/catch-ups/{requestId}/cancel} — annulation.</li>
 * </ul>
 *
 * <p>Les entités renvoyées par le service sont aplaties en DTO de réponse via
 * {@link CatchUpRequestMapper} ; les séances disponibles sont mappées via
 * {@link SessionMapper}. La gestion des erreurs (400 / 404 / 409, messages en
 * français) est assurée par {@code GlobalExceptionHandler} à partir des
 * {@code CustomServiceException} levées par le service.</p>
 */
@RestController
@RequestMapping("/api/catch-ups")
public class CatchUpController {

    private final CatchUpService catchUpService;
    private final CatchUpRequestMapper catchUpRequestMapper;
    private final SessionMapper sessionMapper;

    public CatchUpController(CatchUpService catchUpService,
                             CatchUpRequestMapper catchUpRequestMapper,
                             SessionMapper sessionMapper) {
        this.catchUpService = catchUpService;
        this.catchUpRequestMapper = catchUpRequestMapper;
        this.sessionMapper = sessionMapper;
    }

    /**
     * Crée une nouvelle demande de rattrapage (statut PENDING).
     *
     * @param request données de création
     * @return la demande créée (HTTP 201)
     */
    @PostMapping
    public ResponseEntity<CatchUpResponseDTO> createCatchUpRequest(@RequestBody CatchUpRequestDTO request) {
        CatchUpRequestEntity created = catchUpService.create(request);
        return new ResponseEntity<>(catchUpRequestMapper.toDto(created), HttpStatus.CREATED);
    }

    /**
     * Liste toutes les demandes de rattrapage (tous statuts confondus).
     *
     * @return la liste complète des demandes
     */
    @GetMapping
    public ResponseEntity<List<CatchUpResponseDTO>> getAllRequests() {
        // La liste renvoie des DTO déjà enrichis des libellés (résolus dans le service).
        return ResponseEntity.ok(catchUpService.getAllRequests());
    }

    /**
     * Liste les demandes en attente (statut PENDING).
     *
     * @return la liste des demandes en attente
     */
    @GetMapping("/pending")
    public ResponseEntity<List<CatchUpResponseDTO>> getPendingRequests() {
        List<CatchUpResponseDTO> requests = catchUpService.getPendingRequests().stream()
                .map(catchUpRequestMapper::toDto)
                .toList();
        return ResponseEntity.ok(requests);
    }

    /**
     * Liste les demandes rattachées à un étudiant.
     *
     * @param studentId identifiant de l'étudiant
     * @return la liste des demandes de l'étudiant
     */
    @GetMapping("/student/{studentId}")
    public ResponseEntity<List<CatchUpResponseDTO>> getRequestsByStudent(@PathVariable Long studentId) {
        List<CatchUpResponseDTO> requests = catchUpService.getRequestsByStudent(studentId).stream()
                .map(catchUpRequestMapper::toDto)
                .toList();
        return ResponseEntity.ok(requests);
    }

    /**
     * Liste les absences d'un étudiant éligibles à une demande de rattrapage.
     *
     * @param studentId identifiant de l'étudiant
     * @return la liste des absences éligibles
     */
    @GetMapping("/eligible-absences")
    public ResponseEntity<List<StudentAbsenceDTO>> getEligibleAbsences(@RequestParam Long studentId) {
        return ResponseEntity.ok(catchUpService.getEligibleAbsences(studentId));
    }

    /**
     * Liste les séances de rattrapage disponibles (compatibles) pour une séance manquée.
     *
     * @param studentId         identifiant de l'étudiant
     * @param originalSessionId identifiant de la séance manquée d'origine
     * @return la liste des séances compatibles
     */
    @GetMapping("/available-sessions")
    public ResponseEntity<List<SessionDTO>> getAvailableSessions(
            @RequestParam Long studentId,
            @RequestParam Long originalSessionId) {
        List<SessionEntity> sessions = catchUpService.getAvailableSessions(studentId, originalSessionId);
        List<SessionDTO> sessionDTOs = sessions.stream()
                .map(sessionMapper::sessionEntityToSessionDto)
                .toList();
        return ResponseEntity.ok(sessionDTOs);
    }

    /**
     * Planifie une demande PENDING sur une séance de rattrapage compatible.
     *
     * @param requestId identifiant de la demande
     * @param body      corps portant l'identifiant de la séance et du groupe de rattrapage
     * @return la demande planifiée (SCHEDULED)
     */
    @PatchMapping("/{requestId}/schedule")
    public ResponseEntity<CatchUpResponseDTO> scheduleCatchUp(
            @PathVariable Long requestId,
            @RequestBody ScheduleRequest body) {
        CatchUpRequestEntity scheduled = catchUpService.schedule(
                requestId, body.catchUpSessionId(), body.catchUpGroupId());
        return ResponseEntity.ok(catchUpRequestMapper.toDto(scheduled));
    }

    /**
     * Complète une demande SCHEDULED (création d'une présence de rattrapage).
     *
     * @param requestId identifiant de la demande
     * @return la demande complétée (COMPLETED)
     */
    @PatchMapping("/{requestId}/complete")
    public ResponseEntity<CatchUpResponseDTO> completeCatchUp(@PathVariable Long requestId) {
        CatchUpRequestEntity completed = catchUpService.complete(requestId);
        return ResponseEntity.ok(catchUpRequestMapper.toDto(completed));
    }

    /**
     * Annule une demande depuis les états PENDING ou SCHEDULED.
     *
     * @param requestId identifiant de la demande
     * @param body      corps portant le motif d'annulation (facultatif)
     * @return la demande annulée (CANCELLED)
     */
    @PatchMapping("/{requestId}/cancel")
    public ResponseEntity<CatchUpResponseDTO> cancelCatchUp(
            @PathVariable Long requestId,
            @RequestBody(required = false) CancelRequest body) {
        String reason = (body != null) ? body.reason() : null;
        CatchUpRequestEntity cancelled = catchUpService.cancel(requestId, reason);
        return ResponseEntity.ok(catchUpRequestMapper.toDto(cancelled));
    }

    /**
     * Corps de la requête de planification : identifiants de la séance et du groupe
     * de rattrapage (aligné sur {@code scheduleCatchUp} du front).
     */
    public record ScheduleRequest(Long catchUpSessionId, Long catchUpGroupId) {
    }

    /**
     * Corps de la requête d'annulation : motif facultatif (aligné sur
     * {@code cancelCatchUp} du front).
     */
    public record CancelRequest(String reason) {
    }
}
