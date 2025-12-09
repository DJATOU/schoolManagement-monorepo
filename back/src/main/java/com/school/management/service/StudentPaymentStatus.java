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
        super(id, firstName, lastName, gender, email, phoneNumber, dateOfBirth, placeOfBirth, photo, level, groupIds, tutorId, establishment, averageScore, active);
        this.isPaymentOverdue = isPaymentOverdue;
    }

}
