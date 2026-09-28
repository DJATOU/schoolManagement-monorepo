package com.school.management.controller;

import com.school.management.dto.PaymentDetailAuditDTO;
import com.school.management.dto.PaymentDetailSearchDTO;
import com.school.management.dto.PaymentDetailUpdateDTO;
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
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

    private static final String SYSTEM_ACTOR = "system";

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

    @PatchMapping("/{id}")
    public ResponseEntity<PaymentDetailEntity> updatePaymentDetail(@PathVariable Long id,
            @RequestBody PaymentDetailUpdateDTO updateDTO) {
        return ResponseEntity.ok(paymentDetailAdminService.updatePaymentDetail(id, updateDTO, currentActor()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deletePaymentDetail(@PathVariable Long id,
            @RequestBody Map<String, String> requestBody) {
        String reason = requestBody.get("reason");
        paymentDetailAdminService.deletePaymentDetail(id, reason, currentActor());
        Map<String, String> response = new HashMap<>();
        response.put("message", "Payment detail deleted successfully");
        return ResponseEntity.ok(response);
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
    public ResponseEntity<PaymentDetailEntity> reactivatePaymentDetail(@PathVariable Long id,
            @RequestBody Map<String, String> requestBody) {
        String reason = requestBody.get("reason");
        return ResponseEntity.ok(paymentDetailAdminService.reactivatePaymentDetail(id, reason, currentActor()));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Auteur de l'action, lu dans le contexte de sécurité.
     *
     * <p>L'ancien en-tête {@code X-Admin-Name} était renseigné par le client (le frontend
     * envoyait la constante « Admin ») : le journal d'audit était à la fois inexploitable et
     * falsifiable. L'identité vient désormais du jeton vérifié côté serveur.</p>
     */
    private String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return SYSTEM_ACTOR;
        }
        return auth.getName();
    }

    /** Tri restreint aux colonnes connues, avec repli sur l'identifiant. */
    private Sort resolveSort(String sort, String direction) {
        String field = SORTABLE_FIELDS.contains(sort) ? sort : DEFAULT_SORT;
        Sort.Direction resolvedDirection = Sort.Direction.fromOptionalString(
                Objects.toString(direction, "")).orElse(Sort.Direction.DESC);
        return Sort.by(resolvedDirection, field);
    }

    // ------------------------------------------------------------------
    // Gestion des erreurs : sans ces gestionnaires, un motif manquant ou une
    // réactivation impossible remontait en 500 générique côté client.
    // ------------------------------------------------------------------

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleValidation(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
    }
}
