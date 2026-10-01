package com.school.management.util;

import com.school.management.service.correction.AmountSnapshot;
import com.school.management.service.correction.CorrectionEffect;
import com.school.management.service.correction.CorrectionEffectType;
import com.school.management.service.correction.CorrectionPreview;
import com.school.management.service.correction.RefundFloorException;
import com.school.management.service.correction.SeriesAmountChange;
import com.school.management.service.correction.StalePreviewException;
import com.school.management.service.exception.CustomServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le 409 d'un Aperçu périmé porte le nouvel Aperçu et son jeton (spec admin-corrections,
 * exigence 4.3) : sans eux, l'écran ne pourrait que dire « réessayez », et l'administratrice
 * confirmerait à l'aveugle.
 */
@DisplayName("GlobalExceptionHandler — refus de correction enrichis")
class StalePreviewErrorHandlingTest {

    private MockMvc mockMvc;

    @RestController
    static class ThrowingController {
        @GetMapping("/stale")
        void stale() {
            AmountSnapshot before = new AmountSnapshot(new BigDecimal("4000"), BigDecimal.ZERO,
                    new BigDecimal("3500"), new BigDecimal("500"), false);
            AmountSnapshot after = new AmountSnapshot(new BigDecimal("4000"), BigDecimal.ZERO,
                    new BigDecimal("500"), new BigDecimal("3500"), false);
            throw new StalePreviewException(CorrectionPreview.of(
                    List.of(new SeriesAmountChange(7L, "Amine Belkacem", 11L, "Janvier", "Math 1ère A", before, after)),
                    List.of(new CorrectionEffect(CorrectionEffectType.ENCASHMENT_CANCELLED, "Reçu RECU-2030-0001 annulé"))),
                    "abc123");
        }

        @GetMapping("/floor")
        void floor() {
            throw new RefundFloorException("Correction refusée : remboursement REMB-2030-0001.",
                    List.of(new RefundFloorException.BlockingRefund("REMB-2030-0001", new java.util.Date(0),
                            new BigDecimal("2000.00"), "Janvier")));
        }

        @GetMapping("/refused")
        void refused() {
            throw new CustomServiceException("Encaissement déjà annulé.", HttpStatus.CONFLICT);
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("409 avec le motif, le nouvel Aperçu et son jeton")
    void conflictCarriesTheNewPreviewAndItsToken() throws Exception {
        mockMvc.perform(get("/stale"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STALE_PREVIEW"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("nouvel aperçu")))
                .andExpect(jsonPath("$.previewToken").value("abc123"))
                .andExpect(jsonPath("$.preview.amountsUnchanged").value(false))
                .andExpect(jsonPath("$.preview.series[0].seriesName").value("Janvier"))
                .andExpect(jsonPath("$.preview.series[0].before.paid").value(3500.0))
                .andExpect(jsonPath("$.preview.series[0].after.paid").value(500.0))
                .andExpect(jsonPath("$.preview.series[0].after.remaining").value(3500.0))
                .andExpect(jsonPath("$.preview.effects[0].type").value("ENCASHMENT_CANCELLED"));
    }

    @Test
    @DisplayName("passer sous le remboursé : 409 nommant les remboursements en cause")
    void refundFloorNamesTheBlockingRefunds() throws Exception {
        mockMvc.perform(get("/floor"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REFUND_FLOOR"))
                .andExpect(jsonPath("$.message").value("Correction refusée : remboursement REMB-2030-0001."))
                .andExpect(jsonPath("$.blockingRefunds[0].refundNumber").value("REMB-2030-0001"))
                .andExpect(jsonPath("$.blockingRefunds[0].amount").value(2000.0))
                .andExpect(jsonPath("$.blockingRefunds[0].seriesName").value("Janvier"));
    }

    @Test
    @DisplayName("un autre refus métier garde son corps ordinaire, sans Aperçu")
    void anOrdinaryRefusalKeepsItsBody() throws Exception {
        mockMvc.perform(get("/refused"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Encaissement déjà annulé."))
                .andExpect(jsonPath("$.preview").doesNotExist());
    }
}
