package com.school.management.controller;

import com.school.management.dto.TutorDTO;
import com.school.management.mapper.TutorMapper;
import com.school.management.persistance.TutorEntity;
import com.school.management.service.TutorService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tutors")
public class TutorController {

    private final TutorService tutorService;
    private final TutorMapper tutorMapper;

    @Autowired
    public TutorController(TutorService tutorService, TutorMapper tutorMapper) {
        this.tutorService = tutorService;
        this.tutorMapper = tutorMapper;
    }

    @GetMapping
    public ResponseEntity<List<TutorDTO>> getAllTutors() {
        List<TutorDTO> tutors = tutorService.getAllTutors().stream()
                .map(tutorMapper::tutorToTutorDTO)
                .toList();
        return ResponseEntity.ok(tutors);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TutorDTO> getTutorById(@PathVariable Long id) {
        return ResponseEntity.ok(tutorMapper.tutorToTutorDTO(tutorService.getTutorById(id)));
    }

    /**
     * Crée un nouveau tuteur à partir d'un DTO et retourne le DTO sauvegardé
     * (avec son id généré), prêt à être attaché à un étudiant.
     */
    @PostMapping
    public ResponseEntity<TutorDTO> createTutor(@Valid @RequestBody TutorDTO tutorDto) {
        TutorEntity saved = tutorService.createTutor(tutorMapper.tutorDTOToTutor(tutorDto));
        return new ResponseEntity<>(tutorMapper.tutorToTutorDTO(saved), HttpStatus.CREATED);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTutor(@PathVariable Long id) {
        tutorService.deleteTutor(id);
        return ResponseEntity.noContent().build();
    }

}
