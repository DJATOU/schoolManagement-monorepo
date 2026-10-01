package com.school.management.repository;

import com.school.management.persistance.EncashmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Accès aux Encaissements. Les lectures métier sont ajoutées avec leur service (lot A, A.3). */
@Repository
public interface EncashmentRepository extends JpaRepository<EncashmentEntity, Long> {
}
