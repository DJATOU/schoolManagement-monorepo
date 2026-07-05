package com.school.management.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDetailUpdateDTO {
    private Double amount;
    private Boolean active;
    private String reason;
}
