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
 *
 * <p><strong>Toutes les agrégations filtrent sur {@code active = true}.</strong> Ce n'était pas le
 * cas auparavant : un remboursement désactivé aurait été exclu du plafond de remboursement mais
 * toujours déduit des recettes, soit deux vérités contradictoires sur la même sortie de caisse. Le
 * comportement observable est inchangé aujourd'hui puisque aucun chemin de code ne désactive un
 * remboursement, mais l'alignement évite qu'une fonctionnalité d'annulation future crée
 * silencieusement cet écart (exigence 7.7, « en permanence »).</p>
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
           "WHERE r.student.id = :studentId AND r.payment.sessionSeries.id = :sessionSeriesId " +
           "AND r.active = true")
    BigDecimal sumRefundsForStudentAndSeries(@Param("studentId") Long studentId,
                                             @Param("sessionSeriesId") Long sessionSeriesId);

    /**
     * Somme des remboursements <strong>actifs</strong> déjà accordés sur un paiement.
     *
     * <p>Base du Plafond_Remboursable (exigence 7.1) : le plafond est le montant versé diminué de
     * cette somme. Sans elle, chaque demande était comparée au seul montant versé, et deux
     * remboursements du montant total d'un même versement étaient tous deux acceptés — la caisse
     * sortait deux fois l'argent entré une fois.</p>
     */
    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM RefundEntity r "
            + "WHERE r.payment.id = :paymentId AND r.active = true")
    BigDecimal sumActiveRefundsForPayment(@Param("paymentId") Long paymentId);

    /**
     * Rang le plus élevé déjà attribué dans la séquence annuelle des numéros de pièce.
     *
     * <p>Le rang est extrait du numéro lui-même plutôt que stocké à part : une colonne dédiée
     * pourrait diverger du numéro imprimé sur un reçu, alors que le numéro est la seule référence
     * que le parent détient. Retourne 0 si l'année est vierge (exigence 6.12).</p>
     *
     * @param prefix préfixe de l'année, de la forme {@code REMB-2026-}
     */
    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(r.refundNumber, LENGTH(:prefix) + 1) AS integer)), 0) "
            + "FROM RefundEntity r WHERE r.refundNumber LIKE CONCAT(:prefix, '%')")
    int findMaxRankForPrefix(@Param("prefix") String prefix);

    // Note : la table démarre vide et aucun remboursement antérieur n'existe. Cette requête
    // n'alimente donc que la numérotation des remboursements à venir, jamais une reprise.

    /**
     * Somme des remboursements accordés sur les paiements d'un groupe, toutes séries
     * confondues. Sert au calcul de l'encaissement net du groupe : un remboursement
     * sort de la caisse et doit donc être retiré des recettes.
     *
     * @param groupId l'identifiant du groupe
     * @return la somme des montants remboursés (0 si aucun)
     */
    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM RefundEntity r "
            + "WHERE r.payment.group.id = :groupId AND r.active = true")
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
            + "AND (CAST(:dateTo AS timestamp) IS NULL OR r.refundDate <= :dateTo) "
            + "AND r.active = true")
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
            + "AND r.active = true "
            + "GROUP BY r.payment.sessionSeries.id")
    List<Object[]> sumRefundsByGroupGroupedBySeries(@Param("groupId") Long groupId);
}
