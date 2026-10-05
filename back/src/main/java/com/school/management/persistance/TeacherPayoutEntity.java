package com.school.management.persistance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Une Paie d'enseignant : la part, versée à l'enseignant du groupe, de ce que la série a encaissé
 * (spec teacher-payroll).
 *
 * <p><b>Une pièce de caisse, copie figée.</b> Taux, base et parts sont recopiés à l'enregistrement
 * et ne changent plus : un taux modifié, un enseignant réaffecté ou un encaissement tardif ne
 * réécrivent jamais une paie versée (exigences 1.6, 6.1). L'argent arrivé ou rendu après coup donne
 * une Régularisation ; une erreur se rectifie par annulation ou remplacement, tracés.</p>
 *
 * <p><b>Pourquoi elle n'hérite pas de {@link BaseEntity}.</b> Comme l'Encaissement : un {@code active}
 * ferait double emploi avec {@link #status}. Date et auteur sont portés par {@link #paidAt} et
 * {@link #paidBy}, fixés par le serveur.</p>
 *
 * <p>Les règles de cohérence — parts qui somment la base, paie initiale qui partage tout l'encaissé,
 * annulation datée et motivée, une seule paie initiale active par série — sont vérifiées par la base
 * (migration V9).</p>
 */
@Entity
@Table(name = "teacher_payout")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class TeacherPayoutEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** {@code PAIE-AAAA-NNNN}, attribué par le serveur, jamais réutilisé. */
    @Column(name = "payout_number", length = 32, nullable = false, unique = true)
    private String payoutNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 20, nullable = false)
    private PayoutKind kind;

    /** Paie initiale de la série ; renseignée seulement pour une Régularisation. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initial_payout_id")
    private TeacherPayoutEntity initialPayout;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private TeacherEntity teacher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private GroupEntity group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id", nullable = false)
    private SessionSeriesEntity series;

    /** Taux choisi, pour les filtres ; la valeur qui fait foi est la copie ci-dessous. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rate_id")
    private TeacherPayRateEntity rate;

    @Column(name = "rate_label", length = 100, nullable = false)
    private String rateLabel;

    @Column(name = "teacher_percent", precision = 5, scale = 2, nullable = false)
    private BigDecimal teacherPercent;

    /** Encaissé brut de la série au moment de la paie. */
    @Column(name = "collected_gross", precision = 12, scale = 2, nullable = false)
    private BigDecimal collectedGross;

    /** Remboursé sur la série au moment de la paie. */
    @Column(name = "refunded", precision = 12, scale = 2, nullable = false)
    private BigDecimal refunded;

    /** Encaissé_Net couvert par cette paie : brut moins remboursé. */
    @Column(name = "collected_net", precision = 12, scale = 2, nullable = false)
    private BigDecimal collectedNet;

    /** Part de l'encaissé que cette paie partage, et qu'aucune paie précédente n'avait couverte. */
    @Column(name = "base_delta", precision = 12, scale = 2, nullable = false)
    private BigDecimal baseDelta;

    /** Part de l'enseignant ; négative pour une Retenue. */
    @Column(name = "teacher_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal teacherAmount;

    /** Part de l'école : {@code baseDelta − teacherAmount}. */
    @Column(name = "school_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal schoolAmount;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "paid_at", nullable = false)
    private Date paidAt;

    @Column(name = "paid_by", nullable = false)
    private String paidBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private PayoutStatus status;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "cancelled_at")
    private Date cancelledAt;

    @Column(name = "cancelled_by")
    private String cancelledBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_reason_type", length = 40)
    private CorrectionReasonType cancelReasonType;

    @Column(name = "cancel_reason_text", length = 500)
    private String cancelReasonText;

    /** Paie annulée que celle-ci remplace. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaces_id")
    private TeacherPayoutEntity replaces;

    /** Paie qui remplace celle-ci ; non nulle seulement si celle-ci est annulée. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_by_id")
    private TeacherPayoutEntity replacedBy;

    /** Vrai tant que la paie compte dans les montants versés. */
    public boolean isActive() {
        return status == PayoutStatus.ACTIVE;
    }
}
