package com.school.management.repository;

import com.school.management.persistance.SessionSeriesEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionSeriesRepository extends JpaRepository<SessionSeriesEntity, Long> {


    /**
     * Séries d'un groupe, dans leur ordre d'ajout (identifiant croissant).
     *
     * <p>L'ordre est fixé ici, à la source, plutôt que laissé à la base : sans clause
     * {@code ORDER BY}, PostgreSQL renvoie les lignes dans un ordre non garanti et les listes
     * de séries apparaissaient mélangées (« 09-2026-002 » avant « Sept 2025 »). Tous les
     * appelants — sélecteur de paiement, relevé, historique — en bénéficient.</p>
     */
    @EntityGraph(attributePaths = {"sessions"})
    @Query("SELECT s FROM SessionSeriesEntity s WHERE s.group.id = :id ORDER BY s.id ASC")
    List<SessionSeriesEntity> findByGroupId(@Param("id") Long id);

    /**
     * La série, verrouillée en écriture jusqu'à la fin de la transaction.
     *
     * <p>Sérialise les paies d'une même série (spec teacher-payroll, D6) : une seconde confirmation
     * attend la première, puis trouve la série déjà payée. L'index unique des paies initiales actives
     * reste le filet de sécurité.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SessionSeriesEntity s WHERE s.id = :id")
    Optional<SessionSeriesEntity> findByIdForUpdate(@Param("id") Long id);


}
