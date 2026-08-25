package com.school.management.service.student;

import com.school.management.config.ImageUrlService;
import com.school.management.mapper.StudentMapper;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentStatus;
import com.school.management.repository.LevelRepository;
import com.school.management.repository.StudentGroupRepository;
import com.school.management.repository.StudentRepository;
import com.school.management.repository.TutorRepository;
import com.school.management.service.CurrentSchoolYearService;
import com.school.management.service.exception.CustomServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires (JUnit 5 + Mockito + AssertJ) pour la réactivation d'un
 * étudiant via {@link StudentService#reactivateStudent(Long)}.
 *
 * <p>Vérifie l'exigence 7.5 : la réactivation d'un étudiant INACTIVE repasse son
 * statut à {@link StudentStatus#ACTIVE} et persiste la modification. Couvre aussi
 * le cas d'erreur d'un étudiant introuvable.</p>
 */
class StudentReactivationTest {

    private StudentRepository studentRepository;
    private StudentService service;

    @BeforeEach
    void setUp() {
        studentRepository = mock(StudentRepository.class);
        StudentMapper studentMapper = mock(StudentMapper.class);
        StudentSearchService studentSearchService = mock(StudentSearchService.class);
        ImageUrlService imageUrlService = mock(ImageUrlService.class);
        LevelRepository levelRepository = mock(LevelRepository.class);
        TutorRepository tutorRepository = mock(TutorRepository.class);
        StudentGroupRepository studentGroupRepository = mock(StudentGroupRepository.class);
        CurrentSchoolYearService currentSchoolYearService = mock(CurrentSchoolYearService.class);

        service = new StudentService(studentRepository, studentMapper,
                studentSearchService, imageUrlService, levelRepository, tutorRepository,
                studentGroupRepository, currentSchoolYearService);
    }

    @Test
    @DisplayName("Réactiver un étudiant INACTIVE le repasse à ACTIVE et le sauvegarde (exigence 7.5)")
    void reactivateStudent_setsInactiveStudentToActiveAndSaves() {
        // Étudiant initialement parti / archivé (INACTIVE).
        StudentEntity inactiveStudent = StudentEntity.builder()
                .status(StudentStatus.INACTIVE)
                .build();
        inactiveStudent.setId(1L);

        when(studentRepository.findById(1L)).thenReturn(Optional.of(inactiveStudent));
        // save renvoie l'entité fournie (comportement usuel du repository).
        when(studentRepository.save(any(StudentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StudentEntity result = service.reactivateStudent(1L);

        // Le statut doit être ACTIVE après réactivation (exigence 7.5).
        assertThat(result.getStatus()).isEqualTo(StudentStatus.ACTIVE);
        assertThat(inactiveStudent.getStatus()).isEqualTo(StudentStatus.ACTIVE);
        // La modification doit être persistée.
        verify(studentRepository).save(inactiveStudent);
    }

    @Test
    @DisplayName("Réactiver un étudiant déjà ACTIVE le laisse ACTIVE (idempotence)")
    void reactivateStudent_keepsActiveStudentActive() {
        StudentEntity activeStudent = StudentEntity.builder()
                .status(StudentStatus.ACTIVE)
                .build();
        activeStudent.setId(2L);

        when(studentRepository.findById(2L)).thenReturn(Optional.of(activeStudent));
        when(studentRepository.save(any(StudentEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StudentEntity result = service.reactivateStudent(2L);

        assertThat(result.getStatus()).isEqualTo(StudentStatus.ACTIVE);
        verify(studentRepository).save(activeStudent);
    }

    @Test
    @DisplayName("Réactiver un étudiant introuvable lève une CustomServiceException")
    void reactivateStudent_throwsWhenStudentNotFound() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reactivateStudent(99L))
                .isInstanceOf(CustomServiceException.class)
                .hasMessageContaining("99");

        verify(studentRepository, never()).save(any(StudentEntity.class));
    }
}
