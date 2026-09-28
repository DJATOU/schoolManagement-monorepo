package com.school.management.controller;

import com.school.management.dto.GroupDTO;
import com.school.management.dto.ParcoursDTO;
import com.school.management.dto.StudentDTO;
import com.school.management.dto.student.StudentFullHistoryDTO;
import com.school.management.mapper.GroupMapper;
import com.school.management.mapper.StudentMapper;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.TutorEntity;
import com.school.management.infrastructure.storage.FileManagementService;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.group.StudentPayableGroupsService;
import com.school.management.service.student.StudentHistoryService;
import com.school.management.service.student.StudentParcoursService;
import com.school.management.service.student.StudentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/students")
public class StudentController {
    private static final Logger LOGGER = LoggerFactory.getLogger(StudentController.class);
    private static final String STUDENT_NOT_FOUND_MESSAGE = "Student not found with id: ";
    private final StudentService studentService;

    @Value("${app.upload.dir}")
    private String uploadDir;
    private final StudentMapper studentMapper;
    private final GroupMapper groupMapper;
    private final StudentHistoryService studentHistoryService;
    private final FileManagementService fileManagementService;
    private final StudentPayableGroupsService studentPayableGroupsService;
    private final StudentParcoursService studentParcoursService;

    @Autowired
    public StudentController(StudentService studentService, StudentMapper studentMapper, GroupMapper groupMapper,
            StudentHistoryService studentHistoryService, FileManagementService fileManagementService,
            StudentPayableGroupsService studentPayableGroupsService,
            StudentParcoursService studentParcoursService) {
        this.studentService = studentService;
        this.studentMapper = studentMapper;
        this.groupMapper = groupMapper;
        this.studentHistoryService = studentHistoryService;
        this.fileManagementService = fileManagementService;
        this.studentPayableGroupsService = studentPayableGroupsService;
        this.studentParcoursService = studentParcoursService;
    }

    @PostMapping("/createStudent")
    public ResponseEntity<Object> createStudent(
            @Valid @ModelAttribute StudentDTO studentDto,
            @RequestParam(value = "file", required = false) MultipartFile file) { // ← MODIFIÉ : required = false

        // Vérifier si un fichier est fourni
        if (file != null && !file.isEmpty()) {
            // PHASE 1 REFACTORING: Utilise FileManagementService au lieu de gérer les
            // fichiers directement
            // Upload du fichier avec rollback automatique en cas d'erreur
            FileManagementService.FileUploadResult uploadResult = fileManagementService.uploadWithRollback(file);

            if (!uploadResult.isSuccess()) {
                LOGGER.warn("File upload failed: {}", uploadResult.getErrorMessage());
                return ResponseEntity.badRequest().body(uploadResult.getErrorMessage());
            }

            // Stocker le nom du fichier dans le DTO
            studentDto.setPhoto(uploadResult.getFilename());
            LOGGER.info("Photo uploaded: {}", uploadResult.getFilename());
        } else {
            // Pas de photo fournie
            studentDto.setPhoto(null);
            LOGGER.info("No photo provided for student");
        }

        try {
            // Sauvegarder l'étudiant en base de données avec MappingContext
            StudentEntity student = studentMapper.studentDTOToStudent(studentDto, studentService.getMappingContext());
            StudentEntity savedStudent = studentService.save(student);

            LOGGER.info("Student created successfully with ID: {}", savedStudent.getId());
            return ResponseEntity.ok(studentMapper.studentToStudentDTO(savedStudent));

        } catch (Exception e) {
            // Le fichier sera automatiquement nettoyé par FileManagementService
            LOGGER.error("Could not save student", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Could not save student: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<StudentDTO> updateStudent(@PathVariable Long id, @Valid @RequestBody StudentDTO studentDto) {
        // Récupérer l'étudiant existant depuis la base de données
        StudentEntity existingStudent = studentService.findById(id)
                .orElseThrow(() -> new CustomServiceException("Student not found with id: " + id));

        // Mettre à jour l'entité existante avec les valeurs du DTO
        studentMapper.updateStudentFromDTO(studentDto, existingStudent, studentService.getMappingContext());

        // Sauvegarder l'entité mise à jour
        StudentEntity updatedStudent = studentService.save(existingStudent);

        // Retourner le DTO mis à jour
        return ResponseEntity.ok(studentMapper.studentToStudentDTO(updatedStudent));
    }

    /**
     * Réactive un étudiant précédemment marqué comme parti (exigence 7.5) : repasse son statut
     * à ACTIVE. L'historique (inscriptions, paiements, présences) est conservé.
     *
     * @param id l'identifiant de l'étudiant à réactiver
     * @return l'étudiant réactivé
     */
    /** Corps de requête de rattachement / détachement du tuteur. */
    public record TutorLinkRequest(Long tutorId) {
    }

    /**
     * Rattache un tuteur à l'étudiant, ou le détache lorsque {@code tutorId} est nul.
     *
     * <p>Point d'entrée dédié : la mise à jour générale ignore les valeurs nulles, elle ne
     * permet donc pas de détacher un tuteur.</p>
     *
     * @param id      identifiant de l'étudiant
     * @param request identifiant du tuteur, ou {@code null} pour détacher
     * @return l'étudiant mis à jour
     */
    @PatchMapping("/{id}/tutor")
    public ResponseEntity<StudentDTO> setStudentTutor(@PathVariable Long id,
                                                     @RequestBody TutorLinkRequest request) {
        StudentEntity updated = studentService.setTutor(id, request.tutorId());
        return ResponseEntity.ok(studentMapper.studentToStudentDTO(updated));
    }

    @PatchMapping("/{id}/reactivate")
    public ResponseEntity<StudentDTO> reactivateStudent(@PathVariable Long id) {
        StudentEntity student = studentService.reactivateStudent(id);
        return ResponseEntity.ok(studentMapper.studentToStudentDTO(student));
    }

    /**
     * Marque un étudiant comme parti / archivé (exigence 7.1) : passe son statut à INACTIVE.
     * L'étudiant est exclu des listes courantes par défaut mais son historique est préservé.
     *
     * @param id l'identifiant de l'étudiant à archiver
     * @return l'étudiant archivé
     */
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<StudentDTO> deactivateStudent(@PathVariable Long id) {
        StudentEntity student = studentService.deactivateStudentStatus(id);
        return ResponseEntity.ok(studentMapper.studentToStudentDTO(student));
    }

    /**
     * Liste les étudiants filtrés selon leur statut d'inscription.
     *
     * <p>Par défaut ({@code includeInactive = false}), seuls les étudiants actifs
     * de l'année courante sont retournés ; les étudiants partis (INACTIVE) sont
     * exclus (exigence 7.3). Lorsque {@code includeInactive = true}, la liste
     * inclut également les étudiants inactifs (exigence 7.4).</p>
     *
     * <p>Le paramètre optionnel {@code schoolYearId} situe la liste dans une année scolaire :
     * pour l'année courante (ou en son absence) le filtrage se fait par statut ; pour une année
     * passée, on renvoie les étudiants inscrits dans les groupes de cette année (historique
     * figé, exigence 9.x / 4.x).</p>
     *
     * @param includeInactive {@code true} pour inclure les étudiants inactifs
     * @param schoolYearId    année scolaire sélectionnée (optionnel)
     * @return la liste des étudiants correspondant à l'année et au filtre de statut
     */
    @Transactional(readOnly = true)
    @GetMapping
    public ResponseEntity<List<StudentDTO>> getAllStudents(
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive,
            @RequestParam(required = false) Long schoolYearId) {
        List<StudentDTO> students = studentService.findStudentsBySchoolYear(schoolYearId, includeInactive).stream()
                .map(studentMapper::studentToStudentDTO)
                .toList();
        return ResponseEntity.ok(students);
    }

    @Transactional(readOnly = true)
    @GetMapping("/search")
    public ResponseEntity<List<StudentDTO>> searchStudents(
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) Long level,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) String establishment) {

        List<StudentEntity> students = studentService.searchStudents(firstName, lastName, level, groupId,
                establishment);
        List<StudentDTO> studentDTOs = students.stream()
                .map(studentMapper::studentToStudentDTO)
                .toList();
        return ResponseEntity.ok(studentDTOs);
    }

    @GetMapping("id/{id}")
    public ResponseEntity<StudentDTO> getStudentById(@PathVariable Long id) {
        StudentEntity student = studentService.findById(id)
                .orElseThrow(() -> new CustomServiceException(STUDENT_NOT_FOUND_MESSAGE + id));
        StudentDTO studentDto = studentMapper.studentToStudentDTO(student);

        return ResponseEntity.ok(studentDto);
    }

    @GetMapping("/groups/{groupId}")
    public ResponseEntity<List<StudentDTO>> getStudentsByGroupId(@PathVariable Long groupId) {
        List<StudentDTO> students = studentService.findByGroupsId(groupId).stream()
                .map(studentMapper::studentToStudentDTO)
                .toList();
        return ResponseEntity.ok(students);
    }

    @GetMapping("/levels/{level}")
    public ResponseEntity<List<StudentDTO>> getStudentsByLevel(@PathVariable long level) {
        List<StudentDTO> students = studentService.findByLevel(level).stream()
                .filter(student -> Boolean.TRUE.equals(student.getActive())) // Filtrer les étudiants actifs
                .map(studentMapper::studentToStudentDTO)
                .toList();
        return ResponseEntity.ok(students);
    }

    @GetMapping("/establishments/{establishment}")
    public ResponseEntity<List<StudentDTO>> getStudentsByEstablishment(@PathVariable String establishment) {
        List<StudentDTO> students = studentService.findByEstablishment(establishment).stream()
                .map(studentMapper::studentToStudentDTO)
                .toList();
        return ResponseEntity.ok(students);
    }

    @Transactional(readOnly = true)
    @GetMapping("/firstnames/{firstName}/lastnames/{lastName}")
    public ResponseEntity<List<StudentDTO>> getStudentsByFirstNameAndLastName(@PathVariable String firstName,
            @PathVariable String lastName) {
        List<StudentDTO> students = studentService.findByFirstNameAndLastName(firstName, lastName).stream()
                .map(studentMapper::studentToStudentDTO)
                .toList();
        return ResponseEntity.ok(students);
    }

    @Transactional(readOnly = true)
    @GetMapping("/searchByNames")
    public ResponseEntity<List<StudentDTO>> getStudentsByFirstNameAndOrLastName(
            @RequestParam(required = false) String search) {
        List<StudentDTO> students = studentService.searchStudentsByNameStartingWithDTO(search);
        return ResponseEntity.ok(students);
    }

    @Transactional(readOnly = true)
    @GetMapping("/lastnames/{lastName}")
    public ResponseEntity<List<StudentDTO>> getStudentsByLastName(@PathVariable String lastName) {
        List<StudentDTO> students = studentService.findByLastName(lastName).stream()
                .map(studentMapper::studentToStudentDTO)
                .toList();
        return ResponseEntity.ok(students);
    }

    @GetMapping("/{id}/tutor")
    public ResponseEntity<TutorEntity> getTutorForStudent(@PathVariable Long id) {
        StudentEntity student = studentService.findById(id)
                .orElseThrow(() -> new CustomServiceException(STUDENT_NOT_FOUND_MESSAGE + id));
        TutorEntity tutor = student.getTutor();
        return ResponseEntity.ok(tutor);
    }

    // get groups for student
    @GetMapping("/{id}/groups")
    public ResponseEntity<Set<GroupDTO>> getGroupsForStudent(@PathVariable Long id) {
        StudentEntity student = studentService.findById(id)
                .orElseThrow(() -> new CustomServiceException("Student not found with id " + id));
        Set<GroupDTO> groupDTOs = student.getGroups().stream()
                .map(groupMapper::groupToGroupDTO)
                .collect(Collectors.toSet());
        return ResponseEntity.ok(groupDTOs);
    }

    // desactivate user
    @DeleteMapping("/disable/{id}")
    public ResponseEntity<Boolean> desactivateStudent(@PathVariable Long id) {
        studentService.desactivateStudent(id);
        return ResponseEntity.ok(true);
    }

    @GetMapping("/photos/{fileName}")
    public ResponseEntity<Resource> getPhoto(@PathVariable String fileName) {
        // Validation Path Traversal - Sécurité critique
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path filePath = uploadPath.resolve(fileName).normalize();

            // Vérifier que le fichier résolu est bien dans le répertoire autorisé
            if (!filePath.startsWith(uploadPath)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            Resource resource = new UrlResource(Objects.requireNonNull(filePath.toUri()));

            if (resource.exists() && resource.isReadable()) {
                MediaType mediaType = getMediaTypeForFileName(fileName);
                return ResponseEntity.ok()
                        .contentType(Objects.requireNonNull(mediaType))
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private MediaType getMediaTypeForFileName(String filename) {
        String extension = getFileExtension(filename).toLowerCase();
        return switch (extension) {
            case "png" -> MediaType.IMAGE_PNG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "webp" -> MediaType.valueOf("image/webp");
            case "svg" -> MediaType.valueOf("image/svg+xml");
            default -> MediaType.IMAGE_JPEG;
        };
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1) : "";
    }

    @GetMapping("/{studentId}/full-history")
    public ResponseEntity<StudentFullHistoryDTO> getStudentFullHistory(@PathVariable Long studentId) {
        StudentFullHistoryDTO fullHistory = studentHistoryService.getStudentFullHistory(studentId);
        return ResponseEntity.ok(fullHistory);
    }

    /**
     * PHASE 3A: Upload photo pour un étudiant
     * 
     * @param id   ID de l'étudiant
     * @param file Fichier photo
     * @return Nom du fichier uploadé
     */
    @PostMapping("/{id}/photo")
    public ResponseEntity<String> uploadStudentPhoto(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        try {
            StudentEntity student = studentService.findById(id)
                    .orElseThrow(() -> new CustomServiceException(STUDENT_NOT_FOUND_MESSAGE + id));

            // Supprimer l'ancienne photo si elle existe
            if (student.getPhoto() != null && !student.getPhoto().isEmpty()) {
                try {
                    fileManagementService.deleteFile(student.getPhoto());
                    LOGGER.debug("Deleted old photo: {}", student.getPhoto());
                } catch (Exception e) {
                    LOGGER.warn("Failed to delete old photo: {}", student.getPhoto(), e);
                }
            }

            // Upload la nouvelle photo avec rollback automatique
            FileManagementService.FileUploadResult result = fileManagementService.uploadWithRollback(file);

            if (!result.isSuccess()) {
                return ResponseEntity.status(500).body("Photo upload failed: " + result.getErrorMessage());
            }

            // Mettre à jour l'entité avec le nom du fichier
            student.setPhoto(result.getFilename());
            studentService.save(student);

            LOGGER.info("Photo uploaded successfully for student ID {}: {}", id, result.getFilename());
            return ResponseEntity.ok(result.getFilename());
        } catch (Exception e) {
            LOGGER.error("Failed to upload photo for student {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(500).body("Failed to upload photo: " + e.getMessage());
        }
    }

    /**
     * PHASE 3A: Récupère la photo d'un étudiant
     * 
     * @param id ID de l'étudiant
     * @return Resource contenant la photo
     */
    @GetMapping("/{id}/photo")
    public ResponseEntity<Resource> getStudentPhoto(@PathVariable Long id) {
        try {
            StudentEntity student = studentService.findById(id)
                    .orElseThrow(() -> new CustomServiceException(STUDENT_NOT_FOUND_MESSAGE + id));

            if (student.getPhoto() == null || student.getPhoto().isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Resource photo = fileManagementService.getFile(student.getPhoto());
            return ResponseEntity.ok()
                    .contentType(Objects.requireNonNull(MediaType.IMAGE_JPEG))
                    .body(photo);
        } catch (Exception e) {
            LOGGER.error("Failed to get photo for student {}: {}", id, e.getMessage(), e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Récupère les groupes "payables" pour un étudiant.
     *
     * LOGIQUE :
     * - Groupes FIXES (student_groups avec active=true) : TOUJOURS retournés
     * - Groupes RATTRAPAGE (attendances sans student_groups) :
     * Retournés UNIQUEMENT si au moins une attendance.active = true
     *
     * UTILISATION :
     * Appelé par le frontend lors de l'ouverture du dialogue de paiement
     * pour afficher uniquement les groupes pour lesquels l'étudiant peut payer.
     *
     * @param studentId l'ID de l'étudiant
     * @return la liste des groupes payables
     */
    @GetMapping("/{studentId}/payable-groups")
    public ResponseEntity<List<GroupDTO>> getPayableGroups(@PathVariable Long studentId) {
        LOGGER.info("Fetching payable groups for student: {}", studentId);

        List<GroupEntity> groups = studentPayableGroupsService.getPayableGroupsForStudent(studentId);
        List<GroupDTO> groupDTOs = groups.stream()
                .map(groupMapper::groupToGroupDTO)
                .toList();

        LOGGER.info("Found {} payable groups for student {}", groupDTOs.size(), studentId);
        return ResponseEntity.ok(groupDTOs);
    }

    /**
     * Récupère le parcours d'un étudiant : pour chaque année scolaire fréquentée,
     * les niveaux distincts et les groupes suivis (exigence 11.5).
     *
     * @param id l'ID de l'étudiant
     * @return le parcours de l'étudiant
     */
    @GetMapping("/{id}/parcours")
    public ResponseEntity<ParcoursDTO> getStudentParcours(@PathVariable Long id) {
        ParcoursDTO parcours = studentParcoursService.getParcours(id);
        return ResponseEntity.ok(parcours);
    }

}