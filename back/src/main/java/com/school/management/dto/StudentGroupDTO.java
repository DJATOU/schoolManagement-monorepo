package com.school.management.dto;

import lombok.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.util.Date;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class StudentGroupDTO {

    private Long studentId;
    private List<Long> groupIds;
    private Long groupId;
    private List<Long> studentIds;

    @PastOrPresent(message = "Date assigned cannot be in the future.")
    private Date dateAssigned;

    @NotNull(message = "Assigned by cannot be null.")
    @Size(min = 1, message = "Assigned by cannot be empty.")
    private String assignedBy;

    @Size(max = 500, message = "Note cannot be longer than 500 characters.")
    private String description;

    public boolean isAddingStudentToGroups() {
        return studentId != null && groupIds != null && !groupIds.isEmpty();
    }

    // Méthode pour vérifier si on ajoute des étudiants à un groupe
    public boolean isAddingStudentsToGroup() {
        return groupId != null && studentIds != null && !studentIds.isEmpty();
    }
}
