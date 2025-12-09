package com.school.management.dto;

import lombok.*;

import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentDetailDTO {
    private Long paymentDetailId;
    private Long sessionId;
    private String sessionName;
    private Double amountPaid;
    private Double remainingBalance;
    private Date paymentDate; // Ajoutez ce champp
}

