package com.school.management.persistance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Trace immuable d'une modification de la justification d'une absence (exigence 5).
 *
 * <p><strong>Pourquoi une table dédiée plutôt que les colonnes d'audit de {@link BaseEntity}.</strong>
 * {@code updated_by} et {@code date_update} disent qu'une présence a changé, pas <em>quel</em> champ
 * a changé, et ne conservent que la dernière modification. Répondre à un parent qui contexte une
 * absence exige l'historique complet : valeur avant, valeur après, auteur, date et motif.</p>
 *
 * <p><strong>Pourquoi {@code attendanceId} n'est pas une association JPA.</strong> C'est une colonne
 * simple, sans clé étrangère, sur le modèle de {@link PaymentDetailAuditEntity} qui fait déjà ce
 * choix dans ce projet. Une association avec cascade supprimerait la trace en même temps que la
 * présence auditée (exigence 5.11), or c'est justement après la disparition d'une donnée qu'on a
 * besoin de savoir qui l'a modifiée.</p>
 *
 * <p><strong>Pourquoi cette entité n'hérite pas de {@link BaseEntity}.</strong> Une ligne d'audit
 * n'a ni cycle de vie ni désactivation : {@code active}, {@code updatedBy} et {@code dateUpdate} y
 * seraient dénués de sens, et un {@code active = false} sur une trace serait un contresens.</p>
 *
 * <p>Aucun point d'entrée de modification ni de suppression n'est exposé sur cette entité
 * (exigence 5.3) : elle est écrite une fois, puis seulement lue.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "attendance_justification_audit")
public class AttendanceJustificationAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Présence auditée. Colonne simple et non association : voir la note de classe. */
    @Column(name = "attendance_id", nullable = false)
    private Long attendanceId;

    /**
     * Valeur de la justification avant modification. Nulle lorsque la justification n'avait jamais
     * été renseignée, ce qui est distinct d'un « non » explicite.
     */
    @Column(name = "old_value")
    private Boolean oldValue;

    /** Valeur appliquée. Jamais nulle : une entrée n'existe que pour un changement effectif. */
    @Column(name = "new_value", nullable = false)
    private Boolean newValue;

    /**
     * Auteur, résolu depuis le contexte de sécurité, avec l'identifiant de repli {@code system}
     * en l'absence d'utilisateur authentifié (exigence 5.2).
     */
    @Column(name = "performed_by", nullable = false)
    private String performedBy;

    /**
     * Horodatage à la milliseconde (exigence 5.1). L'horodatage est fourni par le service et non
     * par un {@code @PrePersist} : le rejeu d'une opération doit pouvoir conserver l'instant de la
     * tentative, et un test doit pouvoir le fixer.
     */
    @Column(name = "performed_at", nullable = false)
    private LocalDateTime performedAt;

    /**
     * Rang de séquence, croissant par présence. Départage deux entrées de même horodatage, ce qui
     * rend déterministes l'ordre de restitution (exigence 5.7) et la valeur courante dérivée de la
     * dernière entrée (exigence 5.8). Sans lui, deux modifications dans la même milliseconde
     * rendraient « la plus récente » ambiguë.
     */
    @Column(name = "sequence_rank", nullable = false)
    private Long sequenceRank;

    /** Commentaire libre accompagnant la modification, au plus 500 caractères. Peut être nul. */
    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;
}
