package com.school.management.repository;

import com.school.management.persistance.RefundReceiptIssuanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Accès aux émissions de reçu de remboursement (exigence 8.10).
 */
@Repository
public interface RefundReceiptIssuanceRepository
        extends JpaRepository<RefundReceiptIssuanceEntity, Long> {

    /**
     * Rang le plus élevé déjà émis pour ce remboursement, ou 0 si le reçu n'a jamais été produit.
     * Un rang retourné strictement positif signifie donc que la prochaine production est un
     * duplicata et doit être signalée comme telle.
     */
    @Query("SELECT COALESCE(MAX(i.rank), 0) FROM RefundReceiptIssuanceEntity i "
            + "WHERE i.refund.id = :refundId")
    int findMaxRank(@Param("refundId") Long refundId);
}
