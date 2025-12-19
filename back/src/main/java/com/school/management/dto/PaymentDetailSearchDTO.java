package com.school.management.dto;

import lombok.*;

import java.util.Date;

/**
 * DTO for Payment Management Search Results
 * Contains complete payment information including student, group, series, and session details
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentDetailSearchDTO {
    private Long id;

    // Student information
    private String studentFirstName;
    private String studentLastName;
    private Long studentId;

    // Group information
    private String groupName;
    private Long groupId;

    // Series information
    private String seriesName;
    private Long seriesId;

    // Session information
    private String sessionName;
    private Long sessionId;

    // Payment details
    private Double amountPaid;
    private Boolean active;
    private Boolean permanentlyDeleted;
    private Date dateCreation;
    private Date paymentDate;

    // Payment parent information
    private Long paymentId;
    private String paymentStatus;

    // Additional info
    private Boolean isCatchUp;
}
