package com.school.management.controller;

import com.school.management.dto.TeacherDTO;
import com.school.management.infrastructure.storage.FileManagementService;
import com.school.management.mapper.TeacherMapper;
import com.school.management.persistance.TeacherEntity;
import com.school.management.service.TeacherService;
import com.school.management.service.exception.CustomServiceException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/teachers")
public class TeacherController {

    private static final Logger LOGGER = LoggerFactory.getLogger(TeacherController.class);

    @Value("${app.upload.dir}")
    private String uploadDir;
    private final TeacherService teacherService;
    private final TeacherMapper teacherMapper;
    private final FileManagementService fileManagementService;

    @Autowired
    public TeacherController(TeacherService teacherService, TeacherMapper teacherMapper,
            FileManagementService fileManagementService) {
        this.teacherService = teacherService;
        this.teacherMapper = teacherMapper;
        this.fileManagementService = fileManagementService;
    }

    @Transactional(readOnly = true)
    @GetMapping
    public ResponseEntity<List<TeacherDTO>> getAllTeachers() {
        List<TeacherDTO> teachers = teacherService.getAllTeachers().stream()
                .map(teacherMapper::teacherToTeacherDTO)
                .toList();
        return ResponseEntity.ok(teachers);
    }

    @Transactional(readOnly = true)
    @GetMapping("/id/{id}")
    public ResponseEntity<TeacherDTO> getTeacherById(@PathVariable Long id) {
        TeacherEntity teacher = teacherService.findById(id)
                .orElseThrow(() -> new CustomServiceException("Teacher not found with id " + id));
        return ResponseEntity.ok(teacherMapper.teacherToTeacherDTO(teacher));
    }

    @GetMapping("/lastname/{lastName}")
    public ResponseEntity<List<TeacherEntity>> getTeachersByLastName(@PathVariable String lastName) {
        return ResponseEntity.ok(teacherService.findByLastName(lastName));
    }

    @GetMapping("/fullname")
    public ResponseEntity<List<TeacherEntity>> getTeachersByFullName(@RequestParam String firstName,
            @RequestParam String lastName) {
        return ResponseEntity.ok(teacherService.findByFirstNameAndLastName(firstName, lastName));
    }

    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<TeacherEntity>> getTeachersByGroupId(@PathVariable Long groupId) {
        return ResponseEntity.ok(teacherService.findByGroupsId(groupId));
    }

    @PostMapping("/createTeacher")
    public ResponseEntity<?> createTeacher(@Valid @ModelAttribute TeacherDTO teacherDto,
            @RequestParam("file") MultipartFile file) {
        // PHASE 1 REFACTORING: Utilise FileManagementService au lieu de gérer les
        // fichiers directement
        // Upload du fichier avec rollback automatique en cas d'erreur
        FileManagementService.FileUploadResult uploadResult = fileManagementService.uploadWithRollback(file);

        if (!uploadResult.isSuccess()) {
            LOGGER.warn("File upload failed: {}", uploadResult.getErrorMessage());
            return ResponseEntity.badRequest().body(uploadResult.getErrorMessage());
        }

        try {
            // Stocker le nom du fichier dans le DTO
            teacherDto.setPhoto(uploadResult.getFilename());

            // Sauvegarder le professeur en base de données
            TeacherEntity teacher = teacherMapper.teacherDTOToTeacher(teacherDto);
            TeacherEntity savedTeacher = teacherService.save(teacher);

            LOGGER.info("Teacher created successfully with photo: {}", uploadResult.getFilename());
            return ResponseEntity.ok(teacherMapper.teacherToTeacherDTO(savedTeacher));

        } catch (Exception e) {
            // Le fichier sera automatiquement nettoyé par FileManagementService
            LOGGER.error("Could not save teacher", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Could not save teacher: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<TeacherDTO> updateTeacher(@PathVariable Long id, @RequestBody TeacherEntity teacher) {
        TeacherEntity updatedTeacher = teacherService.updateTeacher(id, teacher);
        return ResponseEntity.ok(teacherMapper.teacherToTeacherDTO(updatedTeacher));
    }

    @Transactional(readOnly = true)
    @GetMapping("/searchByNames")
    public ResponseEntity<List<TeacherDTO>> getTeachersByFirstNameAndOrLastName(
            @RequestParam(required = false) String search) {
        List<TeacherDTO> teachers = teacherService.searchTeachersByNameStartingWithDTO(search);
        return ResponseEntity.ok(teachers);
    }

    // desactivate a teacher
    @DeleteMapping("disable/{id}")
    public ResponseEntity<Boolean> desactivateTeacher(@PathVariable Long id) {
        teacherService.desactivateTeacher(id);
        return ResponseEntity.ok(true);
    }

    /**
     * PHASE 3A: Upload photo pour un enseignant
     * 
     * @param id   ID de l'enseignant
     * @param file Fichier photo
     * @return Nom du fichier uploadé
     */
    @PostMapping("/{id}/photo")
    public ResponseEntity<String> uploadTeacherPhoto(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        try {
            String filename = teacherService.uploadPhoto(id, file);
            return ResponseEntity.ok(filename);
        } catch (IOException e) {
            LOGGER.error("Failed to upload photo for teacher {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(500).body("Failed to upload photo: " + e.getMessage());
        }
    }

    /**
     * PHASE 3A: Récupère la photo d'un enseignant
     * 
     * @param id ID de l'enseignant
     * @return Resource contenant la photo
     */
    @GetMapping("/{id}/photo")
    public ResponseEntity<Resource> getTeacherPhoto(@PathVariable Long id) {
        try {
            Resource photo = teacherService.getPhoto(id);
            return ResponseEntity.ok()
                    .contentType(Objects.requireNonNull(MediaType.IMAGE_JPEG))
                    .body(photo);
        } catch (IOException e) {
            LOGGER.error("Failed to get photo for teacher {}: {}", id, e.getMessage(), e);
            return ResponseEntity.notFound().build();
        }
    }
}
