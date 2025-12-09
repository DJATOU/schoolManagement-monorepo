package com.school.management.dto;

import lombok.*;

import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceDTO {

    private Long id;
    private Long studentId; // ID of the student
    private Long sessionId; // ID of the session
    private Boolean isPresent;
    private Boolean isJustified;

    private Long sessionSeriesId; // ID of the session series
    private Long groupId; // ID of the group

    // Dates from BaseEntity
    private Date dateCreation;
    private Date dateUpdate;
    private String createdBy;
    private String updatedBy;
    private Boolean active;
    private String description;
    private Boolean isCatchUp;

    // Additional fields if necessary...
}
