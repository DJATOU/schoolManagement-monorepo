package com.school.management.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.Date;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeacherDTO {

    Long id;

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Gender is required")
    private String gender;

    @Email(message = "Email should be valid")
    private String email;

    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    @NotNull(message = "Date of birth is required")
    private Date dateOfBirth;

    @NotBlank(message = "Place of birth is required")
    private String placeOfBirth;

    // Les champs supplémentaires
    @NotBlank(message = "Address is required")
    private String address;

    private String communicationPreference;

    private String specialization;

    @NotBlank(message = "Qualifications are required")
    private String qualifications;

    @Min(0)
    private Integer yearsOfExperience;

    // Assuming groups are identified by their DTOs
    private Set<GroupDTO> groups;

    private String photo;
}
