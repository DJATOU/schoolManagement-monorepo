package com.school.management.persistance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Impression d'un bordereau de Paie (spec teacher-payroll, exigence 5.3).
 *
 * <p>Modèle {@link RefundReceiptIssuanceEntity} : un journal plutôt qu'un compteur, parce que la
 * réimpression d'une pièce de caisse est l'événement qu'on veut pouvoir retracer. Le rang 1 est
 * l'original ; au-delà, le bordereau porte « DUPLICATA ».</p>
 */
@Entity
@Table(name = "payout_slip_issuance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayoutSlipIssuanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payout_id", nullable = false)
    private TeacherPayoutEntity payout;

    @Column(name = "rank", nullable = false)
    private Integer rank;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "issued_by", nullable = false)
    private String issuedBy;
}
