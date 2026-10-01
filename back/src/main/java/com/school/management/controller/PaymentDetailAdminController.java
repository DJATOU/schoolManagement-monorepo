package com.school.management.controller;

import com.school.management.dto.PaymentDetailAuditDTO;
import com.school.management.dto.PaymentDetailSearchDTO;
import com.school.management.persistance.PaymentDetailEntity;
import com.school.management.service.payment.PaymentDetailAdminService;
import com.school.management.service.payment.PaymentDetailAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@RestController
@RequestMapping("/api/payment-details")
public class PaymentDetailAdminController {

    /**
     * Colonnes autorisées pour le tri. Le paramètre {@code sort} alimente directement une
     * clause ORDER BY : sans liste blanche, une valeur arbitraire du client provoque une
     * erreur 500 (voire fuite de structure) sur un nom de propriété inconnu.
     */
    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "id", "amountPaid", "dateCreation", "paymentDate", "active");

    private static final String DEFAULT_SORT = "id";

    private final PaymentDetailAdminService paymentDetailAdminService;
    private final PaymentDetailAuditService paymentDetailAuditService;

    @Autowired
    public PaymentDetailAdminController(PaymentDetailAdminService paymentDetailAdminService,
            PaymentDetailAuditService paymentDetailAuditService) {
        this.paymentDetailAdminService = paymentDetailAdminService;
        this.paymentDetailAuditService = paymentDetailAuditService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getPaymentDetails(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false, name = "sessionSeriesId") Long sessionSeriesId,
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateFrom,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date dateTo,
            @RequestParam(required = false) Long levelId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = DEFAULT_SORT) String sort,
            @RequestParam(defaultValue = "DESC") String direction) {
        Pageable pageable = PageRequest.of(page, size, resolveSort(sort, direction));

        // Use the new search method with complete data (student, group, series,
        // session)
        // This uses DTO projection to avoid lazy loading issues
        Page<PaymentDetailSearchDTO> result = paymentDetailAdminService.searchPaymentDetailsWithCompleteData(
                studentId, groupId, sessionSeriesId, sessionId, active, dateFrom, dateTo, levelId, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("content", result.getContent());
        response.put("totalElements", result.getTotalElements());
        response.put("totalPages", result.getTotalPages());
        response.put("currentPage", result.getNumber());
        response.put("size", result.getSize());

        return ResponseEntity.ok(response);
    }

    // Modifier, supprimer ou réactiver une ligne : refusé, 409 avec le reçu à corriger (spec
    // admin-corrections, A.6). Une ligne est la part d'un Encaissement ; elle se corrige avec lui,
    // par son Annulation ou son Remplacement. Les routes restent déclarées pour qu'un ancien client
    // reçoive l'explication plutôt qu'un 405.

    @PatchMapping("/{id}")
    public ResponseEntity<Void> updatePaymentDetail(@PathVariable Long id) {
        paymentDetailAdminService.refuseLineCorrection(id);
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePaymentDetail(@PathVariable Long id) {
        paymentDetailAdminService.refuseLineCorrection(id);
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<PaymentDetailAuditDTO>> getPaymentDetailHistory(@PathVariable Long id) {
        return ResponseEntity.ok(paymentDetailAuditService.getAuditHistory(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentDetailEntity> getPaymentDetailById(@PathVariable Long id) {
        return ResponseEntity.ok(paymentDetailAdminService.getPaymentDetail(id));
    }

    @PostMapping("/{id}/reactivate")
    public ResponseEntity<Void> reactivatePaymentDetail(@PathVariable Long id) {
        paymentDetailAdminService.refuseLineCorrection(id);
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Tri restreint aux colonnes connues, avec repli sur l'identifiant. */
    private Sort resolveSort(String sort, String direction) {
        String field = SORTABLE_FIELDS.contains(sort) ? sort : DEFAULT_SORT;
        Sort.Direction resolvedDirection = Sort.Direction.fromOptionalString(
                Objects.toString(direction, "")).orElse(Sort.Direction.DESC);
        return Sort.by(resolvedDirection, field);
    }
}
