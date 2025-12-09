package com.school.management.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * DTO for {@link com.school.management.persistance.SessionSeriesEntity}
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionSeriesDto implements Serializable {
    private Long id;

    @NotNull(message = "Name is required")
    @Size(min = 1, max = 255, message = "Name length must be between 1 and 255 characters")
    private String name;

    private LocalDateTime dateCreation;
    private LocalDateTime dateUpdate;
    private String createdBy;
    private String updatedBy;
    private Boolean active;
    private String description;

    @NotNull(message = "Total sessions is required")
    private int totalSessions;

    private Double totalPrice;
    private Double amountPaid;
    private Double balanceDue;

    private int sessionsCompleted;

    @NotNull(message = "Group ID is required")
    private Long groupId;

    private String groupName;

    private Date serieTimeStart;
    private Date serieTimeEnd;

    // Ajouter cette propriété
    private int numberOfSessionsCreated;


}
