package com.school.management.service.payment;

import com.school.management.dto.PaymentDetailUpdateDTO;
import com.school.management.persistance.PaymentDetailEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.repository.PaymentDetailRepository;
import com.school.management.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.Objects;

@Service
public class PaymentDetailAdminService {

    private final PaymentDetailRepository paymentDetailRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentDetailAuditService paymentDetailAuditService;

    @Autowired
    public PaymentDetailAdminService(PaymentDetailRepository paymentDetailRepository,
                                     PaymentRepository paymentRepository,
                                     PaymentDetailAuditService paymentDetailAuditService) {
        this.paymentDetailRepository = paymentDetailRepository;
        this.paymentRepository = paymentRepository;
        this.paymentDetailAuditService = paymentDetailAuditService;
    }

    @Transactional(readOnly = true)
    public Page<PaymentDetailEntity> getAllPaymentDetailsWithFilters(Long studentId,
                                                                     Long groupId,
                                                                     Long sessionSeriesId,
                                                                     Boolean active,
                                                                     Date dateFrom,
                                                                     Date dateTo,
                                                                     Pageable pageable) {
        return paymentDetailRepository.findAllWithFilters(studentId, groupId, sessionSeriesId, active, dateFrom, dateTo, pageable);
    }

    @Transactional(readOnly = true)
    public PaymentDetailEntity getPaymentDetail(Long id) {
        return paymentDetailRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment detail not found with id: " + id));
    }

    @Transactional
    public PaymentDetailEntity updatePaymentDetail(Long id, PaymentDetailUpdateDTO updateDTO, String adminName) {
        validateReason(updateDTO.getReason());

        PaymentDetailEntity detail = paymentDetailRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment detail not found with id: " + id));

        String oldValue = buildValueString(detail);

        if (updateDTO.getAmount() != null) {
            detail.setAmountPaid(updateDTO.getAmount());
        }
        if (updateDTO.getActive() != null) {
            detail.setActive(updateDTO.getActive());
        }

        String newValue = buildValueString(detail);
        paymentDetailRepository.save(detail);

        paymentDetailAuditService.logAction(id, "MODIFIED", adminName, oldValue, newValue, updateDTO.getReason());
        recalculatePayment(detail.getPayment().getId());

        return detail;
    }

    @Transactional
    public void deletePaymentDetail(Long id, String reason, String adminName) {
        validateReason(reason);

        PaymentDetailEntity detail = paymentDetailRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment detail not found with id: " + id));

        String oldValue = buildValueString(detail);
        detail.setActive(false);
        paymentDetailRepository.save(detail);

        paymentDetailAuditService.logAction(id, "DELETED", adminName, oldValue, buildValueString(detail), reason);
        recalculatePayment(detail.getPayment().getId());
    }

    @Transactional
    public void recalculatePayment(Long paymentId) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found with id: " + paymentId));

        double totalPaid = paymentDetailRepository.findByPaymentIdAndActiveTrue(paymentId).stream()
                .map(PaymentDetailEntity::getAmountPaid)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();

        payment.setAmountPaid(totalPaid);

        double expectedAmount = 0;
        if (payment.getGroup() != null && payment.getGroup().getPrice() != null && payment.getGroup().getPrice().getPrice() != null) {
            double pricePerSession = payment.getGroup().getPrice().getPrice();
            int sessions = 1;
            if (payment.getSessionSeries() != null && payment.getSessionSeries().getTotalSessions() > 0) {
                sessions = payment.getSessionSeries().getTotalSessions();
            }
            expectedAmount = pricePerSession * sessions;
        }

        if (totalPaid <= 0) {
            payment.setStatus("PENDING");
        } else if (expectedAmount > 0 && totalPaid >= expectedAmount) {
            payment.setStatus("COMPLETED");
        } else {
            payment.setStatus("IN_PROGRESS");
        }

        paymentRepository.save(payment);
    }

    private void validateReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new IllegalArgumentException("Reason is required for audit logging.");
        }
    }

    private String buildValueString(PaymentDetailEntity detail) {
        return "PaymentDetail{" +
                "id=" + detail.getId() +
                ", amountPaid=" + detail.getAmountPaid() +
                ", active=" + detail.getActive() +
                ", sessionId=" + (detail.getSession() != null ? detail.getSession().getId() : null) +
                ", paymentId=" + (detail.getPayment() != null ? detail.getPayment().getId() : null) +
                '}';
    }
}
