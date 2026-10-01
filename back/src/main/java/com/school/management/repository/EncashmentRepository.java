package com.school.management.repository;

import com.school.management.persistance.EncashmentEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Accès aux Encaissements. */
@Repository
public interface EncashmentRepository extends JpaRepository<EncashmentEntity, Long> {

    /**
     * Encaissement verrouillé en écriture jusqu'à la fin de la transaction : deux annulations
     * simultanées du même encaissement passent l'une après l'autre, et la seconde le trouve annulé.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EncashmentEntity e WHERE e.id = :id")
    Optional<EncashmentEntity> findByIdForUpdate(@Param("id") Long id);
}
