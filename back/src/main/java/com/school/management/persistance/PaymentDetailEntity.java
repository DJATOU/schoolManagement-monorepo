package com.school.management.persistance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Date;

@Entity
@Table(name = "payment_detail")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class PaymentDetailEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private PaymentEntity payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private SessionEntity session;

    @Column(name = "amount_paid", nullable = false)
    private Double amountPaid; // Le montant payé pour cette entrée

    @Column(name = "payment_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date paymentDate; // La date du paiement

    @Column(name = "is_catch_up")
    @Builder.Default
    private Boolean isCatchUp = false;

    /**
     * Indique si ce paiement a été définitivement supprimé.
     * - true: Suppression définitive (irréversible, ne peut pas être re-payé)
     * - false/null: Suppression temporaire ou désactivation (peut être réactivé)
     */
    @Column(name = "permanently_deleted")
    @Builder.Default
    private Boolean permanentlyDeleted = false;

    @Override
    protected void onCreate() {
        super.onCreate();
        paymentDate = new Date();
    }

    @Override
    protected void onUpdate() {
        super.onUpdate();
        paymentDate = new Date();
    }
}
