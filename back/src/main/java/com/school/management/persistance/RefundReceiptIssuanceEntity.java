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
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Émission d'un reçu de remboursement (exigence 8.10).
 *
 * <p><strong>Pourquoi un journal plutôt qu'un compteur.</strong> L'exigence n'impose que le rang de
 * la production courante et sa date, qu'une simple colonne compteur sur {@code refund} aurait suffi
 * à porter. Le journal est retenu parce que la réimpression d'un reçu de caisse est précisément
 * l'événement qu'on veut pouvoir retracer : un reçu réimprimé peut servir deux fois. Savoir qui a
 * réédité une pièce, et quand, a une valeur de contrôle que le compteur ne donne pas.</p>
 *
 * <p><strong>Pourquoi une vraie association ici</strong>, contrairement à
 * {@link AttendanceJustificationAuditEntity} : une émission de reçu n'a aucun sens sans son
 * remboursement, et n'a donc aucune raison de lui survivre.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "refund_receipt_issuance")
public class RefundReceiptIssuanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_id", nullable = false)
    private RefundEntity refund;

    /**
     * Rang de l'émission pour ce remboursement, à partir de 1. Le rang 1 est la production
     * d'origine ; tout rang supérieur est un duplicata et doit être signalé comme tel sur le
     * document (exigence 8.10).
     */
    @Column(name = "rank", nullable = false)
    private Integer rank;

    /** Date de cette production précise, affichée sur le duplicata. */
    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    /** Auteur de l'émission, résolu depuis le contexte de sécurité (repli {@code system}). */
    @Column(name = "issued_by", nullable = false)
    private String issuedBy;
}
