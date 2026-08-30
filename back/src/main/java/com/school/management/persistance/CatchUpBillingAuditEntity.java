package com.school.management.persistance;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Trace immuable d'une décision de facturation prise sur un rattrapage.
 *
 * <p>Deux décisions sont corrigeables — la séance manquée désignée, et « était-elle déjà payée ? » —
 * et toutes deux déplacent de l'argent d'un groupe à un autre. Une correction sans trace laisse
 * l'écart inexplicable : le montant a changé, personne ne sait par qui ni pourquoi.</p>
 *
 * <p><strong>{@code attendanceId} est une colonne simple, volontairement sans clé étrangère.</strong>
 * La trace doit survivre à la suppression de la présence auditée : c'est justement après la
 * disparition d'une donnée qu'on a besoin de savoir qui l'a modifiée. Une clé étrangère avec cascade
 * détruirait la preuve en même temps que son objet. Même parti que
 * {@code attendance_justification_audit} et {@code payment_detail_audit}.</p>
 *
 * <p>Aucun setter public : une entrée d'audit ne se modifie pas. C'est le sens du mot trace.</p>
 */
@Entity
@Table(name = "catch_up_billing_audit")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class CatchUpBillingAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Présence auditée. Sans clé étrangère, afin que la trace lui survive. */
    @Column(name = "attendance_id", nullable = false)
    private Long attendanceId;

    /** Décision modifiée : la séance manquée désignée, ou l'indicateur « déjà payée ». */
    @Enumerated(EnumType.STRING)
    @Column(name = "field", nullable = false, length = 32)
    private CatchUpBillingAuditField field;

    /**
     * Valeurs avant et après, rendues en texte.
     *
     * <p>Elles portent tantôt un identifiant de séance, tantôt un booléen. Une colonne typée par
     * champ multiplierait les colonnes vides sans rien ajouter à la lecture. {@code null} garde son
     * sens propre : « aucune séance désignée » ou « décision non tranchée ».</p>
     */
    @Column(name = "old_value", length = 64)
    private String oldValue;

    @Column(name = "new_value", length = 64)
    private String newValue;

    /** Auteur de la correction, tel que résolu par l'audit de sécurité. */
    @Column(name = "performed_by", nullable = false)
    private String performedBy;

    @Column(name = "performed_at", nullable = false)
    private LocalDateTime performedAt;

    /**
     * Rang de séquence, pour départager deux entrées de même horodatage.
     *
     * <p>Sans lui, l'ordre de restitution de deux corrections faites dans la même milliseconde
     * n'est pas déterministe, et « quelle est la dernière décision » reste ambigu — précisément la
     * question qu'on pose à une piste d'audit.</p>
     */
    @Column(name = "sequence_rank", nullable = false)
    private Long sequenceRank;

    /** Commentaire libre de l'auteur : le « pourquoi », que les valeurs ne portent pas. */
    @Column(name = "comment", columnDefinition = "text")
    private String comment;
}
