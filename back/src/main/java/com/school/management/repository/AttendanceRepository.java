package com.school.management.repository;

import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.SessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@Repository
public interface AttendanceRepository extends JpaRepository<AttendanceEntity, Long> {

    long countByStudentIdAndSessionSeriesIdAndIsPresent(Long studentId, Long sessionSeriesId, boolean isPresent);

    List<SessionEntity> findByStudentIdAndIsPresent(Long studentId, boolean b);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN TRUE ELSE FALSE END FROM AttendanceEntity a WHERE a.student.id = :studentId AND a.session.id = :sessionId")
    boolean existsByStudentIdAndSessionId(@Param("studentId") Long studentId, @Param("sessionId") Long sessionId);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN TRUE ELSE FALSE END FROM AttendanceEntity a WHERE a.student.id = :studentId AND a.session.id = :sessionId AND a.active = true")
    boolean existsByStudentIdAndSessionIdAndActiveTrue(@Param("studentId") Long studentId, @Param("sessionId") Long sessionId);


    @Query("SELECT a FROM AttendanceEntity a WHERE a.session.id = :sessionId")
    List<AttendanceEntity> findBySessionId(@Param("sessionId") Long sessionId);

    void deleteBySessionId(Long sessionId);

    List<AttendanceEntity> findBySessionIdAndActiveTrue(Long sessionId);

    List<AttendanceEntity> findByStudentIdAndSessionSeriesIdAndActiveTrue(Long studentId, Long sessionSeriesId);

    List<AttendanceEntity> findByStudentIdAndIsCatchUp(Long studentId, boolean isCatchUp);

    boolean existsByGroupIdAndStudentIdAndIsCatchUp(Long id, Long studentId, boolean b);
}

