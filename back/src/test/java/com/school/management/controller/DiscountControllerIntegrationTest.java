package com.school.management.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.management.dto.DiscountRequestDTO;
import com.school.management.dto.DiscountResponseDTO;
import com.school.management.mapper.DiscountMapperImpl;
import com.school.management.persistance.DiscountEntity;
import com.school.management.persistance.DiscountScope;
import com.school.management.persistance.StudentEntity;
import com.school.management.service.DiscountService;
import com.school.management.service.DiscountViewService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration web (Spring MVC) du {@link DiscountController}.
 *
 * <p>{@code @WebMvcTest} avec {@link DiscountService} simulé et le mapper réel
 * ({@link DiscountMapperImpl}). Vérifie le routage {@code POST /api/discounts}, le câblage
 * requête/réponse, les noms de champs JSON, et le mapping d'erreurs (400) via le
 * {@link GlobalExceptionHandler}.</p>
 */
@WebMvcTest(DiscountController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, DiscountMapperImpl.class})
class DiscountControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DiscountService discountService;

    @MockBean
    private DiscountViewService discountViewService;

    // Filtre de sécurité auto-détecté par @WebMvcTest ; mocké pour satisfaire sa dépendance.
    @MockBean
    private JwtService jwtService;

    private DiscountEntity groupDiscount() {
        StudentEntity student = new StudentEntity();
        student.setId(7L);
        return DiscountEntity.builder()
                .id(3L)
                .student(student)
                .scope(DiscountScope.GROUP)
                .groupId(31L)
                .rate(new BigDecimal("0.50"))
                .build();
    }

    /** DTO enrichi tel que produit par le {@link DiscountViewService}. */
    private DiscountResponseDTO groupDiscountDto() {
        return new DiscountResponseDTO(3L, 7L, DiscountScope.GROUP, 31L, null, null,
                new BigDecimal("0.50"), "Bilal Amrani", "Math 1ère B");
    }

    @Test
    void createDiscount_returns201WithFrontFieldNames() throws Exception {
        when(discountService.create(any(DiscountRequestDTO.class))).thenReturn(groupDiscount());
        when(discountViewService.toDisplayDto(any(DiscountEntity.class))).thenReturn(groupDiscountDto());

        DiscountRequestDTO body = new DiscountRequestDTO(
                7L, DiscountScope.GROUP, 31L, null, null, new BigDecimal("0.50"));

        mockMvc.perform(post("/api/discounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.studentId").value(7))
                .andExpect(jsonPath("$.scope").value("GROUP"))
                .andExpect(jsonPath("$.groupId").value(31))
                .andExpect(jsonPath("$.rate").value(0.50))
                .andExpect(jsonPath("$.studentName").value("Bilal Amrani"))
                .andExpect(jsonPath("$.targetName").value("Math 1ère B"));
    }

    @Test
    void getAllDiscounts_returnsEnrichedList() throws Exception {
        // Sans paramètre, le filtre étudiant est nul : toutes les réductions sont renvoyées.
        when(discountViewService.findForDisplay(null)).thenReturn(java.util.List.of(groupDiscountDto()));

        mockMvc.perform(get("/api/discounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].studentName").value("Bilal Amrani"))
                .andExpect(jsonPath("$[0].targetName").value("Math 1ère B"));
    }

    @Test
    void getAllDiscounts_withStudentId_filtersOnThatStudent() throws Exception {
        // La fiche étudiante ne doit pas télécharger toutes les réductions de l'école.
        when(discountViewService.findForDisplay(7L)).thenReturn(java.util.List.of(groupDiscountDto()));

        mockMvc.perform(get("/api/discounts").param("studentId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentId").value(7));
    }

    @Test
    void updateRate_returnsUpdatedDiscount() throws Exception {
        when(discountService.updateRate(eq(3L), any(BigDecimal.class))).thenReturn(groupDiscount());
        when(discountViewService.toDisplayDto(any(DiscountEntity.class))).thenReturn(groupDiscountDto());

        mockMvc.perform(put("/api/discounts/{id}", 3L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DiscountController.UpdateRateRequest(new BigDecimal("0.50")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.rate").value(0.50));
    }

    @Test
    void updateRate_whenRateOutOfRange_returns400() throws Exception {
        when(discountService.updateRate(eq(3L), any(BigDecimal.class)))
                .thenThrow(new CustomServiceException(
                        "Le taux de réduction doit être compris entre 0.00 et 1.00.",
                        HttpStatus.BAD_REQUEST));

        mockMvc.perform(put("/api/discounts/{id}", 3L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DiscountController.UpdateRateRequest(new BigDecimal("1.50")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Le taux de réduction doit être compris entre 0.00 et 1.00."));
    }

    @Test
    void deleteDiscount_returns204() throws Exception {
        mockMvc.perform(delete("/api/discounts/{id}", 3L))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteDiscount_whenUnknown_returns404() throws Exception {
        org.mockito.Mockito.doThrow(new CustomServiceException(
                        "Réduction introuvable pour l'identifiant : 999", HttpStatus.NOT_FOUND))
                .when(discountService).delete(999L);

        mockMvc.perform(delete("/api/discounts/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        "Réduction introuvable pour l'identifiant : 999"));
    }

    @Test
    void createDiscount_whenRateOutOfRange_returns400WithFrenchMessage() throws Exception {
        when(discountService.create(any(DiscountRequestDTO.class)))
                .thenThrow(new CustomServiceException(
                        "Le taux de réduction doit être compris entre 0.00 et 1.00.",
                        HttpStatus.BAD_REQUEST));

        DiscountRequestDTO body = new DiscountRequestDTO(
                7L, DiscountScope.GROUP, 31L, null, null, new BigDecimal("1.50"));

        mockMvc.perform(post("/api/discounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Le taux de réduction doit être compris entre 0.00 et 1.00."));
    }

    @Test
    void createDiscount_whenMultiScope_returns400() throws Exception {
        when(discountService.create(any(DiscountRequestDTO.class)))
                .thenThrow(new CustomServiceException(
                        "Une réduction doit avoir exactement une portée correspondant à son scope.",
                        HttpStatus.BAD_REQUEST));

        DiscountRequestDTO body = new DiscountRequestDTO(
                7L, DiscountScope.GROUP, 31L, 41L, null, new BigDecimal("0.25"));

        mockMvc.perform(post("/api/discounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Une réduction doit avoir exactement une portée correspondant à son scope."));
    }
}
