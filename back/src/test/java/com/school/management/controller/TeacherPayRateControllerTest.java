package com.school.management.controller;

import com.school.management.dto.payroll.TeacherPayRateDTO;
import com.school.management.dto.payroll.TeacherPayRateRequest;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.payroll.TeacherPayRateService;
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

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Routage et contrat JSON du catalogue des taux. Les autorisations (ADMIN seul, lecture comprise)
 * sont vérifiées sur la configuration réelle par {@code WriteRoutesAuthorizationIntegrationTest}.
 */
@WebMvcTest(TeacherPayRateController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class TeacherPayRateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TeacherPayRateService rateService;

    // Filtre de sécurité auto-détecté par @WebMvcTest ; mocké pour satisfaire sa dépendance.
    @MockBean
    private JwtService jwtService;

    private static TeacherPayRateDTO standard(boolean active) {
        return new TeacherPayRateDTO(3L, "Standard", new BigDecimal("60.00"), new BigDecimal("40.00"), active);
    }

    private static final TeacherPayRateRequest REQUEST = new TeacherPayRateRequest("Standard", new BigDecimal("60"));
    private static final String BODY = "{\"label\":\"Standard\",\"teacherPercent\":60}";

    @Test
    @DisplayName("liste, 200 : libellé, deux parts, état")
    void lists() throws Exception {
        when(rateService.list()).thenReturn(List.of(standard(true)));

        mockMvc.perform(get("/api/teacher-pay-rates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].label").value("Standard"))
                .andExpect(jsonPath("$[0].teacherPercent").value(60.00))
                .andExpect(jsonPath("$[0].schoolPercent").value(40.00))
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    @DisplayName("création, 201 ; refus du service rendu avec son statut et son message")
    void creates() throws Exception {
        when(rateService.create(REQUEST)).thenReturn(standard(true));

        mockMvc.perform(post("/api/teacher-pay-rates").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("Standard"));

        when(rateService.create(REQUEST)).thenThrow(
                new CustomServiceException("Un taux actif porte déjà le libellé « Standard ».", HttpStatus.CONFLICT));
        mockMvc.perform(post("/api/teacher-pay-rates").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Un taux actif porte déjà le libellé « Standard »."));
    }

    @Test
    @DisplayName("modification, 200")
    void updates() throws Exception {
        when(rateService.update(3L, REQUEST)).thenReturn(standard(true));

        mockMvc.perform(put("/api/teacher-pay-rates/3").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3));
    }

    @Test
    @DisplayName("désactivation, 200 : le taux rendu n'est plus actif")
    void disables() throws Exception {
        when(rateService.disable(3L)).thenReturn(standard(false));

        mockMvc.perform(patch("/api/teacher-pay-rates/3/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }
}
