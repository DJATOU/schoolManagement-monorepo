package com.school.management.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.management.dto.RefundCapDTO;
import com.school.management.dto.RefundReceiptDTO;
import com.school.management.dto.RefundRequestDTO;
import com.school.management.mapper.RefundMapperImpl;
import com.school.management.persistance.PaymentEntity;
import com.school.management.persistance.RefundEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.service.RefundReceiptService;
import com.school.management.service.RefundService;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration web (Spring MVC) du {@link RefundController}.
 *
 * <p>{@code @WebMvcTest} avec {@link RefundService} simulé et le mapper réel
 * ({@link RefundMapperImpl}). Vérifie le routage, le câblage requête/réponse, les noms de champs
 * JSON — dont le numéro de pièce, le motif et le plafond restant — et le mapping d'erreurs
 * (400 / 404) via le {@link GlobalExceptionHandler}.</p>
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

    @MockBean
    private RefundReceiptService refundReceiptService;

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
                .refundNumber("REMB-2026-0007")
                .reason("Trop-perçu sur la série de janvier")
                .build();
    }

    private static RefundRequestDTO body(Long paymentId, BigDecimal amount) {
        return new RefundRequestDTO(paymentId, 7L, amount, null, "Trop-perçu");
    }

    @Test
    @DisplayName("création : 201 avec numéro de pièce, motif et plafond restant")
    void createRefund_returns201WithFrontFieldNames() throws Exception {
        when(refundService.create(any(RefundRequestDTO.class))).thenReturn(refund());
        when(refundService.cap(anyLong())).thenReturn(new RefundCapDTO(
                9L, new BigDecimal("100.00"), new BigDecimal("40.00"), new BigDecimal("60.00")));

        mockMvc.perform(post("/api/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body(9L, new BigDecimal("40.00")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.paymentId").value(9))
                .andExpect(jsonPath("$.studentId").value(7))
                .andExpect(jsonPath("$.amount").value(40.00))
                .andExpect(jsonPath("$.refundNumber").value("REMB-2026-0007"))
                .andExpect(jsonPath("$.reason").value("Trop-perçu sur la série de janvier"))
                // Le plafond restant évite à l'interface un second appel après enregistrement.
                .andExpect(jsonPath("$.refundableCap").value(60.00));
    }

    @Test
    @DisplayName("dépassement du plafond : 400, message nommant les montants")
    void createRefund_whenAmountExceedsCap_returns400WithFrenchMessage() throws Exception {
        String message = "Remboursement impossible : montant demandé 999.00 €, mais le versement a "
                + "rapporté 100.00 € dont 40.00 € déjà remboursé(s). Plafond restant : 60.00 €.";
        when(refundService.create(any(RefundRequestDTO.class)))
                .thenThrow(new CustomServiceException(message, HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body(9L, new BigDecimal("999.00")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(message));
    }

    @Test
    @DisplayName("motif absent : 400")
    void createRefund_whenReasonMissing_returns400() throws Exception {
        when(refundService.create(any(RefundRequestDTO.class)))
                .thenThrow(new CustomServiceException(
                        "Le motif du remboursement est obligatoire : une sortie de caisse doit "
                                + "pouvoir être justifiée.",
                        HttpStatus.BAD_REQUEST));

        RefundRequestDTO sansMotif = new RefundRequestDTO(9L, 7L, new BigDecimal("10.00"), null, null);

        mockMvc.perform(post("/api/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sansMotif)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("paiement absent : 404")
    void createRefund_whenPaymentMissing_returns404() throws Exception {
        when(refundService.create(any(RefundRequestDTO.class)))
                .thenThrow(new CustomServiceException(
                        "Paiement introuvable pour l'identifiant : 99", HttpStatus.NOT_FOUND));

        mockMvc.perform(post("/api/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body(99L, new BigDecimal("10.00")))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Paiement introuvable pour l'identifiant : 99"));
    }

    @Test
    @DisplayName("lecture du plafond : les trois montants sont exposés")
    void refundableCap_returnsThreeAmounts() throws Exception {
        when(refundService.cap(9L)).thenReturn(new RefundCapDTO(
                9L, new BigDecimal("100.00"), new BigDecimal("60.00"), new BigDecimal("40.00")));

        mockMvc.perform(get("/api/refunds/payment/9/cap"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(9))
                .andExpect(jsonPath("$.amountPaid").value(100.00))
                .andExpect(jsonPath("$.alreadyRefunded").value(60.00))
                .andExpect(jsonPath("$.refundableCap").value(40.00));
    }

    @Test
    @DisplayName("lecture du plafond sur paiement absent : 404")
    void refundableCap_whenPaymentMissing_returns404() throws Exception {
        when(refundService.cap(99L)).thenThrow(new CustomServiceException(
                "Paiement introuvable pour l'identifiant : 99", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/refunds/payment/99/cap"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("émission du reçu : 201 avec les données et le rang de production")
    void issueReceipt_returns201WithReceiptData() throws Exception {
        when(refundReceiptService.issue(2L)).thenReturn(new RefundReceiptDTO(
                2L, "REMB-2026-0007", new Date(), new BigDecimal("60.00"),
                "Trop-perçu sur la série de janvier", "Batoul", "Djatou",
                new Date(), new BigDecimal("240.00"), "Maths 1ère année", "Série janvier",
                "mme.martin", 1, LocalDateTime.of(2026, 3, 1, 10, 0),
                "remb-2026-0007_batoul_djatou.pdf"));

        mockMvc.perform(post("/api/refunds/{id}/receipts", 2L))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refundNumber").value("REMB-2026-0007"))
                .andExpect(jsonPath("$.amount").value(60.00))
                .andExpect(jsonPath("$.reason").value("Trop-perçu sur la série de janvier"))
                .andExpect(jsonPath("$.studentLastName").value("Djatou"))
                .andExpect(jsonPath("$.seriesName").value("Série janvier"))
                .andExpect(jsonPath("$.recordedBy").value("mme.martin"))
                .andExpect(jsonPath("$.issuanceRank").value(1))
                .andExpect(jsonPath("$.fileName").value("remb-2026-0007_batoul_djatou.pdf"));
    }

    @Test
    @DisplayName("émission du reçu d'un remboursement absent ou inactif : 404")
    void issueReceipt_whenRefundMissing_returns404() throws Exception {
        when(refundReceiptService.issue(99L)).thenThrow(new CustomServiceException(
                "Remboursement introuvable ou inactif pour l'identifiant : 99",
                HttpStatus.NOT_FOUND));

        mockMvc.perform(post("/api/refunds/{id}/receipts", 99L))
                .andExpect(status().isNotFound());
    }
}
