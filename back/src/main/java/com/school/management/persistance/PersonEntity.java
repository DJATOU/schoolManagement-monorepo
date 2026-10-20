package com.school.management.persistance;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public abstract class PersonEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name ="gender")
    private String gender;

    @Column(name = "email")
    @Email
    private String email;

    @Column(name = "phone_number")
    private String phoneNumber;

    // Jour du calendrier, sans heure ni fuseau : sérialisé "yyyy-MM-dd". Annoté ici aussi car
    // TeacherEntity est lu tel quel en corps de requête (PUT /api/teachers/{id}) et TutorEntity
    // renvoyé tel quel (GET /api/students/{id}/tutor).
    @Column(name = "date_of_birth")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate dateOfBirth;

    @Column(name = "place_of_birth")
    private String placeOfBirth;

    @Column(name = "address")
    private String address;

    @Column(name = "city")
    private String city;

    @Column(name = "photo")
    private String photo;

    @Column(name = "communication_preference")
    private String communicationPreference;

    @Column(name = "nationality")
    private String nationality;

}