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
 * Compteur des numéros de Paie : une seule ligne, verrouillée à chaque attribution.
 *
 * <p>Même modèle que {@link ReceiptCounterEntity} : un calcul par {@code MAX + 1} laisserait deux
 * paies simultanées obtenir le même numéro, et sur PostgreSQL la collision interrompt toute la
 * transaction. La ligne est créée par la migration V9 ; le service la crée lui-même si elle manque,
 * ce qui n'arrive que sur un schéma généré par Hibernate (tests).</p>
 */
@Entity
@Table(name = "payout_counter")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PayoutCounterEntity {

    /** Identifiant de l'unique ligne, imposé par la contrainte {@code ck_payout_counter_single}. */
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
