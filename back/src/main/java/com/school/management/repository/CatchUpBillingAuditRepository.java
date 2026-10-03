package com.school.management.repository;

import com.school.management.persistance.CatchUpBillingAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Lecture et écriture du journal des décisions de facturation d'un rattrapage.
 *
 * <p>Aucune méthode de suppression ni de mise à jour n'est exposée : le journal est en ajout seul.
 * Offrir un {@code delete} ici suffirait à rendre la trace contestable.</p>
 */
@Repository
public interface CatchUpBillingAuditRepository extends JpaRepository<CatchUpBillingAuditEntity, Long> {

    /**
     * Historique d'une présence, de la correction la plus récente à la plus ancienne.
     *
     * <p>{@code sequenceRank} départage deux entrées de même horodatage : sans lui, la réponse à
     * « quelle est la dernière décision ? » dépendrait de l'ordre de lecture du moteur.</p>
     */
    List<CatchUpBillingAuditEntity> findByAttendanceIdOrderByPerformedAtDescSequenceRankDesc(Long attendanceId);

    /**
     * Rang de séquence suivant pour une présence.
     *
     * <p>Le rang est propre à chaque présence, et non global : deux rattrapages corrigés en
     * parallèle n'ont pas à se disputer un compteur commun.</p>
     */
    @Query("SELECT COALESCE(MAX(a.sequenceRank), 0) + 1 FROM CatchUpBillingAuditEntity a "
            + "WHERE a.attendanceId = :attendanceId")
    long nextSequenceRank(@Param("attendanceId") Long attendanceId);

    /** Décisions portant sur ces présences, pour le Journal d'un élève (exigence 12.1). */
    List<CatchUpBillingAuditEntity> findByAttendanceIdIn(Collection<Long> attendanceIds);
}
