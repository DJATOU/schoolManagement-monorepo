package com.school.management.controller;

import com.school.management.dto.StudentRefundDTO;
import com.school.management.service.RefundQueryService;
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
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Routage et contrat JSON des lectures de remboursements : ceux d'un élève et ceux d'un versement.
 * Les autorisations (ADMIN seul) sont vérifiées sur la configuration réelle par
 * {@code WriteRoutesAuthorizationIntegrationTest}.
 */
@WebMvcTest(RefundReadController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class RefundReadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RefundQueryService refundQueryService;

    // Filtre de sécurité auto-détecté par @WebMvcTest ; mocké pour satisfaire sa dépendance.
    @MockBean
    private JwtService jwtService;

    private static StudentRefundDTO refund() {
        return new StudentRefundDTO(7L, "REMB-2026-0007", new Date(1_760_000_000_000L),
                new BigDecimal("400.00"), "Trop-perçu", 12L, "Octobre", 3L, "Maths 4 AM A", 9L, "directrice");
    }

    @Test
    @DisplayName("élève, 200 : les champs que l'historique affiche, sous les noms attendus par le client")
    void listsStudentRefunds() throws Exception {
        when(refundQueryService.forStudent(4L)).thenReturn(List.of(refund()));

        mockMvc.perform(get("/api/students/4/refunds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].refundId").value(7))
                .andExpect(jsonPath("$[0].refundNumber").value("REMB-2026-0007"))
                .andExpect(jsonPath("$[0].refundDate").exists())
                .andExpect(jsonPath("$[0].amount").value(400.00))
                .andExpect(jsonPath("$[0].reason").value("Trop-perçu"))
                .andExpect(jsonPath("$[0].seriesId").value(12))
                .andExpect(jsonPath("$[0].seriesName").value("Octobre"))
                .andExpect(jsonPath("$[0].groupId").value(3))
                .andExpect(jsonPath("$[0].groupName").value("Maths 4 AM A"))
                .andExpect(jsonPath("$[0].paymentId").value(9))
                .andExpect(jsonPath("$[0].recordedBy").value("directrice"));
    }

    @Test
    @DisplayName("élève inconnu : 404, message du service")
    void unknownStudent() throws Exception {
        when(refundQueryService.forStudent(99L))
                .thenThrow(new CustomServiceException("Étudiant introuvable : 99", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/students/99/refunds"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Étudiant introuvable : 99"));
    }

    @Test
    @DisplayName("versement, 200 : ses remboursements, avec leur auteur")
    void listsPaymentRefunds() throws Exception {
        when(refundQueryService.forPayment(9L)).thenReturn(List.of(refund()));

        mockMvc.perform(get("/api/refunds/payment/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].refundNumber").value("REMB-2026-0007"))
                .andExpect(jsonPath("$[0].paymentId").value(9))
                .andExpect(jsonPath("$[0].recordedBy").value("directrice"));
    }

    @Test
    @DisplayName("versement inconnu : 404, message du service")
    void unknownPayment() throws Exception {
        when(refundQueryService.forPayment(98L))
                .thenThrow(new CustomServiceException("Versement introuvable : 98", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/refunds/payment/98"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Versement introuvable : 98"));
    }
}
