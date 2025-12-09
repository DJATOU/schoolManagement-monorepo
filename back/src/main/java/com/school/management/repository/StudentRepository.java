package com.school.management.repository;

import com.school.management.persistance.StudentEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<StudentEntity, Long> {

    List<StudentEntity> findByGroups_Id(Long groupId);

    List<StudentEntity> findByLevelId(Long levelId);

    List<StudentEntity> findByEstablishment(String establishment);

    // find student by last name
    List<StudentEntity> findByLastName(String lastName);

    // find student by first name and last name
    List<StudentEntity> findByFirstNameAndLastName(String firstName, String lastName);

    List<StudentEntity> findAllByActiveTrue();

    @Query("SELECT s FROM StudentEntity s " +
            "LEFT JOIN FETCH s.groups g " +
            "LEFT JOIN FETCH g.series ser " +
            "LEFT JOIN FETCH ser.sessions sess " +
            "WHERE s.id = :studentId")
    StudentEntity findStudentWithAllData(@Param("studentId") Long studentId);

    @EntityGraph(value = "Student.withAllData", type = EntityGraph.EntityGraphType.LOAD)
    Optional<StudentEntity> findById(Long id);
}
