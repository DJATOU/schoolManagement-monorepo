package com.school.management.controller;

import com.school.management.dto.session.SessionDTO;
import com.school.management.dto.session.SessionSearchCriteriaDTO;
import com.school.management.mapper.SessionMapper;
import com.school.management.persistance.SessionEntity;
import com.school.management.service.AttendanceService;
import com.school.management.service.SessionService;
import com.school.management.service.exception.CustomServiceException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {
    private static final Logger logger = LoggerFactory.getLogger(SessionController.class);
    private final SessionService sessionService;


    private final SessionMapper sessionMapper;

    @Autowired
    public SessionController(SessionService sessionService, SessionMapper sessionMapper, AttendanceService attendanceService){
        this.sessionService = sessionService;
        this.sessionMapper = sessionMapper;
    }

    @GetMapping
    public ResponseEntity<List<SessionDTO>> getAllSessions() {
        logger.info("Getting all sessions");
        List<SessionEntity> sessions = sessionService.getAllSessions();
        List<SessionDTO> sessionDTOs = sessions.stream()
                .map(sessionMapper::sessionEntityToSessionDto)
                .toList();
        return ResponseEntity.ok(sessionDTOs);
    }

    @GetMapping("/detail")
    public ResponseEntity<List<SessionDTO>> getAllSessionsWithDetail() {
        logger.info("Getting all sessions");
        List<SessionEntity> sessions = sessionService.getAllSessionsWithDetail();
        List<SessionDTO> sessionDTOs = sessions.stream()
                .map(sessionMapper::sessionEntityToSessionDto)
                .toList();
        return ResponseEntity.ok(sessionDTOs);
    }


    @GetMapping("/{id}")
    public ResponseEntity<SessionDTO> getSessionById(@PathVariable Long id) {
        SessionEntity session = sessionService.getSessionById(id)
                .orElseThrow(() -> new CustomServiceException("Session not found for ID: " + id));
        SessionDTO sessionDTO = sessionMapper.sessionEntityToSessionDto(session);
        return ResponseEntity.ok(sessionDTO);
    }

    @PostMapping
    public ResponseEntity<SessionDTO> createSession(@Valid @RequestBody SessionDTO sessionDTO) {
        logger.info("Received request to create session: {}", sessionDTO);

        // PHASE 1 REFACTORING: Utilise MappingContext au lieu de ApplicationContextProvider
        // Convert DTO to entity
        SessionEntity sessionEntity = sessionMapper.sessionDtoToSessionEntity(sessionDTO, sessionService.getMappingContext());
        logger.debug("Mapped SessionDTO to SessionEntity: {}", sessionEntity);

        // Create session in the database
        SessionEntity createdSession = sessionService.createSession(sessionEntity);
        logger.debug("Session created in database: {}", createdSession);

        // Convert entity back to DTO
        SessionDTO createdSessionDTO = sessionMapper.sessionEntityToSessionDto(createdSession);
        logger.info("Returning created session DTO: {}", createdSessionDTO);

        return ResponseEntity.ok(createdSessionDTO);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSession(@PathVariable Long id) {
        sessionService.deleteSession(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SessionDTO> patchSession(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        logger.info("Received PATCH request to update session with ID: {}", id);
        logger.info("Updates received: {}", updates);

        try {
            SessionEntity updatedSession = sessionService.updateSession(id, updates);
            logger.info("Session successfully updated. Updated session details: {}", updatedSession);

            SessionDTO sessionDTO = sessionMapper.sessionEntityToSessionDto(updatedSession);
            logger.info("Mapped SessionEntity to SessionDTO: {}", sessionDTO);

            return ResponseEntity.ok(sessionDTO);
        } catch (Exception e) {
            logger.error("Error occurred while updating session with ID: {}. Error: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }



    @GetMapping("/search")
    public ResponseEntity<List<SessionDTO>> searchSessions(@RequestBody SessionSearchCriteriaDTO criteria) {
        List<SessionDTO> sessionDTOs = sessionService.findSessionsByCriteria(criteria)
                .stream()
                .map(sessionMapper::sessionEntityToSessionDto)
                .toList();
        return ResponseEntity.ok(sessionDTOs);
    }

    @PatchMapping("/{sessionId}/finish")
    public ResponseEntity<SessionDTO> markSessionAsFinished(@PathVariable Long sessionId) {
        SessionEntity updatedSession = sessionService.markSessionAsFinished(sessionId);
        return ResponseEntity.ok(sessionMapper.sessionEntityToSessionDto(updatedSession));
    }

    @PatchMapping("/{sessionId}/unfinish")
    public ResponseEntity<SessionDTO> markSessionAsUnfinished(@PathVariable Long sessionId) {
        SessionEntity updatedSession = sessionService.markSessionAsUnfinished(sessionId);
        return ResponseEntity.ok(sessionMapper.sessionEntityToSessionDto(updatedSession));
    }


    @GetMapping("/series/{sessionSeriesId}")
    public ResponseEntity<List<SessionDTO>> getSessionsBySeriesId(@PathVariable Long sessionSeriesId) {
        List<SessionDTO> sessions = sessionService.getSessionsBySeriesId(sessionSeriesId)
                .stream()
                .map(sessionMapper::sessionEntityToSessionDto)
                .toList();
        return ResponseEntity.ok(sessions);
    }


    @GetMapping("/range")
    public List<SessionDTO> getSessionsInDateRange(
            @RequestParam("groupId") Long groupId,
            @RequestParam("start") LocalDateTime start,
            @RequestParam("end") LocalDateTime end) {
        return sessionService.findByGroupIdAndSessionTimeStartBetween(groupId, start, end);
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<SessionDTO>> getSessionsInDateRangeOLD(
            @RequestParam Long groupId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        List<SessionEntity> sessions = sessionService.getSessionsByGroupIdAndDateRange(groupId, start, end);
        List<SessionDTO> sessionDTOs = sessions.stream()
                .map(sessionMapper::sessionEntityToSessionDto)
                .toList();
        return ResponseEntity.ok(sessionDTOs);
    }


    // ========== NOUVEAUX ENDPOINTS AJOUTÉS ==========

    /**
     * Désactive une session et tous ses éléments associés.
     *
     * IMPORTANT : Cet endpoint désactive :
     * - La session (session.active = false)
     * - Les attendances (attendance.active = false)
     * - Les PaymentDetails (paymentDetail.active = false)  ← CRITIQUE !
     *
     * UTILISATION DEPUIS LE FRONTEND :
     * PATCH /api/sessions/{sessionId}/deactivate
     *
     * POURQUOI UTILISER CET ENDPOINT :
     * Si vous utilisez simplement PATCH /api/sessions/{id} avec {active: false},
     * les PaymentDetails restent actifs et causent l'erreur de paiement.
     *
     * @param sessionId l'ID de la session à désactiver
     * @return 204 No Content
     */
    @PatchMapping("/{sessionId}/deactivate")
    public ResponseEntity<Void> deactivateSession(@PathVariable Long sessionId) {
        logger.info("Received request to deactivate session: {}", sessionId);
        sessionService.deactivateSession(sessionId);
        logger.info("Session {} successfully deactivated", sessionId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Réactive une session et tous ses éléments associés.
     *
     * Utilisez cet endpoint pour annuler une dévalidation.
     *
     * UTILISATION DEPUIS LE FRONTEND :
     * PATCH /api/sessions/{sessionId}/reactivate
     *
     * @param sessionId l'ID de la session à réactiver
     * @return 204 No Content
     */
    @PatchMapping("/{sessionId}/reactivate")
    public ResponseEntity<Void> reactivateSession(@PathVariable Long sessionId) {
        logger.info("Received request to reactivate session: {}", sessionId);
        sessionService.reactivateSession(sessionId);
        logger.info("Session {} successfully reactivated", sessionId);
        return ResponseEntity.noContent().build();
    }
}