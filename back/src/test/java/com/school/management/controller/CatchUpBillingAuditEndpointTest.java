package com.school.management.controller;

import com.school.management.dto.catchup.CatchUpBillingAuditDTO;
import com.school.management.service.CatchUpBillingResolutionService;
import com.school.management.service.security.JwtService;
import com.school.management.util.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrat JSON de la piste d'audit d'un rattrapage : l'horodatage part en chaîne, que le pipe
 * {@code date} d'Angular accepte. Sérialisé en tableau par la configuration {@code @EnableWebMvc},
 * il faisait échouer l'affichage de l'historique.
 */
@WebMvcTest(CatchUpBillingController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class CatchUpBillingAuditEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CatchUpBillingResolutionService resolutionService;

    // Filtre de sécurité auto-détecté par @WebMvcTest ; mocké pour satisfaire sa dépendance.
    @MockBean
    private JwtService jwtService;

    @Test
    @DisplayName("piste d'audit : horodatage en chaîne, à la milliseconde")
    void auditTimestampIsAString() throws Exception {
        when(resolutionService.auditTrailForDisplay(42L)).thenReturn(List.of(new CatchUpBillingAuditDTO(
                3L, "ALREADY_PAID", null, "true", "directrice", LocalDateTime.of(2030, 1, 12, 9, 0, 0, 250_000_000),
                1L, "Payée dans son groupe")));

        mockMvc.perform(get("/api/catch-up-billing/{id}/audit", 42L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].field").value("ALREADY_PAID"))
                .andExpect(jsonPath("$[0].performedAt").value("2030-01-12T09:00:00.250"))
                .andExpect(jsonPath("$[0].sequenceRank").value(1));
    }
}
