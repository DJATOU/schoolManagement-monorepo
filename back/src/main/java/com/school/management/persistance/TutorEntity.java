package com.school.management.persistance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Set;

@Entity
@Table(name = "tutor")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class TutorEntity extends PersonEntity {

    @Column(name = "relationship")
    private String relationship; // e.g., Parent, Guardian, etc.

    @OneToMany(mappedBy = "tutor")
    private Set<StudentEntity> students ;

    @Column(name = "occupation")
    private String occupation;


}
