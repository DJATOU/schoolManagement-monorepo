package com.school.management.persistance;

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
    private Boolean isCatchUp = false;

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
