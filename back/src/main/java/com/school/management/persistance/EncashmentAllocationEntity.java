package com.school.management.persistance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Imputation : la part d'un Encaissement créditée sur une série (spec admin-corrections, D3).
 *
 * <p>Un encaissement crédite la série visée à hauteur de ce qu'elle peut recevoir, puis reporte
 * le surplus sur les séries suivantes : une Imputation par série créditée, chaque série au plus
 * une fois par encaissement. Les lignes de ventilation par séance et les reports désignent leur
 * Imputation ; l'Encaissement s'en déduit.</p>
 *
 * <p><b>Le cumul devient un dérivé.</b> {@code payments.amount_paid} vaut, pour l'étudiant et la
 * série, la somme des Imputations actives. Il n'est plus réécrit depuis la somme des lignes de
 * ventilation, ce qui effaçait de l'argent reçu à la première correction d'une ligne.</p>
 *
 * <p>Annuler l'encaissement désactive ses Imputations : l'argent ne compte plus, la ligne reste
 * pour l'historique.</p>
 */
@Entity
@Table(name = "encashment_allocation",
        uniqueConstraints = @UniqueConstraint(name = "uk_allocation_encashment_series",
                columnNames = { "encashment_id", "series_id" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class EncashmentAllocationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encashment_id", nullable = false)
    private EncashmentEntity encashment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id", nullable = false)
    private SessionSeriesEntity series;

    /** Cumul de la série pour l'étudiant, crédité par cette Imputation. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private PaymentEntity payment;

    /** Montant crédité sur la série, échelle 2, strictement positif. */
    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    /** Vrai si la série créditée n'est pas la série visée : la part vient d'un report. */
    @Column(name = "carried_over", nullable = false)
    private Boolean carriedOver;

    /** Faux une fois l'Encaissement annulé. */
    @Column(name = "active", nullable = false)
    private Boolean active;
}
