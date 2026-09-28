package com.school.management.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.management.dto.CatchUpRequestDTO;
import com.school.management.mapper.CatchUpRequestMapperImpl;
import com.school.management.mapper.SessionMapperImpl;
import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.CatchUpRequestEntity;
import com.school.management.persistance.CatchUpStatus;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.service.CatchUpService;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.util.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.school.management.service.security.JwtService;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration web (Spring MVC) du {@link CatchUpController}.
 *
 * <p>Approche {@code @WebMvcTest} : seule la couche web est chargée, le
 * {@link CatchUpService} est simulé ({@code @MockBean}). Les mappers réels
 * ({@link CatchUpRequestMapperImpl}, {@link SessionMapperImpl}) sont importés pour vérifier
 * l'aplatissement des relations et les noms de champs JSON attendus par le front
 * ({@code studentId}, {@code originalSessionId}, {@code catchUpSessionId}, etc.). Le
 * {@link GlobalExceptionHandler} est importé pour exercer le mapping des
 * {@link CustomServiceException} vers 400 / 404 / 409 avec messages français. Les filtres de
 * sécurité sont désactivés ({@code addFilters = false}) pour cibler le routage et le câblage.</p>
 */
@WebMvcTest(CatchUpController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, CatchUpRequestMapperImpl.class, SessionMapperImpl.class})
class CatchUpControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Filtre de sécurité auto-détecté par @WebMvcTest ; mocké pour satisfaire sa dépendance.
    @org.springframework.boot.test.mock.mockito.MockBean
    private JwtService jwtService;

    @MockBean
    private CatchUpService catchUpService;

    // ------------------------------------------------------------------
    // Fabriques d'entités de test
    // ------------------------------------------------------------------

    private CatchUpRequestEntity pendingRequest() {
        StudentEntity student = new StudentEntity();
        student.setId(7L);
        SessionEntity originalSession = SessionEntity.builder().id(21L).build();
        GroupEntity originalGroup = GroupEntity.builder().id(31L).build();
        AttendanceEntity originalAttendance = AttendanceEntity.builder().id(11L).build();

        return CatchUpRequestEntity.builder()
                .id(51L)
                .student(student)
                .originalSession(originalSession)
                .originalGroup(originalGroup)
                .originalAttendance(originalAttendance)
                .status(CatchUpStatus.PENDING)
                .requestDate(new Date())
                .notes("demande initiale")
                .build();
    }

    private CatchUpRequestEntity scheduledRequest() {
        CatchUpRequestEntity request = pendingRequest();
        request.setStatus(CatchUpStatus.SCHEDULED);
        request.setCatchUpSession(SessionEntity.builder().id(61L).build());
        request.setCatchUpGroup(GroupEntity.builder().id(71L).build());
        request.setScheduledDate(new Date());
        return request;
    }

    // ------------------------------------------------------------------
    // POST /api/catch-ups (create)
    // ------------------------------------------------------------------

    @Test
    void createCatchUpRequest_returns201WithFrontFieldNames() throws Exception {
        when(catchUpService.create(any(CatchUpRequestDTO.class))).thenReturn(pendingRequest());

        CatchUpRequestDTO body = new CatchUpRequestDTO(7L, 21L, 31L, 11L, "demande initiale");

        mockMvc.perform(post("/api/catch-ups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(51))
                .andExpect(jsonPath("$.studentId").value(7))
                .andExpect(jsonPath("$.originalSessionId").value(21))
                .andExpect(jsonPath("$.originalGroupId").value(31))
                .andExpect(jsonPath("$.originalAttendanceId").value(11))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createCatchUpRequest_whenRightRevoked_returns400WithFrenchMessage() throws Exception {
        when(catchUpService.create(any(CatchUpRequestDTO.class)))
                .thenThrow(new CustomServiceException(
                        "Le droit au rattrapage a été révoqué pour cette absence.",
                        HttpStatus.BAD_REQUEST));

        CatchUpRequestDTO body = new CatchUpRequestDTO(7L, 21L, 31L, 11L, null);

        mockMvc.perform(post("/api/catch-ups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Le droit au rattrapage a été révoqué pour cette absence."));
    }

    // ------------------------------------------------------------------
    // GET /api/catch-ups/pending
    // ------------------------------------------------------------------

    @Test
    void getPendingRequests_returnsList() throws Exception {
        when(catchUpService.getPendingRequests()).thenReturn(List.of(pendingRequest()));

        mockMvc.perform(get("/api/catch-ups/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(51))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    // ------------------------------------------------------------------
    // GET /api/catch-ups/student/{studentId}
    // ------------------------------------------------------------------

    @Test
    void getRequestsByStudent_returnsList() throws Exception {
        when(catchUpService.getRequestsByStudent(7L)).thenReturn(List.of(pendingRequest()));

        mockMvc.perform(get("/api/catch-ups/student/{studentId}", 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentId").value(7));
    }

    // ------------------------------------------------------------------
    // GET /api/catch-ups/available-sessions
    // ------------------------------------------------------------------

    @Test
    void getAvailableSessions_returnsSessionDtos() throws Exception {
        SessionEntity session = SessionEntity.builder()
                .id(61L)
                .title("Rattrapage maths")
                .group(GroupEntity.builder().id(71L).build())
                .build();
        when(catchUpService.getAvailableSessions(7L, 21L)).thenReturn(List.of(session));

        mockMvc.perform(get("/api/catch-ups/available-sessions")
                        .param("studentId", "7")
                        .param("originalSessionId", "21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(61))
                .andExpect(jsonPath("$[0].groupId").value(71));
    }

    @Test
    void getAvailableSessions_whenOriginalSessionMissing_returns404() throws Exception {
        when(catchUpService.getAvailableSessions(eq(7L), eq(999L)))
                .thenThrow(new CustomServiceException(
                        "Séance introuvable pour l'identifiant : 999",
                        HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/catch-ups/available-sessions")
                        .param("studentId", "7")
                        .param("originalSessionId", "999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Séance introuvable pour l'identifiant : 999"));
    }

    // ------------------------------------------------------------------
    // PATCH /api/catch-ups/{requestId}/schedule
    // ------------------------------------------------------------------

    @Test
    void scheduleCatchUp_returnsScheduledRequest() throws Exception {
        when(catchUpService.schedule(51L, 61L, 71L)).thenReturn(scheduledRequest());

        String body = objectMapper.writeValueAsString(
                new CatchUpController.ScheduleRequest(61L, 71L));

        mockMvc.perform(patch("/api/catch-ups/{requestId}/schedule", 51L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.catchUpSessionId").value(61))
                .andExpect(jsonPath("$.catchUpGroupId").value(71));
    }

    @Test
    void scheduleCatchUp_whenNotPending_returns409WithFrenchMessage() throws Exception {
        when(catchUpService.schedule(51L, 61L, 71L))
                .thenThrow(new CustomServiceException(
                        "Transition impossible : seule une demande en attente (PENDING) peut être planifiée.",
                        HttpStatus.CONFLICT));

        String body = objectMapper.writeValueAsString(
                new CatchUpController.ScheduleRequest(61L, 71L));

        mockMvc.perform(patch("/api/catch-ups/{requestId}/schedule", 51L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Transition impossible : seule une demande en attente (PENDING) peut être planifiée."));
    }

    // ------------------------------------------------------------------
    // PATCH /api/catch-ups/{requestId}/complete
    // ------------------------------------------------------------------

    @Test
    void completeCatchUp_returnsCompletedRequest() throws Exception {
        CatchUpRequestEntity completed = scheduledRequest();
        completed.setStatus(CatchUpStatus.COMPLETED);
        completed.setCompletedDate(new Date());
        when(catchUpService.complete(51L)).thenReturn(completed);

        mockMvc.perform(patch("/api/catch-ups/{requestId}/complete", 51L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    // ------------------------------------------------------------------
    // PATCH /api/catch-ups/{requestId}/cancel
    // ------------------------------------------------------------------

    @Test
    void cancelCatchUp_withReason_returnsCancelledRequest() throws Exception {
        CatchUpRequestEntity cancelled = pendingRequest();
        cancelled.setStatus(CatchUpStatus.CANCELLED);
        cancelled.setCancellationReason("annulé par l'admin");
        when(catchUpService.cancel(51L, "annulé par l'admin")).thenReturn(cancelled);

        mockMvc.perform(patch("/api/catch-ups/{requestId}/cancel", 51L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CatchUpController.CancelRequest("annulé par l'admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("annulé par l'admin"));
    }

    @Test
    void cancelCatchUp_whenAlreadyCompleted_returns409() throws Exception {
        when(catchUpService.cancel(eq(51L), any()))
                .thenThrow(new CustomServiceException(
                        "Transition impossible : une demande complétée ou déjà annulée ne peut pas être annulée.",
                        HttpStatus.CONFLICT));

        mockMvc.perform(patch("/api/catch-ups/{requestId}/cancel", 51L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CatchUpController.CancelRequest("trop tard"))))
                .andExpect(status().isConflict());
    }
}
