package com.school.management.service.payment;

import com.school.management.dto.PaymentDetailAuditDTO;
import com.school.management.persistance.PaymentDetailAuditEntity;
import com.school.management.repository.PaymentDetailAuditRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PaymentDetailAuditService {

    private final PaymentDetailAuditRepository paymentDetailAuditRepository;

    public PaymentDetailAuditService(PaymentDetailAuditRepository paymentDetailAuditRepository) {
        this.paymentDetailAuditRepository = paymentDetailAuditRepository;
    }

    public void logAction(Long paymentDetailId,
                          String action,
                          String performedBy,
                          String oldValue,
                          String newValue,
                          String reason) {
        PaymentDetailAuditEntity auditEntity = PaymentDetailAuditEntity.builder()
            .paymentDetailId(paymentDetailId)
            .action(action)
            .performedBy(performedBy)
            .oldValue(oldValue)
            .newValue(newValue)
            .reason(reason)
            .build();

        paymentDetailAuditRepository.save(auditEntity);
    }

    public List<PaymentDetailAuditDTO> getAuditHistory(Long paymentDetailId) {
        return paymentDetailAuditRepository.findByPaymentDetailIdOrderByTimestampDesc(paymentDetailId)
            .stream()
            .map(this::convertToDTO)
            .toList();
    }

    public List<PaymentDetailAuditDTO> getAuditsByAdmin(String adminName) {
        return paymentDetailAuditRepository.findByPerformedByOrderByTimestampDesc(adminName)
            .stream()
            .map(this::convertToDTO)
            .toList();
    }

    public PaymentDetailAuditDTO convertToDTO(PaymentDetailAuditEntity auditEntity) {
        return PaymentDetailAuditDTO.builder()
            .id(auditEntity.getId())
            .paymentDetailId(auditEntity.getPaymentDetailId())
            .action(auditEntity.getAction())
            .performedBy(auditEntity.getPerformedBy())
            .timestamp(auditEntity.getTimestamp())
            .oldValue(auditEntity.getOldValue())
            .newValue(auditEntity.getNewValue())
            .reason(auditEntity.getReason())
            .build();
    }
}
