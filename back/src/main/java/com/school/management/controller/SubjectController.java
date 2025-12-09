package com.school.management.controller;

import com.school.management.persistance.SubjectEntity;
import com.school.management.service.SubjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subjects")
public class SubjectController {

    private final SubjectService subjectService;

    @Autowired
    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    @GetMapping
    public ResponseEntity<List<SubjectEntity>> getAllSubjects() {
        return ResponseEntity.ok(subjectService.getAllSubjects());
    }

    @PostMapping
    public ResponseEntity<SubjectEntity> createSubject(@RequestBody SubjectEntity subject) {
        return ResponseEntity.ok(subjectService.createSubject(subject));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SubjectEntity> updateSubject(@PathVariable Long id, @RequestBody SubjectEntity subject) {
        return ResponseEntity.ok(subjectService.updateSubject(id, subject));
    }

    //disable subjects
    @DeleteMapping("disable/{id_list}")
    public ResponseEntity<Boolean> disableSubjects(@PathVariable String id_list) {
        System.out.println("Request recieved: " + id_list);
        for (String id : id_list.split(",")) {
            subjectService.disableSubjects(Long.parseLong(id));
        }
        return ResponseEntity.ok(true);
    }

}
