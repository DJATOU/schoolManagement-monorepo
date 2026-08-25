package com.school.management.persistance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Date;

/**
 * Demande de rattrapage (catch-up) d'une séance manquée par un étudiant.
 *
 * <p>Une demande relie la séance / le groupe d'origine (où l'étudiant était absent)
 * à la séance / au groupe de rattrapage. Les champs sont alignés 1:1 avec le modèle
 * front {@code CatchUpRequest}.</p>
 */
@Entity
@Table(name = "catch_up_request")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CatchUpRequestEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // L'étudiant concerné par la demande de rattrapage
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private StudentEntity student;

    // Séance d'origine manquée
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_session_id")
    private SessionEntity originalSession;

    // Groupe d'origine de la séance manquée
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_group_id")
    private GroupEntity originalGroup;

    // Enregistrement de présence (absence) à l'origine de la demande
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_attendance_id")
    private AttendanceEntity originalAttendance;

    // Séance de rattrapage planifiée
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catch_up_session_id")
    private SessionEntity catchUpSession;

    // Groupe de rattrapage
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catch_up_group_id")
    private GroupEntity catchUpGroup;

    // Statut du cycle de vie de la demande (PENDING, SCHEDULED, COMPLETED, CANCELLED)
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private CatchUpStatus status;

    // Date de création de la demande
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "request_date")
    private Date requestDate;

    // Date planifiée du rattrapage
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "scheduled_date")
    private Date scheduledDate;

    // Date à laquelle le rattrapage a été effectué
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "completed_date")
    private Date completedDate;

    // Motif d'annulation (si la demande est annulée)
    @Column(name = "cancellation_reason")
    private String cancellationReason;

    // Notes libres
    @Column(name = "notes")
    private String notes;
}
