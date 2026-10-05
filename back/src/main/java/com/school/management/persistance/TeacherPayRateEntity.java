package com.school.management.persistance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * Taux de rémunération des enseignants, entretenu par l'administrateur comme les tarifs, et choisi
 * au moment de chaque Paie (spec teacher-payroll, exigence 1).
 *
 * <p>Seule la part de l'enseignant est stockée : celle de l'école est {@code 100 − teacherPercent},
 * deux parts seulement existant. Un taux n'est jamais supprimé ; désactivé, il n'est plus proposé.
 * Une Paie en garde une copie figée : le modifier ne réécrit aucune paie passée (exigence 1.6).</p>
 */
@Entity
@Table(name = "teacher_pay_rate")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class TeacherPayRateEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "label", length = 100, nullable = false)
    private String label;

    /** Part de l'enseignant en pourcentage, dans ]0 ; 100[, deux décimales. */
    @Column(name = "teacher_percent", precision = 5, scale = 2, nullable = false)
    private BigDecimal teacherPercent;
}
