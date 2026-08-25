package com.school.management.repository;

import com.school.management.persistance.AttendanceEntity;
import com.school.management.persistance.SessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<AttendanceEntity, Long> {

    long countByStudentIdAndSessionSeriesIdAndIsPresent(Long studentId, Long sessionSeriesId, boolean isPresent);

    /**
     * Compte les séances effectivement suivies (présent) par un étudiant dans le
     * périmètre d'une série, tous groupes confondus. Ne compte que les fiches
     * actives avec {@code isPresent = true}.
     *
     * @param studentId       l'identifiant de l'étudiant
     * @param sessionSeriesId l'identifiant de la série de sessions
     * @return le nombre de séances suivies (présent) dans la série
     */
    @Query("SELECT COUNT(a) FROM AttendanceEntity a WHERE a.student.id = :studentId " +
            "AND a.sessionSeries.id = :sessionSeriesId AND a.isPresent = true AND a.active = true")
    long countPresentForStudentAndSeries(@Param("studentId") Long studentId,
            @Param("sessionSeriesId") Long sessionSeriesId);

    /**
     * Compte les séances suivies (présent) par un étudiant dans un groupe donné sur une plage
     * de dates, bornes incluses.
     *
     * <p>Sert au <strong>seul</strong> signalement de changement de groupe (exigence 10.3), qui
     * est informatif. Ce comptage par plage de dates ne doit alimenter aucun calcul monétaire :
     * l'unité de facturation reste la série, et
     * {@link #countPresentForStudentAndSeries(Long, Long)} en demeure la source.</p>
     *
     * @param studentId l'identifiant de l'étudiant
     * @param groupId   l'identifiant du groupe
     * @param from      début de la plage, inclus
     * @param to        fin de la plage, incluse
     * @return le nombre de séances suivies dans ce groupe sur la plage
     */
    @Query("SELECT COUNT(a) FROM AttendanceEntity a WHERE a.student.id = :studentId "
            + "AND a.group.id = :groupId AND a.isPresent = true AND a.active = true "
            + "AND a.session.sessionTimeStart BETWEEN :from AND :to")
    long countPresentForStudentAndGroupBetween(@Param("studentId") Long studentId,
                                               @Param("groupId") Long groupId,
                                               @Param("from") java.util.Date from,
                                               @Param("to") java.util.Date to);

    @Query("SELECT a FROM AttendanceEntity a WHERE a.session.id = :sessionId AND a.student.id = :studentId AND a.active = true ORDER BY a.id DESC LIMIT 1")
    Optional<AttendanceEntity> findBySessionIdAndStudentId(@Param("sessionId") Long sessionId,
            @Param("studentId") Long studentId);

    List<SessionEntity> findByStudentIdAndIsPresent(Long studentId, boolean b);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN TRUE ELSE FALSE END FROM AttendanceEntity a WHERE a.student.id = :studentId AND a.session.id = :sessionId")
    boolean existsByStudentIdAndSessionId(@Param("studentId") Long studentId, @Param("sessionId") Long sessionId);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN TRUE ELSE FALSE END FROM AttendanceEntity a WHERE a.student.id = :studentId AND a.session.id = :sessionId AND a.active = true")
    boolean existsByStudentIdAndSessionIdAndActiveTrue(@Param("studentId") Long studentId,
            @Param("sessionId") Long sessionId);

    @Query("SELECT a FROM AttendanceEntity a WHERE a.session.id = :sessionId")
    List<AttendanceEntity> findBySessionId(@Param("sessionId") Long sessionId);

    void deleteBySessionId(Long sessionId);

    List<AttendanceEntity> findBySessionIdAndActiveTrue(Long sessionId);

    List<AttendanceEntity> findByStudentIdAndSessionSeriesIdAndActiveTrue(Long studentId, Long sessionSeriesId);

    List<AttendanceEntity> findByStudentIdAndIsCatchUp(Long studentId, boolean isCatchUp);

    boolean existsByGroupIdAndStudentIdAndIsCatchUp(Long id, Long studentId, boolean b);

    List<AttendanceEntity> findByStudentIdAndActiveTrue(Long studentId);

    /**
     * Liste les absences actives d'un étudiant (fiches {@code isPresent = false}),
     * triées par date de séance décroissante. Utilisé par le workflow de rattrapage
     * pour proposer les séances manquées éligibles à une demande de rattrapage.
     *
     * @param studentId l'identifiant de l'étudiant
     * @return la liste des absences actives de l'étudiant
     */
    @Query("SELECT a FROM AttendanceEntity a WHERE a.student.id = :studentId " +
            "AND a.isPresent = false AND a.active = true ORDER BY a.session.sessionTimeStart DESC")
    List<AttendanceEntity> findAbsencesByStudentId(@Param("studentId") Long studentId);

    // ===== Statistiques tableau de bord (sur période, via la date de session) =====

    @Query("SELECT COUNT(a) FROM AttendanceEntity a WHERE a.active = true AND a.isPresent = true " +
            "AND a.session.sessionTimeStart BETWEEN :from AND :to")
    long countPresent(@Param("from") java.util.Date from, @Param("to") java.util.Date to);

    @Query("SELECT COUNT(a) FROM AttendanceEntity a WHERE a.active = true AND a.isPresent = false " +
            "AND a.isJustified = true AND a.session.sessionTimeStart BETWEEN :from AND :to")
    long countJustifiedAbsences(@Param("from") java.util.Date from, @Param("to") java.util.Date to);

    @Query("SELECT COUNT(a) FROM AttendanceEntity a WHERE a.active = true AND a.isPresent = false " +
            "AND (a.isJustified = false OR a.isJustified IS NULL) AND a.session.sessionTimeStart BETWEEN :from AND :to")
    long countUnjustifiedAbsences(@Param("from") java.util.Date from, @Param("to") java.util.Date to);

    @Query("SELECT COUNT(a) FROM AttendanceEntity a WHERE a.active = true AND a.isCatchUp = true " +
            "AND a.session.sessionTimeStart BETWEEN :from AND :to")
    long countCatchUp(@Param("from") java.util.Date from, @Param("to") java.util.Date to);

    // ===== Variantes filtrées par année scolaire (via attendance.session.group.schoolYear) =====

    @Query("SELECT COUNT(a) FROM AttendanceEntity a WHERE a.active = true AND a.isPresent = true " +
            "AND a.session.group.schoolYear.id = :schoolYearId AND a.session.sessionTimeStart BETWEEN :from AND :to")
    long countPresentByYear(@Param("from") java.util.Date from, @Param("to") java.util.Date to,
                            @Param("schoolYearId") Long schoolYearId);

    @Query("SELECT COUNT(a) FROM AttendanceEntity a WHERE a.active = true AND a.isPresent = false " +
            "AND a.isJustified = true AND a.session.group.schoolYear.id = :schoolYearId " +
            "AND a.session.sessionTimeStart BETWEEN :from AND :to")
    long countJustifiedAbsencesByYear(@Param("from") java.util.Date from, @Param("to") java.util.Date to,
                                      @Param("schoolYearId") Long schoolYearId);

    @Query("SELECT COUNT(a) FROM AttendanceEntity a WHERE a.active = true AND a.isPresent = false " +
            "AND (a.isJustified = false OR a.isJustified IS NULL) AND a.session.group.schoolYear.id = :schoolYearId " +
            "AND a.session.sessionTimeStart BETWEEN :from AND :to")
    long countUnjustifiedAbsencesByYear(@Param("from") java.util.Date from, @Param("to") java.util.Date to,
                                        @Param("schoolYearId") Long schoolYearId);

    @Query("SELECT COUNT(a) FROM AttendanceEntity a WHERE a.active = true AND a.isCatchUp = true " +
            "AND a.session.group.schoolYear.id = :schoolYearId AND a.session.sessionTimeStart BETWEEN :from AND :to")
    long countCatchUpByYear(@Param("from") java.util.Date from, @Param("to") java.util.Date to,
                            @Param("schoolYearId") Long schoolYearId);
}
