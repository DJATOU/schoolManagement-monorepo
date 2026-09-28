package com.school.management.repository;

import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.lang.NonNull;

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

    // Étudiants par statut (ACTIVE / INACTIVE) — listing actifs/inactifs
    List<StudentEntity> findByStatus(StudentStatus status);

    long countByActiveTrue();

    @Query("SELECT COUNT(s) FROM StudentEntity s WHERE s.active = true AND LOWER(s.gender) IN :genders")
    long countActiveByGenderIn(@Param("genders") List<String> genders);

    long countByActiveTrueAndDateCreationBetween(java.time.LocalDateTime from, java.time.LocalDateTime to);

    long countByActiveFalse();

    /*
     * Comptages d'effectif basés sur le STATUT D'INSCRIPTION (status) et non sur le
     * drapeau technique `active` de BaseEntity.
     *
     * Désactiver un étudiant depuis l'application appelle deactivateStudentStatus(), qui
     * pose status = INACTIVE et laisse `active` à true. Les listings filtrent bien sur le
     * statut, mais le tableau de bord comptait `active` : l'effectif ne bougeait donc
     * jamais. Les deux notions coexistent (`active` = suppression logique technique), on
     * exige donc les deux : statut demandé ET enregistrement non supprimé.
     */

    /** Effectif par statut d'inscription, hors enregistrements supprimés logiquement. */
    @Query("SELECT COUNT(s) FROM StudentEntity s "
            + "WHERE s.status = :status AND (s.active IS NULL OR s.active = true)")
    long countByEnrollmentStatus(@Param("status") StudentStatus status);

    /** Inscriptions créées dans la période, pour un statut donné. */
    @Query("SELECT COUNT(s) FROM StudentEntity s "
            + "WHERE s.status = :status AND (s.active IS NULL OR s.active = true) "
            + "AND s.dateCreation BETWEEN :from AND :to")
    long countByEnrollmentStatusAndDateCreationBetween(@Param("status") StudentStatus status,
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to);

    /** Effectif par statut d'inscription et par genre. */
    @Query("SELECT COUNT(s) FROM StudentEntity s "
            + "WHERE s.status = :status AND (s.active IS NULL OR s.active = true) "
            + "AND LOWER(s.gender) IN :genders")
    long countByEnrollmentStatusAndGenderIn(@Param("status") StudentStatus status,
            @Param("genders") List<String> genders);

    @Query("SELECT s FROM StudentEntity s " +
            "LEFT JOIN FETCH s.groups g " +
            "LEFT JOIN FETCH g.series ser " +
            "LEFT JOIN FETCH ser.sessions sess " +
            "WHERE s.id = :studentId")
    StudentEntity findStudentWithAllData(@Param("studentId") Long studentId);

    @EntityGraph(value = "Student.withAllData", type = EntityGraph.EntityGraphType.LOAD)
    @NonNull
    Optional<StudentEntity> findById(@NonNull Long id);
}
