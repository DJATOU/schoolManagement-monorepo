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

    List<SessionEntity> findBySessionSeriesId(Long sessionSeriesId);

    List<SessionEntity> findBySessionTimeStartBetween(LocalDateTime start, LocalDateTime end);

    List<SessionEntity> findByGroupIdAndSessionTimeStartBetween(Long groupId, LocalDateTime start, LocalDateTime end);

    int countBySessionSeriesId(Long sessionSeriesId);

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
}
