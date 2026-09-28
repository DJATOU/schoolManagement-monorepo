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

    /**
     * Motif du remboursement (exigence 6.1). Obligatoire à toute création nouvelle, contrôle
     * appliqué par {@code RefundService}.
     *
     * <p>La colonne reste néanmoins nullable, et ce n'est pas un oubli : les remboursements
     * enregistrés avant cette traçabilité n'ont pas de motif, et en fabriquer un mentirait sur ce
     * qui a été saisi. Ils sont présentés avec la mention « Motif non renseigné (antérieur à la
     * traçabilité) » (exigence 6.10).</p>
     */
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    /**
     * Numéro de pièce, de la forme {@code REMB-AAAA-NNNN} (exigences 6.4, 6.5).
     *
     * <p>Unique et immuable après création. L'unicité est portée par une contrainte de stockage et
     * non par le calcul applicatif (exigence 6.6) : elle vaut ainsi quel que soit le nombre
     * d'instances de l'application, et le service n'a qu'à rejouer sur collision.</p>
     */
    @Column(name = "refund_number", nullable = false, unique = true, length = 32)
    private String refundNumber;
}
