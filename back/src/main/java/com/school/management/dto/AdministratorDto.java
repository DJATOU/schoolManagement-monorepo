package com.school.management.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Email;
import lombok.Value;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO for {@link com.school.management.persistance.AdministratorEntity}
 */
@Value
public class AdministratorDto implements Serializable {
    LocalDateTime dateCreation;
    LocalDateTime dateUpdate;
    String createdBy;
    String updatedBy;
    Boolean active;
    String description;
    Long id;
    String firstName;
    String lastName;
    String gender;
    @Email
    String email;
    String phoneNumber;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    LocalDate dateOfBirth;
    String placeOfBirth;
    String address;
    byte[] photo;
    String communicationPreference;
    String username;
    String password;
}