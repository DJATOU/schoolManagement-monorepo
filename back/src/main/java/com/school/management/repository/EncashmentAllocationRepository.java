package com.school.management.repository;

import com.school.management.persistance.EncashmentAllocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Accès aux Imputations. Les lectures métier sont ajoutées avec leur service (lot A, A.3). */
@Repository
public interface EncashmentAllocationRepository extends JpaRepository<EncashmentAllocationEntity, Long> {
}
