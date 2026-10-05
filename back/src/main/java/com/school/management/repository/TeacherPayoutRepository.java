package com.school.management.repository;

import com.school.management.persistance.PayoutStatus;
import com.school.management.persistance.TeacherPayoutEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface TeacherPayoutRepository extends JpaRepository<TeacherPayoutEntity, Long> {

    /**
     * Paies actives d'une série, de la plus ancienne à la plus récente : la Paie_Initiale d'abord,
     * puis ses Régularisations. L'identifiant départage deux paies de la même milliseconde.
     */
    @Query("SELECT p FROM TeacherPayoutEntity p WHERE p.series.id = :seriesId AND p.status = 'ACTIVE' "
            + "ORDER BY p.paidAt ASC, p.id ASC")
    List<TeacherPayoutEntity> findActiveForSeries(@Param("seriesId") Long seriesId);

    /**
     * Série d'une paie, sans charger la paie : une correction verrouille la série d'abord, puis lit
     * la paie, pour la lire telle que l'a laissée la dernière transaction validée.
     */
    @Query("SELECT p.series.id FROM TeacherPayoutEntity p WHERE p.id = :payoutId")
    Optional<Long> findSeriesIdById(@Param("payoutId") Long payoutId);

    /** Paies actives de plusieurs séries, pour annoter une liste en une requête. */
    @Query("SELECT p FROM TeacherPayoutEntity p WHERE p.series.id IN :seriesIds AND p.status = 'ACTIVE' "
            + "ORDER BY p.paidAt ASC, p.id ASC")
    List<TeacherPayoutEntity> findActiveForSeriesIn(@Param("seriesIds") Collection<Long> seriesIds);

    /**
     * Paies selon les filtres de l'onglet « Paies versées », la plus récente d'abord. Chaque filtre
     * est facultatif ; les bornes de date sont incluses.
     */
    @Query("SELECT p FROM TeacherPayoutEntity p "
            + "WHERE (:teacherId IS NULL OR p.teacher.id = :teacherId) "
            + "AND (:groupId IS NULL OR p.group.id = :groupId) "
            + "AND (:status IS NULL OR p.status = :status) "
            + "AND (CAST(:from AS timestamp) IS NULL OR p.paidAt >= :from) "
            + "AND (CAST(:to AS timestamp) IS NULL OR p.paidAt <= :to) "
            + "ORDER BY p.paidAt DESC, p.id DESC")
    List<TeacherPayoutEntity> search(@Param("teacherId") Long teacherId,
                                     @Param("groupId") Long groupId,
                                     @Param("status") PayoutStatus status,
                                     @Param("from") Date from,
                                     @Param("to") Date to);
}
