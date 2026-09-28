package com.school.management.persistance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Trace d'un report de versement d'une série vers une autre (Exigence 6).
 *
 * <p>Un versement s'arrête au montant dû de la série visée à la saisie ; le surplus est
 * imputé sur les séries suivantes par identifiant croissant. Chaque imputation reportée
 * donne lieu à une ligne de cette table, qui répond à la question « d'où vient cet
 * argent ». C'est une notion distincte de {@link PaymentDetailEntity}, qui répond à
 * « quelle séance ce montant couvre-t-il » : un seul report se ventile couramment en
 * plusieurs {@code payment_detail} sur la série destination.</p>
 *
 * <p>{@link #sourceSeries} est la série visée par l'administrateur au moment de la saisie,
 * {@link #targetSeries} la série effectivement créditée, et {@link #targetPayment} la ligne
 * de paiement de cette série destination. La présence d'un report pointant une ligne de
 * paiement permet de distinguer un montant reçu par report d'un montant imputé
 * directement (Exigence 6.4).</p>
 *
 * <p>L'auteur et l'horodatage du report proviennent de l'audit JPA porté par
 * {@link BaseEntity} ({@code createdBy}, {@code dateCreation}) : aucun champ
 * supplémentaire n'est nécessaire.</p>
 */
@Entity
@Table(name = "payment_carry_over")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class PaymentCarryOverEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // L'étudiant dont le versement a produit le report
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentEntity student;

    // La série visée à la saisie du versement, d'où provient le surplus
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_series_id", nullable = false)
    private SessionSeriesEntity sourceSeries;

    // La série effectivement créditée par le report
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_series_id", nullable = false)
    private SessionSeriesEntity targetSeries;

    // La ligne de paiement de la série destination créditée par ce report
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_payment_id", nullable = false)
    private PaymentEntity targetPayment;

    // Le montant reporté. BigDecimal échelle 2 : jamais de double sur un montant (audit H4)
    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    // La date du versement d'origine, celle de l'encaissement qui a produit le surplus
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "origin_payment_date")
    private Date originPaymentDate;
}
