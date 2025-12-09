package com.school.management.shared.mapper;

import com.school.management.repository.*;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Contexte pour passer des dépendances aux mappers MapStruct.
 * Remplace l'anti-pattern ApplicationContextProvider.
 *
 * Ce contexte contient tous les repositories nécessaires pour résoudre
 * les relations lors du mapping DTO → Entity.
 *
 * Usage:
 * <pre>
 * MappingContext context = MappingContext.of(levelRepo, tutorRepo, ...);
 * Student student = studentMapper.toEntity(dto, context);
 * </pre>
 *
 * @author Claude Code
 * @since Phase 1 Refactoring
 */
@Getter
@AllArgsConstructor
public class MappingContext {

    // Repositories pour Student/Teacher/Person
    private final LevelRepository levelRepository;
    private final TutorRepository tutorRepository;

    // Repositories pour Group
    private final GroupTypeRepository groupTypeRepository;
    private final SubjectRepository subjectRepository;
    private final PricingRepository pricingRepository;
    private final TeacherRepository teacherRepository;

    // Repositories pour Session
    private final RoomRepository roomRepository;
    private final GroupRepository groupRepository;
    private final SessionSeriesRepository sessionSeriesRepository;

    // Repositories pour Payment
    private final StudentRepository studentRepository;
    private final SessionRepository sessionRepository;

    /**
     * Factory method pour créer un contexte avec tous les repositories
     */
    public static MappingContext of(
            LevelRepository levelRepository,
            TutorRepository tutorRepository,
            GroupTypeRepository groupTypeRepository,
            SubjectRepository subjectRepository,
            PricingRepository pricingRepository,
            TeacherRepository teacherRepository,
            RoomRepository roomRepository,
            GroupRepository groupRepository,
            SessionSeriesRepository sessionSeriesRepository,
            StudentRepository studentRepository,
            SessionRepository sessionRepository) {

        return new MappingContext(
            levelRepository,
            tutorRepository,
            groupTypeRepository,
            subjectRepository,
            pricingRepository,
            teacherRepository,
            roomRepository,
            groupRepository,
            sessionSeriesRepository,
            studentRepository,
            sessionRepository
        );
    }

    /**
     * Factory method simplifié pour Student mapping
     * (seulement les repos nécessaires pour Student)
     */
    public static MappingContext forStudent(
            LevelRepository levelRepository,
            TutorRepository tutorRepository) {

        return new MappingContext(
            levelRepository,
            tutorRepository,
            null, null, null, null, null, null, null, null, null
        );
    }

    /**
     * Factory method simplifié pour Group mapping
     */
    public static MappingContext forGroup(
            GroupTypeRepository groupTypeRepository,
            LevelRepository levelRepository,
            SubjectRepository subjectRepository,
            PricingRepository pricingRepository,
            TeacherRepository teacherRepository) {

        return new MappingContext(
            levelRepository,
            null,
            groupTypeRepository,
            subjectRepository,
            pricingRepository,
            teacherRepository,
            null, null, null, null, null
        );
    }
}
