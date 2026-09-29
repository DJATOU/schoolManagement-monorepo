package com.school.management.repository;

import com.school.management.persistance.SubjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubjectRepository extends JpaRepository<SubjectEntity, Long> {

    List<SubjectEntity> findByNameContaining(String name);

    // Garde d'import : voir LevelRepository.existsByName.
    boolean existsByName(String name);

    // Add other custom methods if needed
}
