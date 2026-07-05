package com.school.management.repository;

import com.school.management.persistance.PaymentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {

    List<PaymentEntity> findAllByStudentIdOrderByPaymentDateDesc(Long studentId);

    @Query("SELECT p FROM PaymentEntity p WHERE p.student.id = :studentId ORDER BY p.paymentDate DESC")
    Page<PaymentEntity> findAllByStudentId(@Param("studentId") Long studentId, Pageable pageable);

    Optional<PaymentEntity> findByStudentIdAndGroupIdAndSessionSeriesId(Long studentId, Long groupId, Long sessionSeriesId);

    @Query("SELECT p.amountPaid FROM PaymentEntity p WHERE p.student.id = :studentId AND p.sessionSeries.id = :sessionSeriesId AND p.status != 'CANCELLED'")
    Double findAmountPaidForStudentAndSeries(@Param("studentId") Long studentId, @Param("sessionSeriesId") Long sessionSeriesId);

    @Query("SELECT p FROM PaymentEntity p WHERE p.student.id = :studentId AND p.sessionSeries.id = :sessionSeriesId")
    List<PaymentEntity> findAllByStudentIdAndSessionSeriesId(Long studentId, Long sessionSeriesId);

    // NOUVELLE MÉTHODE AJOUTÉE
    Optional<PaymentEntity> findByStudentIdAndSessionSeriesId(Long studentId, Long sessionSeriesId);

    /**
     * Trouve un paiement ACTIF (non CANCELLED) pour un étudiant et une série.
     * Permet de créer un nouveau paiement même si un paiement CANCELLED existe.
     * Retourne un Optional pour la recherche d'un paiement unique.
     */
    @Query("SELECT p FROM PaymentEntity p WHERE p.student.id = :studentId AND p.sessionSeries.id = :sessionSeriesId AND p.status != 'CANCELLED'")
    Optional<PaymentEntity> findActiveByStudentIdAndSessionSeriesId(@Param("studentId") Long studentId, @Param("sessionSeriesId") Long sessionSeriesId);

    /**
     * Trouve tous les paiements ACTIFS (non CANCELLED) pour un étudiant et une série.
     * Utilisé pour l'historique des paiements d'une série.
     */
    @Query("SELECT p FROM PaymentEntity p WHERE p.student.id = :studentId AND p.sessionSeries.id = :sessionSeriesId AND p.status != 'CANCELLED' ORDER BY p.paymentDate DESC")
    List<PaymentEntity> findAllActiveByStudentIdAndSessionSeriesId(@Param("studentId") Long studentId, @Param("sessionSeriesId") Long sessionSeriesId);

    /**
     * Trouve tous les paiements ACTIFS (non CANCELLED) pour un étudiant.
     * Utilisé pour l'historique et les calculs.
     */
    @Query("SELECT p FROM PaymentEntity p WHERE p.student.id = :studentId AND p.status != 'CANCELLED' ORDER BY p.paymentDate DESC")
    List<PaymentEntity> findActiveByStudentIdOrderByPaymentDateDesc(@Param("studentId") Long studentId);

    /**
     * Trouve tous les paiements ACTIFS (non CANCELLED) pour un étudiant avec pagination.
     */
    @Query("SELECT p FROM PaymentEntity p WHERE p.student.id = :studentId AND p.status != 'CANCELLED' ORDER BY p.paymentDate DESC")
    Page<PaymentEntity> findActiveByStudentId(@Param("studentId") Long studentId, Pageable pageable);

    /**
     * Trouve tous les paiements ACTIFS (non CANCELLED) avec pagination.
     * Utilisé pour la gestion des paiements (ne pas afficher les CANCELLED par défaut).
     */
    @Query("SELECT p FROM PaymentEntity p WHERE p.status != 'CANCELLED' ORDER BY p.paymentDate DESC")
    Page<PaymentEntity> findAllActive(Pageable pageable);
}