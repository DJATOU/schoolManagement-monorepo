package com.school.management.repository;

import com.school.management.persistance.SessionEntity;
import com.school.management.persistance.SessionSeriesEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SessionRepository extends JpaRepository<SessionEntity, Long>, JpaSpecificationExecutor<SessionEntity> {

    List<SessionEntity> findByGroupId(Long groupId);


    List<SessionEntity> findBySessionSeries(SessionSeriesEntity series);

    @Query("SELECT s from SessionEntity s JOIN FETCH s.group g JOIN FETCH s.room r JOIN FETCH s.teacher t")
    List<SessionEntity> findAllWithDetails();

    /**
     * Séances d'une série, de la plus ancienne à la plus récente.
     *
     * <p>La requête ne portait aucun tri : l'ordre venait de la base et variait d'un appel à
     * l'autre. La liste des séances d'une série s'affichait donc en désordre (une séance
     * d'octobre avant celles de septembre), et la numérotation des lignes ne correspondait à
     * rien. Les séances sans date de début passent en dernier.</p>
     */
    @Query("SELECT s FROM SessionEntity s WHERE s.sessionSeries.id = :sessionSeriesId "
            + "ORDER BY s.sessionTimeStart ASC NULLS LAST, s.id ASC")
    List<SessionEntity> findBySessionSeriesId(
            @org.springframework.data.repository.query.Param("sessionSeriesId") Long sessionSeriesId);

    List<SessionEntity> findBySessionTimeStartBetween(LocalDateTime start, LocalDateTime end);

    List<SessionEntity> findByGroupIdAndSessionTimeStartBetween(Long groupId, LocalDateTime start, LocalDateTime end);

    int countBySessionSeriesId(Long sessionSeriesId);

    /*
     * ===== Détection de chevauchement =====
     *
     * Deux créneaux se chevauchent si « début < finAutre ET fin > débutAutre ». Les
     * séances désactivées sont ignorées : elles ne réservent plus la ressource.
     */

    /** Vrai si la salle est déjà occupée sur le créneau. */
    @Query("SELECT COUNT(s) > 0 FROM SessionEntity s "
            + "WHERE s.room.id = :roomId AND (s.active IS NULL OR s.active = true) "
            + "AND s.sessionTimeStart < :end AND s.sessionTimeEnd > :start")
    boolean existsRoomOverlap(@org.springframework.data.repository.query.Param("roomId") Long roomId,
            @org.springframework.data.repository.query.Param("start") java.util.Date start,
            @org.springframework.data.repository.query.Param("end") java.util.Date end);

    /** Vrai si l'enseignant est déjà occupé sur le créneau. */
    @Query("SELECT COUNT(s) > 0 FROM SessionEntity s "
            + "WHERE s.teacher.id = :teacherId AND (s.active IS NULL OR s.active = true) "
            + "AND s.sessionTimeStart < :end AND s.sessionTimeEnd > :start")
    boolean existsTeacherOverlap(@org.springframework.data.repository.query.Param("teacherId") Long teacherId,
            @org.springframework.data.repository.query.Param("start") java.util.Date start,
            @org.springframework.data.repository.query.Param("end") java.util.Date end);

    // ===== Statistiques tableau de bord (sur période) =====

    @Query("SELECT COUNT(s) FROM SessionEntity s WHERE s.active = true AND s.isFinished = true " +
            "AND s.sessionTimeStart BETWEEN :from AND :to")
    long countValidated(@org.springframework.data.repository.query.Param("from") java.util.Date from,
                        @org.springframework.data.repository.query.Param("to") java.util.Date to);

    @Query("SELECT COUNT(s) FROM SessionEntity s WHERE s.active = true AND (s.isFinished = false OR s.isFinished IS NULL) " +
            "AND s.sessionTimeStart BETWEEN :from AND :to")
    long countScheduled(@org.springframework.data.repository.query.Param("from") java.util.Date from,
                       @org.springframework.data.repository.query.Param("to") java.util.Date to);

    @Query("SELECT COUNT(s) FROM SessionEntity s WHERE s.active = false " +
            "AND s.sessionTimeStart BETWEEN :from AND :to")
    long countDeactivated(@org.springframework.data.repository.query.Param("from") java.util.Date from,
                         @org.springframework.data.repository.query.Param("to") java.util.Date to);

    // ===== Variantes filtrées par année scolaire (via session.group.schoolYear) =====

    @Query("SELECT COUNT(s) FROM SessionEntity s WHERE s.active = true AND s.isFinished = true " +
            "AND s.group.schoolYear.id = :schoolYearId AND s.sessionTimeStart BETWEEN :from AND :to")
    long countValidatedByYear(@org.springframework.data.repository.query.Param("from") java.util.Date from,
                              @org.springframework.data.repository.query.Param("to") java.util.Date to,
                              @org.springframework.data.repository.query.Param("schoolYearId") Long schoolYearId);

    @Query("SELECT COUNT(s) FROM SessionEntity s WHERE s.active = true AND (s.isFinished = false OR s.isFinished IS NULL) " +
            "AND s.group.schoolYear.id = :schoolYearId AND s.sessionTimeStart BETWEEN :from AND :to")
    long countScheduledByYear(@org.springframework.data.repository.query.Param("from") java.util.Date from,
                              @org.springframework.data.repository.query.Param("to") java.util.Date to,
                              @org.springframework.data.repository.query.Param("schoolYearId") Long schoolYearId);

    @Query("SELECT COUNT(s) FROM SessionEntity s WHERE s.active = false " +
            "AND s.group.schoolYear.id = :schoolYearId AND s.sessionTimeStart BETWEEN :from AND :to")
    long countDeactivatedByYear(@org.springframework.data.repository.query.Param("from") java.util.Date from,
                                @org.springframework.data.repository.query.Param("to") java.util.Date to,
                                @org.springframework.data.repository.query.Param("schoolYearId") Long schoolYearId);
}
