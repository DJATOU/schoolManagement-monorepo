package com.school.management.service;

import com.school.management.domain.valueobject.EnrolmentWindow;
import com.school.management.dto.StudentGroupDTO;
import com.school.management.mapper.GroupMapper;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.StudentGroupRepository;
import com.school.management.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * « Ajouter un étudiant » renvoie le nombre d'inscriptions créées.
 *
 * <p>L'import CSV des inscriptions envoie un seul élève par ligne et lit {@code 0} comme « déjà
 * inscrit » : il n'a donc pas à réimplémenter la détection d'un membre actif. L'écran manuel, lui,
 * ignore ce nombre ; un membre actif y reste ignoré sans erreur.</p>
 */
@DisplayName("Ajouter un étudiant : nombre d'inscriptions créées")
class StudentGroupServiceEnrolmentCountTest {

    private static final long GROUP_ID = 1L;

    private StudentGroupRepository studentGroupRepository;
    private StudentRepository studentRepository;
    private StudentGroupService service;

    private GroupEntity group;
    private StudentEntity alreadyMember;
    private StudentEntity newcomer;

    @BeforeEach
    void setUp() {
        studentGroupRepository = mock(StudentGroupRepository.class);
        studentRepository = mock(StudentRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        // Garde simulé : le groupe est de l'année courante.
        ReadOnlyYearGuard readOnlyYearGuard = mock(ReadOnlyYearGuard.class);

        service = new StudentGroupService(studentGroupRepository, studentRepository,
                groupRepository, mock(GroupMapper.class), readOnlyYearGuard);

        SchoolYearEntity year = SchoolYearEntity.builder().label("2029-2030")
                .startDate(day(2029, 9, 1)).endDate(day(2030, 6, 30)).isCurrent(true).build();
        year.setId(5L);
        group = new GroupEntity();
        group.setId(GROUP_ID);
        group.setName("Math 1ère A");
        group.setSchoolYear(year);
        group.setStudents(new HashSet<>());
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

        // Sans niveau : le contrôle de niveau ne bloque pas (données incomplètes).
        alreadyMember = student(7L, "Amine", "Belkacem");
        newcomer = student(8L, "Léa", "Haddad");
        when(studentGroupRepository.existsByStudentAndGroupAndActiveTrue(alreadyMember, group)).thenReturn(true);
        when(studentGroupRepository.existsByStudentAndGroupAndActiveTrue(newcomer, group)).thenReturn(false);
        when(studentGroupRepository.findByStudentId(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("un membre actif et un nouveau : 1, seul le nouveau est inscrit, à 00:00 du jour d'arrivée")
    void countsOnlyCreatedEnrolments() {
        when(studentRepository.findAllById(List.of(7L, 8L))).thenReturn(List.of(alreadyMember, newcomer));

        int created = service.addStudentsToGroup(dto(List.of(7L, 8L)));

        assertThat(created).isEqualTo(1);
        ArgumentCaptor<StudentGroupEntity> saved = ArgumentCaptor.forClass(StudentGroupEntity.class);
        verify(studentGroupRepository).save(saved.capture());
        assertThat(saved.getValue().getStudent()).isSameAs(newcomer);
        assertThat(saved.getValue().getGroup()).isSameAs(group);
        assertThat(saved.getValue().getDateAssigned())
                .isEqualTo(EnrolmentWindow.startOfDay(LocalDate.of(2029, 10, 1)));
    }

    @Test
    @DisplayName("tous déjà membres : 0, rien n'est enregistré, et pas d'erreur")
    void zeroWhenEveryoneIsAlreadyEnrolled() {
        when(studentRepository.findAllById(List.of(7L))).thenReturn(List.of(alreadyMember));

        int created = service.addStudentsToGroup(dto(List.of(7L)));

        assertThat(created).isZero();
        verify(studentGroupRepository, never()).save(any(StudentGroupEntity.class));
    }

    private static StudentGroupDTO dto(List<Long> studentIds) {
        return StudentGroupDTO.builder().groupId(GROUP_ID).studentIds(studentIds)
                .dateAssigned(LocalDate.of(2029, 10, 1)).build();
    }

    private static StudentEntity student(long id, String firstName, String lastName) {
        StudentEntity student = new StudentEntity();
        student.setId(id);
        student.setFirstName(firstName);
        student.setLastName(lastName);
        return student;
    }

    private static Date day(int year, int month, int dayOfMonth) {
        return Date.from(LocalDate.of(year, month, dayOfMonth).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
