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

    /**
     * Imputation dont cette ligne est une part (spec admin-corrections, D3). L'Encaissement s'en
     * déduit. Obligatoire depuis V7 : une ligne qui ne dirait pas de quel versement elle vient ne
     * peut pas exister (exigence 1.3).
     *
     * <p>Ignorée en JSON : cette entité est encore renvoyée telle quelle par
     * {@code PaymentDetailAdminController}, et sérialiser l'association chargerait l'encaissement
     * entier dans chaque réponse.</p>
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encashment_allocation_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private EncashmentAllocationEntity encashmentAllocation;

    /**
     * La date d'une ligne est celle de l'Encaissement qui l'a produite, fixée à la création.
     * Elle n'est datée de l'instant que si l'appelant n'en fournit pas.
     */
    @Override
    protected void onCreate() {
        super.onCreate();
        if (paymentDate == null) {
            paymentDate = new Date();
        }
    }

    /**
     * Une mise à jour ne redate jamais la ligne. La redater faisait glisser l'argent d'un mois à
     * l'autre dans les recettes par mois : une séance réglée en deux fois, sur deux mois, était
     * comptée en entier au mois de la dernière écriture, et une simple désactivation suffisait à
     * déplacer un versement (spec admin-corrections, inventaire A.1).
     */
    @Override
    protected void onUpdate() {
        super.onUpdate();
    }
}
