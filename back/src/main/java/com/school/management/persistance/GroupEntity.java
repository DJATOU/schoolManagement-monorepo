package com.school.management.persistance;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class GroupEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)  // Lazy loading for groupType
    @JoinColumn(name = "group_type_id")
    private GroupTypeEntity groupType;

    @ManyToOne(fetch = FetchType.LAZY)  // Lazy loading for level
    @JoinColumn(name = "level_id")
    private LevelEntity level;

    @ManyToOne(fetch = FetchType.LAZY)  // Lazy loading for subject
    @JoinColumn(name = "subject_id")
    private SubjectEntity subject;

    @Column(name = "session_per_serie")
    private int sessionNumberPerSerie;

    @ManyToOne(fetch = FetchType.LAZY)  // Lazy loading for price
    @JoinColumn(name = "price_id")
    private PricingEntity price;

    @Column(name = "photo")
    private String photo;

    @ManyToOne(fetch = FetchType.LAZY)  // Lazy loading for teacher
    @JoinColumn(name = "teacher_id")
    @JsonBackReference
    private TeacherEntity teacher;

    @Builder.Default
    @ManyToMany(mappedBy = "groups", fetch = FetchType.LAZY)  // Lazy loading for students
    @JsonIgnore
    private Set<StudentEntity> students = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "group", fetch = FetchType.LAZY)  // Lazy loading for series
    private Set<SessionSeriesEntity> series = new HashSet<>();
}
