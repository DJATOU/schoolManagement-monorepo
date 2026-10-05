package com.school.management.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
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

    /**
     * Date_Inscription : un jour, sans heure ni fuseau (exigence 5.4). Facultative : sans elle,
     * l'arrivée est le jour même. Une date future est admise dans l'année scolaire du groupe
     * (exigence 5.2) : le contrôle est fait par le service, qui seul connaît cette année.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate dateAssigned;

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
