package com.school.management.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.management.dto.RefundRequestDTO;
import com.school.management.mapper.RefundMapperImpl;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.RefundEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.service.RefundService;
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

import java.math.BigDecimal;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration web (Spring MVC) du {@link RefundController}.
 *
 * <p>{@code @WebMvcTest} avec {@link RefundService} simulé et le mapper réel
 * ({@link RefundMapperImpl}). Vérifie le routage {@code POST /api/refunds}, le câblage
 * requête/réponse, les noms de champs JSON, et le mapping d'erreurs (400 / 404) via le
 * {@link GlobalExceptionHandler}.</p>
 */
@WebMvcTest(RefundController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, RefundMapperImpl.class})
class RefundControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RefundService refundService;

    // Filtre de sécurité auto-détecté par @WebMvcTest ; mocké pour satisfaire sa dépendance.
    @MockBean
    private JwtService jwtService;

    private RefundEntity refund() {
        StudentEntity student = new StudentEntity();
        student.setId(7L);
        PaymentEntity payment = PaymentEntity.builder().id(9L).build();
        return RefundEntity.builder()
                .id(2L)
                .payment(payment)
                .student(student)
                .amount(new BigDecimal("40.00"))
                .refundDate(new Date())
                .build();
    }

    @Test
    void createRefund_returns201WithFrontFieldNames() throws Exception {
        when(refundService.create(any(RefundRequestDTO.class))).thenReturn(refund());

        RefundRequestDTO body = new RefundRequestDTO(9L, 7L, new BigDecimal("40.00"), null);

        mockMvc.perform(post("/api/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.paymentId").value(9))
                .andExpect(jsonPath("$.studentId").value(7))
                .andExpect(jsonPath("$.amount").value(40.00));
    }

    @Test
    void createRefund_whenAmountExceedsPaid_returns400WithFrenchMessage() throws Exception {
        when(refundService.create(any(RefundRequestDTO.class)))
                .thenThrow(new CustomServiceException(
                        "Le montant du remboursement ne peut pas dépasser le montant payé.",
                        HttpStatus.BAD_REQUEST));

        RefundRequestDTO body = new RefundRequestDTO(9L, 7L, new BigDecimal("999.00"), null);

        mockMvc.perform(post("/api/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Le montant du remboursement ne peut pas dépasser le montant payé."));
    }

    @Test
    void createRefund_whenPaymentMissing_returns404() throws Exception {
        when(refundService.create(any(RefundRequestDTO.class)))
                .thenThrow(new CustomServiceException(
                        "Paiement introuvable pour l'identifiant : 99",
                        HttpStatus.NOT_FOUND));

        RefundRequestDTO body = new RefundRequestDTO(99L, 7L, new BigDecimal("10.00"), null);

        mockMvc.perform(post("/api/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Paiement introuvable pour l'identifiant : 99"));
    }
}
