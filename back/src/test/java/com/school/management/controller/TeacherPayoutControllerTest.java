package com.school.management.controller;

import com.school.management.dto.payroll.PayRequest;
import com.school.management.dto.payroll.PayableSeriesDTO;
import com.school.management.dto.payroll.PayableSeriesDTO.PayableState;
import com.school.management.dto.payroll.PayoutDTO;
import com.school.management.dto.payroll.PayoutListDTO;
import com.school.management.dto.payroll.PayoutPreviewDTO;
import com.school.management.dto.payroll.PayoutSlipDTO;
import com.school.management.persistance.PayoutKind;
import com.school.management.persistance.PayoutStatus;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.payroll.PayoutSlipService;
import com.school.management.service.payroll.StalePayoutPreviewException;
import com.school.management.service.payroll.TeacherPayoutService;
import com.school.management.service.security.JwtService;
import com.school.management.util.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Routage et contrat JSON de la Paie des enseignants. Les autorisations (ADMIN seul, lecture
 * comprise) sont vérifiées sur la configuration réelle par {@code WriteRoutesAuthorizationIntegrationTest}.
 */
@WebMvcTest(TeacherPayoutController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class TeacherPayoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TeacherPayoutService payoutService;

    @MockBean
    private PayoutSlipService slipService;

    // Filtre de sécurité auto-détecté par @WebMvcTest ; mocké pour satisfaire sa dépendance.
    @MockBean
    private JwtService jwtService;

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private static PayoutPreviewDTO preview(String teacherAmount, String token) {
        return new PayoutPreviewDTO(12L, "Octobre", 3L, "Maths 4 AM A", 5L, "Nadia Aït Ahmed", PayoutKind.INITIAL,
                7L, "Standard", money("60.00"), money("74000.00"), money("2000.00"), money("72000.00"),
                money("0.00"), money("72000.00"), money(teacherAmount), money("28800.00"), money("0.00"), token);
    }

    private static PayoutDTO payout() {
        return new PayoutDTO(40L, "PAIE-2030-0001", PayoutKind.INITIAL, null, 5L, "Nadia Aït Ahmed", 3L,
                "Maths 4 AM A", 12L, "Octobre", "Standard", money("60.00"), money("74000.00"), money("2000.00"),
                money("72000.00"), money("72000.00"), money("43200.00"), money("28800.00"), "Remis en main propre",
                new Date(1_900_000_000_000L), "directrice", PayoutStatus.ACTIVE, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("séries à payer, 200 : filtres transmis, état et montants sous les noms attendus")
    void payable() throws Exception {
        when(payoutService.payable(5L, 3L)).thenReturn(List.of(new PayableSeriesDTO(12L, "Octobre", 3L,
                "Maths 4 AM A", 5L, "Nadia Aït Ahmed", PayableState.PAYABLE, 8, 8, money("74000.00"),
                money("2000.00"), money("72000.00"), null, null, null, null)));

        mockMvc.perform(get("/api/teacher-payouts/payable").param("teacherId", "5").param("groupId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].seriesId").value(12))
                .andExpect(jsonPath("$[0].state").value("PAYABLE"))
                .andExpect(jsonPath("$[0].activeSessions").value(8))
                .andExpect(jsonPath("$[0].validatedSessions").value(8))
                .andExpect(jsonPath("$[0].collectedNet").value(72000.00))
                .andExpect(jsonPath("$[0].teacherName").value("Nadia Aït Ahmed"));
    }

    @Test
    @DisplayName("séries à payer sans filtre : null transmis")
    void payableWithoutFilters() throws Exception {
        when(payoutService.payable(null, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/teacher-payouts/payable")).andExpect(status().isOk());
        verify(payoutService).payable(null, null);
    }

    @Test
    @DisplayName("paies versées, 200 : filtres et bornes yyyy-MM-dd transmis, totaux rendus")
    void search() throws Exception {
        when(payoutService.search(eq(5L), eq(3L), eq(PayoutStatus.ACTIVE), any(), any()))
                .thenReturn(new PayoutListDTO(List.of(payout()), money("43200.00"), money("28800.00")));

        mockMvc.perform(get("/api/teacher-payouts").param("teacherId", "5").param("groupId", "3")
                        .param("status", "ACTIVE").param("from", "2030-02-01").param("to", "2030-02-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payouts[0].payoutNumber").value("PAIE-2030-0001"))
                .andExpect(jsonPath("$.payouts[0].teacherAmount").value(43200.00))
                .andExpect(jsonPath("$.payouts[0].schoolAmount").value(28800.00))
                .andExpect(jsonPath("$.payouts[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.payouts[0].kind").value("INITIAL"))
                .andExpect(jsonPath("$.payouts[0].paidBy").value("directrice"))
                .andExpect(jsonPath("$.teacherTotal").value(43200.00))
                .andExpect(jsonPath("$.schoolTotal").value(28800.00));

        ArgumentCaptor<Date> from = ArgumentCaptor.forClass(Date.class);
        ArgumentCaptor<Date> to = ArgumentCaptor.forClass(Date.class);
        verify(payoutService).search(eq(5L), eq(3L), eq(PayoutStatus.ACTIVE), from.capture(), to.capture());
        assertThat(from.getValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate())
                .isEqualTo(LocalDate.of(2030, 2, 1));
        assertThat(to.getValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate())
                .isEqualTo(LocalDate.of(2030, 2, 28));
    }

    @Test
    @DisplayName("paies versées sans filtre : null transmis ; date mal formée : 400")
    void searchWithoutFilters() throws Exception {
        when(payoutService.search(null, null, null, null, null))
                .thenReturn(new PayoutListDTO(List.of(), money("0.00"), money("0.00")));

        mockMvc.perform(get("/api/teacher-payouts")).andExpect(status().isOk());
        verify(payoutService).search(isNull(), isNull(), isNull(), isNull(), isNull());

        mockMvc.perform(get("/api/teacher-payouts").param("from", "01/02/2030"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("une paie, 200 ; inconnue, 404 avec le message du service")
    void getOne() throws Exception {
        when(payoutService.get(40L)).thenReturn(payout());
        when(payoutService.get(99L))
                .thenThrow(new CustomServiceException("Paie introuvable : 99", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/teacher-payouts/40"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(40))
                .andExpect(jsonPath("$.note").value("Remis en main propre"));
        mockMvc.perform(get("/api/teacher-payouts/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Paie introuvable : 99"));
    }

    @Test
    @DisplayName("paies d'un enseignant, 200")
    void forTeacher() throws Exception {
        when(payoutService.forTeacher(5L))
                .thenReturn(new PayoutListDTO(List.of(payout()), money("43200.00"), money("28800.00")));

        mockMvc.perform(get("/api/teachers/5/payouts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payouts[0].teacherId").value(5))
                .andExpect(jsonPath("$.teacherTotal").value(43200.00));
    }

    @Test
    @DisplayName("aperçu de paie, 200 : corps transmis, parts et jeton rendus")
    void previewPay() throws Exception {
        when(payoutService.previewPay(12L, new PayRequest(7L, null, null))).thenReturn(preview("43200.00", "abc"));

        mockMvc.perform(post("/api/teacher-payouts/series/12/pay/preview")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rateId\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherAmount").value(43200.00))
                .andExpect(jsonPath("$.schoolAmount").value(28800.00))
                .andExpect(jsonPath("$.collectedNet").value(72000.00))
                .andExpect(jsonPath("$.rateLabel").value("Standard"))
                .andExpect(jsonPath("$.previewToken").value("abc"));
    }

    @Test
    @DisplayName("confirmation de paie, 201 : la paie enregistrée")
    void confirmPay() throws Exception {
        PayRequest request = new PayRequest(7L, "Remis en main propre", "abc");
        when(payoutService.confirmPay(12L, request)).thenReturn(payout());

        mockMvc.perform(post("/api/teacher-payouts/series/12/pay/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rateId\":7,\"note\":\"Remis en main propre\",\"previewToken\":\"abc\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payoutNumber").value("PAIE-2030-0001"));
    }

    @Test
    @DisplayName("confirmation périmée : 409 STALE_PREVIEW avec le nouvel aperçu et son jeton")
    void staleConfirmation() throws Exception {
        PayRequest request = new PayRequest(7L, null, "abc");
        when(payoutService.confirmPay(12L, request))
                .thenThrow(new StalePayoutPreviewException(preview("43800.00", "def")));

        mockMvc.perform(post("/api/teacher-payouts/series/12/pay/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rateId\":7,\"previewToken\":\"abc\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STALE_PREVIEW"))
                .andExpect(jsonPath("$.message").value(
                        "Les montants ont changé depuis l'aperçu : relisez le nouveau calcul avant de confirmer."))
                .andExpect(jsonPath("$.preview.teacherAmount").value(43800.00))
                .andExpect(jsonPath("$.previewToken").value("def"));
    }

    @Test
    @DisplayName("refus nommé (série non terminée) : 409 avec le message du service")
    void namedRefusal() throws Exception {
        when(payoutService.previewPay(eq(12L), any())).thenThrow(new CustomServiceException(
                "La série « Octobre » n'est pas terminée : 2 séance(s) sur 8 restent à valider.", HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/teacher-payouts/series/12/pay/preview")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rateId\":7}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "La série « Octobre » n'est pas terminée : 2 séance(s) sur 8 restent à valider."));
    }

    @Test
    @DisplayName("régularisation : aperçu sans corps, 200 ; confirmation, 201")
    void regularize() throws Exception {
        when(payoutService.previewRegularize(12L)).thenReturn(preview("1800.00", "reg"));
        PayRequest request = new PayRequest(null, null, "reg");
        when(payoutService.confirmRegularize(12L, request)).thenReturn(payout());

        mockMvc.perform(post("/api/teacher-payouts/series/12/regularize/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherAmount").value(1800.00));
        mockMvc.perform(post("/api/teacher-payouts/series/12/regularize/confirm")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"previewToken\":\"reg\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(40));
    }

    @Test
    @DisplayName("bordereau, 201 : paie, rang, auteur, nom de fichier")
    void issueSlip() throws Exception {
        when(slipService.issue(40L)).thenReturn(new PayoutSlipDTO(payout(), 2,
                LocalDateTime.of(2030, 2, 1, 10, 30), "directrice", "paie-2030-0001_nadia_ait_ahmed.pdf"));

        mockMvc.perform(post("/api/teacher-payouts/40/slips"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payout.payoutNumber").value("PAIE-2030-0001"))
                .andExpect(jsonPath("$.issuanceRank").value(2))
                .andExpect(jsonPath("$.issuedBy").value("directrice"))
                .andExpect(jsonPath("$.issuedAt").exists())
                .andExpect(jsonPath("$.fileName").value("paie-2030-0001_nadia_ait_ahmed.pdf"));
    }
}
