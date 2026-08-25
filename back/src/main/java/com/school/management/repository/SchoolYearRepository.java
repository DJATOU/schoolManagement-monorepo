package com.school.management.repository;

import com.school.management.persistance.SchoolYearEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SchoolYearRepository extends JpaRepository<SchoolYearEntity, Long> {

    // Liste des années scolaires, la plus récente en premier (tri par date de début décroissante)
    List<SchoolYearEntity> findAllByOrderByStartDateDesc();

    // Année scolaire courante (au plus une à la fois)
    Optional<SchoolYearEntity> findByIsCurrentTrue();

    // Recherche par libellé (ex. "2025-2026") — utilisée pour garantir l'unicité
    Optional<SchoolYearEntity> findByLabel(String label);

    // count() est fourni par JpaRepository (utilisé pour l'idempotence de la migration)
}
