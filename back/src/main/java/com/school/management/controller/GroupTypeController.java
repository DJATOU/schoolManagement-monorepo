package com.school.management.controller;

import com.school.management.persistance.GroupTypeEntity;
import com.school.management.repository.GroupTypeRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/grouptypes")
public class GroupTypeController {

    private final GroupTypeRepository groupTypeRepository;

    @Autowired
    public GroupTypeController(GroupTypeRepository groupTypeRepository) {
        this.groupTypeRepository = groupTypeRepository;
    }

    // Get all group types
    @GetMapping
    public ResponseEntity<List<GroupTypeEntity>> getAllGroupTypes() {
        return ResponseEntity.ok(groupTypeRepository.findAll().stream().filter(GroupTypeEntity::isActive).toList());
    }

    // Get a single group type by ID
    @GetMapping("/{id}")
    public ResponseEntity<GroupTypeEntity> getGroupTypeById(@PathVariable Long id) {
        return groupTypeRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    // Create a new group type
    @PostMapping
    public ResponseEntity<GroupTypeEntity> createGroupType(@Valid @RequestBody GroupTypeEntity groupType) {
        GroupTypeEntity savedGroupType = groupTypeRepository.save(groupType);
        return new ResponseEntity<>(savedGroupType, HttpStatus.CREATED);
    }

    // Update an existing group type
    @PutMapping("/{id}")
    public ResponseEntity<GroupTypeEntity> updateGroupType(@PathVariable Long id, @Valid @RequestBody GroupTypeEntity groupTypeDetails) {
        return groupTypeRepository.findById(id)
                .map(groupType -> {
                    groupType.setName(groupTypeDetails.getName());
                    groupType.setSize(groupTypeDetails.getSize());
                    GroupTypeEntity updatedGroupType = groupTypeRepository.save(groupType);
                    return new ResponseEntity<>(updatedGroupType, HttpStatus.OK);
                })
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
/*
    // Delete a group type
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteGroupType(@PathVariable Long id) {
        return groupTypeRepository.findById(id)
                .map(groupType -> {
                    groupTypeRepository.delete(groupType);
                    return ResponseEntity.noContent().build(); // Returns ResponseEntity<HttpStatus>
                })
                .orElse(ResponseEntity.notFound().build()); // Also returns ResponseEntity<HttpStatus>
    }
*/
    @DeleteMapping("disable/{id_list}")
    public ResponseEntity<Boolean> disableGroupType(@PathVariable String id_list) {
        System.out.println("Request recieved: " + id_list);
        for (String id : id_list.split(",")) {
            groupTypeRepository.findById(Long.parseLong(id)).ifPresent(groupType -> {
                groupType.setActive(false);
                groupTypeRepository.save(groupType);
            });
        }
        return ResponseEntity.ok(true);
    }
}

