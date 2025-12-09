package com.school.management.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentDTO {

    @NotBlank(message = "Student ID cannot be null")
    private Long studentId;

    @NotBlank(message = "Session ID cannot be null")
    private Long sessionId;

    @NotBlank(message = "Session Series ID cannot be null")
    private Long sessionSeriesId;

    @NotBlank(message = "Amount paid cannot be null")
    @Min(value = 0, message = "Amount paid must be greater than or equal to 0")
    private Double amountPaid;

    private Date paymentForMonth;
    private String status;
    private String paymentMethod;
    private String paymentDescription;

    @NotBlank(message = "Group ID cannot be null")
    private Long groupId;

    // Additional fields for detailed information
    private Double totalSeriesCost;
    private Double totalPaidForSeries;
    private Double amountOwed;


}
