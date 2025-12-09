package com.school.management.controller;

import com.school.management.dto.PaymentDetailDTO;
import com.school.management.service.PaymentDetailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentDetailController {

    private final PaymentDetailService paymentDetailService;

    @Autowired
    public PaymentDetailController(PaymentDetailService paymentDetailService) {
        this.paymentDetailService = paymentDetailService;
    }

    @GetMapping("/process/{studentId}/series/{sessionSeriesId}")
    public ResponseEntity<List<PaymentDetailDTO>> getPaymentDetailsForSessionsInSeries(
            @PathVariable Long studentId,
            @PathVariable Long sessionSeriesId) {
        List<PaymentDetailDTO> paymentDetails = paymentDetailService.getPaymentDetailsForSessionsInSeries(studentId, sessionSeriesId);
        return ResponseEntity.ok(paymentDetails);
    }

}
