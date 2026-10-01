package com.school.management.persistance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Compteur des numéros de reçu : une seule ligne, verrouillée à chaque attribution.
 *
 * <p>Voir {@code ReceiptNumberService} pour la raison du verrou plutôt que d'un calcul par
 * {@code MAX + 1}. La ligne est créée par la migration V6 ; le service la crée lui-même si elle
 * manque, ce qui n'arrive que sur un schéma généré par Hibernate (tests).</p>
 */
@Entity
@Table(name = "receipt_counter")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptCounterEntity {

    /** Identifiant de l'unique ligne, imposé par la contrainte {@code ck_receipt_counter_single}. */
    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    /** Année civile du dernier numéro attribué ; 0 tant qu'aucun ne l'a été. */
    @Column(name = "counter_year", nullable = false)
    private Integer counterYear;

    /** Rang du dernier numéro attribué dans {@link #counterYear}. */
    @Column(name = "last_rank", nullable = false)
    private Integer lastRank;
}
