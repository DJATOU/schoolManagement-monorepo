package com.school.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentDetailAuditDTO {
    private Long id;
    private Long paymentDetailId;
    private String action;
    private String performedBy;
    private LocalDateTime timestamp;
    private String oldValue;
    private String newValue;
    private String reason;
}
