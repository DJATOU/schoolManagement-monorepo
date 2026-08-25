package com.school.management.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.Date;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupDTO {

    private Long id;

    @NotNull(message = "Group name is required")
    private String name;

    @NotNull(message = "Group type ID is required")
    private Long groupTypeId;

    private String groupTypeName; // Add this field to hold the name of the group type

    @NotNull(message = "Level ID is required")
    private Long levelId;

    private String levelName; // Add this field to hold the name of the level

    @NotNull(message = "Subject ID is required")
    private Long subjectId;

    private String subjectName; // Add this field to hold the name of the subject

    @Min(1)
    private int sessionNumberPerSerie;

    @NotNull(message = "Price ID is required")
    private Long priceId;

    private Double priceAmount; // Add this field to hold the amount of the price

    private Date dateCreation;
    private Date dateUpdate;
    private Boolean active;

    private String description;

    @NotNull(message = "Teacher ID is required")
    private Long teacherId;

    private String teacherName; // Add this field to hold the teacher's name

    // SCHOOL YEAR: année scolaire à laquelle appartient le groupe (Requirement 3.1)
    private Long schoolYearId;

    // Libellé de l'année scolaire (ex. "2025-2026"), renseigné en lecture pour l'affichage
    private String schoolYearLabel;

    private String photo; // PHASE 3A: Photo filename

    private Set<Long> studentIds; // IDs of students in the group

    private boolean isCatchUp;
}
