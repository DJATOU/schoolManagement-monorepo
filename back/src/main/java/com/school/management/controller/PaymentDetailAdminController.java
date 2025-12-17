package com.school.management.controller;

import com.school.management.dto.PaymentDetailAuditDTO;
import com.school.management.dto.PaymentDetailUpdateDTO;
import com.school.management.persistance.PaymentDetailEntity;
import com.school.management.repository.PaymentDetailRepository;
import com.school.management.service.payment.PaymentDetailAdminService;
import com.school.management.service.payment.PaymentDetailAuditService;
import com.school.management.shared.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payment-details")
public class PaymentDetailAdminController {

    private final PaymentDetailAdminService paymentDetailAdminService;
    private final PaymentDetailAuditService paymentDetailAuditService;
    private final PaymentDetailRepository paymentDetailRepository;

    public PaymentDetailAdminController(PaymentDetailAdminService paymentDetailAdminService,
                                        PaymentDetailAuditService paymentDetailAuditService,
                                        PaymentDetailRepository paymentDetailRepository) {
        this.paymentDetailAdminService = paymentDetailAdminService;
        this.paymentDetailAuditService = paymentDetailAuditService;
        this.paymentDetailRepository = paymentDetailRepository;
    }

    @GetMapping
    public Map<String, Object> getPaymentDetails(@RequestParam(required = false) Long studentId,
                                                 @RequestParam(required = false) Long groupId,
                                                 @RequestParam(required = false) Long sessionSeriesId,
                                                 @RequestParam(required = false) Boolean active,
                                                 @RequestParam(required = false)
                                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
                                                 @RequestParam(required = false)
                                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "10") int size,
                                                 @RequestParam(defaultValue = "dateCreation") String sort,
                                                 @RequestParam(defaultValue = "DESC") String direction) {
        Sort sortObj = Sort.by(Sort.Direction.fromString(direction), sort);
        Pageable pageable = PageRequest.of(page, size, sortObj);

        Page<PaymentDetailEntity> result = paymentDetailAdminService.getAllPaymentDetailsWithFilters(
            studentId, groupId, sessionSeriesId, active, dateFrom, dateTo, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("content", result.getContent());
        response.put("totalElements", result.getTotalElements());
        response.put("totalPages", result.getTotalPages());
        response.put("currentPage", result.getNumber());
        response.put("size", result.getSize());
        return response;
    }

    @PatchMapping("/{id}")
    public PaymentDetailEntity updatePaymentDetail(@PathVariable Long id,
                                                   @RequestBody PaymentDetailUpdateDTO updateDTO,
                                                   @RequestHeader("X-Admin-Name") String adminName) {
        return paymentDetailAdminService.updatePaymentDetail(id, updateDTO, adminName);
    }

    @DeleteMapping("/{id}")
    public Map<String, String> deletePaymentDetail(@PathVariable Long id,
                                                   @RequestBody Map<String, String> body,
                                                   @RequestHeader("X-Admin-Name") String adminName) {
        String reason = body.get("reason");
        paymentDetailAdminService.deletePaymentDetail(id, reason, adminName);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Payment detail deleted successfully");
        return response;
    }

    @GetMapping("/{id}/history")
    public List<PaymentDetailAuditDTO> getPaymentDetailHistory(@PathVariable Long id) {
        return paymentDetailAuditService.getAuditHistory(id);
    }

    @GetMapping("/{id}")
    public PaymentDetailEntity getPaymentDetail(@PathVariable Long id) {
        return paymentDetailRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("PaymentDetail", id));
    }
}
