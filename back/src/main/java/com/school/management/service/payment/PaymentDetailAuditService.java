package com.school.management.service.payment;

import com.school.management.dto.PaymentDetailAuditDTO;
import com.school.management.persistance.PaymentDetailAuditEntity;
import com.school.management.repository.PaymentDetailAuditRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PaymentDetailAuditService {

    private final PaymentDetailAuditRepository paymentDetailAuditRepository;

    @Autowired
    public PaymentDetailAuditService(PaymentDetailAuditRepository paymentDetailAuditRepository) {
        this.paymentDetailAuditRepository = paymentDetailAuditRepository;
    }

    // logAction retiré avec A.6 (spec admin-corrections) : les lignes de ventilation ne se
    // corrigent plus à l'unité, il n'y a plus rien à tracer ici. L'historique déjà écrit reste
    // consultable, et les corrections d'Encaissement sont tracées dans correction_audit (lot B).

    public List<PaymentDetailAuditDTO> getAuditHistory(Long paymentDetailId) {
        return paymentDetailAuditRepository.findByPaymentDetailIdOrderByTimestampDesc(paymentDetailId)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<PaymentDetailAuditDTO> getAuditsByAdmin(String adminName) {
        return paymentDetailAuditRepository.findByPerformedByOrderByTimestampDesc(adminName)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public PaymentDetailAuditDTO convertToDTO(PaymentDetailAuditEntity entity) {
        return PaymentDetailAuditDTO.builder()
                .id(entity.getId())
                .paymentDetailId(entity.getPaymentDetailId())
                .action(entity.getAction())
                .performedBy(entity.getPerformedBy())
                .timestamp(entity.getTimestamp())
                .oldValue(entity.getOldValue())
                .newValue(entity.getNewValue())
                .reason(entity.getReason())
                .build();
    }
}
