package com.school.management.controller;

import com.school.management.dto.GroupDTO;
import com.school.management.dto.SessionSeriesDto;
import com.school.management.dto.StudentDTO;
import com.school.management.mapper.GroupMapper;
import com.school.management.persistance.GroupEntity;
import com.school.management.service.group.GroupServiceImpl;
import com.school.management.shared.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private static final Logger LOGGER = LoggerFactory.getLogger(GroupController.class);

    private final GroupServiceImpl groupService;
    private final GroupMapper groupMapper;

    private List<GroupDTO> convertToGroupDTOList(List<GroupEntity> groups) {
        return groups.stream()
                .map(groupMapper::groupToGroupDTO)
                .toList();
    }

    @Autowired
    public GroupController(GroupServiceImpl groupService, GroupMapper groupMapper) {
        this.groupService = groupService;
        this.groupMapper = groupMapper;
    }

    @PostMapping("/createGroupe")
    public ResponseEntity<GroupDTO> createGroup(@Valid @ModelAttribute GroupDTO groupDto) {
        // PHASE 1 REFACTORING: Utilise MappingContext au lieu de
        // ApplicationContextProvider
        GroupEntity group = groupMapper.groupDTOToGroup(groupDto, groupService.getMappingContext());
        GroupEntity savedGroup = groupService.save(group);
        return new ResponseEntity<>(groupMapper.groupToGroupDTO(savedGroup), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<GroupDTO> getGroupById(@PathVariable Long id) {
        GroupEntity group = groupService.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group", id));
        return ResponseEntity.ok(groupMapper.groupToGroupDTO(group));
    }

    @GetMapping("/details/{idGroup}")
    public ResponseEntity<GroupDTO> getGroupDetailsById(@PathVariable Long idGroup) {
        GroupEntity group = groupService.getGroupWithDetails(idGroup);
        return ResponseEntity.ok(groupMapper.groupToGroupDTO(group));
    }

    @GetMapping
    public ResponseEntity<List<GroupDTO>> getAllGroups() {
        List<GroupDTO> groups = groupService.findAll().stream()
                .map(groupMapper::groupToGroupDTO)
                .toList();
        return ResponseEntity.ok(groups);
    }

    @PutMapping("/{id}")
    public ResponseEntity<GroupDTO> updateGroup(@PathVariable Long id, @Valid @RequestBody GroupDTO groupDto) {
        groupService.findById(id).orElseThrow(() -> new ResourceNotFoundException("Group", id));
        // PHASE 1 REFACTORING: Utilise MappingContext au lieu de
        // ApplicationContextProvider
        GroupEntity updatedGroup = groupMapper.groupDTOToGroup(groupDto, groupService.getMappingContext());
        updatedGroup.setId(id);
        GroupEntity savedGroup = groupService.save(updatedGroup);
        return ResponseEntity.ok(groupMapper.groupToGroupDTO(savedGroup));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long id) {
        groupService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // desactivate a group
    @DeleteMapping("disable/{id}")
    public ResponseEntity<Boolean> desactivateGroup(@PathVariable Long id) {
        groupService.desactivateGroup(id);
        return ResponseEntity.ok(true);
    }

    @GetMapping("/teacher/{id}")
    public ResponseEntity<List<GroupDTO>> getAllGroupsByTeacherId(@PathVariable Long id) {
        List<GroupDTO> groupDtos = convertToGroupDTOList(groupService.findByTeacherId(id));
        return ResponseEntity.ok(groupDtos);
    }

    @GetMapping("/student/{id}")
    public ResponseEntity<List<GroupDTO>> getAllGroupsByStudentId(@PathVariable Long id) {
        List<GroupDTO> groupDtos = convertToGroupDTOList(groupService.findByStudentId(id));
        return ResponseEntity.ok(groupDtos);
    }

    @GetMapping("/subject/{id}")
    public ResponseEntity<List<GroupDTO>> getAllGroupsBySubjectId(@PathVariable Long id) {
        List<GroupEntity> groups = groupService.findAll().stream()
                .filter(group -> group.getSubject().getId().equals(id))
                .toList();
        List<GroupDTO> groupDtos = groups.stream()
                .map(groupMapper::groupToGroupDTO)
                .toList();
        return ResponseEntity.ok(groupDtos);
    }

    @GetMapping("/level/{id}")
    public ResponseEntity<List<GroupDTO>> getAllGroupsByLevelId(@PathVariable Long id) {
        List<GroupEntity> groups = groupService.findAll().stream()
                .filter(group -> group.getLevel().getId().equals(id))
                .toList();
        List<GroupDTO> groupDtos = groups.stream()
                .map(groupMapper::groupToGroupDTO)
                .toList();
        return ResponseEntity.ok(groupDtos);
    }

    @GetMapping("/grouptype/{id}")
    public ResponseEntity<List<GroupDTO>> getAllGroupsByGroupTypeId(@PathVariable Long id) {
        List<GroupEntity> groups = groupService.findAll().stream()
                .filter(group -> group.getGroupType().getId().equals(id))
                .toList();
        List<GroupDTO> groupDtos = groups.stream()
                .map(groupMapper::groupToGroupDTO)
                .toList();
        return ResponseEntity.ok(groupDtos);
    }

    @GetMapping("/price/{id}")
    public ResponseEntity<List<GroupDTO>> getAllGroupsByPriceId(@PathVariable Long id) {
        List<GroupEntity> groups = groupService.findAll().stream()
                .filter(group -> group.getPrice().getId().equals(id))
                .toList();
        List<GroupDTO> groupDtos = groups.stream()
                .map(groupMapper::groupToGroupDTO)
                .toList();
        return ResponseEntity.ok(groupDtos);
    }

    @GetMapping("/searchByNames")
    @Transactional(readOnly = true)
    public ResponseEntity<List<GroupDTO>> getGroupsByName(@RequestParam(required = false) String search) {
        List<GroupDTO> groups = groupService.searchGroupsByNameStartingWithDTO(search);
        return ResponseEntity.ok(groups);
    }

    @GetMapping("/{groupId}/series")
    public ResponseEntity<List<SessionSeriesDto>> getSeriesByGroupId(@PathVariable Long groupId) {
        List<SessionSeriesDto> sessionSeries = groupService.getSeriesByGroupId(groupId);
        return ResponseEntity.ok(sessionSeries);
    }

    // In `GroupController.java`
    @GetMapping("/{groupId}/students")
    public ResponseEntity<List<StudentDTO>> getActiveStudentsByGroupId(@PathVariable Long groupId) {
        List<StudentDTO> students = groupService.getActiveStudentsByGroupId(groupId);
        return ResponseEntity.ok(students);
    }

    @GetMapping("/{groupId}/student-count")
    public ResponseEntity<Long> countStudentsInGroup(@PathVariable Long groupId) {
        Long studentCount = groupService.countStudentsInGroup(groupId);
        return ResponseEntity.ok(studentCount);
    }

    @GetMapping("/{studentId}/groups-for-payment")
    public ResponseEntity<List<GroupDTO>> getGroupsForPayment(@PathVariable Long studentId) {
        // Le service renvoie déjà la liste de DTO directement
        List<GroupDTO> groupDtos = groupService.getGroupsForPaymentDto(studentId);
        return ResponseEntity.ok(groupDtos);
    }

    /**
     * PHASE 3A: Upload photo pour un groupe
     * 
     * @param id   ID du groupe
     * @param file Fichier photo
     * @return Nom du fichier uploadé
     */
    @PostMapping("/{id}/photo")
    public ResponseEntity<String> uploadGroupPhoto(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        try {
            String filename = groupService.uploadPhoto(id, file);
            return ResponseEntity.ok(filename);
        } catch (IOException e) {
            LOGGER.error("Failed to upload photo for group {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(500).body("Failed to upload photo: " + e.getMessage());
        }
    }

    /**
     * PHASE 3A: Récupère la photo d'un groupe
     * 
     * @param id ID du groupe
     * @return Resource contenant la photo
     */
    @GetMapping("/{id}/photo")
    public ResponseEntity<Resource> getGroupPhoto(@PathVariable Long id) {
        try {
            Resource photo = groupService.getPhoto(id);
            return ResponseEntity.ok()
                    .contentType(java.util.Objects.requireNonNull(MediaType.IMAGE_JPEG))
                    .body(photo);
        } catch (IOException e) {
            LOGGER.error("Failed to get photo for group {}: {}", id, e.getMessage(), e);
            return ResponseEntity.notFound().build();
        }
    }

}
