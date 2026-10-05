package com.school.management.repository;

import com.school.management.persistance.PayoutSlipIssuanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PayoutSlipIssuanceRepository extends JpaRepository<PayoutSlipIssuanceEntity, Long> {

    /** Rang de la dernière impression du bordereau d'une paie ; 0 s'il n'a jamais été imprimé. */
    @Query("SELECT COALESCE(MAX(i.rank), 0) FROM PayoutSlipIssuanceEntity i WHERE i.payout.id = :payoutId")
    int findMaxRank(@Param("payoutId") Long payoutId);
}
