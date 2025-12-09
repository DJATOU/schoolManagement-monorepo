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
}
