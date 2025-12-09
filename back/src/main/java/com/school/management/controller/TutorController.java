package com.school.management.controller;

import com.school.management.persistance.TutorEntity;
import com.school.management.service.TutorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tutors")
public class TutorController {

    private final TutorService tutorService;

    @Autowired
    public TutorController(TutorService tutorService) {
        this.tutorService = tutorService;
    }

    @GetMapping
    public ResponseEntity<List<TutorEntity>> getAllTutors() {
        return ResponseEntity.ok(tutorService.getAllTutors());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TutorEntity> getTutorById(@PathVariable Long id) {
        return ResponseEntity.ok(tutorService.getTutorById(id));
    }

    @PostMapping
    public ResponseEntity<TutorEntity> createTutor(@RequestBody TutorEntity tutor) {
        return ResponseEntity.ok(tutorService.createTutor(tutor));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TutorEntity> updateTutor(@PathVariable Long id) {
        return ResponseEntity.ok(tutorService.updateTutor(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTutor(@PathVariable Long id) {
        tutorService.deleteTutor(id);
        return ResponseEntity.noContent().build();
    }

}
