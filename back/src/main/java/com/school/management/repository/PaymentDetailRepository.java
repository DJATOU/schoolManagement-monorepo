package com.school.management.repository;

import com.school.management.persistance.PaymentDetailEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

/**
 * Repository pour gérer les PaymentDetails.
 *
 * MODIFICATIONS AJOUTÉES :
 * - findBySessionId() : pour trouver tous les PaymentDetails d'une session
 * - findActiveBySessionId() : pour trouver uniquement les PaymentDetails actifs
 */
public interface PaymentDetailRepository
                extends JpaRepository<PaymentDetailEntity, Long>, JpaSpecificationExecutor<PaymentDetailEntity> {

        // ========== MÉTHODES EXISTANTES (NE PAS TOUCHER) ==========

        Optional<PaymentDetailEntity> findByPaymentIdAndSessionId(Long id, Long id1);

        List<PaymentDetailEntity> findByPayment_StudentId(Long studentId);

        List<PaymentDetailEntity> findByPayment_StudentIdAndSessionId(Long studentId, Long sessionId);

        /**
         * SQL: SELECT * FROM payment_detail WHERE payment_id IN (SELECT id FROM
         * payments WHERE student_id = ?1 AND session_series_id = ?2)
         */
        @Query("SELECT pd FROM PaymentDetailEntity pd WHERE pd.payment.student.id = :studentId AND pd.session.sessionSeries.id = :sessionSeriesId")
        List<PaymentDetailEntity> findByPayment_StudentIdAndSession_SessionSeriesId(@Param("studentId") Long studentId,
                        @Param("sessionSeriesId") Long sessionSeriesId);

        // ========== NOUVELLES MÉTHODES AJOUTÉES ==========

        /**
         * Trouve tous les PaymentDetails pour une session donnée (actifs ET inactifs).
         * Utilisé pour désactiver les paiements quand une session est dévalidée.
         */
        List<PaymentDetailEntity> findBySessionId(Long sessionId);

        /**
         * Trouve tous les PaymentDetails ACTIFS pour une session donnée.
         * Utilisé pour vérifier combien de paiements actifs existent pour une session.
         */
        @Query("SELECT pd FROM PaymentDetailEntity pd WHERE pd.session.id = :sessionId AND pd.active = true")
        List<PaymentDetailEntity> findActiveBySessionId(@Param("sessionId") Long sessionId);

        List<PaymentDetailEntity> findBySessionIdAndActiveTrue(Long sessionId);

        List<PaymentDetailEntity> findByPaymentIdAndActiveTrue(Long paymentId);

        /**
         * Trouve tous les PaymentDetails pour un paiement donné (actifs ET inactifs).
         * Utilisé pour vérifier si tous les paiements ont été définitivement supprimés.
         */
        List<PaymentDetailEntity> findByPaymentId(Long paymentId);

        @Query("SELECT pd FROM PaymentDetailEntity pd " +
                        "JOIN pd.payment p " +
                        "WHERE (:studentId IS NULL OR p.student.id = :studentId) " +
                        "AND (:groupId IS NULL OR p.group.id = :groupId) " +
                        "AND (:sessionSeriesId IS NULL OR p.sessionSeries.id = :sessionSeriesId) " +
                        "AND (:active IS NULL OR pd.active = :active) " +
                        "AND (CAST(:dateFrom AS timestamp) IS NULL OR pd.paymentDate >= :dateFrom) " +
                        "AND (CAST(:dateTo AS timestamp) IS NULL OR pd.paymentDate <= :dateTo)")
        org.springframework.data.domain.Page<PaymentDetailEntity> findAllWithFilters(
                        @Param("studentId") Long studentId,
                        @Param("groupId") Long groupId,
                        @Param("sessionSeriesId") Long sessionSeriesId,
                        @Param("active") Boolean active,
                        @Param("dateFrom") java.util.Date dateFrom,
                        @Param("dateTo") java.util.Date dateTo,
                        org.springframework.data.domain.Pageable pageable);

        @Query("SELECT COUNT(pd) FROM PaymentDetailEntity pd " +
                        "JOIN pd.payment p " +
                        "WHERE (:studentId IS NULL OR p.student.id = :studentId) " +
                        "AND (:groupId IS NULL OR p.group.id = :groupId) " +
                        "AND (:sessionSeriesId IS NULL OR p.sessionSeries.id = :sessionSeriesId) " +
                        "AND (:active IS NULL OR pd.active = :active) " +
                        "AND (CAST(:dateFrom AS timestamp) IS NULL OR pd.paymentDate >= :dateFrom) " +
                        "AND (CAST(:dateTo AS timestamp) IS NULL OR pd.paymentDate <= :dateTo)")
        long countWithFilters(
                        @Param("studentId") Long studentId,
                        @Param("groupId") Long groupId,
                        @Param("sessionSeriesId") Long sessionSeriesId,
                        @Param("active") Boolean active,
                        @Param("dateFrom") java.util.Date dateFrom,
                        @Param("dateTo") java.util.Date dateTo);

        @Query("SELECT pd FROM PaymentDetailEntity pd WHERE pd.payment.group.id = :groupId")
        List<PaymentDetailEntity> findByGroupId(@Param("groupId") Long groupId);

        @Query("SELECT pd FROM PaymentDetailEntity pd WHERE pd.payment.sessionSeries.id = :sessionSeriesId")
        List<PaymentDetailEntity> findBySessionSeriesId(@Param("sessionSeriesId") Long sessionSeriesId);

        @Query("SELECT COALESCE(SUM(pd.amountPaid), 0) FROM PaymentDetailEntity pd " +
                        "WHERE pd.payment.student.id = :studentId AND pd.payment.group.id = :groupId")
        Double sumAmountByStudentAndGroup(@Param("studentId") Long studentId, @Param("groupId") Long groupId);

        @Query("SELECT COALESCE(SUM(pd.amountPaid), 0) FROM PaymentDetailEntity pd " +
                        "WHERE pd.payment.student.id = :studentId AND pd.payment.sessionSeries.id = :sessionSeriesId")
        Double sumAmountByStudentAndSeries(@Param("studentId") Long studentId,
                        @Param("sessionSeriesId") Long sessionSeriesId);

        /**
         * Search query with complete data for Payment Management UI
         * Uses DTO projection to fetch all related data in one query
         * Filters by createdAt (dateCreation) instead of paymentDate
         */
        @Query("SELECT new com.school.management.dto.PaymentDetailSearchDTO(" +
                        "pd.id, " +
                        "p.student.firstName, " +
                        "p.student.lastName, " +
                        "p.student.id, " +
                        "p.group.name, " +
                        "p.group.id, " +
                        "p.sessionSeries.name, " +
                        "p.sessionSeries.id, " +
                        "pd.session.title, " +
                        "pd.session.id, " +
                        "pd.amountPaid, " +
                        "pd.active, " +
                        "pd.permanentlyDeleted, " +
                        "pd.dateCreation, " +
                        "pd.paymentDate, " +
                        "p.id, " +
                        "p.status, " +
                        "pd.isCatchUp" +
                        ") FROM PaymentDetailEntity pd " +
                        "JOIN pd.payment p " +
                        "LEFT JOIN pd.session s " +
                        "WHERE (:studentId IS NULL OR p.student.id = :studentId) " +
                        "AND (:groupId IS NULL OR p.group.id = :groupId) " +
                        "AND (:sessionSeriesId IS NULL OR p.sessionSeries.id = :sessionSeriesId) " +
                        "AND (:active IS NULL OR pd.active = :active) " +
                        "AND (CAST(:dateFrom AS timestamp) IS NULL OR pd.dateCreation >= :dateFrom) " +
                        "AND (CAST(:dateTo AS timestamp) IS NULL OR pd.dateCreation <= :dateTo)")
        Page<com.school.management.dto.PaymentDetailSearchDTO> searchPaymentDetailsWithCompleteData(
                        @Param("studentId") Long studentId,
                        @Param("groupId") Long groupId,
                        @Param("sessionSeriesId") Long sessionSeriesId,
                        @Param("active") Boolean active,
                        @Param("dateFrom") java.util.Date dateFrom,
                        @Param("dateTo") java.util.Date dateTo,
                        Pageable pageable);
}