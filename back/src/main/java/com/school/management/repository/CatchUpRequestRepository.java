package com.school.management.repository;

import com.school.management.persistance.CatchUpRequestEntity;
import com.school.management.persistance.CatchUpStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Accès aux données des demandes de rattrapage (catch-up).
 *
 * <p>Fournit les recherches nécessaires au workflow de rattrapage : liste des
 * demandes par statut (par exemple les demandes en attente) et liste des demandes
 * d'un étudiant.</p>
 */
@Repository
public interface CatchUpRequestRepository extends JpaRepository<CatchUpRequestEntity, Long> {

    // Demandes filtrées par statut (ex. PENDING pour la liste des demandes en attente)
    List<CatchUpRequestEntity> findByStatus(CatchUpStatus status);

    // Demandes rattachées à un étudiant donné
    List<CatchUpRequestEntity> findByStudentId(Long studentId);
}
