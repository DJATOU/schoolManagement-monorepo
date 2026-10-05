package com.school.management.controller.correction;

import com.school.management.dto.payroll.PayoutDTO;
import com.school.management.persistance.CorrectionReasonType;
import com.school.management.persistance.PayoutKind;
import com.school.management.persistance.PayoutStatus;
import com.school.management.service.correction.CorrectionEffect;
import com.school.management.service.correction.CorrectionEffectType;
import com.school.management.service.correction.CorrectionMode;
import com.school.management.service.correction.CorrectionOutcome;
import com.school.management.service.correction.CorrectionPreview;
import com.school.management.service.correction.CorrectionReason;
import com.school.management.service.correction.PayoutCorrection;
import com.school.management.service.correction.PayoutCorrectionService;
import com.school.management.service.security.JwtService;
import com.school.management.util.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Routage et contrat JSON des corrections de paie. Les règles sont éprouvées par
 * {@code PayoutCorrectionIntegrationTest}, les autorisations par
 * {@code WriteRoutesAuthorizationIntegrationTest}.
 */
@WebMvcTest(PayoutCorrectionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PayoutCorrectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PayoutCorrectionService corrections;

    // Filtre de sécurité auto-détecté par @WebMvcTest ; mocké pour satisfaire sa dépendance.
    @MockBean
    private JwtService jwtService;

    private static final CorrectionReason ENTRY_ERROR =
            new CorrectionReason(CorrectionReasonType.DATA_ENTRY_ERROR, "Mauvais taux");

    private static final CorrectionPreview PREVIEW = CorrectionPreview.of(List.of(),
            List.of(new CorrectionEffect(CorrectionEffectType.PAYOUT_CANCELLED, "Paie PAIE-2030-0001 annulée")));

    private static PayoutDTO payout(String number, PayoutStatus status) {
        BigDecimal amount = new BigDecimal("1800.00");
        return new PayoutDTO(40L, number, PayoutKind.INITIAL, null, 5L, "Nadia Aït Ahmed", 3L, "Math 1ère A", 12L,
                "Janvier", "Standard", new BigDecimal("60.00"), amount, amount, amount, amount, amount, amount, null,
                new Date(), "admin", status, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("motifs : ceux d'une paie, dans l'ordre")
    void reasons() throws Exception {
        mockMvc.perform(get("/api/teacher-payouts/correction-reasons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("DATA_ENTRY_ERROR"))
                .andExpect(jsonPath("$[1]").value("WRONG_AMOUNT"))
                .andExpect(jsonPath("$[2]").value("OTHER"));
    }

    @Test
    @DisplayName("annuler, Aperçu puis confirmation : motif, mode et jeton transmis ; effets rendus")
    void cancel() throws Exception {
        when(corrections.cancel(40L, ENTRY_ERROR, CorrectionMode.PREVIEW, null))
                .thenReturn(new CorrectionOutcome<>(CorrectionMode.PREVIEW, PREVIEW, "abc", null));
        when(corrections.cancel(40L, ENTRY_ERROR, CorrectionMode.CONFIRM, "abc"))
                .thenReturn(new CorrectionOutcome<>(CorrectionMode.CONFIRM, PREVIEW, "abc",
                        payout("PAIE-2030-0001", PayoutStatus.CANCELLED)));

        mockMvc.perform(post("/api/teacher-payouts/40/cancel/preview").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonType\":\"DATA_ENTRY_ERROR\",\"reasonText\":\"Mauvais taux\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previewToken").value("abc"))
                .andExpect(jsonPath("$.preview.effects[0].type").value("PAYOUT_CANCELLED"))
                .andExpect(jsonPath("$.result").doesNotExist());
        mockMvc.perform(post("/api/teacher-payouts/40/cancel/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonType\":\"DATA_ENTRY_ERROR\",\"reasonText\":\"Mauvais taux\","
                                + "\"previewToken\":\"abc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("annuler sans corps : motif manquant, 400")
    void cancelWithoutBody() throws Exception {
        mockMvc.perform(post("/api/teacher-payouts/40/cancel/preview"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Motif obligatoire : choisissez la raison de la correction."));
    }

    @Test
    @DisplayName("remplacer, Aperçu puis confirmation : taux et note transmis ; l'originale et la remplaçante rendues")
    void replace() throws Exception {
        when(corrections.replace(40L, 8L, "Virement", ENTRY_ERROR, CorrectionMode.PREVIEW, null))
                .thenReturn(new CorrectionOutcome<>(CorrectionMode.PREVIEW, PREVIEW, "def", null));
        when(corrections.replace(40L, 8L, "Virement", ENTRY_ERROR, CorrectionMode.CONFIRM, "def"))
                .thenReturn(new CorrectionOutcome<>(CorrectionMode.CONFIRM, PREVIEW, "def", new PayoutCorrection(
                        payout("PAIE-2030-0001", PayoutStatus.CANCELLED), payout("PAIE-2030-0002", PayoutStatus.ACTIVE))));
        String body = "{\"rateId\":8,\"note\":\"Virement\",\"reasonType\":\"DATA_ENTRY_ERROR\","
                + "\"reasonText\":\"Mauvais taux\"";

        mockMvc.perform(post("/api/teacher-payouts/40/replace/preview").contentType(MediaType.APPLICATION_JSON)
                        .content(body + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previewToken").value("def"));
        mockMvc.perform(post("/api/teacher-payouts/40/replace/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(body + ",\"previewToken\":\"def\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.original.payoutNumber").value("PAIE-2030-0001"))
                .andExpect(jsonPath("$.result.replacement.payoutNumber").value("PAIE-2030-0002"));
    }

    @Test
    @DisplayName("remplacer sans corps : motif manquant, 400, le service n'est pas appelé")
    void replaceWithoutBody() throws Exception {
        mockMvc.perform(post("/api/teacher-payouts/40/replace/preview"))
                .andExpect(status().isBadRequest());
        verify(corrections, org.mockito.Mockito.never()).replace(eq(40L), any(), any(), any(), any(), any());
    }
}
