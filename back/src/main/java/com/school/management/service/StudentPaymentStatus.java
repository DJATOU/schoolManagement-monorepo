package com.school.management.service;

import com.school.management.dto.StudentDTO;
import lombok.*;

import java.util.Date;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StudentPaymentStatus extends StudentDTO {
    private boolean isPaymentOverdue;

    public StudentPaymentStatus(
            Long id,
            String firstName,
            String lastName,
            String email,
            String gender,
            String phoneNumber,
            Date dateOfBirth,
            String placeOfBirth,
            String photo,
            Long level,
            Set<Long> groupIds,
            Long tutorId,
            String establishment,
            Double averageScore,
            boolean isPaymentOverdue,
            boolean active
    ) {
        // On utilise les setters (hérités de StudentDTO) plutôt que le constructeur
        // positionnel : le code reste stable même si de nouveaux champs sont
        // ajoutés à StudentDTO.
        setId(id);
        setFirstName(firstName);
        setLastName(lastName);
        setEmail(email);
        setGender(gender);
        setPhoneNumber(phoneNumber);
        setDateOfBirth(dateOfBirth);
        setPlaceOfBirth(placeOfBirth);
        setPhoto(photo);
        setLevelId(level);
        setGroupIds(groupIds);
        setTutorId(tutorId);
        setEstablishment(establishment);
        setAverageScore(averageScore);
        setActive(active);
        this.isPaymentOverdue = isPaymentOverdue;
    }

}
