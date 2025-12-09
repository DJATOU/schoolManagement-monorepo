package com.school.management.dto;

import lombok.*;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TutorDTO {

    private Long id;
    private String firstName;
    private String lastName;
    private String gender;
    private String email;
    private String phoneNumber;
    private Date dateOfBirth;
    private String placeOfBirth;
    private String relationship;
    private Boolean active;
    private Date dateCreation;
    private Date dateUpdate;

}
