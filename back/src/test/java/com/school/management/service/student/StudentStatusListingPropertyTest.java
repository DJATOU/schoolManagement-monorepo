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
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests de propriété (jqwik) pour le filtre de statut de {@link StudentService}.
 *
 * <p>Le repository est mocké (Mockito) : {@code findByStatus(ACTIVE)} renvoie le
 * sous-ensemble actif, {@code findAll()} renvoie l'ensemble complet. Les autres
 * dépendances du service sont mockées mais non sollicitées par ce filtre.</p>
 */
class StudentStatusListingPropertyTest {

    // Feature: school-year, Property 14: For any set of Students with mixed statuses, the default listing returns exactly ACTIVE students, and the include-inactive listing additionally returns INACTIVE students.
    @Property(tries = 100)
    void property14_studentStatusListingFilter(@ForAll("mixedStatusStudents") List<StudentEntity> students) {
        // Sous-ensembles attendus, partitionnés par statut.
        List<StudentEntity> active = students.stream()
                .filter(s -> s.getStatus() == StudentStatus.ACTIVE)
                .toList();
        List<StudentEntity> inactive = students.stream()
                .filter(s -> s.getStatus() == StudentStatus.INACTIVE)
                .toList();

        StudentRepository studentRepository = mock(StudentRepository.class);
        StudentMapper studentMapper = mock(StudentMapper.class);
        StudentSearchService studentSearchService = mock(StudentSearchService.class);
        ImageUrlService imageUrlService = mock(ImageUrlService.class);
        LevelRepository levelRepository = mock(LevelRepository.class);
        TutorRepository tutorRepository = mock(TutorRepository.class);
        StudentGroupRepository studentGroupRepository = mock(StudentGroupRepository.class);
        CurrentSchoolYearService currentSchoolYearService = mock(CurrentSchoolYearService.class);

        // Contrat des requêtes dérivées : ACTIVE => sous-ensemble actif ; findAll => tout.
        when(studentRepository.findByStatus(StudentStatus.ACTIVE)).thenReturn(active);
        when(studentRepository.findAll()).thenReturn(students);

        StudentService service = new StudentService(studentRepository, studentMapper,
                studentSearchService, imageUrlService, levelRepository, tutorRepository,
                studentGroupRepository, currentSchoolYearService);

        // Par défaut (includeInactive = false) : exactement les étudiants ACTIVE (exigence 7.3).
        List<StudentEntity> defaultListing = service.findStudentsByStatus(false);
        assertThat(defaultListing).containsExactlyInAnyOrderElementsOf(active);
        assertThat(defaultListing).noneMatch(s -> s.getStatus() == StudentStatus.INACTIVE);

        // Avec includeInactive = true : les actifs plus, en plus, les inactifs (exigence 7.4).
        List<StudentEntity> inclusiveListing = service.findStudentsByStatus(true);
        assertThat(inclusiveListing).containsExactlyInAnyOrderElementsOf(students);
        assertThat(inclusiveListing).containsAll(active);
        assertThat(inclusiveListing).containsAll(inactive);
    }

    /**
     * Ensembles d'étudiants (0 à 30) de statuts mélangés, incluant l'ensemble vide,
     * des ensembles entièrement actifs et entièrement inactifs.
     */
    @Provide
    Arbitrary<List<StudentEntity>> mixedStatusStudents() {
        Arbitrary<StudentStatus> status = Arbitraries.of(StudentStatus.ACTIVE, StudentStatus.INACTIVE);
        return status.list().ofMinSize(0).ofMaxSize(30).map(statuses -> {
            List<StudentEntity> list = new ArrayList<>();
            long id = 1L;
            for (StudentStatus st : statuses) {
                StudentEntity student = StudentEntity.builder().status(st).build();
                student.setId(id++);
                list.add(student);
            }
            return list;
        });
    }
}
