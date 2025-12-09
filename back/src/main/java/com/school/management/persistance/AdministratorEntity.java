package com.school.management.persistance;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "administrator")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AdministratorEntity extends PersonEntity {

    @Column(name = "username")
    private String username;

    @Column(name = "password")
    private String password;


}
