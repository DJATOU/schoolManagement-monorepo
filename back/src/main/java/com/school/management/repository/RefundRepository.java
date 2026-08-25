package com.school.management.repository;

import com.school.management.persistance.RefundEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * Accès aux données des remboursements (refunds).
 *
 * <p>Fournit l'agrégation des montants remboursés à un étudiant pour une série
 * donnée. Comme un remboursement est rattaché à un paiement (lequel référence la
 * série de sessions), la somme est calculée sur les remboursements dont le paiement
 * appartient à la série visée.</p>
 */
@Repository
public interface RefundRepository extends JpaRepository<RefundEntity, Long> {

    /**
     * Somme des remboursements accordés à un étudiant pour une série donnée.
     * Retourne 0 lorsqu'aucun remboursement n'existe (COALESCE).
     *
     * @param studentId       l'identifiant de l'étudiant
     * @param sessionSeriesId l'identifiant de la série de sessions
     * @return la somme des montants remboursés (0 si aucun)
     */
    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM RefundEntity r " +
           "WHERE r.student.id = :studentId AND r.payment.sessionSeries.id = :sessionSeriesId")
    BigDecimal sumRefundsForStudentAndSeries(@Param("studentId") Long studentId,
                                             @Param("sessionSeriesId") Long sessionSeriesId);

    /**
     * Somme des remboursements accordés sur les paiements d'un groupe, toutes séries
     * confondues. Sert au calcul de l'encaissement net du groupe : un remboursement
     * sort de la caisse et doit donc être retiré des recettes.
     *
     * @param groupId l'identifiant du groupe
     * @return la somme des montants remboursés (0 si aucun)
     */
    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM RefundEntity r WHERE r.payment.group.id = :groupId")
    BigDecimal sumRefundsForGroup(@Param("groupId") Long groupId);

    /**
     * Total remboursé sur le périmètre du rapport de recettes.
     *
     * <p>Le filtre de dates porte sur {@code refundDate} : un remboursement sort de la
     * caisse à sa propre date, pas à celle du versement d'origine.</p>
     */
    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM RefundEntity r "
            + "WHERE (:groupId IS NULL OR r.payment.group.id = :groupId) "
            + "AND (:levelId IS NULL OR r.payment.group.level.id = :levelId) "
            + "AND (:seriesId IS NULL OR r.payment.sessionSeries.id = :seriesId) "
            + "AND (:schoolYearId IS NULL OR r.payment.group.schoolYear.id = :schoolYearId) "
            + "AND (CAST(:dateFrom AS timestamp) IS NULL OR r.refundDate >= :dateFrom) "
            + "AND (CAST(:dateTo AS timestamp) IS NULL OR r.refundDate <= :dateTo)")
    BigDecimal sumRefundsForReport(@Param("groupId") Long groupId,
                                   @Param("levelId") Long levelId,
                                   @Param("seriesId") Long seriesId,
                                   @Param("schoolYearId") Long schoolYearId,
                                   @Param("dateFrom") Date dateFrom,
                                   @Param("dateTo") Date dateTo);

    /**
     * Remboursements d'un groupe ventilés par série.
     *
     * @param groupId l'identifiant du groupe
     * @return des lignes {@code [seriesId, montantRemboursé]}
     */
    @Query("SELECT r.payment.sessionSeries.id, COALESCE(SUM(r.amount), 0) FROM RefundEntity r "
            + "WHERE r.payment.group.id = :groupId AND r.payment.sessionSeries IS NOT NULL "
            + "GROUP BY r.payment.sessionSeries.id")
    List<Object[]> sumRefundsByGroupGroupedBySeries(@Param("groupId") Long groupId);
}
