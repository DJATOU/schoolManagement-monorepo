package com.school.management.dto;

import jakarta.validation.constraints.Email;
import lombok.Value;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

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
    Date dateOfBirth;
    String placeOfBirth;
    String address;
    byte[] photo;
    String communicationPreference;
    String username;
    String password;
}