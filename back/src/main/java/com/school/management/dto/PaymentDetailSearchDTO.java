package com.school.management.dto;

import lombok.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * DTO for Payment Management Search Results
 * Contains complete payment information including student, group, series, and session details
 */
@Getter
@Setter
@NoArgsConstructor
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

    /**
     * Constructor matching the JPQL query field order and types
     * IMPORTANT: This constructor must match EXACTLY the order and types from Hibernate
     * dateCreation comes as LocalDateTime from BaseEntity
     * paymentDate comes as Timestamp from PaymentDetailEntity
     */
    public PaymentDetailSearchDTO(
            Long id,
            String studentFirstName,
            String studentLastName,
            Long studentId,
            String groupName,
            Long groupId,
            String seriesName,
            Long seriesId,
            String sessionName,
            Long sessionId,
            Double amountPaid,
            Boolean active,
            Boolean permanentlyDeleted,
            LocalDateTime dateCreation,
            Timestamp paymentDate,
            Long paymentId,
            String paymentStatus,
            Boolean isCatchUp
    ) {
        this.id = id;
        this.studentFirstName = studentFirstName;
        this.studentLastName = studentLastName;
        this.studentId = studentId;
        this.groupName = groupName;
        this.groupId = groupId;
        this.seriesName = seriesName;
        this.seriesId = seriesId;
        this.sessionName = sessionName;
        this.sessionId = sessionId;
        this.amountPaid = amountPaid;
        this.active = active;
        this.permanentlyDeleted = permanentlyDeleted;
        // Convert LocalDateTime to Date for frontend compatibility
        this.dateCreation = dateCreation != null ? java.sql.Timestamp.valueOf(dateCreation) : null;
        this.paymentDate = paymentDate;
        this.paymentId = paymentId;
        this.paymentStatus = paymentStatus;
        this.isCatchUp = isCatchUp;
    }
}
