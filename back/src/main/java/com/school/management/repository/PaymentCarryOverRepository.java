package com.school.management.repository;

import com.school.management.persistance.PaymentCarryOverEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Accès aux traces de report de versement (Exigence 6).
 *
 * <p>Deux lectures sont nécessaires :</p>
 * <ul>
 *   <li>par étudiant, pour restituer dans son historique chaque report avec son montant,
 *       sa série source et sa série destination (Exigence 6.2) ;</li>
 *   <li>par ligne de paiement, pour distinguer sur une série un montant reçu par report
 *       d'un montant imputé directement (Exigence 6.4) : l'absence de report pointant la
 *       ligne signifie une imputation directe.</li>
 * </ul>
 *
 * <p>Les deux méthodes filtrent sur {@code active = true} : un report désactivé n'est plus
 * une imputation à présenter. L'ordre est stable (identifiant croissant) pour que
 * l'historique ne varie pas d'un appel à l'autre.</p>
 */
@Repository
public interface PaymentCarryOverRepository extends JpaRepository<PaymentCarryOverEntity, Long> {

    /**
     * Reports produits par les versements d'un étudiant, toutes séries confondues.
     *
     * @param studentId l'identifiant de l'étudiant
     * @return les reports actifs, par identifiant croissant (liste vide si aucun)
     */
    List<PaymentCarryOverEntity> findByStudentIdAndActiveTrueOrderByIdAsc(Long studentId);

    /**
     * Reports ayant crédité une ligne de paiement donnée.
     *
     * @param targetPaymentId l'identifiant de la ligne de paiement créditée
     * @return les reports actifs, par identifiant croissant (liste vide si aucun)
     */
    List<PaymentCarryOverEntity> findByTargetPaymentIdAndActiveTrueOrderByIdAsc(Long targetPaymentId);
}
