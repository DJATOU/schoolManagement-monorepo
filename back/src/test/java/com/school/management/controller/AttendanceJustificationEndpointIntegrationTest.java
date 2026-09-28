package com.school.management.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.management.dto.JustificationAuditDTO;
import com.school.management.dto.JustificationUpdateRequest;
import com.school.management.dto.JustificationUpdateResult;
import com.school.management.mapper.AttendanceMapper;
import com.school.management.service.AttendanceJustificationService;
import com.school.management.service.AttendanceService;
import com.school.management.service.JustificationRetryTemplate;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.security.JwtService;
import com.school.management.util.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration web des points d'entrée de justification (exigences 4.1, 4.7, 4.9, 5.7).
 *
 * <p>Vérifie notamment que l'ancien {@code PUT /api/attendances/{id}} — qui réussissait sans rien
 * modifier — n'existe plus, et que le corps de la nouvelle requête est bien fermé à deux champs.</p>
 */
@WebMvcTest(AttendanceController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AttendanceJustificationEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AttendanceService attendanceService;

    @MockBean
    private AttendanceMapper attendanceMapper;

    @MockBean
    private AttendanceJustificationService attendanceJustificationService;

    @MockBean
    private JustificationRetryTemplate justificationRetryTemplate;

    @MockBean
    private JwtService jwtService;

    @Test
    @DisplayName("PATCH justification : 200 avec la valeur appliquée")
    void patchJustification_returnsAppliedValue() throws Exception {
        when(justificationRetryTemplate.updateJustification(anyLong(), anyBoolean(), any()))
                .thenReturn(new JustificationUpdateResult(42L, true, true));

        mockMvc.perform(patch("/api/attendances/{id}/justification", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new JustificationUpdateRequest(true, "Certificat remis"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendanceId").value(42))
                .andExpect(jsonPath("$.justified").value(true))
                .andExpect(jsonPath("$.changed").value(true));
    }

    @Test
    @DisplayName("PATCH justification sans valeur : 400, le service n'est pas appelé")
    void patchJustification_withoutValue_returns400() throws Exception {
        // La validation du corps s'applique avant le service : une valeur absente serait ambiguë
        // entre « passer à non justifié » et « ne rien changer ».
        mockMvc.perform(patch("/api/attendances/{id}/justification", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"sans valeur\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(justificationRetryTemplate);
    }

    @Test
    @DisplayName("PATCH justification avec commentaire trop long : 400")
    void patchJustification_commentTooLong_returns400() throws Exception {
        mockMvc.perform(patch("/api/attendances/{id}/justification", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new JustificationUpdateRequest(true, "x".repeat(501)))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(justificationRetryTemplate);
    }

    @Test
    @DisplayName("PATCH justification sur présence marquée présent : 400 du service")
    void patchJustification_onPresentAttendance_returns400() throws Exception {
        when(justificationRetryTemplate.updateJustification(anyLong(), anyBoolean(), any()))
                .thenThrow(new CustomServiceException(
                        "La justification ne s'applique qu'à une absence : cette présence est "
                                + "marquée présent.",
                        HttpStatus.BAD_REQUEST));

        mockMvc.perform(patch("/api/attendances/{id}/justification", 42L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new JustificationUpdateRequest(true, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET piste d'audit : entrées restituées")
    void getAuditTrail_returnsEntries() throws Exception {
        when(attendanceJustificationService.auditTrail(42L)).thenReturn(List.of(
                new JustificationAuditDTO(2L, 42L, false, true, "mme.martin",
                        LocalDateTime.of(2026, 3, 1, 10, 0), "Certificat remis")));

        mockMvc.perform(get("/api/attendances/{id}/justification-audit", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].oldValue").value(false))
                .andExpect(jsonPath("$[0].newValue").value(true))
                .andExpect(jsonPath("$[0].performedBy").value("mme.martin"))
                .andExpect(jsonPath("$[0].comment").value("Certificat remis"));
    }

    @Test
    @DisplayName("GET piste d'audit d'une présence jamais modifiée : tableau vide")
    void getAuditTrail_whenNeverModified_returnsEmptyArray() throws Exception {
        when(attendanceJustificationService.auditTrail(42L)).thenReturn(List.of());

        mockMvc.perform(get("/api/attendances/{id}/justification-audit", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("l'ancien PUT inerte n'existe plus (exigence 4.9)")
    void oldInertPutIsGone() throws Exception {
        // Il réussissait sans rien modifier, laissant croire à une correction enregistrée.
        mockMvc.perform(put("/api/attendances/{id}", 42L))
                .andExpect(status().is4xxClientError());
    }
}
