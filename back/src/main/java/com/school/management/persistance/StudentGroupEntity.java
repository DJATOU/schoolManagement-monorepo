package com.school.management.persistance;

import com.school.management.domain.valueobject.EnrolmentWindow;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.Date;

@Entity
@Table(name = "student_groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class StudentGroupEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "student_id")
    private StudentEntity student;

    @ManyToOne
    @JoinColumn(name = "group_id")
    private GroupEntity group;

    /** Date_Inscription : jour d'arrivée, stocké à 00:00 (décision D1). */
    @Column(name = "date_assigned")
    @Temporal(TemporalType.TIMESTAMP)
    private Date dateAssigned;

    /**
     * Date_Sortie : jour de départ, <strong>inclus</strong> dans la fenêtre, stocké à 00:00.
     * Posée si et seulement si l'inscription est clôturée.
     */
    @Column(name = "date_left")
    @Temporal(TemporalType.TIMESTAMP)
    private Date dateLeft;

    /**
     * Une date d'arrivée fournie est conservée ; sans elle, l'arrivée est le jour même
     * (exigence 5.1). Auparavant, la date fournie était écrasée par l'instant de l'écriture.
     */
    @Override
    protected void onCreate() {
        super.onCreate();
        dateAssigned = EnrolmentWindow.startOfDay(dateAssigned != null ? dateAssigned : new Date());
        dateLeft = EnrolmentWindow.startOfDay(dateLeft);
    }

    /**
     * Toute écriture ramène les deux dates au jour : quel que soit le code qui les pose, la
     * fenêtre se compare en jours (exigence 5.4).
     */
    @Override
    protected void onUpdate() {
        super.onUpdate();
        dateAssigned = EnrolmentWindow.startOfDay(dateAssigned);
        dateLeft = EnrolmentWindow.startOfDay(dateLeft);
    }

    /** Fenêtre_Inscription de cette inscription. */
    public EnrolmentWindow window() {
        return EnrolmentWindow.of(dateAssigned, dateLeft);
    }
}
