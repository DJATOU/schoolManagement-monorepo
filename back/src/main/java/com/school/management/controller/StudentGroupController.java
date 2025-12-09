package com.school.management.controller;

import com.school.management.dto.GroupDTO;
import com.school.management.dto.StudentDTO;
import com.school.management.dto.StudentGroupDTO;
import com.school.management.service.StudentGroupService;
import com.school.management.service.exception.GroupAlreadyAssociatedException;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student-groups")
public class StudentGroupController {

    private static final Logger logger = LoggerFactory.getLogger(StudentGroupController.class);
    private static final String ERROR_MESSAGE = "error";
    private static final String MESSAGE = "message";

    private final StudentGroupService studentGroupService;

    @Autowired
    public StudentGroupController(StudentGroupService studentGroupService) {
        this.studentGroupService = studentGroupService;
    }

    @PostMapping("/{studentId}/addGroups")
    public ResponseEntity<Map<String, Object>> addGroupsToStudent(@PathVariable Long studentId,
                                                                  @RequestBody StudentGroupDTO studentGroupDto) {
        studentGroupDto.setStudentId(studentId);
        return handleGroupAssociation(() -> studentGroupService.manageStudentGroupAssociations(studentGroupDto));
    }

    @PostMapping("/{groupId}/addStudents")
    public ResponseEntity<Map<String, Object>> addStudentsToGroup(@PathVariable Long groupId,
                                                                  @RequestBody StudentGroupDTO studentGroupDto) {
        studentGroupDto.setGroupId(groupId);
        return handleGroupAssociation(() -> studentGroupService.manageStudentGroupAssociations(studentGroupDto));
    }

    @GetMapping("/{groupId}/students")
    public ResponseEntity<List<StudentDTO>> getStudentsOfGroup(@PathVariable Long groupId) {
        List<StudentDTO> students = studentGroupService.getStudentsByGroupId(groupId);
        return ResponseEntity.ok(students);
    }

    @GetMapping("/{groupId}/studentsForSession")
    public ResponseEntity<List<StudentDTO>> getStudentsForSession(
            @PathVariable Long groupId,
            @RequestParam("date") @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Date sessionDate) {

        List<StudentDTO> students = studentGroupService.getStudentsForSession(groupId, sessionDate);
        return ResponseEntity.ok(students);
    }


    @GetMapping("/{studentId}/groups")
    public ResponseEntity<List<GroupDTO>> getGroupsOfStudent(@PathVariable Long studentId) {
        List<GroupDTO> groups = studentGroupService.getGroupsOfStudent(studentId);
        return ResponseEntity.ok(groups);
    }

    private ResponseEntity<Map<String, Object>> handleGroupAssociation(Runnable associationTask) {
        try {
            associationTask.run();
            Map<String, Object> response = new HashMap<>();
            response.put(MESSAGE, "Operation completed successfully");
            return ResponseEntity.ok(response);
        } catch (GroupAlreadyAssociatedException e) {
            logger.error("GroupAlreadyAssociatedException: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put(MESSAGE, "Some entities were already associated");
            response.put("alreadyAssociatedEntities", e.getGroupNames());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        } catch (EntityNotFoundException e) {
            logger.error("EntityNotFoundException: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put(ERROR_MESSAGE, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } catch (Exception e) {
            logger.error("Exception: {}", e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put(ERROR_MESSAGE, "Error during operation: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @DeleteMapping("/{groupId}/students/{studentId}")
    public ResponseEntity<Map<String, Object>> removeStudentFromGroup(@PathVariable Long groupId, @PathVariable Long studentId) {
        logger.info("Received request to remove student {} from group {}", studentId, groupId);
        studentGroupService.removeStudentFromGroup(groupId, studentId);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Student removed from group successfully");
        return ResponseEntity.ok(response);
    }

}