package com.school.management.repository;

import com.school.management.persistance.TutorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TutorRepository extends JpaRepository<TutorEntity, Long> {

    // Custom methods:
    List<TutorEntity> findByLastName(String lastName);

    List<TutorEntity> findByFirstNameAndLastName(String firstName, String lastName);

    // Method to find tutors associated with a specific student
    List<TutorEntity> findByStudents_Id(Long studentId);

}
