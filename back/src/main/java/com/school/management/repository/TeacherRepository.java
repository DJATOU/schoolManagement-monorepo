package com.school.management.repository;

import com.school.management.persistance.TeacherEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeacherRepository extends JpaRepository<TeacherEntity, Long> {

    // Custom methods:
    List<TeacherEntity> findByLastName(String lastName);

    List<TeacherEntity> findByFirstNameAndLastName(String firstName, String lastName);

    // Method to find teachers associated with a specific group
    List<TeacherEntity> findByGroups_Id(Long groupId);

    @Query("SELECT CONCAT(t.firstName, ' ', t.lastName) FROM TeacherEntity t WHERE t.id = :id")
    Optional<String> findTeacherNameById(Long id);
}
