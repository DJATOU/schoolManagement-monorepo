package com.school.management.persistance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Remboursement (refund) accordé à un étudiant et rattaché à un paiement précis.
 *
 * <p>Un remboursement référence le paiement d'origine ({@link #payment}) et
 * l'étudiant concerné ({@link #student}). Le montant remboursé ({@link #amount})
 * ne doit pas dépasser le montant versé du paiement rattaché (aucun geste
 * commercial) ; cette règle est appliquée par la couche service.</p>
 */
@Entity
@Table(name = "refund")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class RefundEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Le paiement d'origine auquel se rattache le remboursement
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private PaymentEntity payment;

    // L'étudiant bénéficiaire du remboursement
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private StudentEntity student;

    // Le montant remboursé
    @Column(name = "amount", precision = 12, scale = 2)
    private BigDecimal amount;

    // La date du remboursement
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "refund_date")
    private Date refundDate;
}
