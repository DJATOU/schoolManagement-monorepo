package com.school.management.persistance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.Date;

/**
 * Un versement reçu d'un parent, tel qu'il a eu lieu (spec admin-corrections, exigence 1).
 *
 * <p><b>Pourquoi cette entité.</b> Le registre {@code payments} ne porte qu'un cumul par étudiant
 * et par série, dont la date est réécrite à chaque écriture ; une séance n'a qu'une ligne de
 * ventilation, complétée par les versements successifs. Un versement n'avait donc pas d'existence
 * propre, et l'annuler était impossible sans toucher aux autres. L'Encaissement est l'unité que
 * l'administratrice retrouve sur un reçu, annule ou corrige.</p>
 *
 * <p><b>Ce qui ne change jamais.</b> Montant, date, étudiant et série ne sont pas modifiés après
 * l'enregistrement : une erreur se rectifie par annulation, ou par remplacement par un nouvel
 * encaissement qui désigne l'original (exigence 1.5). Seuls le mode de paiement et la note, qui
 * n'entrent dans aucun calcul, sont modifiables.</p>
 *
 * <p><b>Pourquoi elle n'hérite pas de {@link BaseEntity}.</b> Son {@code active} ferait double
 * emploi avec {@link #status}, et un filtre {@code active = true} écrit par habitude laisserait
 * passer un encaissement annulé. Auteur et date sont portés explicitement par
 * {@link #receivedBy}, {@link #receivedAt} et les champs d'annulation.</p>
 *
 * <p>Les règles de cohérence — montant strictement positif, annulation toujours datée et
 * motivée, encaissement remplacé forcément annulé — sont vérifiées par la base (migration V6) :
 * elles valent quel que soit le code qui écrit.</p>
 */
@Entity
@Table(name = "encashment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class EncashmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Numéro imprimé sur le reçu, attribué par le serveur, jamais réutilisé. */
    @Column(name = "receipt_number", length = 32, nullable = false, unique = true)
    private String receiptNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private StudentEntity student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private GroupEntity group;

    /** Série visée à la saisie ; le surplus éventuel est imputé sur les suivantes. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_series_id", nullable = false)
    private SessionSeriesEntity targetSeries;

    /** Montant reçu, échelle 2. Jamais de {@code double} sur un montant (audit H4). */
    @Column(name = "amount_received", precision = 12, scale = 2, nullable = false)
    private BigDecimal amountReceived;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 20, nullable = false)
    private EncashmentKind kind;

    /** Date et heure d'encaissement fixées par le serveur, jamais par le poste client. */
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "received_at", nullable = false)
    private Date receivedAt;

    @Column(name = "received_by", nullable = false)
    private String receivedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private EncashmentStatus status;

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

    /** Encaissement annulé que celui-ci remplace. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaces_id")
    private EncashmentEntity replaces;

    /** Encaissement qui remplace celui-ci ; non nul seulement si celui-ci est annulé. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_by_id")
    private EncashmentEntity replacedBy;

    /** Vrai tant que l'encaissement compte dans les montants versés. */
    public boolean isActive() {
        return status == EncashmentStatus.ACTIVE;
    }
}
