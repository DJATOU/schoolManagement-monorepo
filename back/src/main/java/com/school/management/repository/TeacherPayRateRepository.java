package com.school.management.repository;

import com.school.management.persistance.TeacherPayRateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeacherPayRateRepository extends JpaRepository<TeacherPayRateEntity, Long> {

    /** Taux du catalogue, actifs d'abord, puis par pourcentage et libellé. */
    @Query("SELECT r FROM TeacherPayRateEntity r "
            + "ORDER BY CASE WHEN r.active = true THEN 0 ELSE 1 END, r.teacherPercent, lower(r.label)")
    List<TeacherPayRateEntity> findAllOrdered();

    /**
     * Vrai si un autre taux actif porte déjà ce libellé, casse et espaces de bord ignorés : deux
     * taux de même libellé seraient indiscernables dans le dialogue de paie (exigence 1.3).
     *
     * @param excludedId taux à ignorer (celui qu'on modifie) ; {@code -1} pour une création
     */
    @Query("SELECT COUNT(r) > 0 FROM TeacherPayRateEntity r WHERE r.active = true "
            + "AND lower(trim(r.label)) = lower(trim(:label)) AND r.id <> :excludedId")
    boolean existsActiveLabel(@Param("label") String label, @Param("excludedId") Long excludedId);
}
