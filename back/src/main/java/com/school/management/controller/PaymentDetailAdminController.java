package com.school.management.controller;

import com.school.management.dto.PaymentDetailAuditDTO;
import com.school.management.dto.PaymentDetailUpdateDTO;
import com.school.management.persistance.PaymentDetailEntity;
import com.school.management.service.payment.PaymentDetailAdminService;
import com.school.management.service.payment.PaymentDetailAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payment-details")
public class PaymentDetailAdminController {

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
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Date dateFrom,
            @RequestParam(required = false) Date dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(defaultValue = "DESC") String direction
    ) {
        Sort sortOrder = Sort.by(Sort.Direction.fromString(direction), sort);
        Pageable pageable = PageRequest.of(page, size, sortOrder);
        Page<PaymentDetailEntity> result = paymentDetailAdminService.getAllPaymentDetailsWithFilters(
                studentId, groupId, sessionSeriesId, active, dateFrom, dateTo, pageable);

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
                                                                   @RequestHeader("X-Admin-Name") String adminName,
                                                                   @RequestBody PaymentDetailUpdateDTO updateDTO) {
        return ResponseEntity.ok(paymentDetailAdminService.updatePaymentDetail(id, updateDTO, adminName));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deletePaymentDetail(@PathVariable Long id,
                                                                   @RequestHeader("X-Admin-Name") String adminName,
                                                                   @RequestBody Map<String, String> requestBody) {
        String reason = requestBody.get("reason");
        paymentDetailAdminService.deletePaymentDetail(id, reason, adminName);
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
}
