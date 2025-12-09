package com.school.management.persistance;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.boot.autoconfigure.domain.EntityScan;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Set;

@Entity
@Table(name = "session_series")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class SessionSeriesEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    @ManyToOne
    @JoinColumn(name = "group_id")
    private GroupEntity group; // Le groupe associé à la série de sessions

    @Column(name = "total_sessions")
    private int totalSessions; // Nombre total de séances dans la série

    @Column(name = "sessions_completed")
    private int sessionsCompleted; // Nombre de séances déjà complétées

    @OneToMany(mappedBy = "sessionSeries")
    private Set<SessionEntity> sessions ;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "serie_time_start")
    private Date serieTimeStart;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "serie_time_end")
    private Date serieTimeEnd;
    // ... autres champs et méthodes ...
}

