package com.school.management.repository;

import com.school.management.persistance.PaymentEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {

    /**
     * Charge un paiement en verrou d'écriture, pour sérialiser les enregistrements de
     * remboursement portant sur ce paiement (exigence 7.8).
     *
     * <p><strong>Pourquoi un verrou.</strong> Le plafond de remboursement se calcule en lisant la
     * somme déjà remboursée, puis s'applique en écrivant un nouveau remboursement. Entre les deux,
     * une demande concurrente peut lire le même plafond : les deux passent le contrôle et leur
     * somme dépasse le montant versé. Deux onglets du navigateur suffisent à produire le cas, et le
     * déploiement mono-instance n'en protège pas. Un dépassement de plafond est une perte d'argent,
     * pas une gêne d'affichage : le verrou est donc pris même si la collision est rare.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentEntity p WHERE p.id = :id")
    Optional<PaymentEntity> findByIdForUpdate(@Param("id") Long id);

    List<PaymentEntity> findAllByStudentIdOrderByPaymentDateDesc(Long studentId);

    @Query("SELECT p FROM PaymentEntity p WHERE p.student.id = :studentId ORDER BY p.paymentDate DESC")
    Page<PaymentEntity> findAllByStudentId(@Param("studentId") Long studentId, Pageable pageable);

    Optional<PaymentEntity> findByStudentIdAndGroupIdAndSessionSeriesId(Long studentId, Long groupId, Long sessionSeriesId);

    @Query("SELECT COALESCE(SUM(p.amountPaid), 0) FROM PaymentEntity p " +
           "WHERE p.student.id = :studentId AND p.sessionSeries.id = :sessionSeriesId " +
           "AND p.status <> 'CANCELLED'")
    BigDecimal sumAmountPaidForStudentAndSeries(@Param("studentId") Long studentId, @Param("sessionSeriesId") Long sessionSeriesId);

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

    /**
     * Somme encaissée par série d'un groupe, lue sur le <strong>registre des paiements</strong>.
     *
     * <p>Le relevé lisait ces montants dans {@code PaymentDetailEntity}, qui n'est qu'une
     * ventilation par séance : tout versement non ventilable (avance sur des séances non encore
     * tenues) en était absent, alors qu'il figure bien au registre. Relevé et situation
     * individuelle de l'étudiant affichaient donc des montants différents.</p>
     *
     * @return des lignes {@code [seriesId, montant]}
     */
    @Query("SELECT p.sessionSeries.id, COALESCE(SUM(p.amountPaid), 0) FROM PaymentEntity p "
            + "WHERE p.group.id = :groupId AND p.sessionSeries IS NOT NULL "
            + "AND (p.status IS NULL OR p.status <> 'CANCELLED') "
            + "GROUP BY p.sessionSeries.id")
    List<Object[]> sumPaidByGroupGroupedBySeries(@Param("groupId") Long groupId);

    /** Total encaissé d'un groupe selon le registre des paiements (hors remboursements). */
    @Query("SELECT COALESCE(SUM(p.amountPaid), 0) FROM PaymentEntity p "
            + "WHERE p.group.id = :groupId AND (p.status IS NULL OR p.status <> 'CANCELLED')")
    BigDecimal sumPaidForGroup(@Param("groupId") Long groupId);
}