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

    /**
     * Récupère les paiements d'un étudiant avec pagination.
     *
     * @param studentId l'ID de l'étudiant
     * @param pageable les paramètres de pagination
     * @return une page de paiements
     */
    @Query("SELECT p FROM PaymentEntity p WHERE p.student.id = :studentId ORDER BY p.paymentDate DESC")
    Page<PaymentEntity> findAllByStudentId(@Param("studentId") Long studentId, Pageable pageable);

    Optional<PaymentEntity> findByStudentIdAndGroupIdAndSessionSeriesId(Long studentId, Long groupId, Long sessionSeriesId);

    @Query("SELECT p.amountPaid FROM PaymentEntity p WHERE p.student.id = :studentId AND p.sessionSeries.id = :sessionSeriesId")
    Double findAmountPaidForStudentAndSeries(@Param("studentId") Long studentId, @Param("sessionSeriesId") Long sessionSeriesId);

    @Query("""
            SELECT p
            FROM PaymentEntity p
            WHERE p.student.id = :studentId
              AND (
                    p.sessionSeries.id = :sessionSeriesId
                 OR (p.session.id IS NOT NULL AND p.session.sessionSeries.id = :sessionSeriesId)
              )
            """)
    List<PaymentEntity> findAllByStudentIdAndSessionSeriesId(Long studentId, Long sessionSeriesId);

}
