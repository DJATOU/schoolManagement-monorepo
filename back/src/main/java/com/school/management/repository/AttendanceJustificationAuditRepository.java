package com.school.management.repository;

import com.school.management.persistance.AttendanceJustificationAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Accès à la piste d'audit de la justification des absences (exigence 5).
 *
 * <p>Aucune méthode de modification ni de suppression n'est exposée : une trace est écrite une fois
 * puis seulement lue (exigence 5.3). Les méthodes d'écriture héritées de {@link JpaRepository} ne
 * sont utilisées qu'à la création.</p>
 */
@Repository
public interface AttendanceJustificationAuditRepository
        extends JpaRepository<AttendanceJustificationAuditEntity, Long> {

    /**
     * Piste d'audit d'une présence, de la plus récente à la plus ancienne (exigence 5.7).
     *
     * <p>Le tri porte sur l'horodatage <em>puis</em> sur le rang de séquence : deux modifications
     * survenues dans la même milliseconde ne seraient sinon pas ordonnées de façon déterministe,
     * et « la plus récente » deviendrait ambiguë.</p>
     */
    @Query("SELECT a FROM AttendanceJustificationAuditEntity a "
            + "WHERE a.attendanceId = :attendanceId "
            + "ORDER BY a.performedAt DESC, a.sequenceRank DESC")
    List<AttendanceJustificationAuditEntity> findTrail(@Param("attendanceId") Long attendanceId);

    /**
     * Entrée la plus récente d'une présence, dont la valeur appliquée doit égaler la valeur
     * courante de la justification (exigence 5.8).
     */
    @Query("SELECT a FROM AttendanceJustificationAuditEntity a "
            + "WHERE a.attendanceId = :attendanceId "
            + "ORDER BY a.performedAt DESC, a.sequenceRank DESC LIMIT 1")
    Optional<AttendanceJustificationAuditEntity> findLatest(@Param("attendanceId") Long attendanceId);

    /**
     * Rang de séquence le plus élevé déjà attribué pour cette présence, ou 0 si aucune entrée.
     * Le service en dérive le rang suivant.
     */
    @Query("SELECT COALESCE(MAX(a.sequenceRank), 0) FROM AttendanceJustificationAuditEntity a "
            + "WHERE a.attendanceId = :attendanceId")
    long findMaxSequenceRank(@Param("attendanceId") Long attendanceId);
}
