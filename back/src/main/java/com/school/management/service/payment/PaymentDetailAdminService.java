package com.school.management.service.payment;

import com.school.management.dto.PaymentDetailUpdateDTO;
import com.school.management.persistance.PaymentDetailEntity;
import com.school.management.persistance.PaymentEntity;
import com.school.management.repository.PaymentDetailRepository;
import com.school.management.repository.PaymentRepository;
import com.school.management.shared.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PaymentDetailAdminService {

    private final PaymentDetailRepository paymentDetailRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentDetailAuditService paymentDetailAuditService;

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
                                                                     LocalDateTime dateFrom,
                                                                     LocalDateTime dateTo,
                                                                     Pageable pageable) {
        return paymentDetailRepository.findAllWithFilters(studentId, groupId, sessionSeriesId, active, dateFrom, dateTo, pageable);
    }

    @Transactional
    public PaymentDetailEntity updatePaymentDetail(Long id, PaymentDetailUpdateDTO updateDTO, String adminName) {
        validateReason(updateDTO.getReason());

        PaymentDetailEntity detail = paymentDetailRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("PaymentDetail", id));

        String oldValue = buildValueString(detail);

        if (updateDTO.getAmount() != null) {
            detail.setAmountPaid(updateDTO.getAmount());
        }

        if (updateDTO.getActive() != null) {
            detail.setActive(updateDTO.getActive());
        }

        PaymentDetailEntity updatedDetail = paymentDetailRepository.save(detail);
        String newValue = buildValueString(updatedDetail);

        paymentDetailAuditService.logAction(
            updatedDetail.getId(),
            "MODIFIED",
            adminName,
            oldValue,
            newValue,
            updateDTO.getReason()
        );

        recalculatePayment(updatedDetail.getPayment().getId());
        return updatedDetail;
    }

    @Transactional
    public PaymentDetailEntity deletePaymentDetail(Long id, String reason, String adminName) {
        validateReason(reason);

        PaymentDetailEntity detail = paymentDetailRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("PaymentDetail", id));

        String oldValue = buildValueString(detail);
        detail.setActive(false);

        PaymentDetailEntity updatedDetail = paymentDetailRepository.save(detail);

        paymentDetailAuditService.logAction(
            updatedDetail.getId(),
            "DELETED",
            adminName,
            oldValue,
            buildValueString(updatedDetail),
            reason
        );

        recalculatePayment(updatedDetail.getPayment().getId());
        return updatedDetail;
    }

    @Transactional
    public void recalculatePayment(Long paymentId) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        List<PaymentDetailEntity> activeDetails = paymentDetailRepository.findByPaymentIdAndActiveTrue(paymentId);
        double totalPaid = activeDetails.stream()
            .map(PaymentDetailEntity::getAmountPaid)
            .filter(amount -> amount != null)
            .mapToDouble(Double::doubleValue)
            .sum();

        payment.setAmountPaid(totalPaid);

        Double expectedAmount = null;
        if (payment.getGroup() != null && payment.getGroup().getPrice() != null) {
            expectedAmount = payment.getGroup().getPrice().getPrice();
        }

        if (totalPaid <= 0) {
            payment.setStatus("PENDING");
        } else if (expectedAmount != null && totalPaid >= expectedAmount) {
            payment.setStatus("COMPLETED");
        } else {
            payment.setStatus("IN_PROGRESS");
        }

        paymentRepository.save(payment);
    }

    private void validateReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Reason is required for audit logging");
        }
    }

    private String buildValueString(PaymentDetailEntity detail) {
        return String.format(
            "PaymentDetail{id=%d, paymentId=%d, sessionId=%s, amount=%.2f, active=%s}",
            detail.getId(),
            detail.getPayment() != null ? detail.getPayment().getId() : null,
            detail.getSession() != null ? detail.getSession().getId() : null,
            detail.getAmountPaid() != null ? detail.getAmountPaid() : 0.0,
            detail.getActive()
        );
    }
}
