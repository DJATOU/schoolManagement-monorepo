package com.school.management.repository;

import com.school.management.persistance.AdministratorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdministratorRepository extends JpaRepository<AdministratorEntity, Long> {

    AdministratorEntity findByUsername(String username);

}
