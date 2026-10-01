package com.school.management.repository;

import com.school.management.persistance.EncashmentAllocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/** Accès aux Imputations. */
@Repository
public interface EncashmentAllocationRepository extends JpaRepository<EncashmentAllocationEntity, Long> {

    /** Imputations actives d'un encaissement, dans l'ordre de création. */
    List<EncashmentAllocationEntity> findByEncashmentIdAndActiveTrueOrderByIdAsc(Long encashmentId);

    /**
     * Cumul d'une série pour un étudiant : la somme des Imputations actives de sa ligne de paiement.
     * C'est la définition de {@code payments.amount_paid} (spec admin-corrections, exigence 1.4).
     */
    @Query("SELECT COALESCE(SUM(a.amount), 0) FROM EncashmentAllocationEntity a "
            + "WHERE a.payment.id = :paymentId AND a.active = true")
    BigDecimal sumActiveAmountForPayment(@Param("paymentId") Long paymentId);

    /** Part déjà imputée d'un encaissement, pour qu'elle ne dépasse jamais le montant reçu. */
    @Query("SELECT COALESCE(SUM(a.amount), 0) FROM EncashmentAllocationEntity a "
            + "WHERE a.encashment.id = :encashmentId AND a.active = true")
    BigDecimal sumActiveAmountForEncashment(@Param("encashmentId") Long encashmentId);
}
