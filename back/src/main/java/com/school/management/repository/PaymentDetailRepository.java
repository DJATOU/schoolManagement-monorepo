package com.school.management.repository;

import com.school.management.persistance.PaymentDetailEntity;
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
public interface PaymentDetailRepository extends JpaRepository<PaymentDetailEntity, Long>, JpaSpecificationExecutor<PaymentDetailEntity> {

    // ========== MÉTHODES EXISTANTES (NE PAS TOUCHER) ==========

    Optional<PaymentDetailEntity> findByPaymentIdAndSessionId(Long id, Long id1);

    List<PaymentDetailEntity> findByPayment_StudentId(Long studentId);

    List<PaymentDetailEntity> findByPayment_StudentIdAndSessionId(Long studentId, Long sessionId);

    /**
     * SQL: SELECT * FROM payment_detail WHERE payment_id IN (SELECT id FROM payments WHERE student_id = ?1 AND session_series_id = ?2)
     */
    @Query("SELECT pd FROM PaymentDetailEntity pd WHERE pd.payment.student.id = :studentId AND pd.session.sessionSeries.id = :sessionSeriesId")
    List<PaymentDetailEntity> findByPayment_StudentIdAndSession_SessionSeriesId(@Param("studentId") Long studentId, @Param("sessionSeriesId") Long sessionSeriesId);


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
}