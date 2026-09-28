package com.school.management.persistance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Date;

/**
 * Entité représentant une année scolaire.
 * Chaque année scolaire est identifiée par un libellé unique (ex. "2024-2025")
 * et possède une date de début et une date de fin.
 * Une seule année scolaire peut être marquée comme courante à la fois.
 */
@Entity
@Table(name = "school_year",
        uniqueConstraints = @UniqueConstraint(name = "uk_school_year_label", columnNames = "label"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SchoolYearEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Libellé unique de l'année scolaire (ex. "2024-2025")
    @Column(name = "label", nullable = false, unique = true)
    private String label;

    // Date de début de l'année scolaire
    @Column(name = "start_date", nullable = false)
    @Temporal(TemporalType.DATE)
    private Date startDate;

    // Date de fin de l'année scolaire
    @Column(name = "end_date", nullable = false)
    @Temporal(TemporalType.DATE)
    private Date endDate;

    // Indique si l'année scolaire est l'année courante (une seule à la fois)
    @Column(name = "is_current", nullable = false)
    @Builder.Default
    private Boolean isCurrent = false;
}
