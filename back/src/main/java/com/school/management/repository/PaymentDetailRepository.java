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

        // ========== ENCAISSEMENTS (agrégations) ==========

        /*
         * Les agrégations ci-dessous répondent à « combien ce groupe a encaissé ». Elles
         * partagent trois exclusions, appliquées en SQL et non côté client :
         *   - versement désactivé (pd.active = false) ;
         *   - versement définitivement supprimé ;
         *   - paiement parent ANNULÉ.
         * Les remboursements sont retirés séparément (voir RefundRepository) : ils vivent
         * dans une autre table et ne peuvent pas être joints ici sans fausser les SUM.
         */

        /** Total encaissé (brut, hors remboursements) sur l'ensemble des paiements d'un groupe. */
        @Query("SELECT COALESCE(SUM(pd.amountPaid), 0) FROM PaymentDetailEntity pd JOIN pd.payment p "
                        + "WHERE p.group.id = :groupId AND pd.active = true "
                        + "AND (pd.permanentlyDeleted IS NULL OR pd.permanentlyDeleted = false) "
                        + "AND (p.status IS NULL OR p.status <> 'CANCELLED')")
        Double sumCollectedForGroup(@Param("groupId") Long groupId);

        /**
         * Encaissements d'un groupe ventilés par série.
         *
         * @return des lignes {@code [seriesId, seriesName, montant]}
         */
        @Query("SELECT p.sessionSeries.id, p.sessionSeries.name, COALESCE(SUM(pd.amountPaid), 0) "
                        + "FROM PaymentDetailEntity pd JOIN pd.payment p "
                        + "WHERE p.group.id = :groupId AND p.sessionSeries IS NOT NULL AND pd.active = true "
                        + "AND (pd.permanentlyDeleted IS NULL OR pd.permanentlyDeleted = false) "
                        + "AND (p.status IS NULL OR p.status <> 'CANCELLED') "
                        + "GROUP BY p.sessionSeries.id, p.sessionSeries.name")
        List<Object[]> sumCollectedByGroupGroupedBySeries(@Param("groupId") Long groupId);

        /**
         * Encaissements d'un groupe ventilés par séance.
         *
         * <p>Un versement non rattaché à une séance est exclu de cette ventilation : il
         * reste comptabilisé dans le total du groupe et de sa série.</p>
         *
         * @return des lignes {@code [seriesId, sessionId, sessionTitle, sessionStart, montant]}
         */
        @Query("SELECT pd.session.sessionSeries.id, pd.session.id, pd.session.title, "
                        + "pd.session.sessionTimeStart, COALESCE(SUM(pd.amountPaid), 0) "
                        + "FROM PaymentDetailEntity pd JOIN pd.payment p "
                        + "WHERE p.group.id = :groupId AND pd.session IS NOT NULL AND pd.active = true "
                        + "AND (pd.permanentlyDeleted IS NULL OR pd.permanentlyDeleted = false) "
                        + "AND (p.status IS NULL OR p.status <> 'CANCELLED') "
                        + "GROUP BY pd.session.sessionSeries.id, pd.session.id, pd.session.title, "
                        + "pd.session.sessionTimeStart "
                        + "ORDER BY pd.session.sessionTimeStart")
        List<Object[]> sumCollectedByGroupGroupedBySession(@Param("groupId") Long groupId);

        /**
         * Encaissements d'un groupe ventilés par mois civil de <strong>date
         * d'encaissement</strong>.
         *
         * <p>Cet axe ne se confond pas avec la ventilation par série : un versement reçu en
         * septembre peut solder une série d'août. « Par série » dit si un mois de cours est
         * payé, « par mois » dit ce qui est entré en caisse.</p>
         *
         * @return des lignes {@code [année, mois, montant]}
         */
        @Query("SELECT YEAR(pd.paymentDate), MONTH(pd.paymentDate), COALESCE(SUM(pd.amountPaid), 0) "
                        + "FROM PaymentDetailEntity pd JOIN pd.payment p "
                        + "WHERE p.group.id = :groupId AND pd.paymentDate IS NOT NULL AND pd.active = true "
                        + "AND (pd.permanentlyDeleted IS NULL OR pd.permanentlyDeleted = false) "
                        + "AND (p.status IS NULL OR p.status <> 'CANCELLED') "
                        + "GROUP BY YEAR(pd.paymentDate), MONTH(pd.paymentDate) "
                        + "ORDER BY YEAR(pd.paymentDate), MONTH(pd.paymentDate)")
        List<Object[]> sumCollectedByGroupGroupedByMonth(@Param("groupId") Long groupId);

        // ========== RAPPORT DE RECETTES (transversal, filtré) ==========

        /*
         * Une requête par axe d'agrégation, partageant le même jeu de filtres optionnels
         * (« :param IS NULL OR ... »). L'agrégation est faite par la base : la page balaie
         * potentiellement tous les groupes de l'école, charger les versements pour les
         * sommer côté application ne tiendrait pas.
         *
         * Le filtre de dates porte sur pd.paymentDate (date d'encaissement), qui est la
         * date pertinente pour une recette.
         */

        String REVENUE_FILTERS = "AND (:groupId IS NULL OR p.group.id = :groupId) "
                        + "AND (:levelId IS NULL OR p.group.level.id = :levelId) "
                        + "AND (:seriesId IS NULL OR p.sessionSeries.id = :seriesId) "
                        + "AND (:schoolYearId IS NULL OR p.group.schoolYear.id = :schoolYearId) "
                        + "AND (CAST(:dateFrom AS timestamp) IS NULL OR pd.paymentDate >= :dateFrom) "
                        + "AND (CAST(:dateTo AS timestamp) IS NULL OR pd.paymentDate <= :dateTo) ";

        String REVENUE_BASE = "FROM PaymentDetailEntity pd JOIN pd.payment p "
                        + "WHERE pd.active = true "
                        + "AND (pd.permanentlyDeleted IS NULL OR pd.permanentlyDeleted = false) "
                        + "AND (p.status IS NULL OR p.status <> 'CANCELLED') ";

        /** Total encaissé brut sur le périmètre filtré. */
        @Query("SELECT COALESCE(SUM(pd.amountPaid), 0) " + REVENUE_BASE + REVENUE_FILTERS)
        Double sumRevenue(@Param("groupId") Long groupId,
                        @Param("levelId") Long levelId,
                        @Param("seriesId") Long seriesId,
                        @Param("schoolYearId") Long schoolYearId,
                        @Param("dateFrom") java.util.Date dateFrom,
                        @Param("dateTo") java.util.Date dateTo);

        /** Recettes par groupe. Lignes {@code [groupId, groupName, montant]}. */
        @Query("SELECT p.group.id, p.group.name, COALESCE(SUM(pd.amountPaid), 0) " + REVENUE_BASE
                        + "AND p.group IS NOT NULL " + REVENUE_FILTERS
                        + "GROUP BY p.group.id, p.group.name ORDER BY COALESCE(SUM(pd.amountPaid), 0) DESC")
        List<Object[]> revenueByGroup(@Param("groupId") Long groupId,
                        @Param("levelId") Long levelId,
                        @Param("seriesId") Long seriesId,
                        @Param("schoolYearId") Long schoolYearId,
                        @Param("dateFrom") java.util.Date dateFrom,
                        @Param("dateTo") java.util.Date dateTo);

        /** Recettes par série. Lignes {@code [seriesId, seriesName, groupName, montant]}. */
        @Query("SELECT p.sessionSeries.id, p.sessionSeries.name, p.group.name, COALESCE(SUM(pd.amountPaid), 0) "
                        + REVENUE_BASE + "AND p.sessionSeries IS NOT NULL " + REVENUE_FILTERS
                        + "GROUP BY p.sessionSeries.id, p.sessionSeries.name, p.group.name "
                        + "ORDER BY COALESCE(SUM(pd.amountPaid), 0) DESC")
        List<Object[]> revenueBySeries(@Param("groupId") Long groupId,
                        @Param("levelId") Long levelId,
                        @Param("seriesId") Long seriesId,
                        @Param("schoolYearId") Long schoolYearId,
                        @Param("dateFrom") java.util.Date dateFrom,
                        @Param("dateTo") java.util.Date dateTo);

        /** Recettes par séance. Lignes {@code [sessionId, title, groupName, montant]}. */
        @Query("SELECT pd.session.id, pd.session.title, p.group.name, COALESCE(SUM(pd.amountPaid), 0) "
                        + REVENUE_BASE + "AND pd.session IS NOT NULL " + REVENUE_FILTERS
                        + "GROUP BY pd.session.id, pd.session.title, p.group.name "
                        + "ORDER BY COALESCE(SUM(pd.amountPaid), 0) DESC")
        List<Object[]> revenueBySession(@Param("groupId") Long groupId,
                        @Param("levelId") Long levelId,
                        @Param("seriesId") Long seriesId,
                        @Param("schoolYearId") Long schoolYearId,
                        @Param("dateFrom") java.util.Date dateFrom,
                        @Param("dateTo") java.util.Date dateTo);

        /** Recettes par mois d'encaissement. Lignes {@code [année, mois, montant]}. */
        @Query("SELECT YEAR(pd.paymentDate), MONTH(pd.paymentDate), COALESCE(SUM(pd.amountPaid), 0) "
                        + REVENUE_BASE + "AND pd.paymentDate IS NOT NULL " + REVENUE_FILTERS
                        + "GROUP BY YEAR(pd.paymentDate), MONTH(pd.paymentDate) "
                        + "ORDER BY YEAR(pd.paymentDate) DESC, MONTH(pd.paymentDate) DESC")
        List<Object[]> revenueByMonth(@Param("groupId") Long groupId,
                        @Param("levelId") Long levelId,
                        @Param("seriesId") Long seriesId,
                        @Param("schoolYearId") Long schoolYearId,
                        @Param("dateFrom") java.util.Date dateFrom,
                        @Param("dateTo") java.util.Date dateTo);

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
                        "AND (:sessionId IS NULL OR pd.session.id = :sessionId) " +
                        "AND (:active IS NULL OR pd.active = :active) " +
                        "AND (CAST(:dateFrom AS timestamp) IS NULL OR pd.dateCreation >= :dateFrom) " +
                        "AND (CAST(:dateTo AS timestamp) IS NULL OR pd.dateCreation <= :dateTo) " +
                        "AND (:levelId IS NULL OR p.student.level.id = :levelId)")
        Page<com.school.management.dto.PaymentDetailSearchDTO> searchPaymentDetailsWithCompleteData(
                        @Param("studentId") Long studentId,
                        @Param("groupId") Long groupId,
                        @Param("sessionSeriesId") Long sessionSeriesId,
                        @Param("sessionId") Long sessionId,
                        @Param("active") Boolean active,
                        @Param("dateFrom") java.util.Date dateFrom,
                        @Param("dateTo") java.util.Date dateTo,
                        @Param("levelId") Long levelId,
                        Pageable pageable);
}