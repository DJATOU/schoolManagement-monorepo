package com.school.management.service;

import com.school.management.dto.StudentGroupDTO;
import com.school.management.mapper.GroupMapper;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.StudentGroupRepository;
import com.school.management.repository.StudentRepository;
import com.school.management.service.exception.ReadOnlySchoolYearException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Un groupe rattaché à une année scolaire passée est <strong>figé</strong> : il conserve ses
 * étudiants et sa composition ne change plus (Exigence 9.2).
 *
 * <p>Le garde protégeait déjà le groupe lui-même, ses séries et ses séances, mais pas la table
 * d'affectation : on pouvait encore inscrire ou retirer un étudiant d'un groupe de l'année
 * précédente, ce qui aurait faussé les présences et les soldes déjà enregistrés.</p>
 */
class StudentGroupReadOnlyYearTest {

    private static final long GROUP_ID = 1L;
    private static final long STUDENT_ID = 7L;

    private StudentGroupRepository studentGroupRepository;
    private StudentRepository studentRepository;
    private GroupRepository groupRepository;
    private ReadOnlyYearGuard readOnlyYearGuard;
    private StudentGroupService service;

    private GroupEntity pastYearGroup;
    private StudentEntity student;

    @BeforeEach
    void setUp() {
        studentGroupRepository = mock(StudentGroupRepository.class);
        studentRepository = mock(StudentRepository.class);
        groupRepository = mock(GroupRepository.class);
        readOnlyYearGuard = mock(ReadOnlyYearGuard.class);

        service = new StudentGroupService(studentGroupRepository, studentRepository,
                groupRepository, mock(GroupMapper.class), readOnlyYearGuard);

        pastYearGroup = new GroupEntity();
        pastYearGroup.setId(GROUP_ID);
        pastYearGroup.setName("Math 1ère A");
        pastYearGroup.setStudents(new HashSet<>());

        student = new StudentEntity();
        student.setId(STUDENT_ID);
        student.setFirstName("Rayan");
        student.setLastName("Saadi");

        // Le garde refuse toute mutation sur ce groupe (année autre que l'année courante).
        doThrow(new ReadOnlySchoolYearException())
                .when(readOnlyYearGuard).assertGroupMutable(pastYearGroup);
    }

    @Test
    void addStudentsToGroup_pastYearGroup_isRejected() {
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(pastYearGroup));

        StudentGroupDTO dto = new StudentGroupDTO();
        dto.setGroupId(GROUP_ID);
        dto.setStudentIds(List.of(STUDENT_ID));

        assertThatThrownBy(() -> service.addStudentsToGroup(dto))
                .isInstanceOf(ReadOnlySchoolYearException.class);

        verify(studentGroupRepository, never()).save(any(StudentGroupEntity.class));
    }

    @Test
    void addGroupsToStudent_pastYearGroup_isRejected() {
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
        when(groupRepository.findAllById(List.of(GROUP_ID))).thenReturn(List.of(pastYearGroup));

        StudentGroupDTO dto = new StudentGroupDTO();
        dto.setStudentId(STUDENT_ID);
        dto.setGroupIds(List.of(GROUP_ID));

        assertThatThrownBy(() -> service.addGroupsToStudent(dto))
                .isInstanceOf(ReadOnlySchoolYearException.class);

        verify(studentGroupRepository, never()).save(any(StudentGroupEntity.class));
    }

    @Test
    void removeStudentFromGroup_pastYearGroup_isRejected() {
        StudentGroupEntity enrollment = StudentGroupEntity.builder()
                .student(student)
                .group(pastYearGroup)
                .build();
        enrollment.setActive(true);
        when(studentGroupRepository.findByGroupIdAndStudentIdAndActiveTrue(GROUP_ID, STUDENT_ID))
                .thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> service.removeStudentFromGroup(GROUP_ID, STUDENT_ID))
                .isInstanceOf(ReadOnlySchoolYearException.class);

        // L'affectation reste active : l'historique de composition est préservé.
        verify(studentGroupRepository, never()).save(any(StudentGroupEntity.class));
    }
}
