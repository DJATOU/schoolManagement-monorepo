package com.school.management.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.school.management.persistance.StudentStatus;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.Set;

import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString
public class StudentDTO {

    private Long id;

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Gender is required")
    private String gender;

    @Email(message = "Email should be valid")
    private String email;

    private String phoneNumber;

    private String nationality;

    private String communicationPreference;

    // Facultative : un élève importé par CSV n'en a pas, et doit rester modifiable (PUT @Valid).
    // Le formulaire d'inscription l'exige côté écran.
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate dateOfBirth;

    private String placeOfBirth;

    private String address;

    private String city;

    @JsonProperty("photo")
    private String photo;

    @NotNull(message = "Level is required")
    private Long levelId; // Reference the level by its ID

    private Set<Long> groupIds; // Assuming groups are identified by their IDs

    private Long tutorId; // Assuming tutor is identified by their ID

    private String establishment;

    @Min(0)
    @Max(100)
    private Double averageScore;

    // Remarques libres sur l'étudiant. Le champ existe sur l'entité (BaseEntity) et était
    // saisi à l'inscription, mais absent du DTO : sa valeur n'était donc jamais persistée.
    private String description;

    private Boolean active; // Ajouter ce champ

    // SCHOOL YEAR: statut d'inscription de l'étudiant (ACTIVE / INACTIVE) — Requirement 7.1
    private StudentStatus status;

}
