package com.school.management.repository;

import com.school.management.persistance.PaymentDetailAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentDetailAuditRepository extends JpaRepository<PaymentDetailAuditEntity, Long> {

    List<PaymentDetailAuditEntity> findByPaymentDetailIdOrderByTimestampDesc(Long paymentDetailId);

    List<PaymentDetailAuditEntity> findByPerformedByOrderByTimestampDesc(String performedBy);
}
