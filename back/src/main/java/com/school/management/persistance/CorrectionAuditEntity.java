package com.school.management.persistance;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/**
 * Trace immuable d'une correction (spec admin-corrections, exigences 11 et 12).
 *
 * <p><b>Une table commune.</b> Les traces existantes sont dispersées en trois tables, chacune
 * avec ses colonnes. Les nouvelles corrections — encaissement, inscription, présence, séance —
 * partagent celle-ci, ce qui permet de présenter un seul Journal par étudiant.</p>
 *
 * <p><b>Lisible sans la donnée tracée.</b> {@link #summary} est une phrase en français rédigée
 * à l'écriture, quand la séance, l'encaissement et l'étudiant sont tous disponibles. La trace
 * reste lisible si la donnée disparaît ensuite. Les identifiants sont de simples colonnes, sans
 * clé étrangère ni association : une trace doit survivre à ce qu'elle décrit.</p>
 *
 * <p><b>Le rang est l'identifiant.</b> Attribué par la base, strictement croissant dans l'ordre
 * des écritures, il départage deux corrections de la même milliseconde. Un rang calculé par
 * {@code MAX + 1} laisserait deux écritures simultanées obtenir le même.</p>
 *
 * <p>{@link Immutable} : une trace est écrite une fois, puis seulement lue.</p>
 */
@Entity
@Immutable
@Table(name = "correction_audit")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CorrectionAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "domain", length = 40, nullable = false)
    private CorrectionDomain domain;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 40, nullable = false)
    private CorrectionAction action;

    /** Identifiant de la donnée corrigée, dans son domaine. */
    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "series_id")
    private Long seriesId;

    /** Valeur avant, structurée, pour la machine. */
    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    /** Valeur après, structurée, pour la machine. */
    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    /** Phrase en français, pour l'administratrice. */
    @Column(name = "summary", length = 500, nullable = false)
    private String summary;

    /** Effet sur le montant dû, par exemple « dû 6 000,00 → 4 000,00 DA » ; nul s'il n'y en a pas. */
    @Column(name = "amount_effect", length = 500)
    private String amountEffect;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_type", length = 40, nullable = false)
    private CorrectionReasonType reasonType;

    /** Obligatoire pour {@link CorrectionReasonType#OTHER}, facultatif sinon. */
    @Column(name = "reason_text", length = 500)
    private String reasonText;

    /** Administrateur authentifié, jamais une valeur fournie par le client. */
    @Column(name = "performed_by", nullable = false)
    private String performedBy;

    @Column(name = "performed_at", nullable = false)
    private LocalDateTime performedAt;
}
