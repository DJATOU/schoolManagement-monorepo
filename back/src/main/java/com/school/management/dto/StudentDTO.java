package com.school.management.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Date;
import java.util.Set;

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

    @NotNull(message = "Date of birth is required")
    private Date dateOfBirth;

    private String placeOfBirth;

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

    private Boolean active; // Ajouter ce champ


}
