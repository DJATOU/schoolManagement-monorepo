package com.school.management.persistance;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "attendance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AttendanceEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private StudentEntity student; // Reference to the student

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private SessionEntity session; // Reference to the session

    @Column(name = "status")
    private Boolean isPresent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_series_id")
    private SessionSeriesEntity sessionSeries;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private GroupEntity group;

    @Column(name = "is_justified")
    private Boolean isJustified;

    @Column(name = "is_catch_up")
    @Builder.Default
    private Boolean isCatchUp = false;

    // Droit au rattrapage : vrai par défaut, indépendamment de la justification de l'absence
    @Column(name = "catch_up_right")
    @Builder.Default
    private Boolean catchUpRight = true;

    // Séance manquée : lien depuis une présence de rattrapage vers la séance d'origine
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "missed_session_id")
    private SessionEntity missedSession;

    /**
     * État de facturation du rattrapage. Nul pour une présence ordinaire.
     *
     * <p>Distingue un rattrapage dont la facturation est décidée d'un rattrapage dont personne n'a
     * rien décidé — confusion qui faisait facturer la séance au groupe d'accueil par défaut, y
     * compris quand l'étudiant l'avait déjà payée dans son propre groupe.</p>
     */
    @Column(name = "catch_up_billing_state")
    @Enumerated(EnumType.STRING)
    private CatchUpBillingState catchUpBillingState;

    /**
     * La séance manquée était-elle déjà payée dans sa série d'origine ?
     *
     * <p><strong>Volontairement nullable, et {@code null} n'est pas un défaut :</strong> c'est
     * l'absence de décision. Une valeur par défaut serait relue par personne, et le jour où elle
     * serait fausse l'erreur passerait inaperçue — c'est-à-dire exactement le scénario de double
     * facturation que ce champ existe pour empêcher. Tant qu'il est nul, le rattrapage reste
     * {@link CatchUpBillingState#PENDING} et ne facture rien.</p>
     *
     * <p>La valeur est <strong>stockée</strong>, jamais recalculée à la lecture : une décision prise
     * à un instant donné est une donnée, comme une date. La recalculer depuis l'état de paiement
     * rendrait le coût d'une série sensible aux versements faits sur une autre, et le résultat
     * dépendrait de l'ordre d'évaluation.</p>
     */
    @Column(name = "missed_session_already_paid")
    private Boolean missedSessionAlreadyPaid;
}
