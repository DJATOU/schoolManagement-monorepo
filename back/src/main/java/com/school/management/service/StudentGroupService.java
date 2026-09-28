package com.school.management.service;

import com.school.management.dto.GroupDTO;
import com.school.management.dto.StudentDTO;
import com.school.management.dto.StudentGroupDTO;
import com.school.management.mapper.GroupMapper;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.StudentGroupRepository;
import com.school.management.persistance.LevelEntity;
import com.school.management.repository.StudentRepository;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.exception.GroupAlreadyAssociatedException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StudentGroupService {

    private final StudentGroupRepository studentGroupRepository;
    private final StudentRepository studentRepository;
    private final GroupRepository groupRepository;

    private final GroupMapper groupMapper;

    /** Garde lecture seule des années passées (Exigence 9.2). */
    private final ReadOnlyYearGuard readOnlyYearGuard;

    @Autowired
    public StudentGroupService(StudentGroupRepository studentGroupRepository,
            StudentRepository studentRepository,
            GroupRepository groupRepository,
            GroupMapper groupMapper,
            ReadOnlyYearGuard readOnlyYearGuard) {
        this.studentGroupRepository = studentGroupRepository;
        this.studentRepository = studentRepository;
        this.groupRepository = groupRepository;
        this.groupMapper = groupMapper;
        this.readOnlyYearGuard = readOnlyYearGuard;
    }

    @Transactional
    public void manageStudentGroupAssociations(StudentGroupDTO studentGroupDto) {
        if (studentGroupDto.isAddingStudentToGroups()) {
            addGroupsToStudent(studentGroupDto);
        } else if (studentGroupDto.isAddingStudentsToGroup()) {
            addStudentsToGroup(studentGroupDto);
        } else {
            throw new IllegalArgumentException("Invalid student group association data");
        }
    }

    public void addGroupsToStudent(StudentGroupDTO studentGroupDto) {
        StudentEntity student = studentRepository.findById(Objects.requireNonNull(studentGroupDto.getStudentId()))
                .orElseThrow(() -> new EntityNotFoundException(
                        "Student not found with id: " + studentGroupDto.getStudentId()));

        List<GroupEntity> groups = groupRepository.findAllById(Objects.requireNonNull(studentGroupDto.getGroupIds()));

        if (groups.size() != studentGroupDto.getGroupIds().size()) {
            throw new EntityNotFoundException("One or more groups not found");
        }

        // Un groupe d'une année scolaire passée est figé : sa composition ne change plus
        // (Exigence 9.2). Le garde manquait ici, alors qu'il protégeait déjà le groupe
        // lui-même, ses séries et ses séances : on pouvait donc encore inscrire un étudiant
        // dans un groupe de l'année précédente.
        groups.forEach(readOnlyYearGuard::assertGroupMutable);

        // Validation : l'élève ne peut être inscrit que dans des groupes de son niveau courant.
        groups.forEach(group -> assertSameLevel(student, group));

        List<GroupEntity> alreadyAssociatedGroups = new ArrayList<>();
        groups.forEach(group -> {
            boolean exists = studentGroupRepository.existsByStudentAndGroupAndActiveTrue(student, group);
            if (!exists) {
                StudentGroupEntity studentGroup = StudentGroupEntity.builder()
                        .student(student)
                        .group(group)
                        .dateAssigned(studentGroupDto.getDateAssigned() != null ? studentGroupDto.getDateAssigned()
                                : new Date())
                        .createdBy(studentGroupDto.getAssignedBy())
                        .description(studentGroupDto.getDescription())
                        .build();
                studentGroupRepository.save(Objects.requireNonNull(studentGroup));
            } else {
                alreadyAssociatedGroups.add(group);
            }
        });

        if (!alreadyAssociatedGroups.isEmpty()) {
            List<String> alreadyAssociatedGroupNames = alreadyAssociatedGroups.stream()
                    .map(GroupEntity::getName)
                    .toList();
            throw new GroupAlreadyAssociatedException("Groups already associated with student",
                    alreadyAssociatedGroupNames);
        }
    }

    public void addStudentsToGroup(StudentGroupDTO studentGroupDto) {
        GroupEntity group = groupRepository.findById(Objects.requireNonNull(studentGroupDto.getGroupId()))
                .orElseThrow(
                        () -> new EntityNotFoundException("Group not found with id: " + studentGroupDto.getGroupId()));

        // Composition figée pour un groupe d'une année passée (Exigence 9.2).
        readOnlyYearGuard.assertGroupMutable(group);

        List<StudentEntity> students = studentRepository
                .findAllById(Objects.requireNonNull(studentGroupDto.getStudentIds()));
        if (students.size() != studentGroupDto.getStudentIds().size()) {
            throw new EntityNotFoundException("One or more students not found");
        }

        // Validation : chaque élève ne peut rejoindre un groupe que s'il est de son niveau courant.
        students.forEach(student -> assertSameLevel(student, group));

        Set<StudentEntity> existingStudents = group.getStudents();
        students.forEach(student -> {
            if (!existingStudents.contains(student)) {
                StudentGroupEntity studentGroup = StudentGroupEntity.builder()
                        .student(student)
                        .group(group)
                        .dateAssigned(studentGroupDto.getDateAssigned() != null ? studentGroupDto.getDateAssigned()
                                : new Date())
                        .createdBy(studentGroupDto.getAssignedBy())
                        .description(studentGroupDto.getDescription())
                        .build();

                studentGroupRepository.save(Objects.requireNonNull(studentGroup));
                existingStudents.add(student); // Ajout de l'étudiant aux étudiants existants du groupe
            }
        });

        group.setStudents(existingStudents);
        groupRepository.save(group);
    }

    /**
     * Vérifie que le niveau du groupe correspond au niveau courant de l'étudiant.
     *
     * <p>Un étudiant ne peut être inscrit (inscription régulière) que dans des groupes de son
     * propre niveau. Lève une {@link CustomServiceException} (HTTP 400) en cas de non-concordance.
     * Cette règle ne concerne pas le rattrapage, qui passe par un flux dédié (présence dans un
     * autre groupe) et n'utilise pas cette inscription.</p>
     *
     * @param student l'étudiant concerné
     * @param group   le groupe visé
     */
    private void assertSameLevel(StudentEntity student, GroupEntity group) {
        LevelEntity studentLevel = student.getLevel();
        LevelEntity groupLevel = group.getLevel();

        // Si l'un des niveaux n'est pas défini, on ne bloque pas (données incomplètes).
        if (studentLevel == null || groupLevel == null
                || studentLevel.getId() == null || groupLevel.getId() == null) {
            return;
        }

        if (!studentLevel.getId().equals(groupLevel.getId())) {
            String studentName = (student.getFirstName() != null ? student.getFirstName() : "")
                    + " " + (student.getLastName() != null ? student.getLastName() : "");
            throw new CustomServiceException(
                    "L'étudiant " + studentName.trim() + " (niveau " + studentLevel.getName()
                            + ") ne peut pas être inscrit au groupe « " + group.getName()
                            + " » de niveau " + groupLevel.getName()
                            + ". Un étudiant ne s'inscrit que dans des groupes de son niveau.",
                    HttpStatus.BAD_REQUEST);
        }
    }

    public List<StudentDTO> getStudentsByGroupId(Long groupId) {
        List<StudentGroupEntity> studentGroups = studentGroupRepository.findByGroupId(groupId);
        return studentGroups.stream()
                .map(sg -> StudentDTO.builder()
                        .id(sg.getStudent().getId())
                        .gender(sg.getStudent().getGender())
                        .lastName(sg.getStudent().getLastName())
                        .firstName(sg.getStudent().getFirstName())
                        .build())
                .collect(Collectors.toList());
    }

    // In `StudentGroupService.java`
    @Transactional
    public void removeStudentFromGroup(Long groupId, Long studentId) {
        StudentGroupEntity studentGroup = studentGroupRepository
                .findByGroupIdAndStudentIdAndActiveTrue(groupId, studentId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "StudentGroup not found for groupId " + groupId + " and studentId " + studentId));

        // Un groupe d'une année passée conserve ses étudiants : on ne peut plus en retirer
        // (Exigence 9.2). Sinon la composition historique aurait pu être vidée après coup,
        // faussant les présences et les soldes déjà enregistrés.
        readOnlyYearGuard.assertGroupMutable(studentGroup.getGroup());

        studentGroup.setActive(false);
        studentGroupRepository.save(studentGroup);
    }

    /**
     * Groupes de l'étudiant, filtrés sur une année scolaire lorsqu'elle est fournie.
     *
     * <p>Un {@code schoolYearId} nul renvoie tous les groupes, toutes années confondues : le
     * parcours en a besoin pour reconstituer l'historique. Les écrans qui suivent le sélecteur
     * d'année passent l'identifiant, sans quoi ils affichaient des groupes d'années révolues
     * absents de la liste des groupes.</p>
     *
     * @param studentId    identifiant de l'étudiant
     * @param schoolYearId année scolaire à filtrer, ou {@code null} pour toutes les années
     */
    @Transactional(readOnly = true)
    public List<GroupDTO> getGroupsOfStudent(Long studentId, Long schoolYearId) {
        return studentGroupRepository.findByStudentIdAndActiveTrue(studentId).stream()
                .map(StudentGroupEntity::getGroup)
                .filter(Objects::nonNull)
                .filter(group -> matchesSchoolYear(group, schoolYearId))
                .map(groupMapper::groupToGroupDTO)
                .toList();
    }

    /** Vrai si aucun filtre n'est demandé, ou si le groupe appartient à l'année demandée. */
    private boolean matchesSchoolYear(GroupEntity group, Long schoolYearId) {
        if (schoolYearId == null) {
            return true;
        }
        return group.getSchoolYear() != null
                && schoolYearId.equals(group.getSchoolYear().getId());
    }

    public List<StudentDTO> getStudentsForSession(Long groupId, Date sessionStartDate) {
        List<StudentGroupEntity> studentGroups = studentGroupRepository
                .findByGroupIdAndDateAssignedBefore(groupId, sessionStartDate);

        return studentGroups.stream()
                .map(sg -> StudentDTO.builder()
                        .id(sg.getStudent().getId())
                        .gender(sg.getStudent().getGender())
                        .lastName(sg.getStudent().getLastName())
                        .firstName(sg.getStudent().getFirstName())
                        .build())
                .collect(Collectors.toList());
    }

}