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
import com.school.management.repository.StudentRepository;
import com.school.management.service.exception.GroupAlreadyAssociatedException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StudentGroupService {

    private final StudentGroupRepository studentGroupRepository;
    private final StudentRepository studentRepository;
    private final GroupRepository groupRepository;

    private final GroupMapper groupMapper;

    @Autowired
    public StudentGroupService(StudentGroupRepository studentGroupRepository,
                               StudentRepository studentRepository,
                               GroupRepository groupRepository,
                               GroupMapper groupMapper){
        this.studentGroupRepository = studentGroupRepository;
        this.studentRepository = studentRepository;
        this.groupRepository = groupRepository;
        this.groupMapper = groupMapper;
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
        StudentEntity student = studentRepository.findById(studentGroupDto.getStudentId())
                .orElseThrow(() -> new EntityNotFoundException("Student not found with id: " + studentGroupDto.getStudentId()));

        List<GroupEntity> groups = groupRepository.findAllById(studentGroupDto.getGroupIds());

        if (groups.size() != studentGroupDto.getGroupIds().size()) {
            throw new EntityNotFoundException("One or more groups not found");
        }

        List<GroupEntity> alreadyAssociatedGroups = new ArrayList<>();
        groups.forEach(group -> {
            boolean exists = studentGroupRepository.existsByStudentAndGroupAndActiveTrue(student, group);
            if (!exists) {
                StudentGroupEntity studentGroup = StudentGroupEntity.builder()
                        .student(student)
                        .group(group)
                        .dateAssigned(studentGroupDto.getDateAssigned() != null ? studentGroupDto.getDateAssigned() : new Date())
                        .createdBy(studentGroupDto.getAssignedBy())
                        .description(studentGroupDto.getDescription())
                        .build();
                studentGroupRepository.save(studentGroup);
            } else {
                alreadyAssociatedGroups.add(group);
            }
        });

        if (!alreadyAssociatedGroups.isEmpty()) {
            List<String> alreadyAssociatedGroupNames = alreadyAssociatedGroups.stream()
                    .map(GroupEntity::getName)
                    .toList();
            throw new GroupAlreadyAssociatedException("Groups already associated with student", alreadyAssociatedGroupNames);
        }
    }

    public void addStudentsToGroup(StudentGroupDTO studentGroupDto) {
        GroupEntity group = groupRepository.findById(studentGroupDto.getGroupId())
                .orElseThrow(() -> new EntityNotFoundException("Group not found with id: " + studentGroupDto.getGroupId()));

        List<StudentEntity> students = studentRepository.findAllById(studentGroupDto.getStudentIds());
        if (students.size() != studentGroupDto.getStudentIds().size()) {
            throw new EntityNotFoundException("One or more students not found");
        }

        Set<StudentEntity> existingStudents = group.getStudents();
        students.forEach(student -> {
            if (!existingStudents.contains(student)) {
                StudentGroupEntity studentGroup = StudentGroupEntity.builder()
                        .student(student)
                        .group(group)
                        .dateAssigned(studentGroupDto.getDateAssigned() != null ? studentGroupDto.getDateAssigned() : new Date())
                        .createdBy(studentGroupDto.getAssignedBy())
                        .description(studentGroupDto.getDescription())
                        .build();

                studentGroupRepository.save(studentGroup);
                existingStudents.add(student); // Ajout de l'étudiant aux étudiants existants du groupe
            }
        });

        group.setStudents(existingStudents);
        groupRepository.save(group);
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
        StudentGroupEntity studentGroup = studentGroupRepository.findByGroupIdAndStudentIdAndActiveTrue(groupId, studentId)
                .orElseThrow(() -> new EntityNotFoundException("StudentGroup not found for groupId " + groupId + " and studentId " + studentId));
        studentGroup.setActive(false);
        studentGroupRepository.save(studentGroup);
    }

    // In `StudentGroupService.java`
    @Transactional(readOnly = true)
    public List<GroupDTO> getGroupsOfStudent(Long studentId) {
        List<StudentGroupEntity> studentGroups = studentGroupRepository.findByStudentIdAndActiveTrue(studentId);
        return studentGroups.stream()
                .map(studentGroup -> groupMapper.groupToGroupDTO(studentGroup.getGroup()))
                .toList();
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